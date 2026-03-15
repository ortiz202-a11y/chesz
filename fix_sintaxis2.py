import os, re
filepath = os.path.expanduser("~/chesz/app/src/main/java/com/chesz/floating/BubbleService.kt")
with open(filepath, "r") as f: code = f.read()

patron = r"val stream = if \(respCode.*?\} catch \(e: Exception\)"

sano = """val stream = if (respCode in 200..299) conn.inputStream else conn.errorStream
                val rawResp = stream?.bufferedReader()?.readText() ?: "Sin respuesta"

                if (respCode == 200) {
                    val json = org.json.JSONObject(rawResp)
                    val fen = json.optString("fen", "SIN_FEN")
                    if (esFenValido64(fen)) {
                        updateDebug("✅ FEN: " + fen)
                    } else {
                        updateDebug("❌ FEN Incompleto: \\n" + fen + "\\n\\nCRUDO:\\n" + rawResp)
                    }
                } else {
                    updateDebug("🚨 ERROR " + respCode + "\\nCRUDO:\\n" + rawResp)
                }
            } catch (e: Exception)"""

if re.search(patron, code, re.DOTALL):
    code = re.sub(patron, sano, code, flags=re.DOTALL)
    with open(filepath, "w") as f: f.write(code)
    print("✅ Sintaxis reparada. Lista para compilar.")
else:
    print("❌ Error: No se encontro el bloque.")
