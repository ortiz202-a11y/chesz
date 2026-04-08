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
import com.chesz.R
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

    // ===== MediaProjection permission cache =====
    private var mpResultCode: Int? = null
    private var mpData: Intent? = null
    private var activeMediaProjection: android.media.projection.MediaProjection? = null
    private var activeVirtualDisplay: android.hardware.display.VirtualDisplay? = null
    private var activeImageReader: android.media.ImageReader? = null

    // ===== FenEngine local =====
    private val fenEngine by lazy { com.chesz.engine.FenEngine(this) }

    // ===== Panel UI refs =====
    private lateinit var permBar: FrameLayout
    private lateinit var permText: TextView
    private lateinit var debugText: TextView
    private lateinit var fenTitle: TextView
    private lateinit var btnPing: TextView
    private lateinit var btnBench: TextView

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
                        updatePermUi() // Destruir boton de permiso instantaneamente
                        flashBubbleRed() // Feedback visual
                        if (!panelShown) showPanelIfFits()
                        if (this::devBar.isInitialized) devBar.visibility = View.VISIBLE
                        root.post {
                            fenTitle.text = "MODE DEBUG"
                            debugText.text = "" // Consola en silencio
                        }
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
        if (isCapturing) return
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
        devHandler.removeCallbacksAndMessages(null)
        isHostChecked = false
        isDeveloperMode = false
        fenTitle.text = ""
        debugText.text = ""
        debugText.visibility = View.GONE
        if (this::devBar.isInitialized) devBar.visibility = View.GONE
        
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
        val panel = FrameLayout(this).apply {
            setBackgroundColor(COLOR_PANEL_BG)
            clipChildren = false
            clipToPadding = false
        }

        val panelBorder = android.graphics.drawable.GradientDrawable().apply {
            setColor(0x00000000)
            setStroke(dp(BTN_STROKE_DP).toInt(), COLOR_GREEN)
            cornerRadius = 0f
        }

        val col = LinearLayout(this).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, 0)
            background = panelBorder
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

        btnPing = TextView(this).apply {
            text = "HOST P/R"
            typeface = customFont
            setTextColor(COLOR_GREEN)
            textSize = TEXT_SIZE_BTN
            gravity = android.view.Gravity.CENTER
            background = btnBg
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener { pingAndResetHost() }
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

        devBar.addView(btnPing, LinearLayout.LayoutParams(-2, -2))
        devBar.addView(android.view.View(this), LinearLayout.LayoutParams(dp(BTN_SPACING_DP), 0))
        devBar.addView(btnBench, LinearLayout.LayoutParams(-2, -2))
        col.addView(devBar, LinearLayout.LayoutParams(-1, -2).apply { leftMargin = dp(PANEL_LEFT_MARGIN_DP); rightMargin = dp(0); bottomMargin = dp(4) })

        permBar = FrameLayout(this).apply {
            setOnClickListener { requestCapturePermission() }
            val permIcon = ImageView(context).apply {
                setImageResource(R.drawable.permit_icon)
                adjustViewBounds = true
            }
            addView(permIcon, FrameLayout.LayoutParams(-2, -2, android.view.Gravity.CENTER))
        }
        col.addView(permBar, LinearLayout.LayoutParams(-1, dp(PERM_BAR_HEIGHT_DP)).apply { leftMargin = dp(PANEL_LEFT_MARGIN_DP); rightMargin = dp(0); bottomMargin = dp(4) })

        panel.addView(col, FrameLayout.LayoutParams(-1, -1))

        val close = ImageView(this).apply {
            setImageResource(R.drawable.close)
            setPadding(0, 0, 0, 0)
            setOnClickListener { hidePanel() }
        }
        panel.addView(close, FrameLayout.LayoutParams(dp(CLOSE_BTN_SIZE_DP), dp(CLOSE_BTN_SIZE_DP)).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.END
            topMargin = dp(-2)
            rightMargin = dp(-2)
        })
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
            if (this::btnPing.isInitialized) {
                btnPing.visibility = android.view.View.VISIBLE
                val g = android.graphics.drawable.GradientDrawable().apply {
                    setColor(COLOR_BLACK)
                    setStroke(dp(BTN_STROKE_DP), COLOR_GREEN)
                    cornerRadius = dp(BTN_CORNER_DP).toFloat()
                }
                btnPing.background = g
                btnPing.setTextColor(COLOR_GREEN)
            }
            if (this::btnBench.isInitialized) btnBench.visibility = android.view.View.VISIBLE
            updateDebug("")
        }
    }

    private fun countdown(seconds: Int, onFinish: (() -> Unit)? = null) {
        for (sec in seconds downTo 1) {
            root.post { fenTitle.text = "${sec}s" }
            Thread.sleep(1000)
        }
        root.post { fenTitle.text = "0s" }
        Thread.sleep(300)
        root.post { fenTitle.text = "" }
        if (onFinish != null) {
            Thread.sleep(300)
            root.post { onFinish() }
        }
    }

    private fun pingAndResetHost() {
        if (!isHostChecked) {
            devHandler.removeCallbacksAndMessages(null)
            root.post { 
                fenTitle.text = ""
                updateDebug("PING ENVIADO...") 
            }
            if (this::btnBench.isInitialized) btnBench.visibility = android.view.View.GONE
            if (this::btnPing.isInitialized) btnPing.visibility = android.view.View.GONE

            kotlin.concurrent.thread {
                try {
                    val conn = java.net.URL(URL_ENGINE_PING).openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = TIMEOUT_PING_CONNECT
                    conn.readTimeout = TIMEOUT_PING_READ
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    conn.instanceFollowRedirects = true
                    val rc = conn.responseCode

                    root.post {
                        if (rc == 200 || rc == 503 || rc == 404) {
                            isHostChecked = true
                            val isOnline = (rc == 200 || rc == 404)
                            val nColor = if (isOnline) COLOR_ORANGE_BG else COLOR_NEON_RED_BG
                            val nStroke = if (isOnline) COLOR_ORANGE_STROKE else COLOR_NEON_RED_STROKE

                            if (this::btnPing.isInitialized) {
                                btnPing.visibility = android.view.View.VISIBLE
                                btnPing.background = android.graphics.drawable.GradientDrawable().apply {
                                    setColor(nColor)
                                    setStroke(dp(BTN_STROKE_ALERT_DP), nStroke)
                                    cornerRadius = dp(BTN_CORNER_DP).toFloat()
                                }
                                btnPing.setTextColor(COLOR_WHITE)
                            }

                            val msg = if (isOnline) "HOST : ONLINE\nOPTIONAL RESTART: ORANGE BTN" else "HOST : SLEEP\nWAKE UP HOST: RED BTN"
                            updateDebug(msg)

                            Thread {
                                countdown(5) {
                                    if (isHostChecked) {
                                        isHostChecked = false
                                        updateDebug("TIME OUT")
                                        root.postDelayed({ fenTitle.text = "MODE DEBUG"; resetToGodMode() }, 1500)
                                    }
                                }
                            }.start()
                        } else {
                            updateDebug("STATUS: OFFLINE\nCHECK HOST / MANUAL REBOOT")
                            Thread {
                                countdown(5) { fenTitle.text = "MODE DEBUG"; resetToGodMode() }
                            }.start()
                        }
                    }
                } catch (e: Exception) {
                    root.post {
                        updateDebug("STATUS: OFFLINE\nCHECK HOST / MANUAL REBOOT")
                        Thread { countdown(5) { fenTitle.text = "MODE DEBUG"; resetToGodMode() } }.start()
                    }
                }
            }
        } else {
            isHostChecked = false
            root.post { updateDebug("HOST RESTARTING...\nREADY IN 1-3MIN.") }

            Thread { countdown(5) { fenTitle.text = "MODE DEBUG"; resetToGodMode() } }.start()

            kotlin.concurrent.thread {
                try {
                    val conn = java.net.URL(URL_HF_RESTART).openConnection() as java.net.HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    conn.setRequestProperty("Authorization", "Bearer " + "hf_" + "cnQEZ" + "zRccH" + "MdJcO" + "HgQfI" + "rueGa" + "uQypd" + "khuM")
                    val rc = conn.responseCode
                } catch (e: Exception) {}
            }
        }
    }

    private fun updateDebug(msg: String) {
        root.post {
            debugText.visibility = View.VISIBLE
            debugText.maxLines = DEBUG_MAX_LINES

            debugText.text = msg
        }
    }

    private fun takeScreenshotOnce() {
        val rc = mpResultCode ?: return
        val data = mpData ?: return
        updateDebug("⚙ Iniciando captura...")
        root.post { fenTitle.text = "" }
        isCapturing = true
        root.postDelayed({ isCapturing = false }, DELAY_CAPTURE_RESET_MS)

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
                    val image = reader.acquireLatestImage()
                    if (image == null) {
                        updateDebug("⏳ Esperando frame (Toca de nuevo)")
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
                val fen = fenEngine.processBoard(bitmap)
                lastFen = fen
                val fenPosicion = fen.substringBefore(" ")
                root.post {
                    fenTitle.text = fenPosicion
                    if (esFenValido64(fen)) {
                        updateDebug(fenPosicion)
                        runCatching {
                            val logDir = getExternalFilesDir(null)
                            if (logDir != null) {
                                val ts = java.text.SimpleDateFormat(
                                    "MM/dd HH:mm", java.util.Locale.getDefault()
                                ).format(java.util.Date())
                                // fen_last.txt: último FEN detectado (archivo separado, no interfiere con chesz_log.txt)
                                java.io.File(logDir, "fen_last.txt")
                                    .writeText("[$ts]\n$fen\n")
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


    private fun runBenchmark() {
        if (this::btnBench.isInitialized) btnBench.visibility = android.view.View.GONE
        if (this::btnPing.isInitialized) btnPing.visibility = android.view.View.GONE

        Thread {
            try {
                val truthLines = assets.open("benchmark/truth.txt").bufferedReader().readLines()
                val dirLog = getExternalFilesDir(null)
                if (dirLog != null && !dirLog.exists()) dirLog.mkdirs()
                val logFile = java.io.File(dirLog, "FEN.TXT")
                // Limpiar ambos logs al iniciar — cada ejecución del test parte de cero
                val tsB = java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
                logFile.writeText("=== BENCHMARK [$tsB] ===\n")
                java.io.File(dirLog, "chesz_log.txt").writeText("")
                
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

                        fenEngine.debugPhotoNum = i
                        val predictedFen = fenEngine.processBoard(bmp).substringBefore(" ")
                        bmp.recycle()

                        val expectedFen = truthLines.getOrNull(i - 1)?.substringBefore(" ") ?: ""
                        logFile.appendText("FOTO $i | LOCAL | P: [$predictedFen] | E: [$expectedFen]\n")

                        return predictedFen == expectedFen && expectedFen.isNotEmpty()

                    } catch (e: Exception) {
                        logFile.appendText("FOTO $i | ERROR LOCAL: ${e.message}\n")
                        return false
                    }
                }

                root.post { fenTitle.text = "" }
                for (i in 1..5) {
                    root.post { updateDebug("TEST 1/2\nFOTO $i / 5") }
                    val ok = procesarFoto(i)
                    if (ok) correctWhite++ else fallosBlancas.add(i)
                }
                val pctWhite = (correctWhite * 100) / 5
                val resWhite = formatRes("WHITE", pctWhite, fallosBlancas)
                
                var phase2Triggered = false
                root.post { 
                    updateDebug("TEST 1/2\nMATCH\n$resWhite\nOPTIONAL 2 TEST")
                    if (this::btnBench.isInitialized) {
                        btnBench.text = "TEST 2/2"
                        btnBench.background = android.graphics.drawable.GradientDrawable().apply {
                            setColor(COLOR_ORANGE_BG)
                            setStroke(dp(BTN_STROKE_ALERT_DP), COLOR_ORANGE_STROKE)
                            cornerRadius = dp(BTN_CORNER_DP).toFloat()
                        }
                        btnBench.setTextColor(COLOR_WHITE)
                        btnBench.visibility = android.view.View.VISIBLE
                        btnBench.setOnClickListener {
                            phase2Triggered = true
                            btnBench.visibility = android.view.View.GONE
                        }
                    }
                }
                
                for (sec in 10 downTo 1) {
                    root.post { fenTitle.text = "${sec}s" }
                    for (ms in 0 until 10) {
                        if (phase2Triggered) break
                        Thread.sleep(100)
                    }
                    if (phase2Triggered) break
                }

                if (!phase2Triggered) {
                    root.post { fenTitle.text = "0s" }
                    Thread.sleep(300)
                    root.post { fenTitle.text = "" }
                    logFile.appendText("=== ABORTO MANUAL ===\n")
                    throw Exception("ABORT_MANUAL")
                }

                root.post { fenTitle.text = "" }
                for (i in 6..10) {
                    val currentFoto = i - 5
                    root.post { updateDebug("TEST 2/2\nFOTO $currentFoto / 5") }
                    val ok = procesarFoto(i)
                    if (ok) correctBlack++ else fallosNegras.add(i)
                }
                val pctBlack = (correctBlack * 100) / 5
                val resBlack = formatRes("BLACK", pctBlack, fallosNegras)
                val pctTotal = ((correctWhite + correctBlack) * 100) / 10
                
                logFile.appendText("=== CHESZ ===\n")
                
                root.post {
                    updateDebug("MATCH\n$resWhite\n$resBlack\nTOTAL TEST $pctTotal%")
                }
                countdown(10)

            } catch (e: Exception) {
                if (e.message != "ABORT_MANUAL") {
                    root.post { updateDebug("ERROR: ${e.message}") }
                    Thread.sleep(5000)
                }
            } finally {
                root.post {
                    if (this::btnBench.isInitialized) {
                        btnBench.text = "TEST FEN"
                        btnBench.background = android.graphics.drawable.GradientDrawable().apply {
                            setColor(COLOR_BLACK)
                            setStroke(dp(BTN_STROKE_DP), COLOR_GREEN)
                            cornerRadius = dp(BTN_CORNER_DP).toFloat()
                        }
                        btnBench.setTextColor(COLOR_GREEN)
                        btnBench.setOnClickListener { runBenchmark() }
                    }
                    fenTitle.text = "MODE DEBUG"
                    resetToGodMode()
                }
            }
        }.start()
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
        private const val TEXT_SIZE_FEN        = 11f
        private const val TEXT_SIZE_DEBUG      = 13f
        private const val TEXT_SIZE_BTN        = 12f

        // --- Colores ---
        val COLOR_GREEN         = 0xFF33FF00.toInt()
        val COLOR_BLACK         = 0xFF000000.toInt()
        val COLOR_WHITE         = 0xFFFFFFFF.toInt()
        val COLOR_PANEL_BG      = 0xA8000000.toInt()
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
        private const val DELAY_DEV_MODE_MS       = 3500L
        private const val DELAY_GOD_TOUCH_IGNORE_MS = 400L  // ms que se ignora el touch al activar modo dios
        private const val DELAY_SCREENSHOT_MS     = 600L
        private const val DELAY_FLASH_MS          = 220L
        private const val DELAY_KILL_ANIM_MS      = 60L
        private const val DELAY_CAPTURE_RESET_MS  = 3000L
        private const val DELAY_BENCH_BETWEEN_MS  = 1500L

        // --- Misc ---
        private const val KILL_HOVER_SCALE        = 1.40f
        private const val DEBUG_MAX_LINES         = 15
        private const val BENCH_CONTINUATION_LIMIT = 8

        // --- URLs ---
        private const val URL_ENGINE_PING    = "https://daxer2-chesz-engine.hf.space/"
        private const val URL_ENGINE_PREDICT = "https://daxer2-chesz-engine.hf.space/predict"
        private const val URL_HF_RESTART     = "https://huggingface.co/api/spaces/Daxer2/chesz-engine/restart"
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
}

val testUI = "TEXTO MS-DOS CON ESPACIOS          "

