package com.chesz.floating

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.chesz.engine.FenEngine

class ChessboardA11yService : AccessibilityService() {

    private val fenEngine by lazy { FenEngine(this) }
    private val handler = Handler(Looper.getMainLooper())
    private var pendingRead: Runnable? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        com.chesz.AppLog.init(this)
        com.chesz.AppLog.log("A11y", "onServiceConnected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.packageName != "com.chess") return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        com.chesz.AppLog.log("A11y", "evento com.chess tipo=${event.eventType} debounce 300ms")
        // Debounce 300 ms para que el árbol se estabilice tras mover una pieza
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
            val pieces = mutableMapOf<String, Char>()
            try {
                collectPieces(root, pieces)
            } finally {
                root.recycle()
            }
            if (pieces.isEmpty()) {
                android.util.Log.d("CheszA11y", "árbol vacío, sin piezas")
                com.chesz.AppLog.log("A11y", "readBoardFromTree: árbol vacío, 0 piezas")
                return
            }
            val fen = fenEngine.buildFenFromPieces(pieces)
            val fenPieces = fen.substringBefore(" ")
            com.chesz.AppLog.log("A11y", "FEN construido piezas=${pieces.size} fen=$fen")
            if ('K' !in fenPieces || 'k' !in fenPieces) {
                android.util.Log.w("CheszA11y", "FEN incompleto (faltan reyes): $fen")
                com.chesz.AppLog.log("A11y", "FEN RECHAZADO faltan reyes: $fen")
                return
            }
            val cb = BubbleService.a11yFenCallback
            if (cb == null) {
                com.chesz.AppLog.log("A11y", "a11yFenCallback=null BubbleService no está vivo")
            } else {
                com.chesz.AppLog.log("A11y", "invocando a11yFenCallback con fen=$fen")
                cb.invoke(fen)
            }
        } catch (e: Throwable) {
            android.util.Log.e("CheszA11y", "error leyendo árbol: ${e.message}", e)
            com.chesz.AppLog.log("A11y", "readBoardFromTree ERROR: ${e.message}")
        }
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

    override fun onInterrupt() {}
}
