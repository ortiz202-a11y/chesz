import sys

file_path = 'app/src/main/java/com/chesz/floating/BubbleService.kt'
with open(file_path, 'r') as f:
    content = f.read()

# 1. Corregir LayoutParams Inicial
content = content.replace('x = 0', 'x = dp(60)')
content = content.replace('y = dp(120)', 'y = dp(180)')

# 2. Corregir setStateA_layout para que no resetee a 0,0
content = content.replace('rootLp.x = clampedA.first', 'rootLp.x = dp(60)')
content = content.replace('rootLp.y = clampedA.second', 'rootLp.y = dp(180)')

with open(file_path, 'w') as f:
    f.write(content)
print("Integridad 1:1: Coordenadas (60, 180) blindadas en el inicio y reset.")
