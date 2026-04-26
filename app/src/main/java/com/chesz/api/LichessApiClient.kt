package com.chesz.api

import com.chesz.engine.StockfishEngine
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Cliente para las APIs de Lichess.
 * - Eval: Stockfish local (dentro del APK) — sin red.
 * - Opening name: Lichess Opening Explorer.
 * - Endgame ≤7 piezas: Lichess Tablebase.
 */
class LichessApiClient {

    companion object {
        var context: android.content.Context? = null
        var stockfishEngine: StockfishEngine? = null
    }

    data class LichessInfo(
        val status: Status,
        val pieceCount: Int,
        val openingName: String? = null,
        val nextMoves: String? = null,
        val bestMove: String? = null,
        val counterAttack: String? = null,
        val tbResult: String? = null,
        val mateIn: Int? = null,
    )

    enum class Status {
        LOADING,
        ERROR,
        SUCCESS,
    }

    fun queryLichess(fen: String, callback: (LichessInfo) -> Unit) {
        Thread {
            try {
                val pieceCount = countPieces(fen)

                if (pieceCount <= 7) {
                    val tbInfo = queryTablebase(fen)
                    callback(tbInfo.copy(pieceCount = pieceCount))
                } else {
                    val options = stockfishEngine?.analyze(fen) ?: emptyList()
                    val top = options.firstOrNull()
                    val openingInfo = queryOpening(fen)

                    logStockfish(fen, options)

                    callback(
                        LichessInfo(
                            status = Status.SUCCESS,
                            pieceCount = pieceCount,
                            openingName = openingInfo.first,
                            nextMoves = openingInfo.second,
                            bestMove = top?.pv?.getOrNull(0),
                            counterAttack = calculateWinRate(top?.cp),
                            tbResult = null,
                            mateIn = top?.mate?.takeIf { it > 0 },
                        ),
                    )
                }
            } catch (e: Exception) {
                callback(
                    LichessInfo(
                        status = Status.ERROR,
                        pieceCount = countPieces(fen),
                    ),
                )
            }
        }.start()
    }

    private fun countPieces(fen: String): Int {
        val position = fen.substringBefore(" ")
        return position.count { it.isLetter() }
    }

    /**
     * Calcula el Win Rate desde el centipawn score de Stockfish.
     * Fórmula: winRate = 50 + 50 * (2 / (1 + exp(-0.00368208 * cp)) - 1)
     */
    private fun calculateWinRate(cp: Int?): String? {
        if (cp == null) return null
        val winRate = 50 + 50 * (2 / (1 + Math.exp(-0.00368208 * cp)) - 1)
        return "${winRate.toInt()}%"
    }

    private fun logStockfish(fen: String, options: List<StockfishEngine.Option>) {
        context?.getExternalFilesDir(null)?.let { logDir ->
            try {
                val logFile = java.io.File(logDir, "logfen_last.txt")
                val sb = StringBuilder()
                sb.append("\n=== STOCKFISH ===\nFEN: ").append(fen).append('\n')
                options.forEachIndexed { idx, opt ->
                    sb.append("#").append(idx + 1)
                        .append(" d=").append(opt.depth)
                    if (opt.mate != null) sb.append(" mate=").append(opt.mate)
                    if (opt.cp != null) sb.append(" cp=").append(opt.cp)
                    sb.append(" pv=").append(opt.pv.joinToString(" "))
                    sb.append('\n')
                }
                if (options.isEmpty()) sb.append("(no options)\n")
                logFile.appendText(sb.toString())
            } catch (_: Exception) {
                // ignorar errores de log
            }
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
                    pieceCount = 0,
                    openingName = null,
                    nextMoves = null,
                    bestMove = bestMove,
                    counterAttack = null,
                    tbResult = tbResult,
                    mateIn = mateIn,
                )
            } else {
                LichessInfo(status = Status.ERROR, pieceCount = 0)
            }
        } catch (e: Exception) {
            LichessInfo(status = Status.ERROR, pieceCount = 0)
        }
    }
}
