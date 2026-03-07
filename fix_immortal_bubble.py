import sys

file_path = 'app/src/main/java/com/chesz/floating/BubbleService.kt'
with open(file_path, 'r') as f:
    lines = f.readlines()

# 1. Blindaje contra cierres por excepción de hardware
for i, line in enumerate(lines):
    if "private fun takeScreenshotOnce() {" in line:
        lines.insert(i + 1, '    runCatching {\n')
        # Buscamos el final de la función para cerrar el runCatching
        break

# Cerramos el runCatching al final de la función (antes del final de la clase)
for i in range(len(lines)-1, 0, -1):
    if "}, 200)" in lines[i]:
        lines.insert(i + 1, '    }.onFailure { panelTitle.text = "Sshot/Err" }\n')
        break

# 2. Reset automático del título para permitir re-intento (3 seg)
for i, line in enumerate(lines):
    if "panelTitle.text = \"Sshot/\"" in line:
        lines.insert(i + 1, '        root.postDelayed({ panelTitle.text = "Chesz" }, 3000)\n')

with open(file_path, 'w') as f:
    f.writelines(lines)
print("Soberanía: Botón blindado contra cierres. Reset de ciclo activo.")
