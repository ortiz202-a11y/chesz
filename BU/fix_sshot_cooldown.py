import os

def aplicar_fix_quirurgico():
    file_path = os.path.expanduser("~/chesz/app/src/main/java/com/chesz/floating/BubbleService.kt")
    with open(file_path, 'r') as f:
        lineas = f.readlines()

    # 1. Insertar variable de control de captura
    for i, linea in enumerate(lineas):
        if "private var dragging = false" in linea:
            lineas.insert(i + 1, "    private var isCapturing = false\n")
            break

    # 2. Aplicar bloqueo de 3 segundos en el disparador
    for i, linea in enumerate(lineas):
        if "private fun togglePanel() {" in linea:
            lineas.insert(i + 1, "        if (isCapturing) return\n")
            break

    # 3. Lógica de persistencia, delay de 1s y desbloqueo
    for i, linea in enumerate(lineas):
        if "updateDebug(\"Step 1: Init...\")" in linea:
            lineas.insert(i + 1, "        isCapturing = true\n")
            lineas.insert(i + 2, "        root.postDelayed({ isCapturing = false }, 3000)\n")
        
        # Ampliar delay a 1000ms (1 segundo)
        if "}, 400) // Aumentado para estabilidad en Xiaomi" in linea:
            lineas[i] = "        }, 1000) // Delay de 1s para hardware Xiaomi\n"
        
        # Evitar el cierre de la raíz (mp.stop) ante un nulo temporal
        if "runCatching { mp.stop() }" in linea:
            lineas[i] = "                            // Raíz protegida: no cerrar mp aquí\n"

    with open(file_path, 'w') as f:
        f.writelines(lineas)
    print("✅ ADN Actualizado: Delay 1s + Bloqueo 3s + Raíz protegida.")

if __name__ == '__main__':
    aplicar_fix_quirurgico()
