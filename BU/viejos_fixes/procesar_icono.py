from PIL import Image
import os

input_path = "/storage/emulated/0/Download/iconos/launcher.png"
output_path = os.path.expanduser("~/chesz/iconos/launcher.png")
copy_path = "/storage/emulated/0/Download/iconos/copia.png"

def procesar():
    if not os.path.exists(input_path):
        print(f"[-] Error: No se encontró {input_path}")
        return

    # Cargar y asegurar canal alfa
    img = Image.open(input_path).convert("RGBA")
    data = img.getdata()
    
    # Limpieza de píxeles blancos (marco) a transparente
    new_data = []
    for item in data:
        # Umbral agresivo (200) para eliminar bordes sucio/grisáceos
        if item[0] > 200 and item[1] > 200 and item[2] > 200:
            new_data.append((255, 255, 255, 0))
        else:
            new_data.append(item)
    img.putdata(new_data)

    # Obtener el recuadro del contenido real (sin espacio vacío)
    bbox = img.getbbox()
    if bbox:
        img = img.crop(bbox)

    # Ajuste a la totalidad de los bordes (512x512)
    # Resize expande el contenido para que toque exactamente los 4 límites
    final_img = img.resize((512, 512), Image.Resampling.LANCZOS)

    # Guardado en ambas rutas
    final_img.save(output_path, "PNG")
    final_img.save(copy_path, "PNG")
    
    print(f"[+] Icono ajustado a bordes: {output_path}")
    print(f"[+] Copia generada: {copy_path}")

if __name__ == "__main__":
    procesar()
