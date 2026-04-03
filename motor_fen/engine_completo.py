import cv2
import numpy as np
import os

# Configuración de rutas
RUTA_MASTER = os.path.expanduser("~/chesz/motor_fen/input/Master.png")
DIR_TEMPLATES = os.path.expanduser("~/chesz-engine/templates/")

piece_map = {'wp':'P','wn':'N','wb':'B','wr':'R','wq':'Q','wk':'K',
             'bp':'p','bn':'n','bb':'b','br':'r','bq':'q','bk':'k'}

def procesar_motor():
    # 1. Cargar y convertir a grises
    img_color = cv2.imread(RUTA_MASTER)
    if img_color is None: return "Error: No se encontró Master.png"
    img_gray = cv2.cvtColor(img_color, cv2.COLOR_BGR2GRAY)
    
    # 2. Cargar plantillas (Templates) con la lógica del calibrador
    template_cache = {}
    kernel = np.ones((3,3), np.uint8)
    for name in piece_map.keys():
        t = cv2.imread(os.path.join(DIR_TEMPLATES, f"{name}.png"), 0)
        if t is not None:
            # Canny + Dilatacion 3x3 (Soberanía del calibrador.py)
            t_edges = cv2.dilate(cv2.Canny(t, 50, 150), kernel, iterations=1)
            template_cache[name] = t_edges

    # 3. Analizar el tablero (8x8)
    grid = [['' for _ in range(8)] for _ in range(8)]
    for row in range(8):
        for col in range(8):
            # Recorte 90x90
            y1, y2, x1, x2 = row*90, (row+1)*90, col*90, (col+1)*90
            sq_gray = img_gray[y1:y2, x1:x2]
            
            # Procesar silueta de la casilla
            sq_edges = cv2.dilate(cv2.Canny(sq_gray, 50, 150), kernel, iterations=1)
            
            # Comparación y validación de brillo en [45, 45]
            best_v, detected = 0, ''
            center_pixel = sq_gray[45, 45]
            
            for name, t_edges in template_cache.items():
                res = cv2.matchTemplate(sq_edges, t_edges, cv2.TM_CCOEFF_NORMED)
                _, max_val, _, _ = cv2.minMaxLoc(res)
                
                if max_val > 0.45 and max_val > best_v:
                    piece = piece_map[name]
                    # Validación de bando: Mayúsculas > 120, Minúsculas <= 120
                    if (piece.isupper() and center_pixel > 120) or (piece.islower() and center_pixel <= 120):
                        best_v, detected = max_val, piece
            grid[row][col] = detected

    # 4. Construir cadena FEN
    fen_rows = []
    for r in grid:
        empty, row_str = 0, ""
        for cell in r:
            if cell == '': empty += 1
            else:
                if empty > 0: row_str += str(empty); empty = 0
                row_str += cell
        if empty > 0: row_str += str(empty)
        fen_rows.append(row_str)
    
    return "/".join(fen_rows) + " w - - 0 1"

print(f"\n🧩 FEN GENERADO: {procesar_motor()}")
