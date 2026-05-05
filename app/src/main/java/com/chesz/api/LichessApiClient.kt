package com.chesz.api

import com.chesz.engine.StockfishEngine
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

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
        val mateText: String? = null,  // FIX3: Texto de mate ("M# TÚ" o "M# RIVAL")
        val inCheck: Boolean = false,  // FIX5: Indica si el jugador está en jaque
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

                    // FIX3: Calcular texto de mate
                    val mateText = if (top?.mate != null && top.mate != 0) {
                        val absMateMoves = kotlin.math.abs(top.mate)
                        if (top.mate > 0) "M#$absMateMoves TÚ" else "M#$absMateMoves RIVAL"
                    } else null

                    // FIX5: Detectar si el rey está en jaque
                    val inCheck = stockfishEngine?.isInCheck(fen) ?: false

                    callback(
                        LichessInfo(
                            status = Status.SUCCESS,
                            pieceCount = pieceCount,
                            openingName = openingInfo.first,
                            nextMoves = openingInfo.second,
                            bestMove = top?.pv?.getOrNull(0),
                            counterAttack = calculateWinRate(top?.cp, top?.mate, fen),
                            tbResult = null,
                            mateIn = top?.mate?.takeIf { it > 0 },
                            mateText = mateText,
                            inCheck = inCheck,
                        ),
                    )
                }
            } catch (e: Exception) {
                callback(
                    LichessInfo(
                        status = Status.ERROR,
                        pieceCount = countPieces(fen),
                        mateText = null,
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
     * Si hay mate: compara turno del FEN con evaluación. Si turno coincide con mate positivo → 100%, si no → 0%
     */
    private fun calculateWinRate(cp: Int?, mate: Int?, fen: String): String? {
        // FIX4: Manejar mate correctamente
        if (mate != null && mate != 0) {
            // Obtener turno del FEN (segundo campo)
            val turno = fen.split(" ").getOrNull(1) ?: "w"
            // Si mate > 0, el turno actual tiene mate
            // Si mate < 0, el oponente tiene mate
            return if (mate > 0) {
                "100%"  // El jugador en turno tiene mate
            } else {
                "0%"    // El oponente tiene mate
            }
        }

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
        val encodedFen = fen.replace(" ", "%20")
        val url = URL("https://explorer.lichess.ovh/lichess?fen=$encodedFen")

        return try {
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty("User-Agent", "Chesz-App/1.0")

            val responseCode = connection.responseCode
            val response = if (responseCode == 200) {
                connection.inputStream.bufferedReader().readText()
            } else {
                connection.errorStream?.bufferedReader()?.readText() ?: ""
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
                    for (i in 0 until minOf(10, movesArray.length())) {
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
     * Consulta Explorer de Lichess para el modo FM.
     * Extrae los primeros 5 movimientos del array "moves" como pares (e.g. "e4 e5 d4 d5 Nf3").
     */
    private fun queryTablebase(fen: String): LichessInfo {
        val encodedFen = fen.replace(" ", "%20")
        val url = URL("https://explorer.lichess.ovh/lichess?fen=$encodedFen")

        return try {
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty("User-Agent", "Chesz-App/1.0")

            val responseCode = connection.responseCode
            val response = if (responseCode == 200) {
                connection.inputStream.bufferedReader().readText()
            } else {
                connection.errorStream?.bufferedReader()?.readText() ?: ""
            }

            context?.getExternalFilesDir(null)?.let { logDir ->
                try {
                    val logFile = java.io.File(logDir, "logfen_last.txt")
                    val logEntry = "\n=== FM EXPLORER ===\nFEN: $fen\nHTTP $responseCode\n$response\n"
                    logFile.appendText(logEntry)
                } catch (e: Exception) {
                    // Ignorar errores de log
                }
            }

            if (responseCode == 200) {
                val json = JSONObject(response)

                val nextMoves = if (json.has("moves") && json.getJSONArray("moves").length() > 0) {
                    val movesArray = json.getJSONArray("moves")
                    val topMoves = mutableListOf<String>()
                    for (i in 0 until minOf(10, movesArray.length())) {
                        val move = movesArray.getJSONObject(i)
                        if (move.has("san")) topMoves.add(move.getString("san"))
                    }
                    if (topMoves.isNotEmpty()) topMoves.joinToString(" ") else null
                } else {
                    null
                }

                LichessInfo(
                    status = Status.SUCCESS,
                    pieceCount = 0,
                    openingName = null,
                    nextMoves = nextMoves,
                    bestMove = null,
                    counterAttack = null,
                    tbResult = null,
                    mateIn = null,
                    mateText = null,
                )
            } else {
                LichessInfo(status = Status.ERROR, pieceCount = 0, mateText = null)
            }
        } catch (e: Exception) {
            LichessInfo(status = Status.ERROR, pieceCount = 0, mateText = null)
        }
    }
}
