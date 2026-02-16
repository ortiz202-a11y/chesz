import re

path = './app/src/main/java/com/chesz/analyzer/bubble/BubbleService.kt'
with open(path, 'r') as f:
    content = f.read()

# 1. Neutralizar el brinco en closePanel (reemplazo directo y agresivo)
content = re.sub(
    r"private fun closePanel\(\) \{.*?\}",
    """private fun closePanel() {
          panelBubble?.visibility = View.GONE
          val root = bubbleView
          if (root != null && ::bubbleLp.isInitialized) {
              // Eliminamos el clamp aquí para que NO brinque
              wm.updateViewLayout(root, bubbleLp)
          }
      }""",
    content,
    flags=re.DOTALL
)

# 2. Forzar el Clamp a ser "ciego" al margen superior
# Buscamos la línea del coerceIn y la obligamos a aceptar el 0 real
content = content.replace(
    "lp.y = lp.y.coerceIn(0, maxY)",
    "lp.y = lp.y.coerceIn(0, (getScreenSizePx().second - effectiveOverlaySizePx(overlayView).second).coerceAtLeast(0))"
)

# 3. Eliminar el post traicionero al final de createBubble
content = content.replace("root.post { updateOverlayLayoutClamped(root, bubbleLp) }", "// Post eliminado")

with open(path, 'w') as f:
    f.write(content)

print("CIRUGÍA COMPLETADA: El brinco ha sido extirpado y el límite superior liberado.")
