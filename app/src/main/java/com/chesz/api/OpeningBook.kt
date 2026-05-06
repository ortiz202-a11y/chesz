package com.chesz.api

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

object OpeningBook {
    private var loaded = false
    private val nameByEpd = HashMap<String, String>()
    private var childrenJson: JSONObject? = null

    fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            try {
                context.assets.open("openings.tsv").use { input ->
                    BufferedReader(InputStreamReader(input)).use { br ->
                        br.lineSequence().forEach { line ->
                            val tab = line.indexOf('\t')
                            if (tab > 0) {
                                nameByEpd[line.substring(0, tab)] = line.substring(tab + 1)
                            }
                        }
                    }
                }
                val txt = context.assets.open("openings_children.json").bufferedReader().use { it.readText() }
                childrenJson = JSONObject(txt)
                loaded = true
            } catch (e: Exception) {
                // queda no-cargado; lookup devolverá null
            }
        }
    }

    private fun fenToEpd(fen: String): String {
        val parts = fen.trim().split(" ")
        return if (parts.size >= 4) parts.subList(0, 4).joinToString(" ") else fen
    }

    fun lookup(fen: String): String? {
        if (!loaded) return null
        return nameByEpd[fenToEpd(fen)]
    }

    fun variants(fen: String, max: Int = 5): String? {
        if (!loaded) return null
        val arr = childrenJson?.optJSONArray(fenToEpd(fen)) ?: return null
        if (arr.length() == 0) return null
        val items = mutableListOf<String>()
        for (i in 0 until minOf(max, arr.length())) {
            val o = arr.getJSONObject(i)
            val move = o.optString("move", "")
            val name = o.optString("name", "")
            if (move.isNotEmpty() && name.isNotEmpty()) {
                val shortName = if (name.contains(":")) name.substringAfterLast(":").trim() else name
                items.add("$shortName ($move)")
            }
        }
        return if (items.isNotEmpty()) items.joinToString(", ") else null
    }
}
