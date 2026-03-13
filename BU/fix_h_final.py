import os

def corregir_referencia_h_final():
    file_path = os.path.expanduser("~/chesz/app/src/main/java/com/chesz/floating/BubbleService.kt")
    
    if not os.path.exists(file_path):
        print("❌ Error: No se encontró el archivo BubbleService.kt")
        return

    with open(file_path, 'r') as f:
        lineas = f.readlines()

    modificado = False
    for i, linea in enumerate(lineas):
        # Localizamos la línea con la variable huérfana 'h'
        if "val maxY = (sh - h - bottomInsetCache).coerceAtLeast(0)" in linea:
            # Reemplazamos por la referencia válida al objeto rootLp
            lineas[i] = "        val maxY = (sh - rootLp.height - bottomInsetCache).coerceAtLeast(0)\n"
            modificado = True
            break

    if modificado:
        with open(file_path, 'w') as f:
            f.writelines(lineas)
        print("✅ ADN Corregido: 'h' reemplazada por 'rootLp.height'.")
    else:
        print("⚠️ No se encontró la línea o ya estaba corregida.")

if __name__ == '__main__':
    corregir_referencia_h_final()
