from pathlib import Path
import shutil
import sys

F = Path("app/src/main/java/com/chesz/floating/BubbleService.kt")
if not F.exists():
    print("NO EXISTE:", F)
    sys.exit(1)

src = F.read_text(encoding="utf-8")
bak = F.with_suffix(F.suffix + ".bak_drag_v2")
shutil.copy2(F, bak)
print("🧷 Backup:", bak)

def find_block(text: str, start_pat: str):
    i = text.find(start_pat)
    if i < 0:
        return None
    # buscar la primera '{' después del patrón
    j = text.find("{", i)
    if j < 0:
        return None
    # recorrer contando llaves para encontrar el cierre
    depth = 0
    k = j
    while k < len(text):
        ch = text[k]
        if ch == "{":
            depth += 1
        elif ch == "}":
            depth -= 1
            if depth == 0:
                return (i, k + 1)  # [inicio del patrón, fin incluyendo '}']
        k += 1
    return None

def replace_block(text: str, start_pat: str, new_block: str):
    blk = find_block(text, start_pat)
    if not blk:
        return text, False
    a, b = blk
    return text[:a] + new_block + text[b:], True

# 1) Quitar vibración (imports y función) si existe (simple, sin regex)
for imp in [
    "import android.os.VibrationEffect\n",
    "import android.os.Vibrator\n",
    "import androidx.core.content.getSystemService\n",
]:
    src = src.replace(imp, "")

# quitar llamadas vibrateKill()
src = src.replace("vibrateKill()\n", "")

# quitar función vibrateKill completa si está
marker = "private fun vibrateKill()"
p = src.find(marker)
if p >= 0:
    blk = find_block(src[p:], "private fun vibrateKill()")
    if blk:
        a, b = blk
        src = src[:p + a] + "\n" + src[p + b:]

# 2) ACTION_DOWN: quitar showKill(true) si está ahí
# (no reemplazamos todo el bloque; solo removemos la línea)
src = src.replace("showKill(true)\n", "")

# 3) ACTION_MOVE: reemplazar TODO el bloque con versión estándar
move_pat = "MotionEvent.ACTION_MOVE ->"
new_move = """MotionEvent.ACTION_MOVE -> {
            val dx = (e.rawX - downRawX).toInt()
            val dy = (e.rawY - downRawY).toInt()

            // Estándar: el kill SOLO aparece cuando ya es drag real (umbral)
            if (!dragging && (kotlin.math.abs(dx) + kotlin.math.abs(dy) > dp(6))) {
              dragging = true
              showKill(true)
            }

            bubbleLp.x = startX + dx
            bubbleLp.y = startY + dy
            wm.updateViewLayout(bubbleRoot, bubbleLp)

            // Hover solo durante drag
            if (dragging) {
              val over = isOverKillCenter(bubbleCenterX(), bubbleCenterY())
              setKillHover(over)
            }
            true
          }"""

src, ok_move = replace_block(src, move_pat, new_move)
if not ok_move:
    print("❌ No encontré el bloque ACTION_MOVE en el archivo.")
    sys.exit(2)

# 4) ACTION_UP/CANCEL: reemplazar bloque para que solo cierre al soltar encima
# Hay 2 formatos comunes: "MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->" o separados.
up_pat = "MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->"
new_up = """MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
            val shouldKill = dragging && isOverKillCenter(bubbleCenterX(), bubbleCenterY())
            showKill(false)

            if (shouldKill) {
              stopSelf()
            } else {
              // tap normal (no hace nada)
            }
            true
          }"""

src, ok_up = replace_block(src, up_pat, new_up)

if not ok_up:
    # fallback: si están separados, intentamos reemplazar ACTION_UP solo
    up_pat2 = "MotionEvent.ACTION_UP ->"
    new_up2 = """MotionEvent.ACTION_UP -> {
            val shouldKill = dragging && isOverKillCenter(bubbleCenterX(), bubbleCenterY())
            showKill(false)

            if (shouldKill) {
              stopSelf()
            } else {
              // tap normal (no hace nada)
            }
            true
          }"""
    src, ok_up2 = replace_block(src, up_pat2, new_up2)
    if not ok_up2:
        print("❌ No encontré ACTION_UP (ni combinado ni solo).")
        sys.exit(3)

# 5) Guardar
F.write_text(src, encoding="utf-8")
print("✅ Patch aplicado:", F)
