import sys
import os

file_path = 'app/src/main/java/com/chesz/floating/BubbleService.kt'

if not os.path.exists(file_path):
    print(f"Error: No se encuentra {file_path}")
    sys.exit(1)

with open(file_path, 'r') as f:
    lines = f.readlines()

new_lines = []
for line in lines:
    # Localización y eliminación del root.post (L171) detectado en Fisgón
    if "root.post { runCatching { wm.updateViewLayout(root, rootLp) } }" in line:
        new_lines.append("    runCatching { wm.updateViewLayout(root, rootLp) }\n")
    else:
        new_lines.append(line)

with open(file_path, 'w') as f:
    f.writelines(new_lines)

print("Integridad 1:1: Paso 2 aplicado. Movimiento optimizado.")
