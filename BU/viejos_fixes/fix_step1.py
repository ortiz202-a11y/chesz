import sys
import os

file_path = 'app/src/main/java/com/chesz/floating/BubbleService.kt'

if not os.path.exists(file_path):
    print(f"Error: No se encuentra {file_path}")
    sys.exit(1)

with open(file_path, 'r') as f:
    lines = f.readlines()

new_lines = []
inserted = False

for line in lines:
    # [span_0](start_span)Buscamos el punto exacto en onDestroy[span_0](end_span)
    if "killShown = false" in line and not inserted:
        new_lines.append("    mpData = null\n")
        new_lines.append("    mpResultCode = null\n")
        new_lines.append(line)
        inserted = True
    else:
        new_lines.append(line)

with open(file_path, 'w') as f:
    f.writelines(new_lines)

print("Integridad 1:1: Bloque onDestroy actualizado con éxito.")
