import cv2
import os

dir_cuadros = os.path.expanduser("~/chesz/motor_fen/cuadros/")

# Lista de piezas clave para auditar
auditoria = ['br1', 'bk', 'bq', 'wr1', 'wk', 'wq', 'bp1', 'wp1']

print(f"{'PIEZA':<10} | {'COORD (y,x)':<12} | {'BRILLO [45,45]':<15} | {'ESTADO'}")
print("-" * 60)

for nombre in auditoria:
    ruta = os.path.join(dir_cuadros, f"{nombre}.png")
    img = cv2.imread(ruta, cv2.IMREAD_GRAYSCALE)
    
    if img is not None:
        # El motor lee el centro exacto del cuadro de 90x90
        brillo = img[45, 45]
        
        # Lógica del Engine: > 120 es Blanca, <= 120 es Negra
        es_blanca = brillo > 120
        tipo_detectado = "Blanca ⚪" if es_blanca else "Negra ⚫"
        
        # Verificar si coincide con el nombre (w = white, b = black)
        error = ""
        if (nombre.startswith('w') and not es_blanca) or (nombre.startswith('b') and es_blanca):
            error = "⚠️ ¡DISCREPANCIA!"

        print(f"{nombre:<10} | (45, 45)     | {brillo:<15} | {tipo_detectado} {error}")
    else:
        print(f"{nombre:<10} | ---          | ---             | ❌ No encontrado")
