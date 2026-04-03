import os
ruta_log = "/sdcard/Android/data/com.chesz/files/Pictures/FEN.TXT"
def analizar():
    if not os.path.exists(ruta_log):
        print("❌ ERROR: No hay resultados. Ejecuta el test en la App primero.")
        return
    with open(ruta_log, "r", encoding="utf-8") as f:
        lineas = f.readlines()
    aciertos, total = 0, 0
    print("\n=== ANALIZADOR DE PRECISIÓN (ADN 0.65) ===")
    for l in lineas:
        if "FOTO" in l:
            total += 1
            p = l.split("P: [")[1].split("]")[0]
            e = l.split("E: [")[1].split("]")[0]
            status = "✅" if p == e else "❌"
            if p == e: aciertos += 1
            print(f"Foto {total}: {status} | P: {p if p else 'VACIO'} | E: {e}")
    print(f"===========================================")
    print(f"PRECISIÓN FINAL: {aciertos}/{total} ({(aciertos*100/total) if total>0 else 0}%)")
if __name__ == "__main__": analizar()
