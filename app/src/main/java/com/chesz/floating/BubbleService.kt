package com.chesz.floating
import org.json.JSONObject
import android.os.Handler
import android.os.Looper

import android.app.Activity
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.Outline
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.Spannable
import android.content.Context
import com.chesz.R
import com.chesz.api.LichessApiClient
import com.chesz.engine.StockfishEngine
import kotlin.math.abs

class BubbleService : Service() {
    private lateinit var wm: WindowManager

    // === SINGLE ROOT OVERLAY (botón + panel) ===
    private lateinit var root: FrameLayout
    private lateinit var rootLp: WindowManager.LayoutParams

    private lateinit var bubbleIcon: ImageView
    private lateinit var panelRoot: FrameLayout

    private var panelShown = false
    private var panelDyPx: Int = 0
    private var lastFen: String? = null
    private var fenAwaitingUserColor: String? = null

    // Drag state (sobre el ROOT)
    private var downRawX = 0f
    private var downRawY = 0f
    private var startX = 0
    private var startY = 0
    private var dragging = false
    private var ignoreTouchUntil = 0L
    private var isCapturing = false
    private var sw = 0
    private var sh = 0
    private var bottomInsetCache = 0

    // Kill area
    private lateinit var killRoot: FrameLayout
    private lateinit var killCircle: FrameLayout
    private lateinit var killLp: WindowManager.LayoutParams
    private var killShown = false
    private var killHovered = false

    // ===== Modo Dios =====
    private var isDeveloperMode = false
    private var isHostChecked = false
    private val devHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var devRunnable: Runnable? = null
    private lateinit var devBar: LinearLayout
    private lateinit var dotsRow: LinearLayout
    private var benchmarkThread: Thread? = null
    private var abortBenchmark = false

    // ===== Cache del último BM (para rescatar por 10s cuando falla captura) =====
    private var lastBestMove: String = ""
    private var bmCacheTime: Long = 0
    private val BM_CACHE_DURATION_MS = 10000L // 10 segundos

    // ===== MediaProjection permission cache =====
    private var mpResultCode: Int? = null
    private var mpData: Intent? = null
    private var activeMediaProjection: android.media.projection.MediaProjection? = null
    private var activeVirtualDisplay: android.hardware.display.VirtualDisplay? = null
    private var activeImageReader: android.media.ImageReader? = null

    // ===== FenEngine local =====
    private val fenEngine by lazy { com.chesz.engine.FenEngine(this) }

    // ===== Lichess API =====
    private val lichessApiClient by lazy { LichessApiClient() }

    // ===== Stockfish local =====
    private lateinit var stockfishEngine: StockfishEngine

    // ===== Color de acento (toggle verde / ámbar IBM 5151) =====
    private var accentColor: Int = 0xFF33FF00.toInt()
    private lateinit var panelBorderDrawable: android.graphics.drawable.GradientDrawable

    // ===== Panel UI refs =====
    private lateinit var permBar: FrameLayout
    private lateinit var permText: TextView
    private lateinit var debugText: TextView
    private lateinit var fenTitle: TextView
    private lateinit var btnBench: TextView
    private lateinit var btnPrueba: TextView
    private lateinit var userColorChoiceBar: LinearLayout
    private lateinit var btnUserWhite: TextView
    private lateinit var btnUserBlack: TextView

    // ===== Lichess UI refs =====
    private lateinit var lichessContainer: LinearLayout
    private lateinit var tvOpeningName: TextView
    private lateinit var tvNextMoves: TextView
    private lateinit var tvBestMove: TextView
    private lateinit var tvCounterAttack: TextView
    private lateinit var tvTablebaseResult: TextView
    private lateinit var tvMateIn: TextView
    private lateinit var rowOpeningName: LinearLayout
    private lateinit var rowNextMoves: LinearLayout
    private lateinit var rowBestMove: LinearLayout
    private lateinit var rowCounterAttack: LinearLayout
    private lateinit var rowTablebaseResult: LinearLayout
    private lateinit var rowMateIn: LinearLayout
    private lateinit var closeBtn: ImageView

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent?.action == "CHESZ_CAPTURE_PERMISSION_RESULT") {
            mpResultCode = intent.getIntExtra("resultCode", Activity.RESULT_CANCELED)
            @Suppress("DEPRECATION")
            mpData = intent.getParcelableExtra("data")
            runCatching { upgradeToMediaProjection() }
            updatePermUi()
        }
        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        LichessApiClient.context = this
        stockfishEngine = StockfishEngine(this)
        LichessApiClient.stockfishEngine = stockfishEngine
        Thread {
            runCatching { stockfishEngine.start() }
                .onFailure { android.util.Log.e("Stockfish", "init failed", it) }
        }.start()
        startForegroundForMediaProjection()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        updateScreenCache()
        createRootOverlay()
        createKillArea()
        Thread { fenEngine.loadTemplates() }.start()
    }

    private fun startForegroundForMediaProjection() {
        val channelId = "chesz_channel"
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(channelId, "Chesz Service", android.app.NotificationManager.IMPORTANCE_HIGH)
            val nm = getSystemService(android.app.NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
        val notif =
            android.app.Notification
                .Builder(this, channelId)
                .setContentTitle("Chesz")
                .setContentText("Servicio de captura activo")
                .setSmallIcon(R.drawable.ic_check_green)
                .build()

        if (android.os.Build.VERSION.SDK_INT >= 34) {
            startForeground(1, notif, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(1, notif)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        runCatching { wm.removeViewImmediate(root) }
        runCatching { if (killShown) wm.removeViewImmediate(killRoot) }
        Thread {
            runCatching { activeMediaProjection?.stop() }
            activeMediaProjection = null
            runCatching { stockfishEngine.shutdown() }
            LichessApiClient.stockfishEngine = null
        }.start()
        mpData = null
        mpResultCode = null
        killShown = false
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private fun createRootOverlay() {
        root =
            FrameLayout(this).apply {
                clipChildren = false
                clipToPadding = false
            }

        panelRoot =
            buildPanel().apply {
                visibility = View.GONE
            }
        root.addView(panelRoot)

        val btnPx = dp(BUBBLE_SIZE_DP)
        bubbleIcon =
            ImageView(this).apply {
                setImageResource(R.drawable.bubble_icon)
                scaleType = ImageView.ScaleType.FIT_XY
                adjustViewBounds = false
            }
        val bubbleWrap =
            FrameLayout(this).apply {
                addView(bubbleIcon, FrameLayout.LayoutParams(btnPx, btnPx))
                clipChildren = false
                clipToPadding = false
            }
        root.addView(bubbleWrap)

        rootLp =
            WindowManager
                .LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    overlayType(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    x = dp(BUBBLE_INIT_X_DP)
                    y = dp(BUBBLE_INIT_Y_DP)
                }

        setStateALayout()

        root.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragging = false
                    downRawX = e.rawX
                    
                    // Iniciar temporizador Modo Dios
                    devRunnable = Runnable {
                        isDeveloperMode = true
                        ignoreTouchUntil = System.currentTimeMillis() + DELAY_GOD_TOUCH_IGNORE_MS
                        updatePermUi()
                        flashBubbleRed()
                        if (!panelShown) showPanelIfFits()
                        if (this::devBar.isInitialized) devBar.visibility = View.VISIBLE
                        clearPanel()
                        root.post { fenTitle.text = "DEBUG MODE" }
                    }
                    devHandler.postDelayed(devRunnable!!, DELAY_DEV_MODE_MS)

                    downRawY = e.rawY
                    startX = rootLp.x
                    startY = rootLp.y
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (System.currentTimeMillis() < ignoreTouchUntil) return@setOnTouchListener true
                    val dx = (e.rawX - downRawX).toInt()
                    val dy = (e.rawY - downRawY).toInt()

                    if (!dragging && (abs(dx) + abs(dy) > dp(DRAG_THRESHOLD_DP))) {
                        devHandler.removeCallbacks(devRunnable!!) // Cancelar Modo Dios por arrastre
                        dragging = true
                        showKill(true)
                    }

                    val clamped = clampRootToScreen(startX + dx, startY + dy)
                    rootLp.x = clamped.first
                    rootLp.y = clamped.second
                    runCatching { wm.updateViewLayout(root, rootLp) }

                    if (dragging) {
                        val over = isOverKillCenter(bubbleCenterX(), bubbleCenterY())
                        if (over != killHovered) {
                            killHovered = over
                            setKillHover(over)
                        }
                    }
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    devHandler.removeCallbacks(devRunnable!!) // Cancelar temporizador
                    
                    // 1. Siempre procesar el arrastre y apagar el Kill Area primero
                    if (dragging) {
                        if (isOverKillCenter(bubbleCenterX(), bubbleCenterY())) {
                            performKill()
                        } else {
                            setKillHover(false)
                            showKill(false)
                        }
                        dragging = false
                        return@setOnTouchListener true
                    }
                    
                    // 2. Si no fue arrastre, fue un Tap. Aplicar escudo si es Modo Dios.
                    if (isDeveloperMode) {
                        return@setOnTouchListener true // Escudo: Ignorar tap normal
                    } else {
                        val dist = kotlin.math.hypot(e.rawX - bubbleCenterX(), e.rawY - bubbleCenterY())
                        if (dist <= dp(TAP_RADIUS_DP).toFloat()) togglePanel()
                    }
                    
                    dragging = false
                    true
                }

                else -> false
            }
        }

        wm.addView(root, rootLp)
    }

    private fun togglePanel() {
        if (isCapturing) {
            // Cambiar botón a rojo si se toca durante el período de captura
            bubbleIcon.setColorFilter(COLOR_FLASH_RED)
            return
        }
        val hasPerm = (mpResultCode == android.app.Activity.RESULT_OK) && (mpData != null)
        if (!panelShown) {
            showPanelIfFits()
        }
        if (hasPerm) {
            takeScreenshotOnce()
        }
    }

    private fun setStateALayout() {
        val btnPx = dp(BUBBLE_SIZE_DP)
        rootLp.width = btnPx
        rootLp.height = btnPx
        panelRoot.visibility = View.GONE
        panelShown = false

        (panelRoot.layoutParams as? FrameLayout.LayoutParams)?.apply {
            leftMargin = 0
            topMargin = 0
        }
        val bubbleWrap = root.getChildAt(1)
        bubbleWrap.layoutParams =
            FrameLayout.LayoutParams(btnPx, btnPx).apply {
                leftMargin = 0
                topMargin = 0
            }

        val clampedA = clampRootToScreen(rootLp.x, rootLp.y)
        rootLp.x = clampedA.first
        rootLp.y = clampedA.second

        root.requestLayout()
        runCatching { wm.updateViewLayout(root, rootLp) }
    }

    private fun showPanelIfFits() {
        val dm = resources.displayMetrics
        val btnW = dp(BUBBLE_SIZE_DP)
        val btnH = dp(BUBBLE_SIZE_DP)
        val panelW = (dm.widthPixels * PANEL_WIDTH_RATIO).toInt()
        val panelH = (dm.heightPixels * PANEL_HEIGHT_RATIO).toInt()

        val rootX = rootLp.x
        val rootY = rootLp.y - (panelH - btnH)
        val rootW = panelW + (btnW / 2)
        val rootH = panelH
        val (sw, sh) = this.sw to this.sh

        val maxY = (sh - bottomInsetCache).coerceAtLeast(0)

        val fits =
            rootX >= 0 &&
                rootY >= 0 &&
                (rootX + rootW) <= sw &&
                (rootY + rootH) <= maxY
        if (!fits) {
            flashBubbleRed()
            return
        }

        rootLp.x = rootX
        rootLp.y = rootY
        rootLp.width = rootW
        rootLp.height = rootH

        val clampedB = clampRootToScreen(rootLp.x, rootLp.y)
        rootLp.x = clampedB.first
        rootLp.y = clampedB.second

        panelRoot.visibility = View.VISIBLE
        panelRoot.layoutParams =
            FrameLayout.LayoutParams(panelW, panelH).apply {
                leftMargin = (btnW / 2)
                topMargin = 0
            }

        val bubbleWrap = root.getChildAt(1)
        bubbleWrap.layoutParams =
            FrameLayout.LayoutParams(btnW, btnH).apply {
                leftMargin = 0
                topMargin = (panelH - btnH)
            }

        panelShown = true
        resetToGodMode()
        updatePermUi()
        // requestLayout primero: el botón baja su topMargin antes de que el root suba,
        // evitando el salto visual de un frame.
        root.requestLayout()
        root.post { runCatching { wm.updateViewLayout(root, rootLp) } }
    }

    private fun hidePanel() {
        // Terminar todos los procesos pendientes
        devHandler.removeCallbacksAndMessages(null)

        // Abortar benchmark si está corriendo
        abortBenchmark = true
        benchmarkThread?.interrupt()
        benchmarkThread = null

        // Resetear estado
        isHostChecked = false
        isDeveloperMode = false
        isCapturing = false
        fenTitle.text = ""
        debugText.text = ""
        debugText.visibility = View.GONE
        if (this::devBar.isInitialized) devBar.visibility = View.GONE
        if (this::userColorChoiceBar.isInitialized) userColorChoiceBar.visibility = View.GONE
        fenAwaitingUserColor = null

        if (panelShown) {
            val dm = resources.displayMetrics
            val btnH = dp(BUBBLE_SIZE_DP)
            val panelH = (dm.heightPixels * PANEL_HEIGHT_RATIO).toInt()
            rootLp.y = rootLp.y + (panelH - btnH)
        }
        setStateALayout()
    }

            private fun buildPanel(): FrameLayout {
        val customFont = android.graphics.Typeface.createFromAsset(assets, "fonts/perfect_dos_vga.ttf")
        panelBorderDrawable = android.graphics.drawable.GradientDrawable().apply {
            setColor(COLOR_PANEL_BG)
            setStroke(dp(BTN_STROKE_DP).toInt(), accentColor)
            cornerRadius = 0f
        }

        val panel = FrameLayout(this).apply {
            background = panelBorderDrawable
            clipChildren = false
            clipToPadding = false
        }

        val col = LinearLayout(this).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 0)
        }

        fenTitle = TextView(this).apply {
            text = ""
            textSize = TEXT_SIZE_FEN
            typeface = customFont
            setTextColor(COLOR_GREEN)
            includeFontPadding = false
            setSingleLine(false)
            minLines = 2
            maxLines = 2

            gravity = android.view.Gravity.CENTER
            setLineSpacing(0f, 0.9f)
            setPadding(dp(3), dp(1), dp(3), 0)
            layoutParams = LinearLayout.LayoutParams(-1, -2).apply { topMargin = 0; rightMargin = dp(27) }
        }
        col.addView(fenTitle)

        // ===== INFORMACIÓN LICHESS =====
        lichessContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(35), dp(2), dp(33), 0)
            visibility = View.GONE
        }

        // Crear dots (siempre visibles)
        val dotOP = TextView(this).apply {
            text = "●"
            textSize = 28f
            typeface = customFont
            setTextColor(COLOR_GREEN)
            includeFontPadding = false
            setPadding(0, 0, dp(4), 0)
        }

        val dotBM = TextView(this).apply {
            text = "●"
            textSize = 28f
            typeface = customFont
            setTextColor(COLOR_GREEN)
            includeFontPadding = false
            setPadding(0, 0, dp(4), 0)
        }

        val dotLN = TextView(this).apply {
            text = "●"
            textSize = 28f
            typeface = customFont
            setTextColor(COLOR_GREEN)
            includeFontPadding = false
            setPadding(0, 0, dp(4), 0)
        }

        val dotWR = TextView(this).apply {
            text = "●"
            textSize = 28f
            typeface = customFont
            setTextColor(COLOR_GREEN)
            includeFontPadding = false
            setPadding(0, 0, dp(4), 0)
        }

        // Labels para los dots
        val labelDotOP = TextView(this).apply {
            text = "OP"
            textSize = 13f
            typeface = customFont
            setTextColor(COLOR_GREEN)
            includeFontPadding = false
            setPadding(dp(2), 0, 0, 0)
        }

        val labelDotBM = TextView(this).apply {
            text = "BM"
            textSize = 13f
            typeface = customFont
            setTextColor(COLOR_GREEN)
            includeFontPadding = false
            setPadding(dp(2), 0, 0, 0)
        }

        val labelDotLN = TextView(this).apply {
            text = "LN"
            textSize = 13f
            typeface = customFont
            setTextColor(COLOR_GREEN)
            includeFontPadding = false
            setPadding(dp(2), 0, 0, 0)
        }

        val labelDotWR = TextView(this).apply {
            text = "WR"
            textSize = 13f
            typeface = customFont
            setTextColor(COLOR_GREEN)
            includeFontPadding = false
            setPadding(dp(2), 0, 0, 0)
        }

        // Fila de dots (HORIZONTAL) - aparece con el FEN
        dotsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.BOTTOM
            setPadding(dp(5), 0, dp(5), dp(2))
            visibility = View.GONE  // Oculto por defecto, aparece con el FEN

            addView(dotOP, LinearLayout.LayoutParams(-2, -2).apply { gravity = android.view.Gravity.CENTER_VERTICAL })
            addView(labelDotOP, LinearLayout.LayoutParams(-2, -2).apply { gravity = android.view.Gravity.CENTER_VERTICAL })
            addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(dp(6), 0) })

            addView(dotBM, LinearLayout.LayoutParams(-2, -2).apply { gravity = android.view.Gravity.CENTER_VERTICAL })
            addView(labelDotBM, LinearLayout.LayoutParams(-2, -2).apply { gravity = android.view.Gravity.CENTER_VERTICAL })
            addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(dp(6), 0) })

            addView(dotLN, LinearLayout.LayoutParams(-2, -2).apply { gravity = android.view.Gravity.CENTER_VERTICAL })
            addView(labelDotLN, LinearLayout.LayoutParams(-2, -2).apply { gravity = android.view.Gravity.CENTER_VERTICAL })
            addView(View(context).apply { layoutParams = LinearLayout.LayoutParams(dp(6), 0) })

            addView(dotWR, LinearLayout.LayoutParams(-2, -2).apply { gravity = android.view.Gravity.CENTER_VERTICAL })
            addView(labelDotWR, LinearLayout.LayoutParams(-2, -2).apply { gravity = android.view.Gravity.CENTER_VERTICAL })
        }
        // topMargin negativo: compensa el ascender del glifo "●" a 28sp (título es 11sp)
        // para pegar la fila de dots al fenTitle.
        col.addView(dotsRow, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(-10) })

        // Helper para crear filas de valores (sin dot)
        fun createValueRow(label: TextView, textView: TextView, labelText: String): LinearLayout {
            return LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL

                label.apply {
                    text = labelText
                    textSize = 12f
                    typeface = customFont
                    setTextColor(COLOR_GREEN)
                    setPadding(0, 0, dp(3), 0)
                }
                addView(label, LinearLayout.LayoutParams(-2, -2))

                textView.apply {
                    text = ""
                    textSize = 12f
                    typeface = customFont
                    setTextColor(COLOR_GREEN)
                    includeFontPadding = false
                    maxLines = 1
                    setSingleLine(true)
                }
                addView(textView, LinearLayout.LayoutParams(0, -2, 1f))
            }
        }

        // Crear filas de valores
        val labelOP = TextView(this)
        tvOpeningName = TextView(this)
        rowOpeningName = createValueRow(labelOP, tvOpeningName, "OP")
        lichessContainer.addView(rowOpeningName)

        val labelBM = TextView(this)
        tvBestMove = TextView(this)
        rowBestMove = createValueRow(labelBM, tvBestMove, "BM")
        lichessContainer.addView(rowBestMove)

        val labelLN = TextView(this)
        tvNextMoves = TextView(this)
        rowNextMoves = createValueRow(labelLN, tvNextMoves, "LN")
        lichessContainer.addView(rowNextMoves)

        val labelWR = TextView(this)
        tvCounterAttack = TextView(this)
        rowCounterAttack = createValueRow(labelWR, tvCounterAttack, "WR")
        lichessContainer.addView(rowCounterAttack)

        // TB y Mate sin toggles
        tvTablebaseResult = TextView(this)
        rowTablebaseResult = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            visibility = View.GONE

            val labelTB = TextView(context).apply {
                text = "TB"
                textSize = 12f
                typeface = customFont
                setTextColor(COLOR_GREEN)
                setPadding(0, 0, dp(3), 0)
            }
            addView(labelTB, LinearLayout.LayoutParams(-2, -2))

            tvTablebaseResult.apply {
                text = ""
                textSize = 12f
                typeface = customFont
                setTextColor(COLOR_GREEN)
                includeFontPadding = false
                maxLines = 1
                setSingleLine(true)
            }
            addView(tvTablebaseResult, LinearLayout.LayoutParams(0, -2, 1f))
        }
        lichessContainer.addView(rowTablebaseResult)

        tvMateIn = TextView(this)
        rowMateIn = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            visibility = View.GONE

            val labelMate = TextView(context).apply {
                text = ""
                textSize = 12f
                typeface = customFont
                setTextColor(COLOR_GREEN)
                setPadding(0, 0, dp(3), 0)
            }
            addView(labelMate, LinearLayout.LayoutParams(-2, -2))

            tvMateIn.apply {
                text = ""
                textSize = 12f
                typeface = customFont
                setTextColor(COLOR_GREEN)
                includeFontPadding = false
                maxLines = 1
                setSingleLine(true)
            }
            addView(tvMateIn, LinearLayout.LayoutParams(0, -2, 1f))
        }
        lichessContainer.addView(rowMateIn)

        col.addView(lichessContainer, LinearLayout.LayoutParams(-1, -2))

        debugText = TextView(this).apply {
            typeface = customFont
            setTextColor(COLOR_GREEN)
            textSize = TEXT_SIZE_DEBUG
            gravity = android.view.Gravity.CENTER_VERTICAL or android.view.Gravity.START
            visibility = android.view.View.GONE
            // AQUI RESTAURAMOS EL MARGEN DEL ANALISIS SIN AFECTAR AL FEN
            setPadding(dp(40), dp(2), 0, 0)
        }
        col.addView(debugText, LinearLayout.LayoutParams(-1, -2))

        col.addView(View(this), LinearLayout.LayoutParams(-1, 0, 1f))

        // --- BARRA MODO DIOS ---
        devBar = LinearLayout(this).apply {
            gravity = android.view.Gravity.CENTER
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
            setPadding(0, dp(5), 0, 0)
        }
        
        val btnBg = android.graphics.drawable.GradientDrawable().apply {
            setColor(COLOR_BLACK) // Fondo Negro
            setStroke(dp(BTN_STROKE_DP), COLOR_GREEN) // Borde Verde
            cornerRadius = dp(BTN_CORNER_DP).toFloat() // Forma Pastilla
        }

        btnBench = TextView(this).apply {
            text = "TEST FEN"
            typeface = customFont
            setTextColor(COLOR_GREEN)
            textSize = TEXT_SIZE_BTN
            gravity = android.view.Gravity.CENTER
            background = btnBg
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener { runBenchmark() }
        }

        btnPrueba = TextView(this).apply {
            text = "COLOR"
            typeface = customFont
            setTextColor(COLOR_WHITE)
            textSize = TEXT_SIZE_BTN
            gravity = android.view.Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(COLOR_ORANGE_BG)
                setStroke(dp(BTN_STROKE_ALERT_DP), COLOR_ORANGE_STROKE)
                cornerRadius = dp(BTN_CORNER_DP).toFloat()
            }
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener {
                accentColor = if (accentColor == COLOR_GREEN) 0xFFFFB000.toInt() else COLOR_GREEN
                applyAccentColor()
            }
        }

        devBar.addView(btnBench, LinearLayout.LayoutParams(-2, -2))
        devBar.addView(android.view.View(this), LinearLayout.LayoutParams(dp(BTN_SPACING_DP), 0))
        devBar.addView(btnPrueba, LinearLayout.LayoutParams(-2, -2))

        // --- BARRA DE ELECCIÓN MANUAL DEL COLOR DEL USUARIO ---
        userColorChoiceBar = LinearLayout(this).apply {
            gravity = android.view.Gravity.CENTER
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
            setPadding(0, dp(5), 0, 0)
        }

        btnUserWhite = TextView(this).apply {
            text = "WHITE"
            typeface = customFont
            setTextColor(COLOR_GREEN)
            textSize = 15f
            gravity = android.view.Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(COLOR_BLACK)
                setStroke(dp(BTN_STROKE_DP), COLOR_GREEN)
                cornerRadius = dp(BTN_CORNER_DP).toFloat()
            }
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener { onUserColorChosen("w") }
        }

        btnUserBlack = TextView(this).apply {
            text = "BLACK"
            typeface = customFont
            setTextColor(COLOR_GREEN)
            textSize = 15f
            gravity = android.view.Gravity.CENTER
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(COLOR_BLACK)
                setStroke(dp(BTN_STROKE_DP), COLOR_GREEN)
                cornerRadius = dp(BTN_CORNER_DP).toFloat()
            }
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener { onUserColorChosen("b") }
        }

        userColorChoiceBar.addView(btnUserWhite, LinearLayout.LayoutParams(-2, -2))
        userColorChoiceBar.addView(android.view.View(this), LinearLayout.LayoutParams(dp(BTN_SPACING_DP), 0))
        userColorChoiceBar.addView(btnUserBlack, LinearLayout.LayoutParams(-2, -2))
        col.addView(userColorChoiceBar, LinearLayout.LayoutParams(-1, -2).apply { leftMargin = dp(PANEL_LEFT_MARGIN_DP); rightMargin = dp(0); bottomMargin = dp(4) })

        permBar = FrameLayout(this).apply {
            setOnClickListener { requestCapturePermission() }
            val permIcon = ImageView(context).apply {
                setImageResource(R.drawable.permit_icon)
                adjustViewBounds = true
            }
            addView(permIcon, FrameLayout.LayoutParams(-2, -2, android.view.Gravity.CENTER))
        }

        // Envolver col en ScrollView para contenido largo
        val scrollView = android.widget.ScrollView(this).apply {
            isVerticalScrollBarEnabled = false
            addView(col)
        }
        panel.addView(scrollView, FrameLayout.LayoutParams(-1, -1))

        panel.addView(permBar, FrameLayout.LayoutParams(-1, dp(PERM_BAR_HEIGHT_DP)).apply {
            gravity = android.view.Gravity.CENTER
        })

        panel.addView(devBar, FrameLayout.LayoutParams(-2, -2).apply {
            gravity = android.view.Gravity.BOTTOM or android.view.Gravity.CENTER_HORIZONTAL
            bottomMargin = dp(2)
        })

        closeBtn = ImageView(this).apply {
            setImageResource(R.drawable.close)
            setPadding(0, 0, 0, 0)
            setOnClickListener { hidePanel() }
        }
        panel.addView(closeBtn, FrameLayout.LayoutParams(dp(CLOSE_BTN_SIZE_DP), dp(CLOSE_BTN_SIZE_DP)).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.END
            topMargin = dp(-2)
            rightMargin = dp(-2)
        })

        // === LÓGICA DE TOGGLES ===
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        fun applyDot(dot: TextView, label: TextView, row: LinearLayout, active: Boolean) {
            // Dot siempre visible, solo cambia símbolo y color
            dot.text = if (active) "●" else "○"
            dot.setTextColor(if (active) accentColor else 0xFF888888.toInt())
            // Label también cambia de color
            label.setTextColor(if (active) accentColor else 0xFF888888.toInt())
            // Row completa se muestra/oculta según estado
            row.visibility = if (active) View.VISIBLE else View.GONE
        }

        fun checkAllInactive(): Boolean {
            // BM siempre activo → el panel nunca queda sin dots activos
            return false
        }

        applyDot(dotOP, labelDotOP, rowOpeningName,   prefs.getBoolean(PREF_OP, true))
        applyDot(dotBM, labelDotBM, rowBestMove,      prefs.getBoolean(PREF_BM, true))
        applyDot(dotLN, labelDotLN, rowNextMoves,     prefs.getBoolean(PREF_LN, true))
        applyDot(dotWR, labelDotWR, rowCounterAttack, prefs.getBoolean(PREF_WR, false))

        fun dotClick(dot: TextView, label: TextView, row: LinearLayout, key: String, default: Boolean, antiEmpty: String? = null) {
            dot.setOnClickListener {
                val current = prefs.getBoolean(key, default)
                val next = !current

                // Si estamos desactivando y quedarían todos inactivos, activar antiEmpty
                if (!next && antiEmpty != null) {
                    // Simular el cambio temporalmente para verificar
                    prefs.edit().putBoolean(key, next).apply()
                    if (checkAllInactive()) {
                        // Activar el antiEmpty
                        prefs.edit().putBoolean(antiEmpty, true).apply()
                        applyDot(if (antiEmpty == PREF_BM) dotBM else dotLN,
                                if (antiEmpty == PREF_BM) labelDotBM else labelDotLN,
                                if (antiEmpty == PREF_BM) rowBestMove else rowNextMoves,
                                true)
                    }
                } else {
                    prefs.edit().putBoolean(key, next).apply()
                }

                applyDot(dot, label, row, next)
            }
        }

        dotClick(dotOP, labelDotOP, rowOpeningName,   PREF_OP, true)
        // BM siempre activo - no clickeable
        prefs.edit().putBoolean(PREF_BM, true).apply()
        dotClick(dotLN, labelDotLN, rowNextMoves,     PREF_LN, true)
        dotClick(dotWR, labelDotWR, rowCounterAttack, PREF_WR, false)

        return panel
    }

    private fun flashBubbleRed() {
        runCatching {
            bubbleIcon.setColorFilter(COLOR_FLASH_RED)
            bubbleIcon.postDelayed({ runCatching { bubbleIcon.clearColorFilter() } }, DELAY_FLASH_MS)
        }
    }

    private fun requestCapturePermission() {
        hidePanel()
        val pi =
            Intent(this, com.chesz.CapturePermissionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        startActivity(pi)
    }

    private fun upgradeToMediaProjection() {
        val channelId = "chesz_channel"
        val notif =
            android.app.Notification
                .Builder(this, channelId)
                .setContentTitle("Chesz")
                .setContentText("Captura habilitada")
                .setSmallIcon(R.drawable.ic_check_green)
                .build()
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            startForeground(1, notif, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        }
    }

    private fun updatePermUi() {
        if (!this::permBar.isInitialized) return
        permBar.visibility = if (isDeveloperMode) View.GONE else if (mpData != null) View.GONE else View.VISIBLE
    }

    private fun createKillArea() {
        killRoot =
            FrameLayout(this).apply {
                setBackgroundColor(0)
            }

        val sizePx = dp(KILL_CIRCLE_SIZE_DP)
        killCircle =
            FrameLayout(this).apply {
                background =
                    android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.OVAL
                        setColor(COLOR_KILL_RED)
                    }
            }

        val xIcon =
            ImageView(this).apply {
                setImageResource(android.R.drawable.ic_delete)
                setColorFilter(COLOR_WHITE)
            }

        killRoot.addView(killCircle, FrameLayout.LayoutParams(sizePx, sizePx, Gravity.CENTER))
        killCircle.addView(xIcon, FrameLayout.LayoutParams(dp(KILL_ICON_SIZE_DP), dp(KILL_ICON_SIZE_DP), Gravity.CENTER))

        killLp =
            WindowManager
                .LayoutParams(
                    sizePx,
                    sizePx,
                    overlayType(),
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    y = dp(KILL_BOTTOM_OFFSET_DP)
                }
    }

    private fun showKill(show: Boolean) {
        if (show && !killShown) {
            runCatching { wm.addView(killRoot, killLp) }
            killShown = true
        } else if (!show && killShown) {
            runCatching { wm.removeViewImmediate(killRoot) }
            killShown = false
        }
    }

    private fun setKillHover(hover: Boolean) {
        val target = if (hover) KILL_HOVER_SCALE else 1.0f
        killCircle.animate().scaleX(target).scaleY(target).setDuration(DELAY_KILL_ANIM_MS).start()
    }

    private fun bubbleCenterX(): Float {
        val loc = IntArray(2)
        root.getLocationOnScreen(loc)
        return loc[0] + (dp(BUBBLE_SIZE_DP) / 2f)
    }

    private fun bubbleCenterY(): Float {
        val loc = IntArray(2)
        root.getLocationOnScreen(loc)
        val offset = if (panelShown) ((resources.displayMetrics.heightPixels * PANEL_HEIGHT_RATIO).toInt() - dp(BUBBLE_SIZE_DP)) else 0
        return loc[1] + offset + (dp(BUBBLE_SIZE_DP) / 2f)
    }

    private fun isOverKillCenter(
        x: Float,
        y: Float,
    ): Boolean {
        if (!killShown) return false
        val loc = IntArray(2)
        killRoot.getLocationOnScreen(loc)
        val pad = dp(KILL_HOVER_PADDING_DP)
        return x in (loc[0] - pad).toFloat()..(loc[0] + killRoot.width + pad).toFloat() &&
            y in (loc[1] - pad).toFloat()..(loc[1] + killRoot.height + pad).toFloat()
    }

    private fun performKill() {
        runCatching { wm.removeViewImmediate(root) }
        runCatching { if (killShown) wm.removeViewImmediate(killRoot) }
        stopSelf()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun screenRealSize(): Pair<Int, Int> {
        val b = wm.maximumWindowMetrics.bounds
        return b.width() to b.height()
    }

    private fun clampRootToScreen(
        x: Int,
        y: Int,
    ): Pair<Int, Int> {
        val w = if (rootLp.width > 0) rootLp.width else dp(BUBBLE_SIZE_DP)
        val maxX = (sw - w).coerceAtLeast(0)
        val maxY = (sh - rootLp.height - bottomInsetCache).coerceAtLeast(0)
        return x.coerceIn(0, maxX) to y.coerceIn(0, maxY)
    }
    private fun updateScreenCache() {
        val size = screenRealSize()
        sw = size.first
        sh = size.second
        val insets =
            wm.maximumWindowMetrics.windowInsets.getInsetsIgnoringVisibility(
                android.view.WindowInsets.Type.navigationBars(),
            )
        bottomInsetCache = insets.bottom
    }

            private fun resetToGodMode() {
        root.post {
            if (this::btnBench.isInitialized) btnBench.visibility = android.view.View.VISIBLE
            if (this::btnPrueba.isInitialized) btnPrueba.visibility = android.view.View.VISIBLE
            if (this::dotsRow.isInitialized) dotsRow.visibility = View.GONE
            if (this::lichessContainer.isInitialized) lichessContainer.visibility = View.GONE
            if (this::userColorChoiceBar.isInitialized) userColorChoiceBar.visibility = View.GONE
            updateDebug("")
        }
    }

    private fun applyAccentColor() {
        root.post {
            // Marco del overlay
            if (this::panelBorderDrawable.isInitialized)
                panelBorderDrawable.setStroke(dp(BTN_STROKE_DP).toInt(), accentColor)
            // Botón cerrar — teñir el PNG con el color del tema
            if (this::closeBtn.isInitialized)
                closeBtn.setColorFilter(accentColor, android.graphics.PorterDuff.Mode.SRC_IN)
            // FEN title
            if (this::fenTitle.isInitialized) fenTitle.setTextColor(accentColor)
            // Debug text
            if (this::debugText.isInitialized) debugText.setTextColor(accentColor)
            // Labels e valores de filas (child 0 = label, child 1 = valor)
            listOf(rowOpeningName, rowBestMove, rowNextMoves, rowCounterAttack,
                   rowTablebaseResult, rowMateIn).forEach { row ->
                if (this::lichessContainer.isInitialized) {
                    (row.getChildAt(0) as? TextView)?.setTextColor(accentColor)
                    (row.getChildAt(1) as? TextView)?.setTextColor(accentColor)
                }
            }
            // Dots y sus labels (pattern: dot,label,spacer, dot,label,spacer, ...)
            if (this::dotsRow.isInitialized) {
                for (i in 0 until dotsRow.childCount) {
                    val child = dotsRow.getChildAt(i) as? TextView ?: continue
                    // dot activo "●" → accentColor, inactivo "○" → gris, label → sigue al dot anterior
                    child.setTextColor(if (child.text == "●") accentColor else if (child.text == "○") 0xFF888888.toInt() else accentColor)
                }
            }
            // btnBench
            if (this::btnBench.isInitialized) {
                btnBench.setTextColor(accentColor)
                (btnBench.background as? android.graphics.drawable.GradientDrawable)?.setStroke(dp(BTN_STROKE_DP), accentColor)
            }
            // Botones de color del usuario
            if (this::btnUserWhite.isInitialized) {
                btnUserWhite.setTextColor(accentColor)
                (btnUserWhite.background as? android.graphics.drawable.GradientDrawable)?.setStroke(dp(BTN_STROKE_DP), accentColor)
            }
            if (this::btnUserBlack.isInitialized) {
                btnUserBlack.setTextColor(accentColor)
                (btnUserBlack.background as? android.graphics.drawable.GradientDrawable)?.setStroke(dp(BTN_STROKE_DP), accentColor)
            }
        }
    }

    private fun countdown(seconds: Int, onFinish: (() -> Unit)? = null) {
        for (sec in seconds downTo 1) {
            if (abortBenchmark) return
            root.post { fenTitle.text = "${sec}s" }
            Thread.sleep(1000)
        }
        if (abortBenchmark) return
        root.post { fenTitle.text = "0s" }
        Thread.sleep(300)
        root.post { fenTitle.text = "" }
        if (onFinish != null) {
            Thread.sleep(300)
            root.post { onFinish() }
        }
    }

    private fun updateDebug(msg: String) {
        root.post {
            debugText.visibility = View.VISIBLE
            debugText.maxLines = DEBUG_MAX_LINES

            debugText.text = msg
        }
    }

    private fun clearPanel() {
        root.post {
            // Limpiar FEN
            fenTitle.text = ""

            // Ocultar fila de dots
            if (this::dotsRow.isInitialized) {
                dotsRow.visibility = View.GONE
            }

            // Ocultar botones de elección manual del color del usuario
            if (this::userColorChoiceBar.isInitialized) {
                userColorChoiceBar.visibility = View.GONE
            }
            fenAwaitingUserColor = null

            // Ocultar contenedor de Lichess y todas sus filas
            if (this::lichessContainer.isInitialized) {
                lichessContainer.visibility = View.GONE
            }

            // Limpiar textos individuales por si acaso
            if (this::tvOpeningName.isInitialized) tvOpeningName.text = ""
            if (this::tvNextMoves.isInitialized) tvNextMoves.text = ""
            if (this::tvBestMove.isInitialized) tvBestMove.text = ""
            if (this::tvCounterAttack.isInitialized) tvCounterAttack.text = ""
            if (this::tvTablebaseResult.isInitialized) tvTablebaseResult.text = ""
            if (this::tvMateIn.isInitialized) tvMateIn.text = ""

            // Ocultar todas las filas
            if (this::rowOpeningName.isInitialized) rowOpeningName.visibility = View.GONE
            if (this::rowNextMoves.isInitialized) rowNextMoves.visibility = View.GONE
            if (this::rowBestMove.isInitialized) rowBestMove.visibility = View.GONE
            if (this::rowCounterAttack.isInitialized) rowCounterAttack.visibility = View.GONE
            if (this::rowTablebaseResult.isInitialized) rowTablebaseResult.visibility = View.GONE
            if (this::rowMateIn.isInitialized) rowMateIn.visibility = View.GONE
        }
    }

    private fun takeScreenshotOnce() {
        val rc = mpResultCode ?: return
        val data = mpData ?: return

        // 1. Limpiar TODO el panel inmediatamente
        clearPanel()

        // 2. Mostrar solo "PROCESANDO..."
        updateDebug("PROCESANDO...")

        // 3. Limpiar filtro de color del botón (volver a color normal)
        bubbleIcon.clearColorFilter()

        // 4. Bloquear botón por 3 segundos
        isCapturing = true
        root.postDelayed({
            isCapturing = false
            bubbleIcon.clearColorFilter() // Asegurar que se limpie al final
        }, DELAY_CAPTURE_RESET_MS)

        runCatching {
            if (activeMediaProjection == null) {
                val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
                activeMediaProjection = mgr.getMediaProjection(rc, data)

                // 🛡️ LEY DE ANDROID 14: Callback OBLIGATORIO
                activeMediaProjection?.registerCallback(object : android.media.projection.MediaProjection.Callback() {
                    override fun onStop() {
                        activeVirtualDisplay?.release()
                        activeVirtualDisplay = null
                        activeImageReader?.close()
                        activeImageReader = null
                        activeMediaProjection = null
                        mpData = null
                        mpResultCode = null
                        updatePermUi()
                    }
                }, android.os.Handler(android.os.Looper.getMainLooper()))

                val safeW = if (sw % 2 != 0) sw - 1 else sw
                val safeH = if (sh % 2 != 0) sh - 1 else sh
                activeImageReader = android.media.ImageReader.newInstance(safeW, safeH, android.graphics.PixelFormat.RGBA_8888, 2)
                activeVirtualDisplay = activeMediaProjection!!.createVirtualDisplay(
                    "chesz-shot", safeW, safeH, resources.displayMetrics.densityDpi,
                    android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    activeImageReader!!.surface, null, null
                )
            }

            val reader = activeImageReader ?: return@runCatching

            root.postDelayed({
                try {
                    val image = reader.acquireLatestImage() ?: run {
                        if (isDeveloperMode) return@postDelayed
                        root.post {
                            if (this@BubbleService::dotsRow.isInitialized) {
                                dotsRow.visibility = View.VISIBLE
                            }
                            if (this@BubbleService::lichessContainer.isInitialized) {
                                lichessContainer.visibility = View.VISIBLE
                            }
                            val cacheValido = lastBestMove.isNotEmpty() &&
                                (System.currentTimeMillis() - bmCacheTime) < BM_CACHE_DURATION_MS
                            if (cacheValido) {
                                tvBestMove.text = lastBestMove
                                rowBestMove.visibility = View.VISIBLE
                                updateDebug("")
                            } else {
                                updateDebug("Move any piece and try again")
                            }
                        }
                        return@postDelayed
                    }

                    val safeW = if (sw % 2 != 0) sw - 1 else sw
                    val safeH = if (sh % 2 != 0) sh - 1 else sh

                    Thread {
                        try {
                            val plane = image.planes[0]
                            val buffer = plane.buffer
                            val rowStride = plane.rowStride
                            val pixelStride = plane.pixelStride
                            val rowPadding = rowStride - pixelStride * safeW

                            val bitmap = android.graphics.Bitmap.createBitmap(
                                safeW + rowPadding / pixelStride,
                                safeH,
                                android.graphics.Bitmap.Config.ARGB_8888,
                            )
                            bitmap.copyPixelsFromBuffer(buffer)
                            val croppedLimpio = android.graphics.Bitmap.createBitmap(bitmap, 0, 0, safeW, safeH)
                            bitmap.recycle()

                            // --- INYECCION: VISION DE IA (Recorte y Escala de Grises) ---
                            // 1. Recorte Definitivo Photopea (Coord: 0, 458, 720x720)
                            val boardX = BOARD_X
                            val boardY = BOARD_Y
                            val boardSize = BOARD_SIZE

                            val safeCropW = if (boardX + boardSize > croppedLimpio.width) croppedLimpio.width - boardX else boardSize
                            val safeCropH = if (boardY + boardSize > croppedLimpio.height) croppedLimpio.height - boardY else boardSize

                            val recortado = android.graphics.Bitmap.createBitmap(
                                croppedLimpio,
                                boardX, boardY, safeCropW, safeCropH
                            )
                            croppedLimpio.recycle() // Liberar pantalla completa

                            // 2. Guardar imagen para debug
                            val dir = getExternalFilesDir(null)
                            if (dir != null) {
                                if (!dir.exists()) dir.mkdirs()
                                java.io.FileOutputStream(java.io.File(dir, "chesz_last.png")).use {
                                    recortado.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                                }
                            }
                            updateDebug("PROCESANDO...")
                            procesarConFenEngine(recortado) // recycle dentro del hilo

                            // 🆕 LIMPIEZA POST-CAPTURA (durante cooldown de 3s)
                            Thread.sleep(150)  // Dar tiempo a que procesarConFenEngine inicie

                            root.post {
                                activeImageReader?.let { reader ->
                                    var cleared = 0
                                    while (true) {
                                        val old = reader.acquireLatestImage()
                                        if (old == null) break
                                        old.close()
                                        cleared++
                                    }
                                    if (cleared > 0) {
                                        android.util.Log.d("Chesz", "✓ Buffer limpiado: $cleared frames")
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            updateDebug("📂 Error de archivo: ${e.message}")
                        } finally {
                            image.close()
                        }
                    }.start()
                } catch (e: Exception) {
                    updateDebug("❌ Error de lectura: ${e.message}")
                }
            }, DELAY_SCREENSHOT_MS)

        }.onFailure {
            updateDebug("❌ " + it.javaClass.simpleName + ": " + it.message)
            activeVirtualDisplay?.release()
            activeVirtualDisplay = null
            activeImageReader?.close()
            activeImageReader = null
            activeMediaProjection?.stop()
            activeMediaProjection = null
            mpData = null
            mpResultCode = null
            updatePermUi()
        }
    }

    private fun procesarConFenEngine(bitmap: android.graphics.Bitmap) {
        Thread {
            try {
                val fenOriginal = fenEngine.processBoard(bitmap)
                val fenPosicion = fenOriginal.substringBefore(" ")

                // Si la detección visual no pudo decidir el color del usuario, pausar el
                // pipeline y pedirlo manualmente antes de consultar Lichess.
                if (fenEngine.userColorAmbiguous) {
                    fenAwaitingUserColor = fenOriginal
                    root.post {
                        fenTitle.text = fenPosicion
                        if (this::dotsRow.isInitialized) {
                            dotsRow.visibility = View.VISIBLE
                        }
                        if (this::userColorChoiceBar.isInitialized) {
                            userColorChoiceBar.visibility = View.VISIBLE
                        }
                    }
                    return@Thread
                }

                // userColor = orientación visual del tablero (qué piezas juega el usuario abajo).
                // Asumimos color del usuario == side-to-move FEN — válido sólo cuando es el turno del usuario.
                val userColor = if (fenEngine.userPlaysBlack) "b" else "w"

                val fenParts = fenOriginal.split(" ")
                val fenFinal = if (fenParts.size >= 2) {
                    "${fenParts[0]} $userColor ${fenParts.drop(2).joinToString(" ")}"
                } else {
                    "${fenParts[0]} $userColor - - 0 1"
                }

                lastFen = fenFinal

                root.post {
                    fenTitle.text = fenPosicion

                    // Mostrar fila de dots cuando aparece el FEN
                    if (this::dotsRow.isInitialized) {
                        dotsRow.visibility = View.VISIBLE
                    }

                    if (esFenValido64(fenFinal)) {
                        lichessApiClient.queryLichess(fenFinal) { info ->
                            root.post {
                                updateLichessInfo(info)
                            }
                        }

                        runCatching {
                            val logDir = getExternalFilesDir(null)
                            if (logDir != null) {
                                val ts = java.text.SimpleDateFormat(
                                    "MM/dd HH:mm", java.util.Locale.getDefault()
                                ).format(java.util.Date())
                                // logfen_last.txt: último FEN detectado (archivo separado)
                                java.io.File(logDir, "logfen_last.txt")
                                    .writeText("[$ts]\n$fenFinal\n")
                            }
                        }
                    } else {
                        updateDebug("[FEN IMPERFECTO]\n$fenPosicion")
                    }
                }
            } catch (e: Exception) {
                root.post { updateDebug("Error FenEngine: ${e.message}") }
            } finally {
                bitmap.recycle()
            }
        }.start()
    }

    private fun formatLN(moves: String): SpannableString {
        if (moves.isBlank()) return SpannableString("")
        val tokens = moves.trim().split(" ")
        val sb = StringBuilder()
        data class Seg(val start: Int, val end: Int, val color: Int)
        val segs = mutableListOf<Seg>()
        tokens.forEachIndexed { i, move ->
            val isOwn = i % 2 == 0
            val chunk = when (i) {
                0 -> "[★$move "
                1 -> "$move] "
                else -> "$move "
            }
            val s = sb.length
            sb.append(chunk)
            segs.add(Seg(s, sb.length, if (isOwn) accentColor else 0xFF888888.toInt()))
        }
        val span = SpannableString(sb.toString())
        segs.forEach { span.setSpan(ForegroundColorSpan(it.color), it.start, it.end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE) }
        return span
    }

    private fun updateLichessInfo(info: LichessApiClient.LichessInfo) {
        if (!this::lichessContainer.isInitialized) return
        if (isDeveloperMode) return

        when (info.status) {
            LichessApiClient.Status.LOADING -> {
                lichessContainer.visibility = View.GONE
            }
            LichessApiClient.Status.ERROR -> {
                // Ocultar mensaje "PROCESANDO..." cuando hay error
                debugText.visibility = View.GONE
                lichessContainer.visibility = View.GONE
            }
            LichessApiClient.Status.SUCCESS -> {
                // Ocultar mensaje "PROCESANDO..." cuando se completa el análisis
                debugText.visibility = View.GONE
                lichessContainer.visibility = View.VISIBLE

                // Actualizar campos
                // Opening Name
                if (!info.openingName.isNullOrEmpty()) {
                    tvOpeningName.text = info.openingName
                    rowOpeningName.visibility = View.VISIBLE
                } else {
                    rowOpeningName.visibility = View.GONE
                }

                // Best Move
                if (!info.bestMove.isNullOrEmpty()) {
                    lastBestMove = info.bestMove!!
                    bmCacheTime = System.currentTimeMillis()
                    tvBestMove.text = info.bestMove
                    rowBestMove.visibility = View.VISIBLE
                } else {
                    rowBestMove.visibility = View.GONE
                }

                // Line (Next Moves) con formato especial
                if (!info.nextMoves.isNullOrEmpty()) {
                    tvNextMoves.text = formatLN(info.nextMoves ?: "")
                    rowNextMoves.visibility = View.VISIBLE
                } else {
                    rowNextMoves.visibility = View.GONE
                }

                // Win Rate (Counter Attack)
                val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val wrEnabled = prefs.getBoolean(PREF_WR, false)
                if (!info.counterAttack.isNullOrEmpty() && wrEnabled) {
                    tvCounterAttack.text = info.counterAttack
                    rowCounterAttack.visibility = View.VISIBLE
                } else {
                    rowCounterAttack.visibility = View.GONE
                }

                // TB y Mate automáticos según FEN actual
                val currentFen = lastFen ?: ""
                val pieceCount = currentFen.split(" ").getOrNull(0)?.count { it.isLetter() } ?: 99

                // Tablebase (solo si <= 7 piezas y hay dato)
                if (pieceCount <= 7 && !info.tbResult.isNullOrEmpty()) {
                    tvTablebaseResult.text = info.tbResult
                    rowTablebaseResult.visibility = View.VISIBLE
                } else {
                    rowTablebaseResult.visibility = View.GONE
                }

                // FIX3 y FIX5: Mate con texto "M# TÚ" o "M# RIVAL" + alerta JAQUE
                val mateDisplayText = if (info.mateText != null) {
                    // Si hay mate, mostrar texto de mate
                    if (info.inCheck) "${info.mateText} JAQUE" else info.mateText
                } else if (info.inCheck) {
                    // Si solo hay jaque (sin mate), mostrar JAQUE
                    "JAQUE"
                } else {
                    null
                }

                if (mateDisplayText != null) {
                    tvMateIn.text = mateDisplayText
                    rowMateIn.visibility = View.VISIBLE
                } else {
                    rowMateIn.visibility = View.GONE
                }
            }
        }
    }


    private fun runBenchmark() {
        if (this::btnBench.isInitialized) btnBench.visibility = android.view.View.GONE
        if (this::btnPrueba.isInitialized) btnPrueba.visibility = android.view.View.GONE

        abortBenchmark = false
        benchmarkThread = Thread {
            try {
                val truthLines = assets.open("benchmark/truth.txt").bufferedReader().readLines()
                val dirLog = getExternalFilesDir(null)
                if (dirLog != null && !dirLog.exists()) dirLog.mkdirs()
                val logFile = java.io.File(dirLog, "Testfenlog.txt")
                // Limpiar log al iniciar — cada ejecución parte de cero
                val tsB = java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
                logFile.writeText("=== BENCHMARK [$tsB] ===\n")

                var correctWhite = 0
                var correctBlack = 0
                val fallosBlancas = mutableListOf<Int>()
                val fallosNegras = mutableListOf<Int>()

                fun formatRes(color: String, pct: Int, fallos: List<Int>): String {
                    return if (pct == 100) "$color  100%" else "$color  $pct%  [X ${fallos.joinToString(",")}]"
                }

                fun procesarFoto(i: Int): Boolean {
                    try {
                        val bmp = assets.open("benchmark/$i.png").use {
                            android.graphics.BitmapFactory.decodeStream(it)
                        } ?: throw Exception("No se pudo decodificar benchmark/$i.png")

                        val expectedFen = truthLines.getOrNull(i - 1)?.substringBefore(" ") ?: ""
                        fenEngine.debugPhotoNum = i
                        fenEngine.debugExpectedFen = expectedFen  // FenEngine sabrá qué esperar
                        val predictedFen = fenEngine.processBoard(bmp).substringBefore(" ")
                        val detailedLog = fenEngine.getLastLog()
                        fenEngine.debugExpectedFen = null
                        bmp.recycle()

                        logFile.appendText("FOTO $i | LOCAL | P: [$predictedFen] | E: [$expectedFen]\n")
                        logFile.appendText(detailedLog)
                        return predictedFen == expectedFen && expectedFen.isNotEmpty()

                    } catch (e: Exception) {
                        logFile.appendText("FOTO $i | ERROR LOCAL: ${e.message}\n")
                        return false
                    }
                }

                root.post { fenTitle.text = "" }

                // Fase 1: Blancas (fotos 1-5)
                for (i in 1..5) {
                    if (abortBenchmark) throw Exception("ABORT_MANUAL")
                    root.post { updateDebug("TESTING WHITE\nFOTO $i / 5") }
                    val ok = procesarFoto(i)
                    if (ok) correctWhite++ else fallosBlancas.add(i)
                }
                val pctWhite = (correctWhite * 100) / 5
                val resWhite = formatRes("WHITE", pctWhite, fallosBlancas)

                if (abortBenchmark) throw Exception("ABORT_MANUAL")

                // Fase 2: Negras (fotos 6-10) - ejecuta automáticamente
                for (i in 6..10) {
                    if (abortBenchmark) throw Exception("ABORT_MANUAL")
                    val currentFoto = i - 5
                    root.post { updateDebug("TESTING BLACK\nFOTO $currentFoto / 5") }
                    val ok = procesarFoto(i)
                    if (ok) correctBlack++ else fallosNegras.add(i)
                }
                val pctBlack = (correctBlack * 100) / 5
                val resBlack = formatRes("BLACK", pctBlack, fallosNegras)
                val pctTotal = ((correctWhite + correctBlack) * 100) / 10
                
                logFile.appendText("=== CHESZ ===\n$resWhite\n$resBlack\nTOTAL $pctTotal%\n")

                root.post {
                    updateDebug("MATCH\n$resWhite\n$resBlack\nTOTAL TEST $pctTotal%")
                    if (this::btnBench.isInitialized) {
                        if (pctTotal < 100) {
                            btnBench.text = "ERROR -FIX ENGINE"
                            btnBench.background = android.graphics.drawable.GradientDrawable().apply {
                                setColor(COLOR_NEON_RED_BG)
                                setStroke(dp(BTN_STROKE_ALERT_DP), COLOR_NEON_RED_STROKE)
                                cornerRadius = 0f
                            }
                        } else {
                            btnBench.text = "ENGINE OK 100%"
                            btnBench.background = android.graphics.drawable.GradientDrawable().apply {
                                setColor(COLOR_ORANGE_BG)
                                setStroke(dp(BTN_STROKE_ALERT_DP), COLOR_ORANGE_STROKE)
                                cornerRadius = 0f
                            }
                        }
                        btnBench.textSize = 13f
                        btnBench.setTextColor(COLOR_WHITE)
                        btnBench.setOnClickListener { } // Sin acción
                        btnBench.visibility = android.view.View.VISIBLE
                    }
                }
                countdown(10)

            } catch (e: Exception) {
                if (e.message != "ABORT_MANUAL") {
                    root.post { updateDebug("ERROR: ${e.message}") }
                    Thread.sleep(5000)
                }
            } finally {
                benchmarkThread = null
                root.post {
                    if (this::btnBench.isInitialized) {
                        btnBench.text = "TEST FEN"
                        btnBench.textSize = TEXT_SIZE_BTN
                        btnBench.background = android.graphics.drawable.GradientDrawable().apply {
                            setColor(COLOR_BLACK)
                            setStroke(dp(BTN_STROKE_DP), COLOR_GREEN)
                            cornerRadius = dp(BTN_CORNER_DP).toFloat()
                        }
                        btnBench.setTextColor(COLOR_GREEN)
                        btnBench.setOnClickListener { runBenchmark() }
                    }
                    if (!abortBenchmark) {
                        fenTitle.text = "DEBUG MODE"
                        resetToGodMode()
                    }
                }
            }
        }.also { it.start() }
    }


    companion object {
        // --- Dimensiones UI (dp) ---
        private const val BUBBLE_SIZE_DP       = 60
        private const val BUBBLE_INIT_X_DP     = 35
        private const val BUBBLE_INIT_Y_DP     = 167
        private const val DRAG_THRESHOLD_DP    = 6
        private const val TAP_RADIUS_DP        = 30
        private const val PANEL_LEFT_MARGIN_DP = 30
        private const val KILL_CIRCLE_SIZE_DP  = 100
        private const val KILL_ICON_SIZE_DP    = 44
        private const val KILL_BOTTOM_OFFSET_DP = 60
        private const val KILL_HOVER_PADDING_DP = 18
        private const val BTN_CORNER_DP        = 20
        private const val BTN_STROKE_DP        = 1
        private const val BTN_STROKE_ALERT_DP  = 2
        private const val BTN_SPACING_DP       = 15
        private const val CLOSE_BTN_SIZE_DP    = 28
        private const val PERM_BAR_HEIGHT_DP   = 40

        // --- Proporciones del panel ---
        private const val PANEL_WIDTH_RATIO    = 0.60f
        private const val PANEL_HEIGHT_RATIO   = 0.17f

        // --- Tamaños de texto (sp) ---
        private const val TEXT_SIZE_FEN        = 9f
        private const val TEXT_SIZE_DEBUG      = 13f
        private const val TEXT_SIZE_BTN        = 12f

        // --- Colores ---
        val COLOR_GREEN         = 0xFF33FF00.toInt()
        val COLOR_BLACK         = 0xFF000000.toInt()
        val COLOR_WHITE         = 0xFFFFFFFF.toInt()
        val COLOR_PANEL_BG      = 0xBF000000.toInt()
        val COLOR_FLASH_RED     = 0xFFFF3333.toInt()
        val COLOR_KILL_RED      = 0xCCFF0000.toInt()
        val COLOR_NEON_RED_BG   = 0xD9FF0033.toInt()
        val COLOR_NEON_RED_STROKE = 0xFFFF0033.toInt()
        val COLOR_ORANGE_BG     = 0xD9FF8800.toInt()
        val COLOR_ORANGE_STROKE = 0xFFFFCC00.toInt()

        // --- Coordenadas de recorte del tablero ---
        private const val BOARD_X    = 0
        private const val BOARD_Y    = 458
        private const val BOARD_SIZE = 720

        // --- Timeouts de red (ms) ---
        private const val TIMEOUT_PING_CONNECT   = 4000
        private const val TIMEOUT_PING_READ      = 6000
private const val TIMEOUT_BENCH_CONNECT  = 4000
        private const val TIMEOUT_BENCH_READ     = 8500

        // --- Delays (ms) ---
        private const val DELAY_DEV_MODE_MS       = 2000L
        private const val DELAY_GOD_TOUCH_IGNORE_MS = 1000L  // ms que se ignora el touch al activar modo dios
        private const val DELAY_SCREENSHOT_MS     = 1500L
        private const val DELAY_FLASH_MS          = 220L
        private const val DELAY_KILL_ANIM_MS      = 60L
        private const val DELAY_CAPTURE_RESET_MS  = 3000L

        // --- Misc ---
        private const val KILL_HOVER_SCALE        = 1.40f
        private const val DEBUG_MAX_LINES         = 15
        private const val BENCH_CONTINUATION_LIMIT = 8

        // --- URLs ---
        private const val URL_ENGINE_PING    = "https://daxer2-chesz-engine.hf.space/"
        private const val URL_ENGINE_PREDICT = "https://daxer2-chesz-engine.hf.space/predict"
        private const val URL_HF_RESTART     = "https://huggingface.co/api/spaces/Daxer2/chesz-engine/restart"

        // --- SharedPreferences ---
        private const val PREFS_NAME = "chesz_overlay_prefs"
        private const val PREF_OP = "toggle_op"
        private const val PREF_BM = "toggle_bm"
        private const val PREF_LN = "toggle_ln"
        private const val PREF_WR = "toggle_wr"
    }

    private fun esFenValido64(fen: String): Boolean {
        val filas = fen.split(" ")[0].split("/")
        if (filas.size != 8) return false
        for (fila in filas) {
            var cuenta = 0
            for (char in fila) {
                if (char.isDigit()) cuenta += char.toString().toInt() else cuenta += 1
            }
            if (cuenta != 8) return false
        }
        return true
    }

    /**
     * Maneja la elección manual del color del usuario.
     */
    private fun onUserColorChosen(color: String) {
        val fen = fenAwaitingUserColor ?: return

        // Ocultar botones de elección
        if (this::userColorChoiceBar.isInitialized) {
            userColorChoiceBar.visibility = View.GONE
        }

        // Inyectar el color del usuario como side-to-move en el FEN
        val fenParts = fen.split(" ")
        val newFen = if (fenParts.size >= 2) {
            "${fenParts[0]} $color ${fenParts.drop(2).joinToString(" ")}"
        } else {
            "${fenParts[0]} $color - - 0 1"
        }

        // Actualizar lastFen y continuar con el procesamiento normal
        lastFen = newFen
        fenAwaitingUserColor = null

        // Consultar Lichess con el FEN actualizado
        if (esFenValido64(newFen)) {
            lichessApiClient.queryLichess(newFen) { info ->
                root.post {
                    updateLichessInfo(info)
                }
            }
        }
    }
}

val testUI = "TEXTO MS-DOS CON ESPACIOS          "

