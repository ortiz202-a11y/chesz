import cv2
import os

ruta_in = os.path.expanduser("~/chesz/motor_fen/input/Master.png")
ruta_out = os.path.expanduser("~/chesz/motor_fen/input/Master_normalizado.png")

# 1. Leer en escala de grises
img_gray = cv2.imread(ruta_in, cv2.IMREAD_GRAYSCALE)

if img_gray is None:
    print(f"Error: No se encontró {ruta_in}")
    exit()

# 2. Suavizado ligero para reducir ruido del tablero
blur = cv2.GaussianBlur(img_gray, (5, 5), 0)

# 3. Filtro Universal de Otsu (calcula el umbral dinámicamente)
_, umbral = cv2.threshold(blur, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)

cv2.imwrite(ruta_out, umbral)
print(f"Imagen normalizada guardada en: {ruta_out}")
