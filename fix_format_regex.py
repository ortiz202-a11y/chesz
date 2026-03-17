import sys
import re

with open('app/src/main/java/com/chesz/floating/BubbleService.kt', 'r', encoding='utf8') as f:
    content = f.read()

new_block = '''                            val stockfishData = json.optString("stockfish", "")
                            if (stockfishData.isNotEmpty() && stockfishData != "null") {
                                try {
                                    val sfJson = org.json.JSONObject(stockfishData)
                                    val rawMove = sfJson.optString("bestmove", "")
                                    val eval = sfJson.optDouble("evaluation", 0.0)
                                    val mate = sfJson.optString("mate", "null")
                                    val continuation = sfJson.optString("continuation", "")
                                    
                                    var move = rawMove
                                    var ponder = ""
                                    if (rawMove.startsWith("bestmove ")) {
                                        val parts = rawMove.split(" ")
                                        if (parts.size >= 2) move = parts[1]
                                        if (parts.size >= 4 && parts[2] == "ponder") ponder = parts[3]
                                    }
                                    
                                    textoFinal += "\n\n[BM] >  ${move.uppercase()}"
                                    if (ponder.isNotEmpty()) textoFinal += "\n[CA] >  ${ponder.uppercase()}"
                                    if (mate != "null" && mate.isNotEmpty()) {
                                        textoFinal += "\n[VV] >  M$mate"
                                    } else {
                                        val eStr = if (eval > 0) "+$eval" else "$eval"
                                        textoFinal += "\n[VV] >  $eStr"
                                    }
                                    
                                    if (continuation.isNotEmpty()) {
                                        val contParts = continuation.split(" ")
                                        var nmString = ""
                                        val limit = Math.min(8, contParts.size)
                                        for (i in 2 until limit) {
                                            val m = contParts[i].uppercase()
                                            if (i % 2 == 0) {
                                                nmString += "($m) "
                                            } else {
                                                nmString += "$m "
                                            }
                                        }
                                        if (nmString.isNotEmpty()) {
                                            textoFinal += "\n[NM] >  ${nmString.trim()}"
                                        }
                                    }
                                } catch (e: Exception) {
                                    textoFinal += "\n[ RAW ]> " + stockfishData.trim()
                                }
                            }'''

pattern = r'val stockfishData = json\.optString\("stockfish", ""\)\s*if \(stockfishData\.isNotEmpty\(\) && stockfishData != "null"\) \{.*?\n}'

if re.search(pattern, content, re.DOTALL):
    content = re.sub(pattern, new_block.replace('\', '\\'), content, flags=re.DOTALL, count=1)
    with open('app/src/main/java/com/chesz/floating/BubbleService.kt', 'w', encoding='utf8') as f:
        f.write(content)
    print("Cirugia Exitosa: Formato Tactico con [NM] inyectado.")
else:
    print("ERROR: No se encontro el patron en el archivo. Verifica que la linea val stockfishData siga ahi.")
