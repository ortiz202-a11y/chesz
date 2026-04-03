import cv2
import numpy as np
import os

ruta_img = os.path.expanduser("~/chesz/motor_fen/input/Master.png")
dir_templates = os.path.expanduser("~/chesz-engine/templates/")

piece_map = {'wp':'P','wn':'N','wb':'B','wr':'R','wq':'Q','wk':'K',
             'bp':'p','bn':'n','bb':'b','br':'r','bq':'q','bk':'k'}

if not os.path.exists(ruta_img):
    print("Error: No se encontro Master.png")
else:
    img = cv2.imread(ruta_img)
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    kernel = np.ones((3,3), np.uint8)
    grid = [['' for _ in range(8)] for _ in range(8)]

    for r in range(8):
        for c in range(8):
            sq = gray[r*90:(r+1)*90, c*90:(c+1)*90]
            sq_edges = cv2.dilate(cv2.Canny(sq, 50, 150), kernel, iterations=1)
            
            brillo = np.mean(sq[35:56, 35:56])
            
            best_v, detectada = 0, ''
            for name, char in piece_map.items():
                t_path = os.path.join(dir_templates, f"{name}.png")
                t = cv2.imread(t_path, 0)
                if t is None: continue
                t_edges = cv2.dilate(cv2.Canny(t, 50, 150), kernel, iterations=1)
                res = cv2.matchTemplate(sq_edges, t_edges, cv2.TM_CCOEFF_NORMED)
                _, max_val, _, _ = cv2.minMaxLoc(res)
                
                if max_val > 0.45 and max_val > best_v:
                    if (char.isupper() and brillo > 120) or (char.islower() and brillo <= 120):
                        best_v, detectada = max_val, char
            grid[r][c] = detectada

    fen_rows = []
    for row in grid:
        vacias, s = 0, ""
        for cell in row:
            if cell == '': vacias += 1
            else:
                if vacias > 0: s += str(vacias); vacias = 0
                s += cell
        if vacias > 0: s += str(vacias)
        fen_rows.append(s)
    print("/".join(fen_rows) + " w - - 0 1")
