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
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.packageName != "com.chess") return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        // Debounce 300 ms para que el árbol se estabilice tras mover una pieza
        pendingRead?.let { handler.removeCallbacks(it) }
        val task = Runnable { readBoardFromTree() }
        pendingRead = task
        handler.postDelayed(task, 300)
    }

    private fun readBoardFromTree() {
        val root = rootInActiveWindow ?: return
        val pieces = mutableMapOf<String, Char>()
        try {
            collectPieces(root, pieces)
        } finally {
            root.recycle()
        }
        if (pieces.isEmpty()) return

        val fen = fenEngine.buildFenFromPieces(pieces)
        BubbleService.a11yFenCallback?.invoke(fen)
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
