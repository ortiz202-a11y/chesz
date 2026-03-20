import os

path = "/data/data/com.termux/files/home/chesz/app/src/main/java/com/chesz/floating/BubbleService.kt"

with open(path, "r") as f:
    code = f.read()

old_code = """                    if (respuesta.isNotBlank()) {
                        val json = JSONObject(respuesta) //
                        val json = JSONObject(linea ?: "{}")
                        val fen = json.optString("fen", "")"""

new_code = """                    if (respuesta.isNotBlank()) {
                        val json = JSONObject(respuesta)
                        val fen = json.optString("fen", "")"""

if old_code in code:
    code = code.replace(old_code, new_code)
    with open(path, "w") as f:
        f.write(code)
    print("CIRUGIA DE SINTAXIS COMPLETADA.")
else:
    print("ERROR: No se encontro el bloque exacto.")
