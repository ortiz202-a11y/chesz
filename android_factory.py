import os
import sys
from PIL import Image

def preparar_boton_final():
    # Ruta que me acabas de pasar
    input_file = "/storage/emulated/0/Download/bubble_icon.png"
    
    if not os.path.exists(input_file):
        print(f"❌ Error: No se encuentra el archivo en {input_file}")
        return

    # Guardar en la misma carpeta con el nuevo nombre
    output_path = "/storage/emulated/0/Download/boton_master.png"

    print(f"🚀 Procesando círculo de alta resolución...")
    img = Image.open(input_file).convert("RGBA")
    
    # 1. Limpieza de blanco/gris (transparencia real)
    datos = img.getdata()
    nuevos_datos = [(255, 255, 255, 0) if (d[0]>240 and d[1]>240 and d[2]>240) else d for d in datos]
    img.putdata(nuevos_datos)

    # 2. Eliminar marcos (ajuste exacto al diseño circular)
    bbox = img.getbbox()
    if bbox:
        img = img.crop(bbox)

    # 3. Guardar Master sin pérdida de calidad
    img.save(output_path, "PNG", optimize=True)
    
    print(f"✨ ¡HECHO! Imagen maestra guardada como: {output_path}")
    print(f"📏 Resolución final: {img.size[0]}x{img.size[1]}px")

if __name__ == "__main__":
    preparar_boton_final()
