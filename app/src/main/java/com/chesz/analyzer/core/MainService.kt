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
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import com.chesz.analyzer.R
import kotlin.math.abs

class MainService : Service() {

    private var windowManager: WindowManager? = null

    // UN SOLO overlay root
    private var overlayView: View? = null
    private lateinit var overlayParams: WindowManager.LayoutParams

    // Referencias internas (hijos)
    private var floatingRoot: View? = null
    private var btnView: View? = null
    private var panelRoot: View? = null
    private var panelCard: View? = null

    // Chips
    private var modeWView: TextView? = null
    private var modeLView: TextView? = null

    // Estado W/L
    private enum class Mode { W, L }
    private var mode: Mode = Mode.W

    // Drag state
    private var touchSlop = 0
    private var isDragging = false
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var startX = 0f
    private var startY = 0f

    // Pantalla
    private var screenW = 0
    private var screenH = 0

    override fun onCreate() {
        super.onCreate()
        touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        startForegroundInternal()
        showOverlayRoot()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        super.onDestroy()
        removeOverlayRoot()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showOverlayRoot() {
        if (overlayView != null) return

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val layoutType =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE

        // Root full-screen para contener panel y botón en la MISMA ventana (Word)
        overlayParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        overlayParams.gravity = Gravity.TOP or Gravity.START
        overlayParams.x = 0
        overlayParams.y = 0

        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_root, null)

        // Hijos
        panelRoot = overlayView!!.findViewById(R.id.panelRoot)
        panelCard = overlayView!!.findViewById(R.id.panelCard)
        floatingRoot = overlayView!!.findViewById(R.id.floatingRoot)

        btnView = overlayView!!.findViewById(R.id.btn)
        modeWView = overlayView!!.findViewById(R.id.modeW)
        modeLView = overlayView!!.findViewById(R.id.modeL)

        // Panel inicia oculto
        panelRoot?.visibility = View.GONE
        // El card siempre visible (ya no hay "fantasma" de addView separado)
        panelCard?.visibility = View.VISIBLE

        // Tocar fuera del card = cerrar panel
        panelRoot?.setOnClickListener { hidePanel() }
        // Tocar dentro no cierra
        panelCard?.setOnClickListener { /* no-op */ }

        // Chips W/L
        mode = Mode.W
        refreshChips()

        modeWView?.setOnClickListener {
            if (mode != Mode.W) {
                mode = Mode.W
                refreshChips()
            }
        }
        modeLView?.setOnClickListener {
            if (mode != Mode.L) {
                mode = Mode.L
                refreshChips()
            }
        }

        // Posición inicial del botón
        floatingRoot?.x = 0f
        floatingRoot?.y = dp(300).toFloat()

        // Touch del botón: drag vs tap
                val touchListener = View.OnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isDragging = false
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    startX = floatingRoot?.x ?: v.x
                    startY = floatingRoot?.y ?: v.y
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = kotlin.math.abs(event.rawX - initialTouchX)
                    val dy = kotlin.math.abs(event.rawY - initialTouchY)
                    if (!isDragging && (dx > touchSlop || dy > touchSlop)) {
                        isDragging = true
                    }

                    val nx = startX + (event.rawX - initialTouchX)
                    val ny = startY + (event.rawY - initialTouchY)

                    updateScreenSize()
                    val target = floatingRoot ?: v
                    val vw = target.width.toFloat().coerceAtLeast(1f)
                    val vh = target.height.toFloat().coerceAtLeast(1f)
                    target.x = nx.coerceIn(0f, (screenW - vw).coerceAtLeast(0f))
                    target.y = ny.coerceIn(0f, (screenH - vh).coerceAtLeast(0f))

                    if (panelRoot?.visibility == View.VISIBLE) {
                        positionOverlayNextToButton()
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    val dx = kotlin.math.abs(event.rawX - initialTouchX)
                    val dy = kotlin.math.abs(event.rawY - initialTouchY)
                    if (!isDragging && dx < 10 && dy < 10) {
                        togglePanel()
                        val target = floatingRoot ?: v
                        target.alpha = 0.5f
                        target.postDelayed({ target.alpha = 1f }, 120)
                    }
                    isDragging = false
                    true
                }

                else -> false
            }
        }

        floatingRoot?.isClickable = true
        btnView?.isClickable = true
        floatingRoot?.setOnTouchListener(touchListener)
        btnView?.setOnTouchListener(touchListener)


        windowManager?.addView(overlayView, overlayParams)

        // Asegurar medidas de pantalla
        updateScreenSize()
        overlayView?.post {
            updateScreenSize()
            // Si panel estuviera visible por alguna razón, reposiciona
            if (panelRoot?.visibility == View.VISIBLE) {
                positionOverlayNextToButton()
            }
        }
    }

    private fun removeOverlayRoot() {
        try {
            overlayView?.let { windowManager?.removeView(it) }
        } catch (_: Throwable) {
        } finally {
            overlayView = null
            floatingRoot = null
            panelRoot = null
            panelCard = null
            modeWView = null
            modeLView = null
        }
    }

    private fun togglePanel() {
        if (panelRoot?.visibility == View.VISIBLE) hidePanel() else showPanel()
    }

    private fun showPanel() {
        panelRoot?.visibility = View.VISIBLE
        positionOverlayNextToButton()
    }

    private fun hidePanel() {
        panelRoot?.visibility = View.GONE
    }

    /**
     * Posiciona el card del panel:
     * - 60% ancho pantalla
     * - 20% alto pantalla
     * - a la derecha del botón
     * - base alineada con base del botón (crece hacia arriba)
     * - si se sale, empuja el GRUPO moviendo el botón
     * - PEGADO al botón
     */
    private fun positionOverlayNextToButton() {
        val card = panelCard ?: return
        val btn = floatingRoot ?: return

        updateScreenSize()

        val bw = btn.width.coerceAtLeast(dp(56))
        val bh = btn.height.coerceAtLeast(dp(56))

        val overlayW = (screenW * 0.60f).toInt().coerceAtLeast(dp(160))
        val overlayH = (screenH * 0.20f).toInt().coerceAtLeast(dp(64))

        // Forzar tamaño del card
        val lp = FrameLayout.LayoutParams(overlayW, overlayH)
        lp.gravity = Gravity.TOP or Gravity.START
        card.layoutParams = lp

        val margin = 0

        fun desiredX(): Float = btn.x + bw + margin
        fun desiredY(): Float = (btn.y + bh) - overlayH

        var x = desiredX()
        var y = desiredY()

        // Auto-ajuste moviendo el botón (el grupo)
        val overflowRight = (x + overlayW) - screenW
        if (overflowRight > 0) {
            btn.x = (btn.x - overflowRight).coerceAtLeast(0f)
            x = desiredX()
            y = desiredY()
        }

        if (y < 0) {
            btn.y = (btn.y + (-y)).coerceAtLeast(0f)
            x = desiredX()
            y = desiredY()
        }

        // Clamp final del card
        x = x.coerceIn(0f, (screenW - overlayW).toFloat().coerceAtLeast(0f))
        y = y.coerceIn(0f, (screenH - overlayH).toFloat().coerceAtLeast(0f))

        card.x = x
        card.y = y
    }

    private fun refreshChips() {
        val w = modeWView ?: return
        val l = modeLView ?: return
        setChipActive(w, mode == Mode.W)
        setChipActive(l, mode == Mode.L)
    }

    private fun setChipActive(tv: TextView, active: Boolean) {
        tv.alpha = if (active) 1f else 0.90f
        tv.setBackgroundResource(if (active) R.drawable.chip_mode_active else R.drawable.chip_mode_inactive)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

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
}
