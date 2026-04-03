import cv2
import numpy as np
import os

dir_cuadros = os.path.expanduser("~/chesz/motor_fen/cuadros/")
dir_siluetas = os.path.expanduser("~/chesz/motor_fen/siluetas/")
os.makedirs(dir_siluetas, exist_ok=True)

# Kernel de 3x3 según calibrador.py
kernel = np.ones((3,3), np.uint8)

for archivo in os.listdir(dir_cuadros):
    if archivo.endswith(".png"):
        img = cv2.imread(os.path.join(dir_cuadros, archivo), cv2.IMREAD_GRAYSCALE)
        
        # 1. Canny + 2. Dilate (Lógica pura del Engine)
        edges = cv2.Canny(img, 50, 150)
        silueta = cv2.dilate(edges, kernel, iterations=1)
        
        cv2.imwrite(os.path.join(dir_siluetas, archivo), silueta)

print(f"✅ Siluetas generadas en: {dir_siluetas}")
