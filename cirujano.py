import os, re

path = "/data/data/com.termux/files/home/chesz/app/src/main/java/com/chesz/floating/BubbleService.kt"

with open(path, "r") as f:
    code = f.read()

# Busqueda de la llave de cierre muda antes del catch de red
patron = r'updateDebug\(textoFinal\.trim\(\)\)\s*\}\s*\}\s*\}\s*\}\s*catch\s*\(e:\s*Exception\)\s*\{'

inyeccion_hablada = r'''updateDebug(textoFinal.trim())
                        } else {
                            // EL FEN LLEGO, PERO ESTA CHUECO. IMPRIMIR DE TODOS MODOS:
                            val sfData = json.optString("stockfish", "Sin datos")
                            updateDebug("[FEN IMPERFECTO]\n" + fen + "\n\n[SF RAW]\n" + sfData.take(50) + "...")
                        }
                    } else {
                        updateDebug("[FALLO] JSON vacio o en blanco")
                    }
                } else {
                    val errorBody = stream?.bufferedReader()?.use { it.readText() } ?: "Sin detalles"
                    updateDebug("[FALLO HTTP] Codigo $rc -> $errorBody")
                }
            } catch (e: Exception) {'''

if re.search(patron, code):
    code = re.sub(patron, inyeccion_hablada, code)
    with open(path, "w") as f:
        f.write(code)
    print("CIRUGIA DE RESCATE (ELSE) COMPLETADA.")
else:
    print("ERROR: No se encontro el bloque exacto.")
