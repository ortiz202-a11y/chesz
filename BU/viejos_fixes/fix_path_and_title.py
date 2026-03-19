import sys

file_path = 'app/src/main/java/com/chesz/floating/BubbleService.kt'
with open(file_path, 'r') as f:
    lines = f.readlines()

# 1. Forzar el Título Sshot/ antes del proceso pesado
# Buscamos el inicio de takeScreenshotOnce
for i, line in enumerate(lines):
    if "private fun takeScreenshotOnce()" in line:                                    lines.insert(i + 2, '    panelTitle.text = "Sshot/"\n')
        break

# 2. Corregir la ruta de guardado para evitar bloqueos de Xiaomi (L765 del Fisgón)
# Buscamos la lógica de Environment.getExternalStoragePublicDirectory
for i, line in enumerate(lines):
    if "val dir = android.os.Environment.getExternalStoragePublicDirectory" in line:
        # Reemplazamos por la ruta interna segura
        lines[i] = '          val dir = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)\n'
        break

with open(file_path, 'w') as f:
    f.writelines(lines)
print("Soberanía: Ruta corregida a carpeta interna y Título Sshot/ activado.")
