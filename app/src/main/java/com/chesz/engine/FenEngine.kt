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
     * Flujo de dos pasadas:
     *  1ª pasada: detección sin bias → determinar orientación (rey → peones → densidad).
     *  2ª pasada: re-detectar filas 1 y 6 del grid final CON bias (ya conocemos la orientación).
     *
     * @return FEN completo, e.g. "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w - - 0 1"
     */
    fun processBoard(board: Bitmap): String {
        val gray = bitmapToGray(board)
        saveDebugGray(gray)

        // 1ª pasada: sin bias de fila
        val grid = Array(BOARD_SQUARES) { row ->
            CharArray(BOARD_SQUARES) { col ->
                detectPiece(gray, row, col, applyBias = false)
            }
        }

        // Orientación: rey (inapelable) → peones → densidad
        val flipped = isBoardFlipped(grid)
        val finalGrid = if (flipped) flipGrid(grid) else grid

        // 2ª pasada CON bias solo en filas de peones (1 y 6), ahora que la orientación es conocida
        for (finalRow in listOf(1, 6)) {
            for (finalCol in 0 until BOARD_SQUARES) {
                val origRow = if (flipped) BOARD_SQUARES - 1 - finalRow else finalRow
                val origCol = if (flipped) BOARD_SQUARES - 1 - finalCol else finalCol
                finalGrid[finalRow][finalCol] = detectPiece(gray, origRow, origCol, applyBias = true)
            }
        }

        return buildFen(finalGrid)
    }

    /**
     * Detecta si el tablero está orientado desde la perspectiva de las negras.
     *
     * Prioridad 1 — Rey blanco (inapelable):
     *   El rey blanco K correcto siempre está en filas 6-7. Si aparece en filas 0-3 → girado.
     * Prioridad 2 — Peones:
     *   P nunca en filas 0-1; p nunca en filas 6-7. Cualquier violación → girado.
     * Prioridad 3 — Densidad (fallback):
     *   Solo cuando no hay rey ni peones visibles.
     */
    private fun isBoardFlipped(grid: Array<CharArray>): Boolean {
        // 1. Rey blanco — regla inapelable
        for (row in 0 until BOARD_SQUARES) {
            for (col in 0 until BOARD_SQUARES) {
                if (grid[row][col] == 'K') {
                    return row < BOARD_SQUARES / 2   // filas 0-3 = girado
                }
            }
        }

        // 2. Validación por peones
        var pawnViolation = false
        var hasPawns = false
        outer@ for (row in 0 until BOARD_SQUARES) {
            for (col in 0 until BOARD_SQUARES) {
                val p = grid[row][col]
                if (p == 'P' || p == 'p') {
                    hasPawns = true
                    if (p == 'P' && row <= 1) { pawnViolation = true; break@outer }
                    if (p == 'p' && row >= 6) { pawnViolation = true; break@outer }
                }
            }
        }
        if (hasPawns) return pawnViolation

        // 3. Fallback densidad — solo si no hay rey ni peones
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
        // Normal: blancas en filas altas (6-7), negras en filas bajas (0-1)
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

    /**
     * Detecta la pieza en la casilla [row, col].
     *
     * [applyBias] activa el ROW1_WHITE_BIAS solo cuando la orientación ya es conocida
     * (2ª pasada sobre filas 1 y 6 del grid final).
     *
     * Desambiguación alfil/peón (reglas 7-9):
     *  - El alfil requiere BISHOP_THRESHOLD (0.55) en lugar del MATCH_THRESHOLD genérico.
     *  - Si el top-1 y top-2 forman par alfil/peón con diferencia < AMBIGUOUS_GAP,
     *    se resuelve por la distribución vertical de masa (alfil = pieza alta).
     */
    private fun detectPiece(boardGray: IntArray, row: Int, col: Int, applyBias: Boolean = false): Char {
        val square = extractSquare(boardGray, row, col)
        val silueta = cannyDilate(square)
        val isWhiteZone = isPieceWhite(square, applyBias)

        // Calcular el mejor score por símbolo (no por plantilla individual)
        val symbolScores = mutableMapOf<Char, Float>()
        for ((symbol, templateList) in templates) {
            if (symbol.isUpperCase() != isWhiteZone) continue
            var best = -1f
            for (template in templateList) {
                val s = matchNormalized(silueta, template)
                if (s > best) best = s
            }
            symbolScores[symbol] = best
        }

        val sorted = symbolScores.entries.sortedByDescending { it.value }
        val top1 = sorted.getOrNull(0) ?: return EMPTY
        val top2 = sorted.getOrNull(1)

        val bestScore  = top1.value
        val bestSymbol = top1.key
        val secondScore  = top2?.value ?: -1f
        val secondSymbol = top2?.key ?: EMPTY

        // Umbral efectivo: el alfil requiere barra más alta para evitar confusión con peón
        val threshold = if (bestSymbol == 'b' || bestSymbol == 'B') BISHOP_THRESHOLD else MATCH_THRESHOLD
        if (bestScore < threshold) return EMPTY

        // Desambiguación alfil/peón por altura cuando scores están muy próximos
        val isBishopPawnPair = secondSymbol != EMPTY &&
            ((bestSymbol.lowercaseChar() == 'b' && secondSymbol.lowercaseChar() == 'p') ||
             (bestSymbol.lowercaseChar() == 'p' && secondSymbol.lowercaseChar() == 'b'))
        if (isBishopPawnPair && secondScore >= MATCH_THRESHOLD && (bestScore - secondScore) < AMBIGUOUS_GAP) {
            return resolveByHeight(square, bestSymbol, secondSymbol)
        }

        return bestSymbol
    }

    /**
     * Desempata alfil vs peón usando la distribución vertical de masa de píxeles brillantes.
     * El alfil es una pieza más alta → centroide de brillo en la mitad superior (y pequeño).
     * El peón tiene masa concentrada en el centro-bajo.
     */
    private fun resolveByHeight(square: IntArray, symbol1: Char, symbol2: Char): Char {
        val s = SQUARE_SIZE
        val brightThreshold = 128
        var weightedSum = 0L
        var count = 0L
        for (y in 0 until s) {
            for (x in 0 until s) {
                if (square[y * s + x] > brightThreshold) {
                    weightedSum += y
                    count++
                }
            }
        }
        val centroid = if (count == 0L) (s / 2f) else weightedSum.toFloat() / count
        val bishopSymbol = if (symbol1.lowercaseChar() == 'b') symbol1 else symbol2
        val pawnSymbol   = if (symbol1.lowercaseChar() == 'p') symbol1 else symbol2
        // Masa en mitad superior (centroide < s/2) → alfil; caso contrario → peón
        return if (centroid < s / 2f) bishopSymbol else pawnSymbol
    }

    /**
     * Determina el bando de la pieza usando contraste relativo:
     *   1. Media de la región central 30×30 px (más robusta que un solo píxel).
     *   2. Umbral dinámico = (min + max) / 2 de toda la casilla.
     *   → blanca si el centro es más luminoso que el punto medio del rango.
     *
     * [applyBias] solo debe ser true en la 2ª pasada (filas 1 y 6 del grid final),
     * cuando la orientación ya está resuelta. Aplicarlo antes provocaba bias en filas
     * equivocadas si el tablero estaba girado.
     */
    private fun isPieceWhite(square: IntArray, applyBias: Boolean = false): Boolean {
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

        // Bias solo cuando se solicita explícitamente (orientación ya conocida)
        val bias = if (applyBias) ROW1_WHITE_BIAS else 0f

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
        private const val ROW1_WHITE_BIAS    = 8f   // bias para filas de peones (2ª pasada, orientación conocida)
        private const val MATCH_THRESHOLD    = 0.45f
        private const val BISHOP_THRESHOLD   = 0.55f // umbral más alto para alfil (evita confusión con peón)
        private const val AMBIGUOUS_GAP      = 0.10f // diferencia mínima para considerar match no ambiguo
        private const val CANNY_LOW          = 50
        private const val CANNY_HIGH         = 150
        private const val STRONG_EDGE        = 255
        private const val WEAK_EDGE          = 128
        private const val EMPTY              = ' '
        private val RAD_TO_DEG              = (180.0 / PI).toFloat()
    }
}
