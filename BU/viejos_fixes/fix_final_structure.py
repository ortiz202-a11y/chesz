import re

file_path = 'app/src/main/java/com/chesz/floating/BubbleService.kt'
with open(file_path, 'r') as f:
    lines = f.readlines()

# Limpiar líneas vacías o mal formadas al final
while lines and not lines[-1].strip():
    lines.pop()

# Asegurar el cierre correcto de la función y la clase
# Eliminamos las últimas llaves para reconstruir el final
while lines and '}' in lines[-1]:
    lines.pop()

# Re-inyectar el cierre limpio
lines.append("    }\n") # Cierra el onFailure/runCatching
lines.append("  }\n")   # Cierra takeScreenshotOnce
lines.append("}\n")     # Cierra la clase BubbleService

with open(file_path, 'w') as f:
    f.writelines(lines)
print("Soberanía: Estructura de llaves normalizada para Build #432.")
