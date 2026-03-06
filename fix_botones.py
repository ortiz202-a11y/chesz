import sys
import os

file_path = 'app/src/main/java/com/chesz/floating/BubbleService.kt'

with open(file_path, 'r') as f:
    lines = f.readlines()

new_lines = []
skip_next = 0

for i, line in enumerate(lines):
    if skip_next > 0:
        skip_next -= 1
        continue

    # 1. Cambiar texto a "Pedir Permiso"
    if 'text = "Aceptar permisos"' in line:
        new_lines.append('      text = "Pedir Permiso"\n')
        
    # 2. Cambiar tamaño de letra a 13f
    elif 'textSize = 15f' in line:
        new_lines.append('      textSize = 13f\n')
        
    # 3. Reparar el ancho invisible (0 -> WRAP_CONTENT)
    elif 'addView(permText, LinearLayout.LayoutParams(' in line:
        new_lines.append(line)
        new_lines.append('        LinearLayout.LayoutParams.WRAP_CONTENT,\n')
        new_lines.append('        LinearLayout.LayoutParams.WRAP_CONTENT\n')
        skip_next = 2 # Salta las 2 líneas defectuosas antiguas
        
    # 4. Evitar que la burbuja cierre el panel
    elif 'if (panelShown) {' in line and lines[i-1].strip() == 'private fun togglePanel() {':
        new_lines.append('    if (!panelShown) {\n')
        new_lines.append('      showPanelIfFits()\n')
        new_lines.append('    }\n')
        skip_next = 4 # Salta el bloque viejo (hidePanel / else / showPanel / })
        
    # 5. Cerrar panel al pedir permiso
    elif 'private fun requestCapturePermission()' in line:
        new_lines.append(line)
        new_lines.append('    hidePanel()\n')
        
    else:
        new_lines.append(line)

with open(file_path, 'w') as f:
    f.writelines(new_lines)

print("Integridad 1:1: Botones reparados (UI y Lógica de apertura/cierre).")
