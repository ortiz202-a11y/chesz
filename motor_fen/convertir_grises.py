import cv2
import os
import shutil

# Rutas estándar del proyecto
DIR_INPUT = os.path.expanduser("~/chesz/motor_fen/input/")
RUTA_MASTER = os.path.join(DIR_INPUT, "Master.png")
RUTA_GRISES = os.path.join(DIR_INPUT, "Master_grises.png")
RUTA_ANDROID = "/storage/emulated/0/Download/Master_grises.png"

def convertir():
    # 1. Cargar Master.png
    img = cv2.imread(RUTA_MASTER)
    if img is None:
        print(f"❌ Error: No se encontró Master.png en {DIR_INPUT}")
        return

    # 2. Transformar a Escala de Grises (Conversión universal de color a Luma)
    img_gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)

    # 3. Guardar localmente y en Downloads para el usuario
    cv2.imwrite(RUTA_GRISES, img_gray)
    try:
        shutil.copy(RUTA_GRISES, RUTA_ANDROID)
        print(f"✅ Conversión a grises completada.")
        print(f"📂 Archivo listo en: /Download/Master_grises.png")
    except PermissionError:
        print("✅ Archivo guardado localmente, pero falta permiso para /Download.")

if __name__ == "__main__":
    convertir()
