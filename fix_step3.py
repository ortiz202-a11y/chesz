import sys
import os

file_path = 'app/src/main/java/com/chesz/floating/BubbleService.kt'

with open(file_path, 'r') as f:
    lines = f.readlines()

new_lines = []
for line in lines:
    # 1. Agregar variables de clase para caché
    if "private var dragging = false" in line:
        new_lines.append(line)
        new_lines.append("  private var sw = 0\n")
        new_lines.append("  private var sh = 0\n")
        new_lines.append("  private var bottomInsetCache = 0\n")
    
    # 2. Inyectar actualización de caché en onCreate
    elif "createKillArea()" in line:
        new_lines.append(line)
        new_lines.append("    updateScreenCache()\n")
    
    # 3. Reemplazar cálculos pesados por las variables de caché en clampRootToScreen
    elif "val (sw, sh) = screenRealSize()" in line:
        new_lines.append("    val (sw, sh) = this.sw to this.sh\n")
    elif "val bottomInset = if (android.os.Build.VERSION.SDK_INT >= 30)" in line:
        new_lines.append("    val bottomInset = bottomInsetCache\n")
        # Saltamos el bloque viejo de cálculo pesado (aprox 5 líneas)
        continue
    elif "wm.maximumWindowMetrics.windowInsets.getInsetsIgnoringVisibility" in line:
        continue
    elif "android.view.WindowInsets.Type.navigationBars()" in line:
        continue
    elif "insets.bottom" in line:
        continue
    elif "} else 0" in line and "val bottomInset =" not in line:
        continue
    
    else:
        new_lines.append(line)

# Agregar la función de utilidad al final de la clase (antes de la última llave)
# Buscamos la última llave de la clase
for i in range(len(new_lines)-1, -1, -1):
    if new_lines[i].strip() == "}":
        new_lines.insert(i, """
  private fun updateScreenCache() {
    val size = screenRealSize()
    sw = size.first
    sh = size.second
    if (android.os.Build.VERSION.SDK_INT >= 30) {
        val metrics = wm.maximumWindowMetrics
        val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
            android.view.WindowInsets.Type.navigationBars()
        )
        bottomInsetCache = insets.bottom
    }
  }
""")
        break

with open(file_path, 'w') as f:
    f.writelines(new_lines)

print("Integridad 1:1: Paso 3 aplicado. Caché de dimensiones inyectado.")
