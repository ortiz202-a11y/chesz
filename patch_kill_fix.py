from pathlib import Path
import re

p = Path("app/src/main/java/com/chesz/floating/BubbleService.kt")
s = p.read_text(encoding="utf-8")

orig = s

# 1) Arreglar bubbleCenterX/Y para evitar width=0
def repl_center(match):
    name = match.group(1)
    axis = "x" if name.endswith("X") else "y"
    # bubbleLp.x / bubbleLp.y
    lp = f"bubbleLp.{axis}"
    # bubbleRoot.width / bubbleRoot.height
    wh = "bubbleRoot.width" if axis == "x" else "bubbleRoot.height"
    return (
        f'  private fun {name}(): Float {{\n'
        f'    val w = if ({wh} > 0) {wh} else dp(60)\n'
        f'    return {lp} + w / 2f\n'
        f'  }}'
    )

# Reemplaza funciones de 1 línea tipo:
# private fun bubbleCenterX(): Float = ...
s = re.sub(
    r'^\s*private fun (bubbleCenter[XY])\(\): Float\s*=\s*.*$',
    repl_center,
    s,
    flags=re.M
)

# 2) Arreglar ACTION_UP/CANCEL: si shouldKill -> performKill() sin esconder antes.
# Buscamos el bloque actual que hace:
# val shouldKill...
# showKill(false)
# if (shouldKill) { stopSelf() } else { ... }
pattern = re.compile(
    r'(MotionEvent\.ACTION_UP,\s*MotionEvent\.ACTION_CANCEL\s*->\s*\{\s*)'
    r'(.*?)'
    r'(\s*\}\s*)',
    re.S
)

m = pattern.search(s)
if not m:
    raise SystemExit("No encontré el bloque ACTION_UP/ACTION_CANCEL en setOnTouchListener.")

block = m.group(2)

# Validamos que exista shouldKill
if "shouldKill" not in block:
    raise SystemExit("El bloque ACTION_UP no contiene shouldKill; no parcheo a ciegas.")

# Reescribimos SOLO si encontramos la forma actual (showKill(false) antes + stopSelf())
# Hacemos un reemplazo robusto:
block2 = block

# A) Reemplaza la lógica de cierre
#  - Quitamos 'showKill(false)' antes del if
#  - En kill: performKill()
#  - En no-kill: showKill(false)
block2 = re.sub(
    r'val\s+shouldKill\s*=\s*dragging\s*&&\s*isOverKillCenter\([^\)]*\)\s*\)\s*'
    r'\s*showKill\(false\)\s*'
    r'\s*if\s*\(\s*shouldKill\s*\)\s*\{\s*stopSelf\(\)\s*\}\s*else\s*\{\s*([^\}]*)\}',
    lambda mm: (
        "val shouldKill = dragging && isOverKillCenter(bubbleCenterX(), bubbleCenterY())\n"
        "if (shouldKill) {\n"
        "  performKill()\n"
        "} else {\n"
        "  showKill(false)\n"
        f"  {mm.group(1).strip()}\n"
        "}\n"
    ),
    block2,
    flags=re.S
)

# Si no pegó (porque el bloque cambió), aplicamos una versión más simple:
if block2 == block:
    # 1) si hay showKill(false) antes del if, lo movemos a else
    block2 = re.sub(r'^\s*showKill\(false\)\s*$', '', block2, flags=re.M)
    block2 = re.sub(
        r'if\s*\(\s*shouldKill\s*\)\s*\{\s*stopSelf\(\)\s*\}\s*else\s*\{',
        'if (shouldKill) {\n  performKill()\n} else {\n  showKill(false)\n',
        block2
    )

# Reconstruye el archivo
s = s[:m.start(2)] + block2 + s[m.end(2):]

if s == orig:
    raise SystemExit("No se aplicó ningún cambio (archivo igual).")

bak = p.with_suffix(p.suffix + ".bak_killfix")
bak.write_text(orig, encoding="utf-8")
p.write_text(s, encoding="utf-8")

print("✅ Patch aplicado:", p)
print("🧷 Backup:", bak)
