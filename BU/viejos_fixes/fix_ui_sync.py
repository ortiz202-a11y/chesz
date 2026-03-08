import sys

file_path = 'app/src/main/java/com/chesz/floating/BubbleService.kt'
with open(file_path, 'r') as f:
    content = f.read()

# 1. Modificar la lógica de visibilidad para ser proactiva con mpData
old_ui_logic = 'permBar.visibility = if (ok) View.GONE else View.VISIBLE'
new_ui_logic = 'permBar.visibility = if (ok || mpData != null) View.GONE else View.VISIBLE'

if old_ui_logic in content:
    content = content.replace(old_ui_logic, new_ui_logic)
    with open(file_path, 'w') as f:
        f.write(content)
    print("Integridad 1:1: Interfaz sincronizada. El botón de permisos ahora es proactivo.")
else:
    print("Error: No se encontró la línea de lógica visual exacta.")
