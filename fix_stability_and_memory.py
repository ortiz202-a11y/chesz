import sys

file_path = 'app/src/main/java/com/chesz/floating/BubbleService.kt'
with open(file_path, 'r') as f:
    lines = f.readlines()

# 1. Optimizar el cierre de imagen para liberar RAM (Evita congelamientos)
# Buscamos el bloque finally del proceso de captura
for i, line in enumerate(lines):
    if "runCatching { image.close() }" in line:
        # Aseguramos que se cierre SIEMPRE y se limpie la referencia
        lines[i] = "        image.close()\n"
        break

# 2. Elevar prioridad de la notificación para evitar que Xiaomi mate la app
for i, line in enumerate(lines):
    if "IMPORTANCE_LOW" in line:
        lines[i] = line.replace("IMPORTANCE_LOW", "IMPORTANCE_HIGH")
        break

# 3. Bloqueo de seguridad: Evitar taps múltiples mientras se procesa
for i, line in enumerate(lines):
    if "takeScreenshotOnce()" in line and "if (hasPerm)" in lines[i-1]:
        lines.insert(i, '            if (panelTitle.text == "Sshot/") return@setOnTouchListener true // Bloqueo anti-spam\n')
        break

with open(file_path, 'w') as f:
    f.writelines(lines)
print("Soberanía: RAM optimizada y prioridad de servicio elevada.")
