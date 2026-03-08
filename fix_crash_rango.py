import os

def parchear_rango():
    file_path = os.path.expanduser("~/chesz/app/src/main/java/com/chesz/floating/BubbleService.kt")
    
    with open(file_path, 'r') as f:
        lineas = f.readlines()

    # Buscamos la lógica de clamp (alrededor de la línea 564-571)
    # Reemplazamos el cálculo de maxY por uno protegido con .coerceAtLeast(0)
    for i, linea in enumerate(lineas):
        if "sh - h - bottomInsetCache" in linea:
            lineas[i] = "        val maxY = (sh - h - bottomInsetCache).coerceAtLeast(0)\n"
        if "sw - w" in linea and "val maxX" in linea:
            lineas[i] = "        val maxX = (sw - w).coerceAtLeast(0)\n"

    with open(file_path, 'w') as f:
        f.writelines(lineas)
    print("✅ Crash de rango corregido en BubbleService.kt")

if __name__ == '__main__':
    parchear_rango()
