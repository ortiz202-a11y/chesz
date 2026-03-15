import os

f = os.path.expanduser("~/chesz/app/src/main/java/com/chesz/floating/BubbleService.kt")
c = open(f).read()

o1 = "                    } else {\n                        if (e.x <= dp(60)) togglePanel()\n                    }"
n1 = "                    } else {\n                        val dist = kotlin.math.hypot(e.rawX - bubbleCenter(), e.rawY - bubbleCenter())\n                        if (dist <= dp(30).toFloat()) togglePanel()\n                    }"
c = c.replace(o1, n1)

s_marker = "val respCode = conn.responseCode"
e_marker = "} catch (e: Exception) {"

if s_marker in c and e_marker in c:
    p1 = c.split(s_marker)[0]
    p2 = c.split(e_marker)[1]
    
    n2 = """val respCode = conn.responseCode
                val stream = if (respCode in 200..299) conn.inputStream else conn.errorStream
                val rawResp = stream?.bufferedReader()?.readText() ?: "Sin respuesta"

                if (respCode == 200) {
                    val json = org.json.JSONObject(rawResp)
                    val fen = json.optString("fen", "SIN_FEN")
                    if (esFenValido64(fen)) {
                        updateDebug("✅ FEN: " + fen)
                    } else {
                        updateDebug("❌ FEN Incompleto: " + fen + " | CRUDO: " + rawResp)
                    }
                } else {
                    updateDebug("🚇 ERROR " + respCode + " | CRUDO: " + rawResp)
                }
            } catch (e: Exception) {"""
    
    c = p1 + n2 + p2

open(f, "w").write(c)
print("✅ Modificacion exitosa")
