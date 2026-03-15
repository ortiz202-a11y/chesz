import os
import re

filepath = os.path.expanduser("~/chesz/app/src/main/java/com/chesz/floating/BubbleService.kt")
with open(filepath, "r") as f: code = f.read()

# 1. FIX TAP MATEMÁTICO (Fuerza Bruta)
roto_tap = """                    } else {
                        if (e.x <= dp(60)) togglePanel()
                    }"""
sano_tap = """                    } else {
                        val dist = kotlin.math.hypot(e.rawX - bubbleCenterX(), e.rawY - bubbleCenterY())
                        if (dist <= dp(30).toFloat()) togglePanel()
                    }"""
code = code.replace(roto_tap, sano_tap)

# 2. FIX CONSOLA (Bloqueo de sobrescritura)
patron = r"val respCode = conn\.responseCode.*?\} catch \(e: Exception\) \{"
reemplazo = """val respCode = conn.responseCode
                val stream = if (respCode in 200..299) conn.inputStream else conn.errorStream
                val rawResp = stream?.bufferedReader()?.readText() ?: "Sin respuesta"

                if (respCode == 200) {
                    val json = org.json.JSONObject(rawResp)
                    val fen = json.optString("fen", "SIN_FEN")
                    if (esFenValido64(fen)) {
                        updateDebug("✅ FEN: " + fen)
                    } else {
                        updateDebug("❌ FEN Incompleto:\n" + fen + "\n\nCRUDO:\n" + rawResp)
                    }
                } else {
                    updateDebug("🚨 ERROR " + respCode + "\nCRUDO:\n" + rawResp)
                }
            } catch (e: Exception) {"""

code = re.sub(patron, reemplazo, code, flags=re.DOTALL)

with open(filepath, "w") as f: f.write(code)
print("✅ Tap bloqueado por radio + Consola Blindada")
