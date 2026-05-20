package com.chesz.api

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

object OpeningBook {
    data class VariantItem(val move: String, val name: String)

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

    private fun allChildrenOf(fen: String): List<VariantItem> {
        if (!loaded) return emptyList()
        val arr = childrenJson?.optJSONArray(fenToEpd(fen)) ?: return emptyList()
        val items = ArrayList<VariantItem>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val move = o.optString("move", "")
            val name = o.optString("name", "")
            if (move.isNotEmpty() && name.isNotEmpty()) {
                items.add(VariantItem(move, name))
            }
        }
        return items
    }

    fun randomVariants(fen: String, max: Int = 5): List<VariantItem> {
        val all = allChildrenOf(fen)
        if (all.isEmpty()) return emptyList()
        return all.shuffled().take(max)
    }

    fun subVariantsOf(fen: String, ancestorName: String, max: Int = 5): List<VariantItem> {
        if (ancestorName.isBlank()) return emptyList()
        return allChildrenOf(fen)
            .filter { it.name.startsWith(ancestorName, ignoreCase = true) }
            .take(max)
    }
}
