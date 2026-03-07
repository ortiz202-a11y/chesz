import sys

file_path = 'app/src/main/java/com/chesz/floating/BubbleService.kt'
with open(file_path, 'r') as f:
    lines = f.readlines()

for i, line in enumerate(lines):
    # 1. Aumentar latencia de captura (de 200 a 400ms)
    if "}, 200)" in line and "takeScreenshotOnce" in lines[i-15:i+5]:
        lines[i] = line.replace("200", "400")
    
    # 2. Feedback si la imagen es nula
    if "return@postDelayed" in line and "acquireLatestImage" in lines[i-4]:
        lines.insert(i, '        panelTitle.text = "Sshot/Fail"\n')

# 3. Asegurar creación de directorio
for i, line in enumerate(lines):
    if "val out = java.io.File(dir, \"chesz_last.png\")" in line:
        lines.insert(i, '          if (dir != null && !dir.exists()) dir.mkdirs()\n')
        break

with open(file_path, 'w') as f:
    f.writelines(lines)
print("Soberanía: Directorios blindados y diagnóstico Sshot/Fail activado.")
