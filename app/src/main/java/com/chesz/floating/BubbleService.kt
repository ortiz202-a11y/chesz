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

        val btnPx = dp(60)
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
                    x = dp(35)
                    y = dp(167)
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
                        updatePermUi() // Destruir boton de permiso instantaneamente
                        flashBubbleRed() // Feedback visual
                        if (!panelShown) showPanelIfFits()
                        if (this::devBar.isInitialized) devBar.visibility = View.VISIBLE
                        root.post { 
                            fenTitle.text = ">_ MODE DEBUG" 
                            debugText.text = "" // Consola en silencio
                        }
                    }
                    devHandler.postDelayed(devRunnable!!, 5000)

                    downRawY = e.rawY
                    startX = rootLp.x
                    startY = rootLp.y
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - downRawX).toInt()
                    val dy = (e.rawY - downRawY).toInt()

                    if (!dragging && (abs(dx) + abs(dy) > dp(6))) {
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
                        if (dist <= dp(30).toFloat()) togglePanel()
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
        val btnPx = dp(60)
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

        runCatching { wm.updateViewLayout(root, rootLp) }
    }

    private fun showPanelIfFits() {
        val dm = resources.displayMetrics
        val btnW = dp(60)
        val btnH = dp(60)
        val panelW = (dm.widthPixels * 0.60f).toInt()
        val panelH = (dm.heightPixels * 0.17f).toInt()

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
        root.requestLayout()
        runCatching { wm.updateViewLayout(root, rootLp) }
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
            val btnH = dp(60)
            val panelH = (dm.heightPixels * 0.17f).toInt()
            rootLp.y = rootLp.y + (panelH - btnH)
        }
        setStateALayout()
    }

            private fun buildPanel(): FrameLayout {
        val customFont = android.graphics.Typeface.createFromAsset(assets, "fonts/perfect_dos_vga.ttf")
        val panel = FrameLayout(this).apply {
            setBackgroundColor(0xA8000000.toInt())
            clipChildren = false
            clipToPadding = false
        }

        val panelBorder = android.graphics.drawable.GradientDrawable().apply {
            setColor(0x00000000)
            setStroke(dp(1).toInt(), 0xFF33FF00.toInt())
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
            textSize = 11f
            typeface = customFont
            setTextColor(0xFF33FF00.toInt())
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
            setTextColor(0xFF33FF00.toInt())
            textSize = 13f
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
            setColor(0xFF000000.toInt()) // Fondo Negro
            setStroke(dp(1), 0xFF33FF00.toInt()) // Borde Verde
            cornerRadius = dp(20).toFloat() // Forma Pastilla
        }

        btnPing = TextView(this).apply {
            text = "HOST P/R"
            typeface = customFont
            setTextColor(0xFF33FF00.toInt())
            textSize = 12f
            gravity = android.view.Gravity.CENTER
            background = btnBg
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener { pingAndResetHost() }
        }

            btnBench = TextView(this).apply {
            text = "TEST FEN"
            typeface = customFont
            setTextColor(0xFF33FF00.toInt())
            textSize = 12f
            gravity = android.view.Gravity.CENTER
            background = btnBg
            setPadding(dp(8), dp(8), dp(8), dp(8))
            setOnClickListener { updateDebug(">_ BENCHMARK: INICIANDO BATERIA ZERO-UI...") }
        }

        devBar.addView(btnPing, LinearLayout.LayoutParams(-2, -2))
        devBar.addView(android.view.View(this), LinearLayout.LayoutParams(dp(15), 0))
        devBar.addView(btnBench, LinearLayout.LayoutParams(-2, -2))
        col.addView(devBar, LinearLayout.LayoutParams(-1, -2).apply { leftMargin = dp(30); rightMargin = dp(0); bottomMargin = dp(4) })

        permBar = FrameLayout(this).apply {
            setOnClickListener { requestCapturePermission() }
            val permIcon = ImageView(context).apply {
                setImageResource(R.drawable.permit_icon)
                adjustViewBounds = true
            }
            addView(permIcon, FrameLayout.LayoutParams(-2, -2, android.view.Gravity.CENTER))
        }
        col.addView(permBar, LinearLayout.LayoutParams(-1, dp(40)).apply { leftMargin = dp(30); rightMargin = dp(0); bottomMargin = dp(4) })

        panel.addView(col, FrameLayout.LayoutParams(-1, -1))

        val close = ImageView(this).apply {
            setImageResource(R.drawable.close)
            setPadding(0, 0, 0, 0)
            setOnClickListener { hidePanel() }
        }
        panel.addView(close, FrameLayout.LayoutParams(dp(28), dp(28)).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.END
            topMargin = dp(-2)
            rightMargin = dp(-2)
        })
        return panel
    }

    private fun flashBubbleRed() {
        runCatching {
            bubbleIcon.setColorFilter(0xFFFF3333.toInt())
            bubbleIcon.postDelayed({ runCatching { bubbleIcon.clearColorFilter() } }, 220)
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

        val sizePx = dp(100)
        killCircle =
            FrameLayout(this).apply {
                background =
                    android.graphics.drawable.GradientDrawable().apply {
                        shape = android.graphics.drawable.GradientDrawable.OVAL
                        setColor(0xCCFF0000.toInt())
                    }
            }

        val xIcon =
            ImageView(this).apply {
                setImageResource(android.R.drawable.ic_delete)
                setColorFilter(0xFFFFFFFF.toInt())
            }

        killRoot.addView(killCircle, FrameLayout.LayoutParams(sizePx, sizePx, Gravity.CENTER))
        killCircle.addView(xIcon, FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER))

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
                    y = dp(60)
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
        val target = if (hover) 1.40f else 1.0f
        killCircle.animate().scaleX(target).scaleY(target).setDuration(60).start()
    }

    private fun bubbleCenterX(): Float {
        val loc = IntArray(2)
        root.getLocationOnScreen(loc)
        return loc[0] + (dp(60) / 2f)
    }

    private fun bubbleCenterY(): Float {
        val loc = IntArray(2)
        root.getLocationOnScreen(loc)
        val offset = if (panelShown) ((resources.displayMetrics.heightPixels * 0.17f).toInt() - dp(60)) else 0
        return loc[1] + offset + (dp(60) / 2f)
    }

    private fun isOverKillCenter(
        x: Float,
        y: Float,
    ): Boolean {
        if (!killShown) return false
        val loc = IntArray(2)
        killRoot.getLocationOnScreen(loc)
        val pad = dp(18)
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
        val w = if (rootLp.width > 0) rootLp.width else dp(60)
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
                    setColor(0xFF000000.toInt())
                    setStroke(dp(1), 0xFF33FF00.toInt())
                    cornerRadius = dp(20).toFloat()
                }
                btnPing.background = g
                btnPing.setTextColor(0xFF33FF00.toInt())
            }
            if (this::btnBench.isInitialized) btnBench.visibility = android.view.View.VISIBLE
            updateDebug("")
        }
    }

    private fun pingAndResetHost() {
        if (!isHostChecked) {
        // Escudo de 10s: Si falla la red, resetear la UI obligatoriamente
        devHandler.removeCallbacksAndMessages(null)
        devHandler.postDelayed({ 
            if (isDeveloperMode && !isHostChecked) {
                updateDebug(">_ ERROR: TIMEOUT DE RED (10s)")
                resetToGodMode()
            }
        }, 10000)

            updateDebug(">_ PING ENVIADO...")
            if (this::btnBench.isInitialized) btnBench.visibility = android.view.View.GONE
            
            kotlin.concurrent.thread {
                try {
                    val conn = java.net.URL("https://daxer2-chesz-engine.hf.space/").openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 4000
                    val rc = conn.responseCode
                    root.post { if (rc != 200 && rc != 302) updateDebug(">_ RESTART STATUS: $rc") }
                    root.post {
                        if (rc == 200 || rc == 503 || rc == 404) {
                            isHostChecked = true
                            val isOnline = (rc == 200 || rc == 404)
                            
                            val nColor = if (isOnline) 0xD9FF8800.toInt() else 0xD9FF0033.toInt()
                            val nStroke = if (isOnline) 0xFFFFCC00.toInt() else 0xFFFF0033.toInt()
                            
                            if (this::btnPing.isInitialized) {
                                btnPing.background = android.graphics.drawable.GradientDrawable().apply {
                                    setColor(nColor)
                                    setStroke(dp(2), nStroke)
                                    cornerRadius = dp(20).toFloat()
                                }
                                btnPing.setTextColor(0xFFFFFFFF.toInt())
                            }
                            
                            val msg = if (isOnline) ">_ HOST : ONLINE\n>_ OPTIONAL RESTART: ORANGE BTN" 
                                      else ">_ HOST : SLEEP\n>_ WAKE UP HOST: RED BTN"
                            updateDebug(msg)

                            root.postDelayed({
                                if (isHostChecked) {
                                    isHostChecked = false
                                    updateDebug(">_ TIME OUT")
                                    root.postDelayed({ resetToGodMode() }, 1500)
                                }
                            }, 10000)
                        } else {
                            if (this::btnPing.isInitialized) btnPing.visibility = android.view.View.GONE
                            updateDebug(">_ STATUS: OFFLINE\n>_ CHECK HOST / MANUAL REBOOT")
                            root.postDelayed({ resetToGodMode() }, 10000)
                        }
                    }
                } catch (e: Exception) {
                    root.post { 
                        if (this::btnPing.isInitialized) btnPing.visibility = android.view.View.GONE
                        updateDebug(">_ STATUS: OFFLINE\n>_ CHECK HOST / MANUAL REBOOT")
                        root.postDelayed({ resetToGodMode() }, 10000)
                    }
                }
            }
        } else {
            isHostChecked = false
            updateDebug(">_ HOST RESTARTING...\n>_ READY IN 1-3MIN.")
            root.postDelayed({ resetToGodMode() }, 5000)
            
            kotlin.concurrent.thread {
                try {
                    val conn = java.net.URL("https://huggingface.co/api/spaces/Daxer2/chesz-engine/restart").openConnection() as java.net.HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.setRequestProperty("Authorization", "Bearer " + "hf_" + "cnQEZ" + "zRccH" + "MdJcO" + "HgQfI" + "rueGa" + "uQypd" + "khuM")     
                    // doOutput quitado para POST simple
                    val rc = conn.responseCode
                    root.post { if (rc != 200 && rc != 302) updateDebug(">_ RESTART STATUS: $rc") }
                } catch (e: Exception) {}
            }
        }
    }

    private fun updateDebug(msg: String) {
        root.post {
            debugText.visibility = View.VISIBLE
            debugText.maxLines = 15

            debugText.text = msg
        }
    }

    private fun takeScreenshotOnce() {
        val rc = mpResultCode ?: return
        val data = mpData ?: return
        updateDebug("⚙ Iniciando captura...")
        root.post { fenTitle.text = "" }
        isCapturing = true
        root.postDelayed({ isCapturing = false }, 3000)

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
                            val boardX = 0
                            val boardY = 458
                            val boardSize = 720

                            val safeCropW = if (boardX + boardSize > croppedLimpio.width) croppedLimpio.width - boardX else boardSize
                            val safeCropH = if (boardY + boardSize > croppedLimpio.height) croppedLimpio.height - boardY else boardSize

                            val recortado = android.graphics.Bitmap.createBitmap(
                                croppedLimpio,
                                boardX, boardY, safeCropW, safeCropH
                            )
                            croppedLimpio.recycle() // Liberar pantalla completa

                            // 2. Conversion a Escala de Grises Universal (Anti-Camuflaje)
                            val grayBitmap = android.graphics.Bitmap.createBitmap(recortado.width, recortado.height, android.graphics.Bitmap.Config.ARGB_8888)
                            val canvas = android.graphics.Canvas(grayBitmap)
                            val paint = android.graphics.Paint()

                            val colorMatrix = android.graphics.ColorMatrix()
                            colorMatrix.setSaturation(0f) // Blanco y negro

                            // Multiplicador universal: Contraste 1.5x y brillo +15
                            val scale = 1.0f
                            val translate = 0f
                            val contrastMatrix = android.graphics.ColorMatrix(floatArrayOf(
                                scale, 0f, 0f, 0f,  translate,
                                0f, scale, 0f, 0f,  translate,
                                0f, 0f, scale, 0f,  translate,
                                0f, 0f, 0f,  1f,  0f
                            ))
                            colorMatrix.postConcat(contrastMatrix)

                            paint.colorFilter = android.graphics.ColorMatrixColorFilter(colorMatrix)
                            canvas.drawBitmap(recortado, 0f, 0f, paint)
                            recortado.recycle() // Liberar recorte a color
                            // -----------------------------------------------------------

                            val dir = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
                            if (dir != null) {
                                if (!dir.exists()) dir.mkdirs()
                                val file = java.io.File(dir, "chesz_last.png")
                                java.io.FileOutputStream(file).use {
                                    grayBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                                }
                                updateDebug(">_ ENVIANDO DATOS..."); sendToCheszEngine(file)
                            }
                            grayBitmap.recycle()
                        } catch (e: Exception) {
                            updateDebug("📂 Error de archivo: ${e.message}")
                        } finally {
                            image.close()
                        }
                    }.start()
                } catch (e: Exception) {
                    updateDebug("❌ Error de lectura: ${e.message}")
                }
            }, 600)

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

    private fun sendToCheszEngine(file: java.io.File) {
        Thread {
            try {
                val url = java.net.URL("https://Daxer2-chesz-engine.hf.space/predict")
                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.connectTimeout = 8000
                conn.readTimeout = 10000
                val boundary = "Boundary-" + System.currentTimeMillis()

                conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json")
                // doOutput quitado para POST simple
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

                conn.outputStream.use { out ->
                    val writer = java.io.PrintWriter(out.writer())
                    writer.print("--$boundary\r\n")
                    writer.print("Content-Disposition: form-data; name=\"file\"; filename=\"${file.name}\"\r\n")
                    writer.print("Content-Type: image/png\r\n")
                    writer.print("\r\n")
                    writer.flush()

                    file.inputStream().use { it.copyTo(out) }

                    writer.print("\r\n")
                    writer.print("--$boundary--\r\n")
                    writer.flush()
                }

                                                val rc = conn.responseCode
                    root.post { if (rc != 200 && rc != 302) updateDebug(">_ RESTART STATUS: $rc") }
                val stream = if (rc in 200..299) conn.inputStream else conn.errorStream

                if (rc == 200) {
                    val respuesta = stream?.bufferedReader()?.use { it.readText() } ?: "{}"
                    if (respuesta.isNotBlank()) {
                        val json = JSONObject(respuesta)
                        val fen = json.optString("fen", "")
                        root.post { fenTitle.text = fen.substringBefore(" ") }

                        lastFen = fen

                        if (esFenValido64(fen)) {
                            var textoFinal = ""

                            val chessdbData = json.optString("chessdb", "")
                            if (chessdbData.isNotEmpty() && chessdbData != "null") {
                                textoFinal += "\nCHESSDB: " + chessdbData.trim()
                            }

                            // --- CAJA NEGRA (LOG) ---
                            try {
                                val logDir = getExternalFilesDir(null)
                                if (logDir != null) {
                                    if (!logDir.exists()) logDir.mkdirs()
                                    val logFile = java.io.File(logDir, "chesz_log.txt")
                                    val ts = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
                                    val fData = json.optString("fen", "Vacio")
                                    val sData = json.optString("stockfish", "Vacio")

                                    val logContent = "==== [ $ts ] ====\nFEN RAW: $fData\n\nSTOCKFISH RAW: $sData\n"
                                    logFile.writeText(logContent)
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            // -------------------------
                            val stockfishData = json.optString("stockfish", "")
                            if (stockfishData.isNotEmpty() && stockfishData != "null") {
                                try {
                                    val sfJson = org.json.JSONObject(stockfishData)
                                    val rawMove = sfJson.optString("bestmove", "")
                                    val evalStr = sfJson.optString("evaluation", "")
                                    val mateStr = sfJson.optString("mate", "")
                                    val contStr = sfJson.optString("continuation", "")

                                    var move = rawMove
                                    var ponder = ""
                                    if (rawMove.startsWith("bestmove ")) {
                                        val parts = rawMove.split(" ")
                                        if (parts.size >= 2) move = parts[1]
                                        if (parts.size >= 4 && parts[2] == "ponder") ponder = parts[3]
                                    }

                                    textoFinal += "\n\n[BM] >  ${move.uppercase()}"
                                    if (ponder.isNotEmpty()) textoFinal += "\n[CA] >  ${ponder.uppercase()}"

                                    if (mateStr.isNotEmpty() && mateStr != "null") {
                                        textoFinal += "\n[VV] >  M$mateStr"
                                    } else if (evalStr.isNotEmpty() && evalStr != "null") {
                                        val d = evalStr.toDoubleOrNull()
                                        if (d != null && d > 0) {
                                            textoFinal += "\n[VV] >  +$evalStr"
                                        } else {
                                            textoFinal += "\n[VV] >  $evalStr"
                                        }
                                    } else {
                                        textoFinal += "\n[VV] >  0.0"
                                    }

                                    if (contStr.isNotEmpty() && contStr != "null") {
                                        val contParts = contStr.split(" ")
                                        var nmString = ""
                                        val limit = Math.min(8, contParts.size)
                                        for (i in 2 until limit) {
                                            val m = contParts[i].uppercase()
                                            if (i % 2 == 0) nmString += "($m) " else nmString += "$m "
                                        }
                                        if (nmString.isNotEmpty()) textoFinal += "\n[NM] >  ${nmString.trim()}"
                                    }
                                } catch (e: Exception) {
                                    textoFinal += "\n[RAW]> " + stockfishData.trim()
                                }
                            }

                            updateDebug(textoFinal.trim())
                        } else {
                            // EL FEN LLEGO, PERO ESTA CHUECO. IMPRIMIR DE TODOS MODOS:
                            val sfData = json.optString("stockfish", "Sin datos")
                            updateDebug("[FEN IMPERFECTO]\n" + fen + "\n\n[SF RAW]\n" + sfData.take(50) + "...")
                        }
                    } else {
                        updateDebug("[FALLO] JSON vacio o en blanco")
                    }
                } else {
                    val errorBody = stream?.bufferedReader()?.use { it.readText() } ?: "Sin detalles"
                    updateDebug("[FALLO HTTP] Codigo $rc -> $errorBody")
                }
            } catch (e: Exception) {
                updateDebug("❌ Error Red: ${e.message}")
            }
        }.start()
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

