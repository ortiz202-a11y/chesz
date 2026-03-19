import sys

file_path = 'app/src/main/java/com/chesz/floating/BubbleService.kt'

with open(file_path, 'r') as f:
    lines = f.readlines()

new_lines = []
in_start_command = False

for line in lines:
    if "override fun onStartCommand(" in line:
        in_start_command = True

    # Si estamos dentro de onStartCommand y vemos el comentario o el flash, los saltamos
    if in_start_command and "// feedback mínimo por ahora" in line:
        continue
    if in_start_command and "runCatching { flashBubbleRed() }" in line:
        continue

    if in_start_command and "return START_STICKY" in line:
        in_start_command = False

    new_lines.append(line)

with open(file_path, 'w') as f:
    f.writelines(new_lines)

print("Integridad 1:1: Parpadeo rojo residual eliminado de onStartCommand.")
