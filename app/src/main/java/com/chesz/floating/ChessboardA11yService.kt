package com.chesz.floating

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ChessboardA11yService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var pendingRead: Runnable? = null

    companion object {
        @Volatile private var instance: ChessboardA11yService? = null

        fun requestImmediateRead() {
            val svc = instance
            if (svc == null) {
                com.chesz.AppLog.log("A11y", "requestImmediateRead: instance=null (servicio no conectado)")
                return
            }
            svc.readBoardFromTree()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        com.chesz.AppLog.init(this)
        com.chesz.AppLog.log("A11y", "onServiceConnected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.packageName != "com.chess") return

        com.chesz.AppLog.log("A11y", "evento com.chess tipo=${event.eventType} debounce 300ms")
        pendingRead?.let { handler.removeCallbacks(it) }
        val task = Runnable { readBoardFromTree() }
        pendingRead = task
        handler.postDelayed(task, 300)
    }

    private fun readBoardFromTree() {
        try {
            val root = rootInActiveWindow ?: run {
                com.chesz.AppLog.log("A11y", "readBoardFromTree: rootInActiveWindow=null")
                return
            }
            try {
                val pieces = mutableMapOf<String, Char>()
                collectPieces(root, pieces)
                com.chesz.AppLog.log("A11y", "readBoardFromTree: ${pieces.size} piezas encontradas")
                if (pieces.size >= 2) {
                    val fen = buildFenFromPieces(pieces)
                    com.chesz.AppLog.log("A11y", "FEN construido=$fen")
                    val cb = BubbleService.a11yFenCallback
                    if (cb != null) Thread { cb(fen) }.start()
                } else {
                    com.chesz.AppLog.log("A11y", "pocas piezas (${pieces.size}), dump del árbol:")
                    dumpAllNodes(root)
                }
            } finally {
                root.recycle()
            }
        } catch (e: Throwable) {
            com.chesz.AppLog.log("A11y", "readBoardFromTree ERROR: ${e.message}")
        }
    }

    private fun buildFenFromPieces(pieces: Map<String, Char>): String {
        val board = Array(8) { CharArray(8) { '.' } }
        for ((sq, piece) in pieces) {
            val col = sq[0] - 'a'
            val row = 7 - (sq[1] - '1')
            if (col in 0..7 && row in 0..7) board[row][col] = piece
        }
        val rows = board.map { row ->
            val sb = StringBuilder()
            var empty = 0
            for (c in row) {
                if (c == '.') {
                    empty++
                } else {
                    if (empty > 0) { sb.append(empty); empty = 0 }
                    sb.append(c)
                }
            }
            if (empty > 0) sb.append(empty)
            sb.toString()
        }
        return rows.joinToString("/") + " w KQkq - 0 1"
    }

    private fun collectPieces(node: AccessibilityNodeInfo, pieces: MutableMap<String, Char>) {
        val desc = node.contentDescription?.toString()
        if (desc != null) {
            parsePieceDescription(desc)?.let { (square, piece) -> pieces[square] = piece }
        }
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { child ->
                try {
                    collectPieces(child, pieces)
                } finally {
                    child.recycle()
                }
            }
        }
    }

    private fun dumpAllNodes(node: AccessibilityNodeInfo, depth: Int = 0, counter: IntArray = intArrayOf(0)) {
        if (counter[0] >= 120) return
        val text = node.text?.toString()?.trim()
        val desc = node.contentDescription?.toString()?.trim()
        val cls = node.className?.toString()?.substringAfterLast('.')
        val id = node.viewIdResourceName?.substringAfterLast('/')
        val hasText = !text.isNullOrBlank()
        val hasDesc = !desc.isNullOrBlank()
        if (hasText || hasDesc) {
            counter[0]++
            val indent = "  ".repeat(depth.coerceAtMost(8))
            val t = if (hasText) "txt=\"$text\"" else ""
            val d = if (hasDesc) "desc=\"$desc\"" else ""
            com.chesz.AppLog.log("A11y", "${indent}[$cls id=$id] $t $d".trimEnd())
        }
        for (i in 0 until node.childCount) {
            if (counter[0] >= 120) break
            node.getChild(i)?.let { child ->
                try { dumpAllNodes(child, depth + 1, counter) } finally { child.recycle() }
            }
        }
    }

    // "White King on e1" → ('K', "e1") | "Black pawn on d5" → ('p', "d5")
    private fun parsePieceDescription(desc: String): Pair<String, Char>? {
        val lower = desc.lowercase().trim()
        val onIdx = lower.lastIndexOf(" on ")
        if (onIdx < 0) return null
        val square = lower.substring(onIdx + 4).trim()
        if (square.length < 2 || square[0] !in 'a'..'h' || square[1] !in '1'..'8') return null

        val isWhite = lower.startsWith("white")
        val piece = when {
            "king"   in lower -> if (isWhite) 'K' else 'k'
            "queen"  in lower -> if (isWhite) 'Q' else 'q'
            "rook"   in lower -> if (isWhite) 'R' else 'r'
            "bishop" in lower -> if (isWhite) 'B' else 'b'
            "knight" in lower -> if (isWhite) 'N' else 'n'
            "pawn"   in lower -> if (isWhite) 'P' else 'p'
            else -> return null
        }
        return Pair(square.substring(0, 2), piece)
    }

    override fun onInterrupt() {
        com.chesz.AppLog.log("A11y", "onInterrupt (servicio sigue activo, instance preservada)")
    }
}
