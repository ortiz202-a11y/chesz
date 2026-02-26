#!/usr/bin/env python3
import re, shutil
from pathlib import Path

ROOT = Path.home() / "chesz"
F = ROOT / "app/src/main/java/com/chesz/floating/BubbleService.kt"

if not F.exists():
    raise SystemExit(f"NO existe: {F}")

src = F.read_text(encoding="utf-8")
bak = F.with_suffix(F.suffix + ".bak_pre_hover_state")
shutil.copy2(F, bak)

# 1) insertar estado killHovered si no existe
if not re.search(r"(?m)^\s*private\s+var\s+killHovered\s*=\s*false\s*$", src):
    m = re.search(r"(?m)^(\s*private\s+var\s+killShown\s*=\s*false\s*)\s*$", src)
    if not m:
        raise SystemExit("NO encontré: private var killShown = false")
    indent = re.match(r"^(\s*)private", m.group(0)).group(1)
    ins = m.group(0) + "\n" + f"{indent}private var killHovered = false"
    src = src[:m.start()] + ins + src[m.end():]

# 2) en showKill(false): resetear killHovered antes de remover
if "killHovered = false" not in src:
    m = re.search(r"(?ms)^\s*private\s+fun\s+showKill\s*\(\s*show\s*:\s*Boolean\s*\)\s*\{.*?\n\s*\}", src)
    if not m:
        raise SystemExit("NO encontré bloque: showKill(show:Boolean)")
    block = m.group(0)

    # buscamos el else y metemos killHovered=false después del guard
    # patrón esperado:
    # else {
    #   if (!killShown) return
    #   ...
    # }
    block2 = re.sub(
        r"(?ms)(else\s*\{\s*\n\s*if\s*\(\s*!killShown\s*\)\s*return\s*\n)",
        r"\1    killHovered = false\n",
        block
    )
    if block2 == block:
        raise SystemExit("No pude insertar killHovered=false en showKill(false) (patrón no coincide).")
    src = src[:m.start()] + block2 + src[m.end():]

# 3) ACTION_MOVE: llamar setKillHover SOLO si cambia el estado
# reemplaza:
#   val over = isOverKillCenter(...)
#   setKillHover(over)
# por:
#   val over = ...
#   if (over != killHovered) { killHovered=over; setKillHover(over) }
src2 = re.sub(
    r"(?m)^\s*val\s+over\s*=\s*isOverKillCenter\([^\n]*\)\s*\n\s*setKillHover\(over\)\s*$",
    "            val over = isOverKillCenter(bubbleCenterX(), bubbleCenterY())\n"
    "            if (over != killHovered) {\n"
    "              killHovered = over\n"
    "              setKillHover(over)\n"
    "            }",
    src
)
if src2 == src:
    raise SystemExit("NO pude parchear ACTION_MOVE: no encontré el patrón 'val over' + 'setKillHover(over)'.")

src = src2

# sanity llaves
if src.count("{") != src.count("}"):
    shutil.copy2(bak, F)
    raise SystemExit(f"ABORTADO: llaves desbalanceadas. Revertí backup: {bak}")

F.write_text(src, encoding="utf-8")
print("✅ Patch OK:", F)
print("🧷 Backup:", bak)
