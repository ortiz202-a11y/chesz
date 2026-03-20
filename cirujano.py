import re

path = "/data/data/com.termux/files/home/chesz/app/src/main/java/com/chesz/floating/BubbleService.kt"
with open(path, "r") as f:
    code = f.read()

# 1. Reparar la cadena de texto partida (Error de Sintaxis)
code = re.sub(
    r'updateDebug\("\[FEN IMPERFECTO\].*?\.\.\."\)',
    r'updateDebug("[FEN IMPERFECTO]\\n" + fen + "\\n\\n[SF RAW]\\n" + sfData.take(50) + "...")',
    code, flags=re.DOTALL
)

# 2. Inyectar Timeouts quirurgicamente bajo la conexion
to_old = 'val conn = url.openConnection() as java.net.HttpURLConnection\n                val boundary'
to_new = 'val conn = url.openConnection() as java.net.HttpURLConnection\n                conn.connectTimeout = 8000\n                conn.readTimeout = 10000\n                val boundary'
code = code.replace(to_old, to_new)

with open(path, "w") as f:
    f.write(code)

print("CIRUGIA EXACTA COMPLETADA.")
