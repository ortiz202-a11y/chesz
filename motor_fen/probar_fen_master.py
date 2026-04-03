import cv2
import numpy as np
import os

# Configuración de rutas y mapas (Lógica app.py)
dir_siluetas = os.path.expanduser("~/chesz/motor_fen/siluetas/")
piezas_maestras = ['wp', 'wn', 'wb', 'wr', 'wq', 'wk', 'bp', 'bn', 'bb', 'br', 'bq', 'bk']
piece_symbols = {'wp':'P','wn':'N','wb':'B','wr':'R','wq':'Q','wk':'K',
                 'bp':'p','bn':'n','bb':'b','br':'r','bq':'q','bk':'k'}

def generar_fen():
    tablero = [['' for _ in range(8)] for _ in range(8)]
    
    for fila in range(8):
        for col in range(8):
            # 1. Cargar la silueta del cuadro actual
            # Usamos el nombre que les dimos al recortar
            # Nota: Para esta prueba, buscaremos los nombres que generamos antes
            nombre_cuadro = f"vacio_{fila}_{col}.png"
            # Reemplazar por nombres de piezas si es fila de inicio
            mapeo_inicial = {0:['br1','bn1','bb1','bq','bk','bb2','bn2','br2'], 
                            7:['wr1','wn1','wb1','wq','wk','wb2','wn2','wr2']}
            
            if fila in mapeo_inicial: nombre_cuadro = f"{mapeo_inicial[fila][col]}.png"
            elif fila == 1: nombre_cuadro = f"bp{col+1}.png"
            elif fila == 6: nombre_cuadro = f"wp{col+1}.png"

            ruta_cuadro = os.path.join(dir_siluetas, nombre_cuadro)
            img_cuadro = cv2.imread(ruta_cuadro, cv2.IMREAD_GRAYSCALE)
            
            if img_cuadro is None: continue

            # 2. Comparar contra cada maestra (Template Matching)
            mejor_v = 0
            pieza_detectada = ""
            
            for p in piezas_maestras:
                maestra = cv2.imread(os.path.join(dir_siluetas, f"{p}.png"), 0)
                if maestra is None: continue
                
                res = cv2.matchTemplate(img_cuadro, maestra, cv2.TM_CCOEFF_NORMED)
                _, max_val, _, _ = cv2.minMaxLoc(res)
                
                if max_val > 0.45 and max_val > mejor_v:
                    mejor_v = max_val
                    pieza_detectada = piece_symbols[p]
            
            tablero[fila][col] = pieza_detectada

    # 3. Convertir matriz a FEN
    fen_rows = []
    for r in tablero:
        vacias = 0
        row_str = ""
        for cell in r:
            if cell == '': vacias += 1
            else:
                if vacias > 0: row_str += str(vacias); vacias = 0
                row_str += cell
        if vacias > 0: row_str += str(vacias)
        fen_rows.append(row_str)
    
    return "/".join(fen_rows)

print(f"🧩 FEN CALCULADO: {generar_fen()}")
