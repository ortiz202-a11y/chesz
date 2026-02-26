from pathlib import Path
import re
import shutil
import sys

F = Path("app/src/main/java/com/chesz/floating/BubbleService.kt")
if not F.exists():
    print("NO EXISTE:", F)
    sys.exit(1)

src = F.read_text(encoding="utf-8")

bak = F.with_suffix(F.suffix + ".bak_drag_standard")
shutil.copy2(F, bak)
print("🧷 Backup:", bak)

# 1) Eliminar imports de vibración si existen
src = re.sub(r'^\s*import\s+android\.os\.VibrationEffect\s*\n', '', src, flags=re.M)
src = re.sub(r'^\s*import\s+android\.os\.Vibrator\s*\n', '', src, flags=re.M)
src = re.sub(r'^\s*import\s+androidx\.core\.content\.getSystemService\s*\n', '', src, flags=re.M)

# 2) Eliminar función vibrateKill() completa si existe
src = re.sub(
    r'\n\s*private\s+fun\s+vibrateKill\(\)\s*\{.*?\n\s*\}\s*\n',
    '\n',
    src,
    flags=re.S
)

# 3) Dentro del touch listener:
#    - QUITAR showKill(true) del ACTION_DOWN
src = src.replace("            showKill(true)\n", "")

# 4) Cambiar ACTION_MOVE para que:
#    - Detecte inicio de drag (umbral)
#    - Si empieza drag -> showKill(true)
#    - Hover mientras drag
move_pattern = r"""
MotionEvent\.ACTION_MOVE\s*->\s*\{
\s*val\s+dx\s*=\s*\(e\.rawX\s*-\s*downRawX\)\.toInt\(\)\s*
\s*val\s+dy\s*=\s*\(e\.rawY\s*-\s*downRawY\)\.toInt\(\)\s*
\s*if\s*\(!dragging\s*&&\s*\(kotlin\.math\.abs\(dx\)\s*\+\s*kotlin\.math\.abs\(dy\)\s*>\s*dp\(6\)\)\)\s*dragging\s*=\s*true\s*
\s*bubbleLp\.x\s*=\s*startX\s*\+\s*dx\s*
\s*bubbleLp\.y\s*=\s*startY\s*\+\s*dy\s*
\s*wm\.updateViewLayout\(bubbleRoot,\s*bubbleLp\)\s*
\s*//\s*feedback\s*visual\s*si\s*está\s*“encima”\s*del\s*kill\s*
\s*val\s+over\s*=\s*isOverKillCenter\(bubbleCenterX\(\),\s*bubbleCenterY\(\)\)\s*
\s*setKillHover\(over\)\s*
\s*true\s*
\}
"""

move_replacement = """MotionEvent.ACTION_MOVE -> {
            val dx = (e.rawX - downRawX).toInt()
            val dy = (e.rawY - downRawY).toInt()

            // Inicio de drag (estándar): solo cuando supera umbral
            if (!dragging && (kotlin.math.abs(dx) + kotlin.math.abs(dy) > dp(6))) {
              dragging = true
              showKill(true) // el círculo rojo SOLO aparece durante drag real
            }

            bubbleLp.x = startX + dx
            bubbleLp.y = startY + dy
            wm.updateViewLayout(bubbleRoot, bubbleLp)

            // Hover mientras arrastras (agranda si está encima)
            if (dragging) {
              val over = isOverKillCenter(bubbleCenterX(), bubbleCenterY())
              setKillHover(over)
            }
            true
          }"""

new_src = re.sub(move_pattern, move_replacement, src, flags=re.X)
if new_src == src:
    print("❌ No pude matchear el bloque ACTION_MOVE. No apliqué patch.")
    sys.exit(2)
src = new_src

# 5) ACTION_UP/CANCEL:
#    - Ocultar kill
#    - Si dragging y encima -> stopSelf()
#    - Quitar vibrateKill()
up_pattern = r"""
MotionEvent\.ACTION_UP,\s*MotionEvent\.ACTION_CANCEL\s*->\s*\{
\s*val\s+shouldKill\s*=\s*dragging\s*&&\s*isOverKillCenter\(bubbleCenterX\(\),\s*bubbleCenterY\(\)\)\s*
\s*showKill\(false\)\s*
\s*if\s*\(shouldKill\)\s*\{
\s*vibrateKill\(\)\s*
\s*stopSelf\(\)\s*
\s*\}\s*else\s*\{
\s*//\s*tap\s+normal.*?\n\s*\}\s*
\s*true\s*
\}
"""
up_replacement = """MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
            val shouldKill = dragging && isOverKillCenter(bubbleCenterX(), bubbleCenterY())
            showKill(false)

            if (shouldKill) {
              stopSelf()
            } else {
              // tap normal (por ahora NO hace nada)
            }
            true
          }"""

src2 = re.sub(up_pattern, up_replacement, src, flags=re.X | re.S)
if src2 == src:
    # Si el comentario cambió, hacemos un reemplazo más laxo: solo quitar vibrateKill()
    src = src.replace("              vibrateKill()\n", "")
else:
    src = src2

F.write_text(src, encoding="utf-8")
print("✅ Patch aplicado:", F)

