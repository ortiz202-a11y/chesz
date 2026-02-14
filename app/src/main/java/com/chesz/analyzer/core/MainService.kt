package com.chesz.analyzer.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.chesz.analyzer.R
import kotlin.math.abs

class MainService : Service() {
    private var windowManager: WindowManager? = null

    // UNA sola ventana/root para TODO
    private var rootView: FrameLayout? = null
    private lateinit var rootParams: WindowManager.LayoutParams

    // Vistas internas
    private var buttonView: View? = null
    private var panelView: View? = null

    // Tamaños pantalla
    private var screenW = 0
    private var screenH = 0

    // Drag
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    // Estado W/L
    private enum class Mode { W, L }

    private var mode: Mode = Mode.W

    override fun onCreate() {
        super.onCreate()
        startForegroundInternal()
        showOverlay()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int = START_STICKY

    override fun onDestroy() {
        super.onDestroy()
        removeOverlay()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showOverlay() {
        if (windowManager != null || rootView != null) return

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        updateScreenSize()

        val layoutType =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            }

        rootParams =
            WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT,
            )
        rootParams.gravity = Gravity.TOP or Gravity.START
        rootParams.x = 0
        rootParams.y = 300

        val root = FrameLayout(this)
        rootView = root

        // 1) Panel (dentro del root)
        val pv = View.inflate(this, R.layout.overlay_panel, null)
        panelView = pv

        // Forzar tamaño del panel (ventana visible) por % pantalla
        val panelW = (screenW * 0.60f).toInt().coerceAtLeast(dp(160))
        val panelH = (screenH * 0.30f).toInt().coerceAtLeast(dp(64))

        val panelLp = FrameLayout.LayoutParams(panelW, panelH)
        panelLp.gravity = Gravity.TOP or Gravity.START
        pv.layoutParams = panelLp
        pv.visibility = View.GONE

        // 2) Botón (dentro del root)
        val bv = View.inflate(this, R.layout.overlay_floating_button, null)
        buttonView = bv

        val btnLp =
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
            )
        btnLp.gravity = Gravity.TOP or Gravity.START
        bv.layoutParams = btnLp

        // Orden: primero panel, luego botón ENCIMA
        root.addView(pv)
        root.addView(bv)

        // Colocar botón ENCIMA del panel (esquina inferior izquierda, invasión 6dp)
        root.post {
            applyButtonOverPanel()
        }

        // Tap fuera del panel NO hace nada aquí (porque es una sola ventana no fullscreen)
        // Toggle panel con tap en botón
        val floatingRoot = bv.findViewById<View>(R.id.floatingRoot)
        floatingRoot.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = rootParams.x
                    initialY = rootParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    rootParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    rootParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    clampRootAndUpdate()
                    true
                }

                MotionEvent.ACTION_UP -> {
                    val dx = abs(event.rawX - initialTouchX)
                    val dy = abs(event.rawY - initialTouchY)
                    if (dx < 10 && dy < 10) {
                        togglePanel()
                        floatingRoot.alpha = 0.5f
                        floatingRoot.postDelayed({ floatingRoot.alpha = 1f }, 120)
                    }
                    true
                }

                else -> {
                    false
                }
            }
        }

        windowManager?.addView(root, rootParams)

        // Configurar chips W/L y resultado limpio
        setupPanelUi()
    }

    private fun setupPanelUi() {
        val pv = panelView ?: return
        val modeWView = pv.findViewById<TextView>(R.id.modeW)
        val modeLView = pv.findViewById<TextView>(R.id.modeL)

        setResult(null)
        mode = Mode.W
        setChipActive(modeWView, true)
        setChipActive(modeLView, false)

        modeWView.setOnClickListener {
            if (mode != Mode.W) {
                mode = Mode.W
                setChipActive(modeWView, true)
                setChipActive(modeLView, false)
            }
        }
        modeLView.setOnClickListener {
            if (mode != Mode.L) {
                mode = Mode.L
                setChipActive(modeLView, true)
                setChipActive(modeWView, false)
            }
        }
    }

    private fun applyButtonOverPanel() {
        val pv = panelView ?: return
        val bv = buttonView ?: return

        // panel visible size (ya forzado)
        val panelW = pv.layoutParams.width
        val panelH = pv.layoutParams.height

        // botón real size
        bv.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val bw = bv.measuredWidth
        val bh = bv.measuredHeight

        // Panel estará “atrás” (0,0). Botón encima en esquina inferior izquierda.
        // Invasión 6dp => el botón entra 6dp al panel.
        val invade = dp(6)

        // Posición del botón relativa al panel (root local coords)
        bv.x = (-invade).toFloat()
        bv.y = (panelH - bh + invade).toFloat()
    }

    private fun togglePanel() {
        val pv = panelView ?: return
        pv.visibility = if (pv.visibility == View.VISIBLE) View.GONE else View.VISIBLE

        // Re-aplicar overlay del botón encima por si cambió layout
        rootView?.post { applyButtonOverPanel() }
    }

    private fun removeOverlay() {
        try {
            rootView?.let { windowManager?.removeView(it) }
        } catch (_: Throwable) {
        } finally {
            rootView = null
            buttonView = null
            panelView = null
            windowManager = null
        }
    }

    private fun clampRootAndUpdate() {
        val wm = windowManager ?: return
        val rv = rootView ?: return

        updateScreenSize()

        // tamaño total del grupo = panel size (porque botón está superpuesto)
        val pv = panelView ?: return
        val groupW = pv.layoutParams.width
        val groupH = pv.layoutParams.height

        val maxX = (screenW - groupW).coerceAtLeast(0)
        val maxY = (screenH - groupH).coerceAtLeast(0)

        rootParams.x = rootParams.x.coerceIn(0, maxX)
        rootParams.y = rootParams.y.coerceIn(0, maxY)

        try {
            wm.updateViewLayout(rv, rootParams)
        } catch (_: Throwable) {
        }
    }

    private fun updateScreenSize() {
        val wm = windowManager ?: return
        if (Build.VERSION.SDK_INT >= 30) {
            val bounds = wm.currentWindowMetrics.bounds
            screenW = bounds.width()
            screenH = bounds.height()
        } else {
            val display = wm.defaultDisplay
            val p = Point()
            display.getRealSize(p)
            screenW = p.x
            screenH = p.y
        }
    }

    private fun setChipActive(
        tv: TextView,
        active: Boolean,
    ) {
        tv.alpha = if (active) 1f else 0.90f
        tv.setBackgroundResource(if (active) R.drawable.chip_mode_active else R.drawable.chip_mode_inactive)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun setResult(msg: String?) {
        val pv = panelView ?: return
        val tv = pv.findViewById<TextView>(R.id.resultText) ?: return
        val clean = msg?.trim().orEmpty()
        if (clean.isEmpty()) {
            tv.text = ""
            tv.visibility = View.GONE
        } else {
            tv.text = clean
            tv.visibility = View.VISIBLE
        }
    }

    private fun startForegroundInternal() {
        val channelId = "chesz_core_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    channelId,
                    "Chesz Core Service",
                    NotificationManager.IMPORTANCE_LOW,
                )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification: Notification =
            Notification
                .Builder(this, channelId)
                .setContentTitle("chesz")
                .setContentText("Overlay activo")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .build()

        startForeground(1, notification)
    }
}
