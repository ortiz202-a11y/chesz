import os, re, base64

path = "/data/data/com.termux/files/home/chesz/app/src/main/java/com/chesz/floating/BubbleService.kt"

with open(path, "r") as f:
    code = f.read()

# 1. IDENTIDAD VISUAL: Eliminacion de iconos
iconos = [ "⏳ ", "✅ ", "📂 ", "❌ ", ">_ "]
for ico in iconos:
    code = code.replace(ico, "")

# 2. PATRON DE BUSQUEDA B64 (Evita fallos por espacios y saltos de linea)
pat_red_b64 = "dmFsXHMrcmNccyo9XHMqY29ubi5yZXNwb25zZUNvZGVbXHNcU10qP1x9XHMqY2F0Y2hccypcKFxzKmU6XHMrRXhjZXB0aW9uXHMqXClccypcW1xzKnVwZGF0ZURlYnVnXDhbXildK1wpXHMqXH0="
pat_red = base64.b64decode(pat_red_b64).decode('utf-8')

replace_red = r'''val rc = conn.responseCode
                val stream = if (rc in 200..299) conn.inputStream else conn.errorStream

                if (rc == 200) {
                    val reader = stream?.bufferedReader()
                    var linea: String?
                    var recibioDatos = false
                    while (reader?.readLine().also { linea = it } != null) {
                        recibioDatos = true
                        try {
                            val json = JSONObject(linea ?: "{}")
                            val fen = json.optString("fen", "")
                            root.post { fenTitle.text = fen.substringBefore(" ") }

                            lastFen = fen

                            if (esFenValido64(fen)) {
                                var textoFinal = ""

                                val chessdbData = json.optString("chessdb", "")
                                if (chessdbData.isNotEmpty() && chessdbData != "null") {
                                    textoFinal += "\nCHESSDB: " + chessdbData.trim()
                                }

                                try {
                                    val logDir = getExternalFilesDir(null)
                                    if (logDir != null) {
                                        if (!logDir.exists()) logDir.mkdirs()
                                        val logFile = java.io.File(logDir, "chesz_log.txt")
                                        val ts = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                                        val fData = json.optString("fen", "Vacio")
                                        val sData = json.optString("stockfish", "Vacio")
                                        val logContent = "==== [ $ts ] ====\nFEN RAW: $fData\n\nSTOCKFISH RAW: $sData\n"
                                        logFile.writeText(logContent)
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }

                                val stockfishData = json.optString("stockfish", "")
                                if (stockfishData.isNotEmpty() && stockfishData != "null") {
                                    try {
                                        val sfJson = org.json.JSONObject(stockfishData)
                                        val rawMove = sfJson.optString("bestmove", "")
                                        val evalStr = sfJson.optString("evaluation", "")
                                        val mateStr = sfJson.optString("mate", "")
                                        val contStr = sfJson.optString("continuation", "")

                                        var move = rawMove
                                        var ponder = ""
                                        if (rawMove.startsWith("bestmove ")) {
                                            val parts = rawMove.split(" ")
                                            if (parts.size >= 2) move = parts[1]
                                            if (parts.size >= 4 && parts[2] == "ponder") ponder = parts[3]
                                        }

                                        textoFinal += "\n\n[BM]  ${move.uppercase()}"
                                        if (ponder.isNotEmpty()) textoFinal += "\n[CA]  ${ponder.uppercase()}"

                                        if (mateStr.isNotEmpty() && mateStr != "null") {
                                            textoFinal += "\n[VV]  M$mateStr"
                                        } else if (evalStr.isNotEmpty() && evalStr != "null") {
                                            val d = evalStr.toDoubleOrNull()
                                            if (d != null && d > 0) {
                                                textoFinal += "\n[VV]  +$evalStr"
                                            } else {
                                                textoFinal += "\n[VV]  $evalStr"
                                            }
                                        } else {
                                            textoFinal += "\n[VV]  0.0"
                                        }

                                        if (contStr.isNotEmpty() && contStr != "null") {
                                            val contParts = contStr.split(" ")
                                            var nmString = ""
                                            val limit = Math.min(8, contParts.size)
                                            for (i in 2 until limit) {
                                                val m = contParts[i].uppercase()
                                                if (i % 2 == 0) nmString += "($m) " else nmString += "$m "
                                            }
                                            if (nmString.isNotEmpty()) textoFinal += "\n[NM]  ${nmString.trim()}"
                                        }
                                    } catch (e: Exception) {
                                        textoFinal += "\n[RAW] " + stockfishData.trim()
                                    }
                                }
                                updateDebug(textoFinal.trim())
                            } else {
                                updateDebug("[FALLA] FEN invalido o vacio: ${fen}")
                            }
                        } catch (e: Exception) {
                            updateDebug("[FALLA JSON] ${e.message}")
                        }
                    }
                    if (!recibioDatos) {
                        updateDebug("[FALLA RED] 200 OK pero sin JSON devuelto")
                    }
                } else {
                    val errorBody = stream?.bufferedReader()?.use { it.readText() } ?: "Sin detalles"
                    updateDebug("[FALLA HTTP] Codigo ${rc} -> ${errorBody}")
                }
            } catch (e: Exception) {
                updateDebug("[FALLA RED] ${e.message}")
            }'''

    code = re.sub(pat_red, replace_red, code)

    # 3. PATRON DE TIMEOUT B64
    pat_to_b64 = "dmFsXHMrY29ublxzKj1ccyp1cmwub3BlbkNvbm5lY3Rpb24oKVxzKmFzXHMramF2YS5uZXQuSHR0cFVSTENvbm5lY3Rpb24="
    pat_to = base64.b64decode(pat_to_b64).decode('utf-8')
    replace_to = '''val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 15000'''

    code = re.sub(pat_to, replace_to, code)

    with open(path, "w") as f:
        f.write(code)

    print("CIRUGIA COMPLETADA.")
