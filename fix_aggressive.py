import sys

path = './app/src/main/java/com/chesz/analyzer/bubble/BubbleService.kt'
with open(path, 'r') as f:
    content = f.read()

# 1. Forzar que el cálculo de límites ignore el tamaño del panel si está cerrado
# Buscamos la función clampToScreen y la reemplazamos por una que ignore todo margen
import re

new_clamp_logic = """
private fun clampToScreen(lp: WindowManager.LayoutParams, overlayView: View) {
    val (sw, sh) = getScreenSizePx()
    val size = effectiveOverlaySizePx(overlayView)
    val vw = size.first
    val vh = size.second
    
    // ELIMINAMOS EL MARCO FANTASMA: 
    // Si el panel está cerrado, el ancho real para chocar con la pared es solo el de la burbuja
    val maxX = if (isPanelOpen()) (sw - vw) else (sw - dp(80)) 
    val maxY = (sh - vh).coerceAtLeast(0)

    // LÍMITE SUPERIOR FORZADO A 0
    lp.x = lp.x.coerceIn(0, sw - dp(80)) 
    lp.y = lp.y.coerceIn(0, sh - dp(80))
}
"""

# Reemplazo total de la función vieja por la nueva
pattern = r"private fun clampToScreen\(lp: WindowManager\.LayoutParams, overlayView: View\) \{.*?\n\}"
content = re.sub(pattern, new_clamp_logic, content, flags=re.DOTALL)

with open(path, 'w') as f:
    f.write(content)

print("CIRUGÍA AGRESIVA: Límites recalculados desde cero.")
