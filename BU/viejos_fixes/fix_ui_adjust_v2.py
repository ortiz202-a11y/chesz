import sys

file_path = 'app/src/main/java/com/chesz/floating/BubbleService.kt'

with open(file_path, 'r') as f:
    content = f.read()

# 1. Eliminar animaciones de hundimiento (Sinking)
content = content.replace('root.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).start()', '')
content = content.replace('root.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()', '')

# 2. Reposicionar burbuja inicial (x=60dp, y=180dp)
# Buscamos el bloque exacto del LayoutParams inicial
old_pos = """      x = 0
      y = dp(120)"""
new_pos = """      x = dp(60)
      y = dp(180)"""

if old_pos in content:
    content = content.replace(old_pos, new_pos)
    with open(file_path, 'w') as f:
        f.write(content)
    print("Integridad 1:1: Sinking eliminado y posición inicial corregida a (60, 180).")
else:
    print("Error: No se encontró el bloque de coordenadas iniciales.")
