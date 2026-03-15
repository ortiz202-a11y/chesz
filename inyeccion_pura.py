import os

f = os.path.expanduser("~/chesz/app/src/main/java/com/chesz/floating/BubbleService.kt")
c = open(f, "r").read()

o_tap = """                    } else {
                        togglePanel()
                    }
                    dragging = false"""

n_tap = """                    } else {
                        val dist = kotlin.math.hypot(e.rawX - bubbleCenter(), e.rawY - bubbleCenter())
                        if (dist <= dp(30).toFloat()) togglePanel()
                    }
                    dragging = false"""
c = c.replace(o_tap, n_tap)

o_api = """                if (conn.responseCode == 200) {
                    val response = conn.inputStream.bufferedReader().readText()
                    val json = JSONObject(response)
                    val fen = json.optString("fen", "")

                    if (esFenValido64(fen)) {
                        updateDebug("✅ FEN ok")"""

n_api = """                val rc = conn.responseCode
                val stream = if (rc in 200..299) conn.inputStream else conn.errorStream
                val raw = stream?.bufferedReader()?.readText() ?: "Sin respuesta"

                if (rc == 200) {
                    val json = JSONObject(raw)
                    val fen = json.optString("fen", "")

                    if (esFenValido64(fen)) {
                        updateDebug("✅ FEN: " + fen)"""
c = c.replace(o_api, n_api)

o_err = """                    } else {
                        updateDebug("❌ FEN CORRUPTO")
                    }
                } else {
                    updateDebug("❌ Error API: ${conn.responseCode}")
                }"""

n_err = """                    } else {
                        updateDebug("❌ FEN CORRUPTO: " + fen + " | CRUDO: " + raw)
                    }
                } else {
                    updateDebug("❌ ERROR API: " + rc.toString() + " | CRUDO: " + raw)
                }"""
c = c.replace(o_err, n_err)

open(f, "w").write(c)
print("✅ Inyeccion exitosa. Panel bloqueado y FEN visible.")