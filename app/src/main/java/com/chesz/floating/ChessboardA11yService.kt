package com.chesz.floating

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast

class ChessboardA11yService : AccessibilityService() {

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
            try {
                dumpAllNodes(root)
            } finally {
                root.recycle()
            }
        } catch (e: Throwable) {
            android.util.Log.e("CheszA11y", "error leyendo árbol: ${e.message}", e)
            com.chesz.AppLog.log("A11y", "readBoardFromTree ERROR: ${e.message}")
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
