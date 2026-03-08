import sys

file_path = 'app/src/main/java/com/chesz/floating/BubbleService.kt'
with open(file_path, 'r') as f:
    lines = f.readlines()

# Inyectamos un log temporal que nos diga en el panel si el archivo existe
for i, line in enumerate(lines):
    if "panelTitle.text = \"Sshot/\"" in line:
        check_logic = """
        val internalFile = java.io.File(getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES), "chesz_last.png")
        if (internalFile.exists()) {
            panelTitle.text = "Exist: ${internalFile.length() / 1024}KB"
        } else {
            panelTitle.text = "Exist: NO"
        }
        """
        lines.insert(i + 1, check_logic)
        break

with open(file_path, 'w') as f:
    f.writelines(lines)
print("Soberanía: Scanner de existencia inyectado. El panel dirá el tamaño si existe.")
