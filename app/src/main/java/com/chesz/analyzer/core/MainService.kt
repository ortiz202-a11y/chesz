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
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import com.chesz.analyzer.R
import kotlin.math.abs

class MainService : Service() {

    private var windowManager: WindowManager? = null

    private var floatingView: View? = null
    private var panelView: View? = null

    private lateinit var floatingParams: WindowManager.LayoutParams
    private lateinit var panelParams: WindowManager.LayoutParams

    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    // Para clamp correcto
    private var screenW = 0
    private var screenH = 0
    private var viewW = 0
    private var viewH = 0

    // Estado actual
    private enum class Mode { W, L }
    private var mode: Mode = Mode.W

    override fun onCreate() {
        super.onCreate()
        startForegroundInternal()
        showFloatingButton()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        super.onDestroy()
        hidePanel()
        removeFloatingButton()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showFloatingButton() {
        if (windowManager != null || floatingView != null) return

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        floatingView = LayoutInflater.from(this).inflate(R.layout.overlay_floating_button, null)

        val layoutType =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE

        floatingParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        // Origen arriba-izquierda
        floatingParams.gravity = Gravity.TOP or Gravity.START
        floatingParams.x = 0
        floatingParams.y = 300

        val root = floatingView!!.findViewById<View>(R.id.floatingRoot)

root.setOnTouchListener { _, event ->
    when (event.action) {
        MotionEvent.ACTION_DOWN -> {
            initialX = floatingParams.x
            initialY = floatingParams.y
            initialTouchX = event.rawX
            initialTouchY = event.rawY
            true
        }

        MotionEvent.ACTION_MOVE -> {
            floatingParams.x = initialX + (event.rawX - initialTouchX).toInt()
            floatingParams.y = initialY + (event.rawY - initialTouchY).toInt()
            clampAndUpdate()

            if (panelView != null) {
                positionOverlayNextToButton()
            }
            true
        }

        MotionEvent.ACTION_UP -> {
            val dx = abs(event.rawX - initialTouchX)
            val dy = abs(event.rawY - initialTouchY)

            if (dx < 10 && dy < 10) {
                togglePanel()
                root.alpha = 0.5f
                root.postDelayed({ root.alpha = 1f }, 120)
            }
            true
        }

        else -> false
    }
}

        windowManager?.addView(floatingView, floatingParams)

        updateScreenSize()
        floatingView?.post {
            viewW = floatingView?.width ?: 0
            viewH = floatingView?.height ?: 0
            clampAndUpdate()
        }
    }
private fun removeFloatingButton() {
    try {
        floatingView?.let { windowManager?.removeView(it) }
    } catch (_: Throwable) {
    } finally {
        floatingView = null
    }
}

private fun togglePanel() {
    if (panelView == null) {
        showPanel()
    } else {
        hidePanel()
        // si luego quieres reubicar en vez de cerrar:
        // positionOverlayNextToButton()
    }
}

    private fun showPanel() {
        val wm = windowManager ?: return
        if (panelView != null) return

        val layoutType =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE

        // Panel NO fullscreen: tamaño overlay (no bloquea arrastrar el botón debajo)
        updateScreenSize()
        val overlayW = (screenW * 0.60f).toInt().coerceAtLeast(dp(160))
        val overlayH = (screenH * 0.20f).toInt().coerceAtLeast(dp(64))

        panelParams = WindowManager.LayoutParams(
            overlayW,
            overlayH,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        )
        panelParams.gravity = Gravity.TOP or Gravity.START

        panelView = LayoutInflater.from(this).inflate(R.layout.overlay_panel, null)

        // Tap fuera del overlay => cerrar
        panelView!!.setOnTouchListener { _, ev ->
            if (ev.action == MotionEvent.ACTION_OUTSIDE) {
                hidePanel()
                return@setOnTouchListener true
            }
            false
        }

        // Chips W/L (TextView)
        val modeWView = panelView!!.findViewById<TextView>(R.id.modeW)
        val modeLView = panelView!!.findViewById<TextView>(R.id.modeL)

        // Overlay limpio: sin mensajes por defecto
        setResult(null)

        // Estado inicial
        mode = Mode.W
        setChipActive(modeWView, true)
        setChipActive(modeLView, false)

        // Exclusión por taps
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

        wm.addView(panelView, panelParams)

        // Después de añadir la vista, posicionamos con proporciones
        panelView!!.post { positionOverlayNextToButton() }
    }

    private fun hidePanel() {
        try {
            panelView?.let { windowManager?.removeView(it) }
        } catch (_: Throwable) {
        } finally {
            panelView = null
        }
    }

    /**
     * Posiciona el overlay:
     * - 55% ancho pantalla
     * - 15% alto pantalla
     * - a la derecha del botón
     * - base alineada con la base del botón (crece hacia arriba)
     * - si se sale, empuja el GRUPO (botón+overlay) para que quepa
     * - PEGADO al botón (sin margen)
     */
    private fun positionOverlayNextToButton() {
        val pv = panelView ?: return
        val card = pv.findViewById<View>(R.id.panelCard) ?: return

        updateScreenSize()
        val bw = floatingView?.width ?: viewW
        val bh = floatingView?.height ?: viewH

        // Tamaño overlay relativo a la pantalla
        val overlayW = (screenW * 0.60f).toInt().coerceAtLeast(dp(160))
        val overlayH = (screenH * 0.20f).toInt().coerceAtLeast(dp(64))

        // Forzar tamaño del card por código
        val lp = FrameLayout.LayoutParams(overlayW, overlayH)
        lp.gravity = Gravity.TOP or Gravity.START
        card.layoutParams = lp

        val margin = 0 // PEGADO

        fun desiredX(): Int = floatingParams.x + bw + margin
        fun desiredY(): Int = (floatingParams.y + bh) - overlayH // crece hacia arriba

        var x = desiredX()
        var y = desiredY()

        // ---- Auto-ajuste moviendo el GRUPO ----
        // Si se sale por derecha: mover botón a la izquierda
        val overflowRight = (x + overlayW) - screenW
        if (overflowRight > 0) {
            floatingParams.x -= overflowRight
            clampAndUpdate()
            x = desiredX()
            y = desiredY()
        }

        // Si se sale por arriba: mover botón hacia abajo
        if (y < 0) {
            floatingParams.y += -y
            clampAndUpdate()
            x = desiredX()
            y = desiredY()
        }

        // Seguridad final
        x = x.coerceIn(0, (screenW - overlayW).coerceAtLeast(0))
        y = y.coerceIn(0, (screenH - overlayH).coerceAtLeast(0))

        card.x = x.toFloat()
        card.y = y.toFloat()
    }

    private fun setChipActive(tv: TextView, active: Boolean) {
        tv.alpha = if (active) 1f else 0.90f
        tv.setBackgroundResource(if (active) R.drawable.chip_mode_active else R.drawable.chip_mode_inactive)
    }

private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    /**
     * Solo resultado final (más adelante).
     */
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
            val channel = NotificationChannel(
                channelId,
                "Chesz Core Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }

        val notification: Notification = Notification.Builder(this, channelId)
            .setContentTitle("chesz")
            .setContentText("Overlay activo")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build()

        startForeground(1, notification)
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

    private fun clampAndUpdate() {
        if (floatingView == null || windowManager == null) return
        if (screenW <= 0 || screenH <= 0 || viewW <= 0 || viewH <= 0) return

        val maxX = (screenW - viewW).coerceAtLeast(0)
        val maxY = (screenH - viewH).coerceAtLeast(0)

        floatingParams.x = floatingParams.x.coerceIn(0, maxX)
        floatingParams.y = floatingParams.y.coerceIn(0, maxY)

        try {
            windowManager?.updateViewLayout(floatingView, floatingParams)
        } catch (_: Throwable) {
        }
    }
}
