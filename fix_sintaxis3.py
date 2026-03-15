import os

filepath = os.path.expanduser("~/chesz/app/src/main/java/com/chesz/floating/BubbleService.kt")
with open(filepath, "r") as f: code = f.read()

start_str = "val stream = if (respCode in 200..299)"
end_str = "} catch (e: Exception) {"

start_idx = code.find(start_str)
end_idx = code.find(end_str, start_idx)

if start_idx != -1 and end_idx != -1:
    end_idx += len(end_str)
    sano = """val stream = if (respCode in 200..299) conn.inputStream else conn.errorStream
                val rawResp = stream?.bufferedReader()?.readText() ?: "Sin respuesta"

                if (respCode == 200) {
                    val json = org.json.JSONObject(rawResp)
                    val fen = json.optString("fen", "SIN_FEN")
                    if (esFenValido64(fen)) {
                        updateDebug("✅ FEN: " + fen)
                    } else {
                        updateDebug("❌ FEN: " + fen + " || CRUDO: " + rawResp)
                    }
                } else {
                    updateDebug("🚨 ERROR: " + str(respCode) + " || CRUDO: " + rawResp)
                }
            } catch (e: Exception) {"""
    
    code = code[:start_idx] + sano + code[end_idx:]
    with open(filepath, "w") as f: f.write(code)
    print("✅ Sintaxis reparada. Cero saltos de linea peligrosos.")
else:
    print("❌ Error: No se encontraron los limites en el archivo.")
