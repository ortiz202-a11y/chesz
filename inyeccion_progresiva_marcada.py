import os
f = os.path.expanduser("~/chesz/app/src/main/java/com/chesz/floating/BubbleService.kt")
c = open(f).read()

o_logic = """                val raw = stream?.bufferedReader()?.readText() ?: "Sin respuesta"

                if (rc == 200) {
                    val json = JSONObject(raw)"""

n_logic = """                // Modificación: Lectura progresiva (Streaming) para mostrar FEN instantáneo
                val reader = stream?.bufferedReader()
                var linea: String?
                while (reader?.readLine().also { linea = it } != null) {
                    val json = JSONObject(linea ?: "{}")
                    val fen = json.optString("fen", "")"""

c = c.replace(o_logic, n_logic)
open(f, "w").write(c)
print("✅ Host marcado y preparado.")