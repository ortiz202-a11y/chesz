import cv2
import numpy as np
import os

# [span_4](start_span)Configuración de rutas según auditoría[span_4](end_span)
ruta_in = os.path.expanduser("~/chesz/motor_fen/input/Master.png")
ruta_out = os.path.expanduser("~/chesz/motor_fen/input/Master_engine.png")
ruta_descarga = "/storage/emulated/0/Download/Master_engine.png"

# 1. [span_5](start_span)[span_6](start_span)Carga en escala de grises (mantiene el detalle que preferiste)[span_5](end_span)[span_6](end_span)
img_gray = cv2.imread(ruta_in, cv2.IMREAD_GRAYSCALE)

if img_gray is None:
    print(f"Error: No se encontró {ruta_in}")
    exit()

# 2. [span_7](start_span)[span_8](start_span)Filtro de Siluetas (Lógica exacta de app.py + calibrador.py)[span_7](end_span)[span_8](end_span)
# Canny extrae bordes y Dilate los engorda para que el fondo no estorbe
bordes = cv2.Canny(img_gray, 50, 150)
kernel = np.ones((3, 3), np.uint8)
normalizada = cv2.dilate(bordes, kernel, iterations=1)

# 3. Guardar y exportar a Downloads
cv2.imwrite(ruta_out, normalizada)
cv2.imwrite(ruta_descarga, normalizada)

print(f"✅ Normalización 'Engine' completada.")
print(f"📲 Archivo listo en Downloads: Master_engine.png")
