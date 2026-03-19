import os

def fix_adn():
    file_path = os.path.expanduser("~/chesz/app/src/main/java/com/chesz/floating/BubbleService.kt")
    with open(file_path, 'r') as f:
        lineas = f.readlines()

    # Reconstrucción de la función clamp (Líneas 564-571 aprox)
    for i, linea in enumerate(lineas):
        if "private fun clampRootToScreen" in linea:
            lineas[i+5] = "        val maxX = (sw - w).coerceAtLeast(0)\n"
            lineas[i+6] = "        val maxY = (sh - h - bottomInsetCache).coerceAtLeast(0)\n"
            lineas[i+7] = "        return x.coerceIn(0, maxX) to y.coerceIn(0, maxY)\n"
            # Limpiar posibles restos de llaves o líneas truncadas
            if "}" in lineas[i+8]: pass 
            else: lineas[i+8] = "    }\n"

    with open(file_path, 'w') as f:
        f.writelines(lineas)
    print("✅ ADN Reconstruido: Clamp seguro y con retorno.")

if __name__ == '__main__':
    fix_adn()
