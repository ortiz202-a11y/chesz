import cv2
import os
import shutil

# --- CONFIGURACIÓN CON FILTRO AVANZADO ---
RUTA_MASTER = "/storage/emulated/0/Download/10352.png" # Usamos tu foto subida
DIR_INTERNO = os.path.expanduser("~/chesz/motor_fen/casillas/")
DIR_DOWNLOADS = "/storage/emulated/0/Download/cuadritos/"
SIZE = 720
CUADRO = 90 # 720 / 8

def procesar_con_filtro_adaptativo():
    # 1. Preparar carpetas
    for d in [DIR_INTERNO, DIR_DOWNLOADS]:
        if os.path.exists(d):
            shutil.rmtree(d)
        os.makedirs(d)

    # 2. Cargar imagen master
    img = cv2.imread(RUTA_MASTER)
    if img is None:
        print(f"❌ No se encontró Master.png en {RUTA_MASTER}")
        return

    # 3. Pre-procesamiento
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    
    # 4. Segmentación y Filtrado de Alta Precisión
    filas = "87654321"
    columnas = "abcdefgh"

    print("✂️ Recortando y aplicando Filtro Adaptativo (Adaptive Threshold)...")

    for i in range(8):
        for j in range(8):
            y1, y2 = i * CUADRO, (i + 1) * CUADRO
            x1, x2 = j * CUADRO, (j + 1) * CUADRO
            
            casilla = gray[y1:y2, x1:x2]

            # --- NUEVA LÓGICA DE VISIÓN ---
            # Este filtro ayuda a rescatar la silueta en zonas oscuras
            # Invierte los colores para que la pieza sea BLANCA sobre fondo NEGRO (ideal para motor)
            ajustada = cv2.adaptiveThreshold(
                casilla, 255, cv2.ADAPTIVE_THRESH_GAUSSIAN_C, 
                cv2.THRESH_BINARY_INV, 11, 2
            )

            nombre = f"{columnas[j]}{filas[i]}.png"
            
            # Guardar en ambos destinos
            cv2.imwrite(os.path.join(DIR_INTERNO, nombre), ajustada)
            cv2.imwrite(os.path.join(DIR_DOWNLOADS, nombre), ajustada)

    print(f"✅ ¡Tablero segmentado y ajustado! 64 casillas listas en {DIR_DOWNLOADS}")

if __name__ == "__main__":
    procesar_con_filtro_adaptativo()
