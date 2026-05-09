package com.chesz.floating

import android.content.Context
import java.io.File

object FavoritesStore {
    data class Favorite(val fen: String, val move: String, val name: String)

    private const val FILE_NAME = "Favorites.txt"
    private const val SEP = "|"

    private var loaded = false
    private val items = mutableListOf<Favorite>()

    private fun fileFor(ctx: Context): File? {
        val dir = ctx.getExternalFilesDir(null) ?: return null
        return File(dir, FILE_NAME)
    }

    private fun fenToEpd(fen: String): String {
        val parts = fen.trim().split(" ")
        return if (parts.size >= 4) parts.subList(0, 4).joinToString(" ") else fen
    }

    @Synchronized
    fun ensureLoaded(ctx: Context) {
        if (loaded) return
        items.clear()
        runCatching {
            val f = fileFor(ctx) ?: return@runCatching
            if (!f.exists()) return@runCatching
            f.bufferedReader().useLines { seq ->
                seq.forEach { line ->
                    if (line.isBlank()) return@forEach
                    val parts = line.split(SEP)
                    if (parts.size >= 3) {
                        // Defensivo: si el nombre contuviera '|' lo reconstruimos.
                        val name = parts.drop(2).joinToString(SEP)
                        items.add(Favorite(parts[0], parts[1], name))
                    }
                }
            }
        }
        loaded = true
    }

    @Synchronized
    fun forFen(fen: String): List<Favorite> {
        val key = fenToEpd(fen)
        return items.filter { fenToEpd(it.fen) == key }
    }

    @Synchronized
    fun isFavorite(fen: String, move: String, name: String): Boolean {
        val key = fenToEpd(fen)
        return items.any { fenToEpd(it.fen) == key && it.move == move && it.name == name }
    }

    @Synchronized
    fun add(ctx: Context, fav: Favorite) {
        val key = fenToEpd(fav.fen)
        val exists = items.any { fenToEpd(it.fen) == key && it.move == fav.move && it.name == fav.name }
        if (exists) return
        items.add(fav)
        persist(ctx)
    }

    @Synchronized
    fun remove(ctx: Context, fav: Favorite) {
        val key = fenToEpd(fav.fen)
        val removed = items.removeAll { fenToEpd(it.fen) == key && it.move == fav.move && it.name == fav.name }
        if (removed) persist(ctx)
    }

    @Synchronized
    fun toggle(ctx: Context, fav: Favorite): Boolean {
        return if (isFavorite(fav.fen, fav.move, fav.name)) {
            remove(ctx, fav); false
        } else {
            add(ctx, fav); true
        }
    }

    private fun persist(ctx: Context) {
        runCatching {
            val f = fileFor(ctx) ?: return@runCatching
            f.parentFile?.let { if (!it.exists()) it.mkdirs() }
            f.writeText(items.joinToString("\n") { "${it.fen}${SEP}${it.move}${SEP}${it.name}" })
        }
    }
}
