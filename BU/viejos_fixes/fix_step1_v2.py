import sys

file_path = 'app/src/main/java/com/chesz/floating/BubbleService.kt'

with open(file_path, 'r') as f:
    lines = f.readlines()

new_lines = []
found_method = False
inserted = False

for line in lines:
    if "override fun onDestroy()" in line:
        found_method = True
    
    # Solo insertamos si estamos dentro de onDestroy y vemos el cierre de la killArea
    if found_method and "killShown = false" in line and not inserted:
        new_lines.append("    mpData = null\n")
        new_lines.append("    mpResultCode = null\n")
        new_lines.append(line)
        inserted = True
    else:
        new_lines.append(line)

with open(file_path, 'w') as f:
    f.writelines(new_lines)

print("Integridad 1:1: Bloque onDestroy sanado quirúrgicamente.")
