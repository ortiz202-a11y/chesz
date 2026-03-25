path = "app/src/main/java/com/chesz/app/BubbleService.kt"
with open(path, "r") as f:
    code = f.read()

# 1. Localizar el inicio del Benchmark para aplicar el borrado (Fase 1)
# Buscamos donde se escribe el encabezado inicial
old_header = 'File(getExternalFilesDir(null), "FEN.txt").appendText("\\n\\n=== NUEVO REPORTE FEN BENCHMARK ===\\n")'
new_header = 'File(getExternalFilesDir(null), "FEN.txt").writeText("=== NUEVO REPORTE FEN BENCHMARK ===\\n")'

if old_header in code:
    code = code.replace(old_header, new_header)
    with open(path, "w") as f:
        f.write(code)
    print("LOGS REPARADOS: El archivo FEN.txt se limpiará en cada inicio.")
else:
    print("ERROR: No se encontró la línea de escritura del FEN.txt.")
