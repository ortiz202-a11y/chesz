import sys

file_path = 'app/src/main/java/com/chesz/floating/BubbleService.kt'
with open(file_path, 'r') as f:
    content = f.read()

# 1. Limpiar el doble retraso anidado por uno simple de 500ms
old_line = 'root.postDelayed({ root.postDelayed({ runCatching { upgradeToMediaProjection() } }, 500) }, 500)'
new_line = 'root.postDelayed({ runCatching { upgradeToMediaProjection() } }, 500)'

if old_line in content:
    content = content.replace(old_line, new_line)
    with open(file_path, 'w') as f:
        f.write(content)
    print("Integridad 1:1: Doble retraso purgado. Latencia estabilizada en 500ms.")
else:
    print("Error: No se encontró la secuencia de doble retraso exacta.")
