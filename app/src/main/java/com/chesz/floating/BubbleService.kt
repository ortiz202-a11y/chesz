package com.chesz.floating

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
                    x = dp(30)
                    y = dp(180)
                }

        setStateA_layout()

        root.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragging = false
                    downRawX = e.rawX
                    downRawY = e.rawY
                    startX = rootLp.x
                    startY = rootLp.y
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - downRawX).toInt()
                    val dy = (e.rawY - downRawY).toInt()

                    if (!dragging && (abs(dx) + abs(dy) > dp(6))) {
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
                    if (dragging) {
                        if (isOverKillCenter(bubbleCenterX(), bubbleCenterY())) {
                            performKill()
                        } else {
                            setKillHover(false)
                            showKill(false)
                        }
                    } else {
                        togglePanel()
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

    private fun setStateA_layout() {
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
        val panelW = (dm.widthPixels * 0.55f).toInt()
        val panelH = (dm.heightPixels * 0.20f).toInt()

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
        updatePermUi()
        root.requestLayout()
        runCatching { wm.updateViewLayout(root, rootLp) }
    }

    private fun hidePanel() {
        if (panelShown) {
            val dm = resources.displayMetrics
            val btnH = dp(60)
            val panelH = (dm.heightPixels * 0.20f).toInt()
            rootLp.y = rootLp.y + (panelH - btnH)
        }
        setStateA_layout()
    }

    private fun buildPanel(): FrameLayout {
        val panel =
            FrameLayout(this).apply {
                setBackgroundColor(0x1A000000.toInt())
                clipChildren = false
                clipToPadding = false
            }

        val col =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(10), dp(0), dp(10), dp(0))
            }

        debugText =
            TextView(this).apply {
                setTextColor(0xFFD1D1D1.toInt())
                textSize = 10f
                gravity = android.view.Gravity.CENTER
                visibility = android.view.View.GONE
            }
        col.addView(debugText)

        permText =
            TextView(this).apply {
                text = "Permitir"
                setTextColor(0xFF000000.toInt())
                textSize = 13f
            }

        val permIcon =
            ImageView(this).apply {
                setImageResource(R.drawable.ic_check_green)
            }

        val permRow =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER
                addView(permText)
                addView(
                    permIcon,
                    LinearLayout.LayoutParams(dp(22), dp(22)).apply {
                        leftMargin = dp(10)
                    },
                )
            }

        permBar =
            FrameLayout(this).apply {
                background =
                    android.graphics.drawable.GradientDrawable().apply {
                        cornerRadius = dp(12).toFloat()
                        setColor(0xFFFFFFFF.toInt())
                    }
                setOnClickListener { requestCapturePermission() }
                setPadding(dp(14), 0, dp(14), 0)
                addView(
                    permRow,
                    FrameLayout.LayoutParams(-2, -1, android.view.Gravity.CENTER),
                )
            }

        col.addView(
            permBar,
            LinearLayout.LayoutParams(-2, dp(40)).apply {
                gravity = android.view.Gravity.CENTER_HORIZONTAL
                topMargin = dp(8)
            },
        )

        val close =
            ImageView(this).apply {
                setImageResource(R.drawable.close)
                setPadding(dp(4), dp(2), dp(4), dp(2))
                setOnClickListener { hidePanel() }
            }

        col.addView(View(this), LinearLayout.LayoutParams(-1, 0, 1f))
        val closeBar =
            FrameLayout(this).apply {
                addView(
                    close,
                    FrameLayout.LayoutParams((resources.displayMetrics.widthPixels * 0.30f).toInt(), dp(28), Gravity.CENTER),
                )
            }
        col.addView(closeBar)

        panel.addView(col, FrameLayout.LayoutParams(-1, -1))
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
        permBar.visibility = if (mpData != null) View.GONE else View.VISIBLE
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
                    y = dp(40)
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
        val offset = if (panelShown) ((resources.displayMetrics.heightPixels * 0.20f).toInt() - dp(60)) else 0
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

    private fun updateDebug(msg: String) {
        root.post {
            debugText.visibility = View.VISIBLE
            debugText.text = msg
        }
    }

    private fun takeScreenshotOnce() {
        val rc = mpResultCode ?: return
        val data = mpData ?: return
        updateDebug("⚙ Iniciando captura...")
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
                            val cropped = android.graphics.Bitmap.createBitmap(bitmap, 0, 0, safeW, safeH)
                            bitmap.recycle()

                            val dir = getExternalFilesDir(android.os.Environment.DIRECTORY_PICTURES)
                            if (dir != null) {
                                if (!dir.exists()) dir.mkdirs()
                                val file = java.io.File(dir, "chesz_last.png")
                                java.io.FileOutputStream(file).use {
                                    cropped.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                                }
                                updateDebug("📡 Enviando a API Soberana..."); sendToCheszEngine(file)
                            }
                            cropped.recycle()
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
                val boundary = "Boundary-" + System.currentTimeMillis()
                
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

                conn.outputStream.use { out ->
                    val writer = java.io.PrintWriter(out.writer())
                    writer.println("--$boundary")
                    writer.println("Content-Disposition: form-data; name=\"image\"; filename=\"${file.name}\"")
                    writer.println("Content-Type: image/png")
                    writer.println()
                    writer.flush()
                    
                    file.inputStream().use { it.copyTo(out) }
                    
                    writer.println()
                    writer.println("--$boundary--")
                    writer.flush()
                }

                if (conn.responseCode == 200) {
                    val response = conn.inputStream.bufferedReader().readText()
                    val fen = response.substringAfter("\"fen\":\"").substringBefore("\"")
                    updateDebug("✅ FEN: $fen")
                } else {
                    updateDebug("❌ Error API: ${conn.responseCode}")
                }
            } catch (e: Exception) {
                updateDebug("❌ Error Red: ${e.message}")
            }
        }.start()
    }

}
