import cv2
import os

# Rutas
ruta_grises = os.path.expanduser("~/chesz/motor_fen/input/Master_grises.png")
dir_cuadros = os.path.expanduser("~/chesz/motor_fen/cuadros/")

# Cargar imagen en grises
img = cv2.imread(ruta_grises, cv2.IMREAD_GRAYSCALE)

if img is None:
    print("❌ Error: No se encontró Master_grises.png")
else:
    # Nombres de las piezas por fila (estándar inicial)
    # Fila 0 (Negras): r, n, b, q, k, b, n, r
    # Fila 7 (Blancas): r, n, b, q, k, b, n, r
    nombres_filas = {
        0: ['br1', 'bn1', 'bb1', 'bq', 'bk', 'bb2', 'bn2', 'br2'],
        1: ['bp1', 'bp2', 'bp3', 'bp4', 'bp5', 'bp6', 'bp7', 'bp8'],
        6: ['wp1', 'wp2', 'wp3', 'wp4', 'wp5', 'wp6', 'wp7', 'wp8'],
        7: ['wr1', 'wn1', 'wb1', 'wq', 'wk', 'wb2', 'wn2', 'wr2']
    }

    for fila in range(8):
        for col in range(8):
            # Coordenadas 90x90 según Engine
            y1, y2 = fila * 90, (fila + 1) * 90
            x1, x2 = col * 90, (col + 1) * 90
            cuadro = img[y1:y2, x1:x2]
            
            # Determinar nombre del archivo
            if fila in nombres_filas:
                nombre = nombres_filas[fila][col]
            else:
                nombre = f"vacio_{fila}_{col}"
            
            cv2.imwrite(f"{dir_cuadros}{nombre}.png", cuadro)

    print(f"✅ Se han generado los 64 cuadros en: {dir_cuadros}")
