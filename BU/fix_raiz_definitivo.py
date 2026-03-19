import os

def blindar_raiz():
    file_path = os.path.expanduser("~/chesz/app/src/main/java/com/chesz/floating/BubbleService.kt")
    with open(file_path, 'r') as f:
        lineas = f.readlines()

    modificado = False
    for i, linea in enumerate(lineas):
        # Buscamos específicamente el cierre de mp dentro del bloque de fallo del reader
        if "runCatching { mp.stop() }" in linea and i > 600:
            lineas[i] = "                            // Raíz protegida: no cerrar mp aquí\n"
            modificado = True
            break

    if modificado:
        with open(file_path, 'w') as f:
            f.writelines(lineas)
        print("✅ ADN Blindado: Raíz protegida aplicada con éxito.")
    else:
        print("❌ Error: No se encontró la línea para blindar. Verifica el archivo.")

if __name__ == '__main__':
    blindar_raiz()
