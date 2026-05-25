package com.chesz.floating

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
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
        com.chesz.AppLog.log("A11y", "RAW pkg=${event.packageName} tipo=${event.eventType}")
        if (event.packageName != "com.chess") return

        com.chesz.AppLog.log("A11y", "evento com.chess tipo=${event.eventType} debounce 300ms")
        handler.post { Toast.makeText(this, "A11y: evento Chess tipo=${event.eventType}", Toast.LENGTH_SHORT).show() }
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
            val debugSample = mutableListOf<String>()
            try {
                collectPieces(root, pieces)
                if (pieces.isEmpty()) collectDescriptions(root, debugSample, max = 5)
            } finally {
                root.recycle()
            }
            if (pieces.isEmpty()) {
                android.util.Log.d("CheszA11y", "árbol vacío, sin piezas")
                com.chesz.AppLog.log("A11y", "readBoardFromTree: árbol vacío, 0 piezas")
                val sampleText = if (debugSample.isEmpty()) "NINGUNA desc" else debugSample.joinToString(" | ")
                handler.post { Toast.makeText(this, "Árbol vacío.\n$sampleText", Toast.LENGTH_LONG).show() }
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
                handler.post { Toast.makeText(this, "FEN OK pero BubbleService MUERTO\n$fen", Toast.LENGTH_LONG).show() }
            } else {
                com.chesz.AppLog.log("A11y", "invocando a11yFenCallback con fen=$fen")
                handler.post { Toast.makeText(this, "FEN enviado a Bubble\n${fen.substringBefore(" ")}", Toast.LENGTH_SHORT).show() }
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

    private fun collectDescriptions(node: AccessibilityNodeInfo, out: MutableList<String>, max: Int) {
        if (out.size >= max) return
        node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { out.add(it.take(40)) }
        for (i in 0 until node.childCount) {
            if (out.size >= max) break
            node.getChild(i)?.let { child ->
                try { collectDescriptions(child, out, max) } finally { child.recycle() }
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
