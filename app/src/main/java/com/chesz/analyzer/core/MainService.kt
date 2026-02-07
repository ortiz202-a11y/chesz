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
import android.widget.Switch
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

    // Estado actual (solo para saber qué modo está activo)
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
        if (panelView == null) showPanel() else hidePanel()
    }

    private fun showPanel
 {
        val wm = windowManager ?: return
        if (panelView != null) return

        val layoutType =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE

        // Panel full-screen, clickeable (sin robar foco)
        panelParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        panelParams.gravity = Gravity.TOP or Gravity.START

        panelView = LayoutInflater.from(this).inflate(R.layout.overlay_panel, null)

        val root = panelView!!.findViewById<View>(R.id.panelRoot)
        val card = panelView!!.findViewById<View>(R.id.panelCard)

        val toggleW = panelView!!.findViewById<Switch>(R.id.toggleW)
        val toggleL = panelView!!.findViewById<Switch>(R.id.toggleL)

        // IMPORTANTE: UI limpia => no mostramos "modo", "listo", etc.
        // Solo mostraremos resultado final cuando exista (más adelante).
        // Placeholder temporal: mientras NO existe captura/FEN/análisis.
        // Esto fuerza a que el panel grande sea visible.
           setResult("CHESZ listo\n\n(Tap fuera para cerrar)\n\nW: online (default)\nL: local (manual)\n\nPaso 3 (captura→FEN) aún NO implementado.")setResult(null)

        // Estado inicial
        toggleW.isChecked = true
        toggleL.isChecked = false
        mode = Mode.W

        // Exclusión W/L (sin imprimir mensajes)
        toggleW.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (toggleL.isChecked) toggleL.isChecked = false
                mode = Mode.W
            } else {
                if (!toggleL.isChecked) toggleW.isChecked = true
            }
        }

        toggleL.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                if (toggleW.isChecked) toggleW.isChecked = false
                mode = Mode.L
            } else {
                if (!toggleW.isChecked) toggleL.isChecked = true
            }
        }

        // Tocar fuera del card => cerrar
        root.setOnClickListener { hidePanel() }

        // Evita que el click en el card cierre el panel
        card.setOnClickListener { /* no-op */ }

// Posicionar el CARD cerca del botón (sin depender del XML)
panelView!!.post {
    val fx = floatingParams.x
    val fy = floatingParams.y
    val bh = floatingView?.height ?: 0

    // Medir card
    card.measure(
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    )
    val cw = card.measuredWidth
    val ch = card.measuredHeight

    // Asegurar screenW/screenH actualizados
    updateScreenSize()

    val maxX = (screenW - cw).coerceAtLeast(0)
    val maxY = (screenH - ch).coerceAtLeast(0)

    val targetX = fx.coerceIn(0, maxX)
    val targetY = (fy + bh).coerceIn(0, maxY)

    card.x = targetX.toFloat()
    card.y = targetY.toFloat()
}
        wm.addView(panelView, panelParams)
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
     * Para futuro: aquí SOLO pondremos resultado FINAL (recomendación / error real).
     * No se usa para logs intermedios.
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
