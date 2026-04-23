package com.chesz.api

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Cliente para las APIs de Lichess.
 * Maneja Cloud Eval, Tablebase y Opening Explorer de manera automática según el número de piezas.
 */
class LichessApiClient {

    companion object {
        var context: android.content.Context? = null
    }

    data class LichessInfo(
        val status: Status,
        val pieceCount: Int,
        val openingName: String? = null,
        val nextMoves: String? = null,
        val bestMove: String? = null,
        val counterAttack: String? = null,
        val tbResult: String? = null,
        val mateIn: Int? = null
    )

    enum class Status {
        LOADING,
        ERROR,
        SUCCESS
    }

    /**
     * Consulta automática según el número de piezas.
     * Si ≤7 → Tablebase, si >7 → Cloud Eval + Opening Explorer
     */
    fun queryLichess(fen: String, callback: (LichessInfo) -> Unit) {
        Thread {
            try {
                val pieceCount = countPieces(fen)

                if (pieceCount <= 7) {
                    // Usar Tablebase
                    val tbInfo = queryTablebase(fen)
                    callback(tbInfo.copy(pieceCount = pieceCount))
                } else {
                    // Usar Cloud Eval + Opening Explorer
                    val cloudInfo = queryCloudEval(fen)
                    val openingInfo = queryOpening(fen)

                    callback(LichessInfo(
                        status = Status.SUCCESS,
                        pieceCount = pieceCount,
                        openingName = openingInfo.first,
                        nextMoves = openingInfo.second,
                        bestMove = cloudInfo.first,
                        counterAttack = cloudInfo.second,
                        tbResult = null,
                        mateIn = null
                    ))
                }
            } catch (e: Exception) {
                callback(LichessInfo(
                    status = Status.ERROR,
                    pieceCount = countPieces(fen)
                ))
            }
        }.start()
    }

    /**
     * Cuenta el número de piezas en el FEN (excluyendo el turno y metadatos).
     */
    private fun countPieces(fen: String): Int {
        val position = fen.substringBefore(" ")
        return position.count { it.isLetter() }
    }

    /**
     * Consulta Cloud Eval de Lichess.
     * Retorna: (bestMove, counterAttack)
     */
    private fun queryCloudEval(fen: String): Pair<String?, String?> {
        val encodedFen = URLEncoder.encode(fen, "UTF-8")
        val url = URL("https://lichess.org/api/cloud-eval?fen=$encodedFen&multiPv=1")

        return try {
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            val responseCode = connection.responseCode
            val response = if (responseCode == 200) {
                connection.inputStream.bufferedReader().readText()
            } else {
                ""
            }

            // Log FEN y respuesta HTTP
            context?.getExternalFilesDir(null)?.let { logDir ->
                try {
                    val logFile = java.io.File(logDir, "logfen_last.txt")
                    val logEntry = "\n=== CLOUD EVAL ===\nFEN: $fen\nHTTP $responseCode\n$response\n"
                    logFile.appendText(logEntry)
                } catch (e: Exception) {
                    // Ignorar errores de log
                }
            }

            if (responseCode == 200) {
                val json = JSONObject(response)

                val bestMove = if (json.has("pvs") && json.getJSONArray("pvs").length() > 0) {
                    val pv = json.getJSONArray("pvs").getJSONObject(0)
                    if (pv.has("moves")) {
                        val moves = pv.getString("moves").split(" ")
                        if (moves.size >= 2) {
                            Pair(moves[0], moves[1])
                        } else if (moves.isNotEmpty()) {
                            Pair(moves[0], null)
                        } else {
                            Pair(null, null)
                        }
                    } else {
                        Pair(null, null)
                    }
                } else {
                    Pair(null, null)
                }

                bestMove
            } else {
                Pair(null, null)
            }
        } catch (e: Exception) {
            Pair(null, null)
        }
    }

    /**
     * Consulta Opening Explorer de Lichess.
     * Retorna: (openingName, nextMoves)
     */
    private fun queryOpening(fen: String): Pair<String?, String?> {
        val encodedFen = URLEncoder.encode(fen, "UTF-8")
        val url = URL("https://explorer.lichess.ovh/master?fen=$encodedFen")

        return try {
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            val responseCode = connection.responseCode
            val response = if (responseCode == 200) {
                connection.inputStream.bufferedReader().readText()
            } else {
                ""
            }

            // Log FEN y respuesta HTTP
            context?.getExternalFilesDir(null)?.let { logDir ->
                try {
                    val logFile = java.io.File(logDir, "logfen_last.txt")
                    val logEntry = "\n=== OPENING EXPLORER ===\nFEN: $fen\nHTTP $responseCode\n$response\n"
                    logFile.appendText(logEntry)
                } catch (e: Exception) {
                    // Ignorar errores de log
                }
            }

            if (responseCode == 200) {
                val json = JSONObject(response)

                val openingName = if (json.has("opening")) {
                    val opening = json.getJSONObject("opening")
                    opening.optString("name", null)
                } else {
                    null
                }

                val nextMoves = if (json.has("moves") && json.getJSONArray("moves").length() > 0) {
                    val movesArray = json.getJSONArray("moves")
                    val topMoves = mutableListOf<String>()
                    for (i in 0 until minOf(4, movesArray.length())) {
                        val move = movesArray.getJSONObject(i)
                        if (move.has("san")) {
                            topMoves.add(move.getString("san"))
                        }
                    }
                    if (topMoves.isNotEmpty()) topMoves.joinToString(" ") else null
                } else {
                    null
                }

                Pair(openingName, nextMoves)
            } else {
                Pair(null, null)
            }
        } catch (e: Exception) {
            Pair(null, null)
        }
    }

    /**
     * Consulta Tablebase de Lichess.
     */
    private fun queryTablebase(fen: String): LichessInfo {
        val encodedFen = URLEncoder.encode(fen, "UTF-8")
        val url = URL("https://tablebase.lichess.ovh/standard?fen=$encodedFen")

        return try {
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            val responseCode = connection.responseCode
            val response = if (responseCode == 200) {
                connection.inputStream.bufferedReader().readText()
            } else {
                ""
            }

            // Log FEN y respuesta HTTP
            context?.getExternalFilesDir(null)?.let { logDir ->
                try {
                    val logFile = java.io.File(logDir, "logfen_last.txt")
                    val logEntry = "\n=== TABLEBASE ===\nFEN: $fen\nHTTP $responseCode\n$response\n"
                    logFile.appendText(logEntry)
                } catch (e: Exception) {
                    // Ignorar errores de log
                }
            }

            if (responseCode == 200) {
                val json = JSONObject(response)

                val category = json.optString("category", null)
                val dtz = json.optInt("dtz", 0)
                val bestMove = if (json.has("moves") && json.getJSONArray("moves").length() > 0) {
                    json.getJSONArray("moves").getJSONObject(0).optString("san", null)
                } else {
                    null
                }

                val tbResult = when (category) {
                    "win" -> "Gana"
                    "loss" -> "Pierde"
                    "draw" -> "Tablas"
                    else -> null
                }

                val mateIn = if (category == "win" && dtz > 0) dtz else null

                LichessInfo(
                    status = Status.SUCCESS,
                    pieceCount = 0, // se actualizará en queryLichess
                    openingName = null,
                    nextMoves = null,
                    bestMove = bestMove,
                    counterAttack = null,
                    tbResult = tbResult,
                    mateIn = mateIn
                )
            } else {
                LichessInfo(status = Status.ERROR, pieceCount = 0)
            }
        } catch (e: Exception) {
            LichessInfo(status = Status.ERROR, pieceCount = 0)
        }
    }
}
