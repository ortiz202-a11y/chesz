package com.chesz.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import java.io.File
import java.io.FileOutputStream
import kotlin.math.*

/**
 * Motor de reconocimiento de tablero de ajedrez.
 *
 * Flujo: Bitmap (720×720) → grises → [por cada casilla 90×90: Canny → Dilate]
 *        → matchTemplate contra siluetas precalculadas → cadena FEN
 *
 * Las plantillas (siluetas ya procesadas con Canny+Dilate) deben estar en
 * app/src/main/assets/siluetas/  (wp1.png … wp8.png, wn1.png, etc.)
 */
class FenEngine(private val context: Context) {

    // Mapa: símbolo FEN → lista de nombres de archivo de plantilla
    private val pieceTemplateNames: Map<Char, List<String>> = mapOf(
        'P' to (1..8).map { "wp$it" },
        'N' to listOf("wn1", "wn2"),
        'B' to listOf("wb1", "wb2"),
        'R' to listOf("wr1", "wr2"),
        'Q' to listOf("wq"),
        'K' to listOf("wk"),
        'p' to (1..8).map { "bp$it" },
        'n' to listOf("bn1", "bn2"),
        'b' to listOf("bb1", "bb2"),
        'r' to listOf("br1", "br2"),
        'q' to listOf("bq"),
        'k' to listOf("bk"),
    )

    // Plantillas cargadas: símbolo FEN → lista de arrays de píxeles (SQUARE×SQUARE)
    private val templates = mutableMapOf<Char, List<IntArray>>()

    // ─────────────────────────────────────────────
    // API pública
    // ─────────────────────────────────────────────

    /**
     * Carga todas las plantillas desde assets/siluetas/.
     * Llamar una sola vez (p.ej. en onCreate del servicio).
     */
    fun loadTemplates() {
        templates.clear()
        for ((symbol, names) in pieceTemplateNames) {
            val loaded = names.mapNotNull { name ->
                runCatching {
                    context.assets.open("siluetas/$name.png").use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }?.let { bmp ->
                        bitmapToGray(bmp).also { bmp.recycle() }
                    }
                }.getOrNull()
            }
            if (loaded.isNotEmpty()) templates[symbol] = loaded
        }
    }

    /**
     * Procesa un Bitmap de 720×720 px (tablero recortado) y devuelve la cadena FEN.
     * Requiere haber llamado [loadTemplates] antes.
     *
     * @return FEN completo, e.g. "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w - - 0 1"
     */
    fun processBoard(board: Bitmap): String {
        val gray = bitmapToGray(board)
        saveDebugGray(gray)
        val grid = Array(BOARD_SQUARES) { CharArray(BOARD_SQUARES) { EMPTY } }

        for (row in 0 until BOARD_SQUARES) {
            for (col in 0 until BOARD_SQUARES) {
                grid[row][col] = detectPiece(gray, row, col)
            }
        }
        val finalGrid = if (isBoardFlipped(grid)) flipGrid(grid) else grid
        return buildFen(finalGrid)
    }

    /**
     * Detecta si el tablero está orientado desde la perspectiva de las negras.
     * Estrategia principal: el rey blanco (K) en la mitad superior indica tablero girado.
     * Fallback: si las piezas blancas tienen un promedio de fila menor que las negras,
     * el tablero está girado (normalmente blancas están en filas bajas = números altos).
     */
    private fun isBoardFlipped(grid: Array<CharArray>): Boolean {
        // Buscar rey blanco primero (el indicador más confiable)
        for (row in 0 until BOARD_SQUARES) {
            for (col in 0 until BOARD_SQUARES) {
                if (grid[row][col] == 'K') {
                    return row < BOARD_SQUARES / 2
                }
            }
        }
        // Fallback: centro de masa de piezas blancas vs negras
        var whiteRowSum = 0; var whiteCount = 0
        var blackRowSum = 0; var blackCount = 0
        for (row in 0 until BOARD_SQUARES) {
            for (col in 0 until BOARD_SQUARES) {
                val p = grid[row][col]
                if (p == EMPTY) continue
                if (p.isUpperCase()) { whiteRowSum += row; whiteCount++ }
                else                 { blackRowSum += row; blackCount++ }
            }
        }
        if (whiteCount == 0 || blackCount == 0) return false
        // Perspectiva normal: blancas en filas altas (6-7), negras en filas bajas (0-1)
        return (whiteRowSum.toFloat() / whiteCount) < (blackRowSum.toFloat() / blackCount)
    }

    /** Gira el tablero 180° (equivale a mirarlo desde el otro lado) */
    private fun flipGrid(grid: Array<CharArray>): Array<CharArray> =
        Array(BOARD_SQUARES) { row ->
            CharArray(BOARD_SQUARES) { col ->
                grid[BOARD_SQUARES - 1 - row][BOARD_SQUARES - 1 - col]
            }
        }

    // ─────────────────────────────────────────────
    // Detección de pieza en una casilla
    // ─────────────────────────────────────────────

    private fun detectPiece(boardGray: IntArray, row: Int, col: Int): Char {
        val square = extractSquare(boardGray, row, col)
        val silueta = cannyDilate(square)

        val isWhiteZone = isPieceWhite(square, row)

        var bestScore = MATCH_THRESHOLD
        var bestSymbol = EMPTY

        for ((symbol, templateList) in templates) {
            val symbolIsWhite = symbol.isUpperCase()
            // Descartar bando contrario antes de calcular (optimización)
            if (symbolIsWhite != isWhiteZone) continue

            for (template in templateList) {
                val score = matchNormalized(silueta, template)
                if (score > bestScore) {
                    bestScore = score
                    bestSymbol = symbol
                }
            }
        }
        return bestSymbol
    }

    /**
     * Determina el bando de la pieza usando contraste relativo:
     *   1. Media de la región central 30×30 px (más robusta que un solo píxel).
     *   2. Umbral dinámico = (min + max) / 2 de toda la casilla.
     *   → blanca si el centro es más luminoso que el punto medio del rango.
     *
     * No depende de ningún umbral absoluto, por lo que funciona con cualquier
     * tema de tablero o condición de iluminación.
     * Si el contraste global es muy bajo (casilla vacía), la clasificación
     * no importa: matchNormalized no superará MATCH_THRESHOLD de todos modos.
     *
     * [row=1] Bias para la fila de peones negros (rank 7 FEN): el umbral se
     * eleva en ROW1_WHITE_BIAS para compensar el leve exceso de brillo central
     * que provoca falsos positivos (P en lugar de p).
     */
    private fun isPieceWhite(square: IntArray, row: Int = 0): Boolean {
        val s = SQUARE_SIZE
        val c0 = CENTER_CROP_START
        val c1 = CENTER_CROP_END

        // Media de la región central
        var sumCenter = 0L
        for (y in c0 until c1) {
            for (x in c0 until c1) {
                sumCenter += square[y * s + x]
            }
        }
        val centerMean = sumCenter.toFloat() / ((c1 - c0) * (c1 - c0))

        // Min y max de toda la casilla
        var minV = 255
        var maxV = 0
        for (v in square) {
            if (v < minV) minV = v
            if (v > maxV) maxV = v
        }

        // Sin contraste suficiente → casilla vacía, la clasificación no importa
        if (maxV - minV < MIN_CONTRAST) return true

        // Bias de fila: fila 1 o fila 6 pueden ser la fila de peones negros
        // dependiendo de la orientación del tablero (blancas arriba o negras arriba)
        val bias = if (row == 1 || row == 6) ROW1_WHITE_BIAS else 0f

        // Blanca si el centro supera el punto medio del rango de la casilla (± bias)
        return centerMean > (minV + maxV) / 2.0f + bias
    }

    // ─────────────────────────────────────────────
    // Debug: guarda chesz_gray.png junto a chesz_log.txt
    // ─────────────────────────────────────────────

    private fun saveDebugGray(gray: IntArray) {
        runCatching {
            val bmp = Bitmap.createBitmap(BOARD_SIZE, BOARD_SIZE, Bitmap.Config.ARGB_8888)
            for (i in gray.indices) {
                val v = gray[i]
                bmp.setPixel(i % BOARD_SIZE, i / BOARD_SIZE, Color.rgb(v, v, v))
            }
            val dir = context.getExternalFilesDir(null) ?: return
            FileOutputStream(File(dir, "chesz_gray.png")).use { out ->
                bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bmp.recycle()
        }
    }

    // ─────────────────────────────────────────────
    // Paso 1: Bitmap → array de grises (luma ITU-R BT.601)
    // ─────────────────────────────────────────────

    private fun bitmapToGray(bmp: Bitmap): IntArray {
        val w = bmp.width
        val h = bmp.height
        val argb = IntArray(w * h)
        bmp.getPixels(argb, 0, w, 0, 0, w, h)
        return IntArray(w * h) { i ->
            val c = argb[i]
            val r = Color.red(c)
            val g = Color.green(c)
            val b = Color.blue(c)
            (0.299 * r + 0.587 * g + 0.114 * b).roundToInt().coerceIn(0, 255)
        }
    }

    // ─────────────────────────────────────────────
    // Paso 2: Extraer casilla 90×90 del tablero 720×720
    // ─────────────────────────────────────────────

    private fun extractSquare(boardGray: IntArray, row: Int, col: Int): IntArray {
        val s = SQUARE_SIZE
        val offsetY = row * s
        val offsetX = col * s
        return IntArray(s * s) { i ->
            val r = i / s
            val c = i % s
            boardGray[(offsetY + r) * BOARD_SIZE + (offsetX + c)]
        }
    }

    // ─────────────────────────────────────────────
    // Paso 3: Canny + Dilate (lógica idéntica al engine Python)
    // ─────────────────────────────────────────────

    private fun cannyDilate(pixels: IntArray): IntArray {
        val blurred = gaussianBlur5x5(pixels)
        val edges   = canny(blurred, CANNY_LOW, CANNY_HIGH)
        return dilate3x3(edges)
    }

    /** Gaussian blur 5×5 — reduce ruido antes de Canny */
    private fun gaussianBlur5x5(src: IntArray): IntArray {
        // Kernel Pascal normalizado (σ ≈ 1.0), suma = 273
        val k = intArrayOf(
             1,  4,  7,  4,  1,
             4, 16, 26, 16,  4,
             7, 26, 41, 26,  7,
             4, 16, 26, 16,  4,
             1,  4,  7,  4,  1
        )
        val s = SQUARE_SIZE
        val dst = IntArray(s * s)
        for (y in 0 until s) {
            for (x in 0 until s) {
                var sum = 0
                for (ky in -2..2) {
                    for (kx in -2..2) {
                        val ny = (y + ky).coerceIn(0, s - 1)
                        val nx = (x + kx).coerceIn(0, s - 1)
                        sum += src[ny * s + nx] * k[(ky + 2) * 5 + (kx + 2)]
                    }
                }
                dst[y * s + x] = (sum / 273).coerceIn(0, 255)
            }
        }
        return dst
    }

    /**
     * Detección de bordes Canny:
     *   1. Gradientes Sobel
     *   2. Non-maximum suppression (NMS)
     *   3. Hysteresis con umbral doble [low, high]
     */
    private fun canny(src: IntArray, low: Int, high: Int): IntArray {
        val s = SQUARE_SIZE
        val mag = FloatArray(s * s)
        val dir = FloatArray(s * s) // ángulo en grados 0..180

        // --- Gradientes Sobel ---
        for (y in 1 until s - 1) {
            for (x in 1 until s - 1) {
                val tl = src[(y-1)*s+(x-1)]; val tm = src[(y-1)*s+x]; val tr = src[(y-1)*s+(x+1)]
                val ml = src[  y  *s+(x-1)];                            val mr = src[  y  *s+(x+1)]
                val bl = src[(y+1)*s+(x-1)]; val bm = src[(y+1)*s+x]; val br = src[(y+1)*s+(x+1)]

                val gx = -tl - 2*ml - bl + tr + 2*mr + br
                val gy = -tl - 2*tm - tr + bl + 2*bm + br

                mag[y*s+x] = sqrt((gx*gx + gy*gy).toFloat())
                // Normalizar ángulo a 0..180 para NMS
                dir[y*s+x] = ((atan2(gy.toFloat(), gx.toFloat()) * RAD_TO_DEG) + 180f) % 180f
            }
        }

        // --- Non-maximum suppression ---
        val nms = FloatArray(s * s)
        for (y in 1 until s - 1) {
            for (x in 1 until s - 1) {
                val angle = dir[y*s+x]
                val m = mag[y*s+x]
                val (n1, n2) = when {
                    angle < 22.5f  || angle >= 157.5f -> // Horizontal (0°)
                        Pair(mag[y*s+(x+1)], mag[y*s+(x-1)])
                    angle < 67.5f                      -> // Diagonal (45°)
                        Pair(mag[(y-1)*s+(x+1)], mag[(y+1)*s+(x-1)])
                    angle < 112.5f                     -> // Vertical (90°)
                        Pair(mag[(y-1)*s+x], mag[(y+1)*s+x])
                    else                               -> // Diagonal (135°)
                        Pair(mag[(y-1)*s+(x-1)], mag[(y+1)*s+(x+1)])
                }
                nms[y*s+x] = if (m >= n1 && m >= n2) m else 0f
            }
        }

        // --- Umbral doble (hysteresis) ---
        val edges = IntArray(s * s) // 0=suprimido, 128=débil, 255=fuerte
        for (i in 0 until s * s) {
            edges[i] = when {
                nms[i] >= high -> STRONG_EDGE
                nms[i] >= low  -> WEAK_EDGE
                else           -> 0
            }
        }

        // Conectar bordes débiles que tocan al menos un borde fuerte (8-vecinos)
        for (y in 1 until s - 1) {
            for (x in 1 until s - 1) {
                if (edges[y*s+x] != WEAK_EDGE) continue
                val connected = (-1..1).any { dy ->
                    (-1..1).any { dx -> edges[(y+dy)*s+(x+dx)] == STRONG_EDGE }
                }
                edges[y*s+x] = if (connected) STRONG_EDGE else 0
            }
        }
        // Eliminar débiles no conectados
        for (i in 0 until s * s) {
            if (edges[i] == WEAK_EDGE) edges[i] = 0
        }
        return edges
    }

    /** Dilatación morfológica 3×3 con elemento estructurante lleno (igual que np.ones(3,3)) */
    private fun dilate3x3(src: IntArray): IntArray {
        val s = SQUARE_SIZE
        val dst = IntArray(s * s)
        for (y in 0 until s) {
            for (x in 0 until s) {
                var maxVal = 0
                for (ky in -1..1) {
                    for (kx in -1..1) {
                        val ny = (y + ky).coerceIn(0, s - 1)
                        val nx = (x + kx).coerceIn(0, s - 1)
                        val v = src[ny * s + nx]
                        if (v > maxVal) maxVal = v
                    }
                }
                dst[y * s + x] = maxVal
            }
        }
        return dst
    }

    // ─────────────────────────────────────────────
    // Paso 4: Template Matching — TM_CCOEFF_NORMED
    // Como source y template son del mismo tamaño, el resultado es
    // el coeficiente de correlación de Pearson entre ambas imágenes.
    // ─────────────────────────────────────────────

    private fun matchNormalized(source: IntArray, template: IntArray): Float {
        val n = source.size
        var sumS = 0L
        var sumT = 0L
        for (i in 0 until n) { sumS += source[i]; sumT += template[i] }
        val meanS = sumS.toFloat() / n
        val meanT = sumT.toFloat() / n

        var num  = 0f
        var denS = 0f
        var denT = 0f
        for (i in 0 until n) {
            val s = source[i]   - meanS
            val t = template[i] - meanT
            num  += s * t
            denS += s * s
            denT += t * t
        }
        val denom = sqrt(denS * denT)
        return if (denom < 1e-8f) 0f else (num / denom).coerceIn(-1f, 1f)
    }

    // ─────────────────────────────────────────────
    // Paso 5: Construir cadena FEN desde la cuadrícula 8×8
    // ─────────────────────────────────────────────

    private fun buildFen(grid: Array<CharArray>): String {
        val rows = grid.map { row ->
            val sb = StringBuilder()
            var empty = 0
            for (cell in row) {
                if (cell == EMPTY) {
                    empty++
                } else {
                    if (empty > 0) { sb.append(empty); empty = 0 }
                    sb.append(cell)
                }
            }
            if (empty > 0) sb.append(empty)
            sb.toString()
        }
        return rows.joinToString("/") + " w - - 0 1"
    }

    // ─────────────────────────────────────────────
    // Constantes
    // ─────────────────────────────────────────────

    companion object {
        private const val BOARD_SIZE         = 720
        private const val BOARD_SQUARES      = 8
        private const val SQUARE_SIZE        = 90   // BOARD_SIZE / BOARD_SQUARES
        private const val CENTER_CROP_START  = 30   // región central 30×30 para bando
        private const val CENTER_CROP_END    = 60
        private const val MIN_CONTRAST       = 20   // contraste mínimo para clasificar bando
        private const val ROW1_WHITE_BIAS    = 8f   // bias fila 1 (peones negros rank 7): umbral más alto para evitar P→p
        private const val MATCH_THRESHOLD    = 0.45f
        private const val CANNY_LOW          = 50
        private const val CANNY_HIGH         = 150
        private const val STRONG_EDGE        = 255
        private const val WEAK_EDGE          = 128
        private const val EMPTY              = ' '
        private val RAD_TO_DEG              = (180.0 / PI).toFloat()
    }
}
