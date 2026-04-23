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

    fun start() {
        synchronized(lock) {
            if (process != null) return
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
    }

    fun analyze(fen: String, depth: Int = 12): List<Option> {
        synchronized(lock) {
            if (process == null) {
                try {
                    startLocked()
                } catch (e: Exception) {
                    return emptyList()
                }
            }
            val r = reader ?: return emptyList()
            return try {
                send("ucinewgame")
                send("isready")
                waitFor("readyok")
                send("position fen $fen")
                send("go depth $depth")

                val byPv = HashMap<Int, Option>()
                val buffer = StringBuilder()
                while (true) {
                    val line = r.readLine() ?: run {
                        resetLocked()
                        return emptyList()
                    }
                    buffer.append(line).append('\n')
                    if (line.startsWith("bestmove")) break
                    if (!line.startsWith("info ")) continue
                    val parsed = parseInfo(line) ?: continue
                    byPv[parsed.first] = parsed.second
                }
                lastRawOutput = buffer.toString()
                saveDebugLog(buffer.toString(), fen, null)
                (1..5).mapNotNull { byPv[it] }
            } catch (e: Exception) {
                saveDebugLog("Exception: ${e.message}", fen, null)
                resetLocked()
                emptyList()
            }
        }
    }

    fun lastOutput(): String = lastRawOutput

    fun shutdown() {
        synchronized(lock) {
            var exitCode: Int? = null
            try { send("quit") } catch (_: Exception) {}
            try {
                process?.destroy()
                exitCode = runCatching { process?.waitFor() }.getOrNull()
            } catch (_: Exception) {}
            saveDebugLog("SHUTDOWN", "", exitCode)
            process = null
            writer = null
            reader = null
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
    }

    private fun send(cmd: String) {
        val w = writer ?: throw IllegalStateException("writer null")
        w.write(cmd)
        w.write("\n")
        w.flush()
    }

    private fun waitFor(token: String) {
        val r = reader ?: throw IllegalStateException("reader null")
        while (true) {
            val line = r.readLine() ?: throw IllegalStateException("stream closed")
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
