import sys

file_path = 'app/src/main/java/com/chesz/floating/BubbleService.kt'

with open(file_path, 'r') as f:
    content = f.read()

# Ancla estricta en el bloque de captura (Sshot)
old_code = """        cropped.recycle()
        runCatching { flashBubbleRed() }"""

new_code = """        cropped.recycle()
        panelTitle.text = "Sshot/" """

if old_code in content:
    content = content.replace(old_code, new_code)
    with open(file_path, 'w') as f:
        f.write(content)
    print("Integridad 1:1: Destello extirpado de la foto. Título 'Sshot/' inyectado.")
else:
    print("Error: No se encontró el bloque de captura exacto.")
