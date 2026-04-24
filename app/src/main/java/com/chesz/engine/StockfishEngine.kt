package com.chesz.engine

import android.content.Context
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class StockfishEngine(private val context: Context) {

    data class Option(
        val move: String,
        val pv: List<String>,
        val cp: Int?,
        val mate: Int?,
        val depth: Int,
    )

    private var process: Process? = null
    private var writer: OutputStreamWriter? = null
    private var reader: BufferedReader? = null
    private val lock = Any()
    @Volatile private var lastRawOutput: String = ""
    @Volatile private var isInitialized = false

    /**
     * Inicia el proceso de Stockfish de forma persistente.
     * Se reutiliza entre llamadas a analyze().
     */
    fun start() {
        synchronized(lock) {
            if (isInitialized && process != null) {
                saveDebugLog("Process already running, reusing", "", null)
                return
            }
            try {
                startLocked()
                isInitialized = true
                saveDebugLog("Stockfish started successfully", "", null)
            } catch (e: Exception) {
                saveDebugLog("Failed to start Stockfish: ${e.message}", "", null)
                isInitialized = false
                throw e
            }
        }
    }

    fun analyze(fen: String, depth: Int = 12): List<Option> {
        synchronized(lock) {
            // Asegurar que el proceso persistente esté iniciado
            if (!isInitialized || process == null) {
                saveDebugLog("Process not initialized, starting...", "", null)
                try {
                    startLocked()
                    isInitialized = true
                } catch (e: Exception) {
                    saveDebugLog("Failed to start in analyze: ${e.message}", fen, null)
                    return emptyList()
                }
            }

            val r = reader ?: run {
                saveDebugLog("Reader is null", fen, null)
                return emptyList()
            }

            return try {
                // Preparar nueva posición
                send("ucinewgame")
                send("isready")
                waitFor("readyok")
                send("position fen $fen")
                send("go depth $depth")

                // Leer línea por línea hasta encontrar bestmove
                val byPv = HashMap<Int, Option>()
                val buffer = StringBuilder()
                var bestmoveFound = false

                while (!bestmoveFound) {
                    val line = r.readLine()
                    if (line == null) {
                        saveDebugLog("Stream closed unexpectedly", fen, null)
                        resetLocked()
                        isInitialized = false
                        return emptyList()
                    }

                    saveDebugLog("<<< RECV: $line", "", null)
                    buffer.append(line).append('\n')

                    when {
                        line.startsWith("bestmove") -> {
                            bestmoveFound = true
                            saveDebugLog("Bestmove found: $line", "", null)
                        }
                        line.startsWith("info ") -> {
                            val parsed = parseInfo(line)
                            if (parsed != null) {
                                byPv[parsed.first] = parsed.second
                            }
                        }
                    }
                }

                lastRawOutput = buffer.toString()
                saveDebugLog("=== ANALYSIS COMPLETE FOR FEN ===", fen, null)
                val results = (1..5).mapNotNull { byPv[it] }
                saveDebugLog("Returning ${results.size} options", fen, null)
                results
            } catch (e: Exception) {
                saveDebugLog("Exception during analysis: ${e.message}", fen, null)
                resetLocked()
                isInitialized = false
                emptyList()
            }
        }
    }

    fun lastOutput(): String = lastRawOutput

    fun shutdown() {
        synchronized(lock) {
            var exitCode: Int? = null
            val pendingOutput = StringBuilder()

            // Capturar cualquier salida pendiente antes del quit
            try {
                val r = reader
                if (r != null && r.ready()) {
                    while (r.ready()) {
                        val line = r.readLine() ?: break
                        pendingOutput.append(line).append('\n')
                    }
                }
            } catch (_: Exception) {}

            try { send("quit") } catch (_: Exception) {}

            // Esperar a que termine y capturar exit code
            try {
                process?.destroy()
                exitCode = runCatching { process?.waitFor() }.getOrNull()
            } catch (_: Exception) {}

            // Log con exit code y stderr/stdout pendiente
            val shutdownInfo = buildString {
                append("SHUTDOWN\n")
                if (exitCode != null) append("Exit code: $exitCode\n")
                if (pendingOutput.isNotEmpty()) {
                    append("Pending output:\n")
                    append(pendingOutput)
                }
            }
            saveDebugLog(shutdownInfo, "", exitCode)

            process = null
            writer = null
            reader = null
            isInitialized = false
        }
    }

    private fun startLocked() {
        val binary = extractBinary()
        val p = ProcessBuilder(binary.absolutePath)
            .redirectErrorStream(true)
            .start()
        process = p
        writer = OutputStreamWriter(p.outputStream)
        reader = BufferedReader(InputStreamReader(p.inputStream))
        send("uci")
        waitFor("uciok")
        send("setoption name MultiPV value 5")
        send("isready")
        waitFor("readyok")
    }

    private fun resetLocked() {
        try { process?.destroy() } catch (_: Exception) {}
        process = null
        writer = null
        reader = null
        isInitialized = false
        saveDebugLog("Process reset", "", null)
    }

    private fun send(cmd: String) {
        val w = writer ?: throw IllegalStateException("writer null")
        w.write(cmd)
        w.write("\n")
        w.flush()
        saveDebugLog(">>> SEND: $cmd", "", null)
    }

    private fun waitFor(token: String) {
        val r = reader ?: throw IllegalStateException("reader null")
        while (true) {
            val line = r.readLine() ?: throw IllegalStateException("stream closed")
            saveDebugLog("<<< RECV: $line", "", null)
            if (line.contains(token)) return
        }
    }

    private fun parseInfo(line: String): Pair<Int, Option>? {
        val tokens = line.split(' ')
        var depth = 0
        var multipv = 1
        var cp: Int? = null
        var mate: Int? = null
        var pv: List<String> = emptyList()
        var i = 1
        while (i < tokens.size) {
            when (tokens[i]) {
                "depth" -> {
                    depth = tokens.getOrNull(i + 1)?.toIntOrNull() ?: 0
                    i += 2
                }
                "multipv" -> {
                    multipv = tokens.getOrNull(i + 1)?.toIntOrNull() ?: 1
                    i += 2
                }
                "score" -> {
                    when (tokens.getOrNull(i + 1)) {
                        "cp" -> {
                            cp = tokens.getOrNull(i + 2)?.toIntOrNull()
                            i += 3
                        }
                        "mate" -> {
                            mate = tokens.getOrNull(i + 2)?.toIntOrNull()
                            i += 3
                        }
                        else -> i += 2
                    }
                }
                "pv" -> {
                    pv = tokens.subList(i + 1, tokens.size).filter { it.isNotBlank() }
                    i = tokens.size
                }
                else -> i++
            }
        }
        if (pv.isEmpty()) return null
        return multipv to Option(pv[0], pv, cp, mate, depth)
    }

    private fun extractBinary(): File {
        val outFile = File(context.filesDir, "stockfish")
        val assetSize = runCatching {
            context.assets.openFd("stockfish").use { it.length }
        }.getOrNull()
        val needsCopy = !outFile.exists() ||
            outFile.length() == 0L ||
            (assetSize != null && outFile.length() != assetSize)
        if (needsCopy) {
            context.assets.open("stockfish").use { input ->
                outFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        if (!outFile.canExecute()) outFile.setExecutable(true, false)
        return outFile
    }

    private fun saveDebugLog(output: String, fen: String, exitCode: Int?) {
        try {
            val debugFile = File("/storage/emulated/0/Android/data/com.chesz/files/logfen_last.txt")
            debugFile.parentFile?.mkdirs()

            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
            val logEntry = buildString {
                append("\n========================================\n")
                append("STOCKFISH DEBUG - $timestamp\n")
                if (fen.isNotEmpty()) append("FEN: $fen\n")
                if (exitCode != null) append("EXIT CODE: $exitCode\n")
                append("----------------------------------------\n")
                append(output)
                append("\n")
            }

            debugFile.appendText(logEntry)
        } catch (e: Exception) {
            android.util.Log.e("StockfishEngine", "Failed to save debug log", e)
        }
    }
}
