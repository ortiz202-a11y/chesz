import re

file_path = 'app/src/main/java/com/chesz/floating/BubbleService.kt'
with open(file_path, 'r') as f:
    content = f.read()

# 1. Regresar a la ruta base de archivos (files/) sin subcarpetas de sistema
old_path = 'val dir = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)'
new_path = 'val dir = getExternalFilesDir(null)'

content = content.replace(old_path, new_path)

# 2. Asegurar que el reporte en el panel sea limpio
content = content.replace('updateDebug("Step 4: Escribiendo PNG...")', 
                          'updateDebug("Guardando en Ruta Segura...")')

with open(file_path, 'w') as f:
    f.write(content)
print("Soberanía: Ruta segura restaurada a /files/. Carpeta Pictures eliminada.")
