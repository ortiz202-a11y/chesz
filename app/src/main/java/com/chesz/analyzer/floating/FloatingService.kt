package com.chesz.analyzer.floating

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
import android.widget.Button
import androidx.core.app.NotificationCompat
import com.chesz.analyzer.R
import kotlin.math.abs

/**
 * FloatingService — Foreground service that creates two overlay views:
 *   1. A draggable floating button.
 *   2. An upward-opening panel that toggles on button tap.
 *
 * Architecture notes / TODO for future PRs:
 *  - Animations: add ValueAnimator for smooth panel open/close.
 *  - Accessibility: add content descriptions, TalkBack support.
 *  - Measurement: use ViewTreeObserver.addOnGlobalLayoutListener for panel sizing
 *    instead of measuring manually with MeasureSpec.
 *  - Snap animation: replace immediate snap with a spring animation (e.g. SpringAnimation).
 *  - Persistence: save last button position to SharedPreferences so it survives restarts.
 */
class FloatingService : Service() {

    // ── Window manager ──────────────────────────────────────────────────────────
    private lateinit var wm: WindowManager

    // ── Button overlay ──────────────────────────────────────────────────────────
    private lateinit var buttonView: View
    private lateinit var buttonParams: WindowManager.LayoutParams

    // ── Panel overlay ───────────────────────────────────────────────────────────
    private lateinit var panelView: View
    private lateinit var panelParams: WindowManager.LayoutParams
    private var isPanelVisible = false

    // ── Drag state ──────────────────────────────────────────────────────────────
    /** Raw X of the finger at the start of the touch gesture. */
    private var initialTouchX = 0f
    /** Raw Y of the finger at the start of the touch gesture. */
    private var initialTouchY = 0f
    /** WindowManager X of the button view when the touch gesture started. */
    private var initialX = 0
    /** WindowManager Y of the button view when the touch gesture started. */
    private var initialY = 0
    /** Distinguish a tap from a drag so we don't toggle the panel on every touch. */
    private var isDragging = false

    // ── Screen bounds (updated on each display change) ──────────────────────────
    private var screenWidth = 0
    private var screenHeight = 0

    // ── Foreground notification ──────────────────────────────────────────────────
    private val CHANNEL_ID = "chesz_overlay_channel"
    private val NOTIFICATION_ID = 1

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        refreshScreenSize()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        addButtonOverlay()
        addPanelOverlay()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        START_STICKY

    override fun onDestroy() {
        super.onDestroy()
        safeRemoveView(buttonView)
        safeRemoveView(panelView)
    }

    // ── Overlay creation ────────────────────────────────────────────────────────

    private fun addButtonOverlay() {
        buttonView = LayoutInflater.from(this).inflate(R.layout.floating_button, null)

        buttonParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            // Default position: ~30 % down the screen
            y = (screenHeight * 0.3).toInt()
        }

        buttonView.setOnTouchListener(::onButtonTouch)
        wm.addView(buttonView, buttonParams)
    }

    private fun addPanelOverlay() {
        panelView = LayoutInflater.from(this).inflate(R.layout.floating_panel, null)

        panelParams = WindowManager.LayoutParams(
            (screenWidth * 0.8).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }

        // "Cerrar" button hides the panel
        panelView.findViewById<Button>(R.id.btn_close_panel).setOnClickListener {
            hidePanel()
        }

        panelView.visibility = View.GONE
        wm.addView(panelView, panelParams)
    }

    // ── Touch / drag handling ────────────────────────────────────────────────────

    private fun onButtonTouch(view: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // Record starting positions to calculate relative movement — prevents jumps.
                initialTouchX = event.rawX
                initialTouchY = event.rawY
                initialX = buttonParams.x
                initialY = buttonParams.y
                isDragging = false
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - initialTouchX
                val dy = event.rawY - initialTouchY

                // Treat movements >8px as a drag to avoid accidental moves on tap.
                if (!isDragging && (abs(dx) > 8 || abs(dy) > 8)) {
                    isDragging = true
                }

                if (isDragging) {
                    val newX = (initialX + dx.toInt()).coerceIn(0, screenWidth - view.width)
                    val newY = (initialY + dy.toInt()).coerceIn(0, screenHeight - view.height)
                    buttonParams.x = newX
                    buttonParams.y = newY
                    // Update position without removing/re-adding the view (avoids flicker).
                    safeUpdateLayout(buttonView, buttonParams)

                    // Keep panel attached to button while dragging.
                    if (isPanelVisible) {
                        positionPanel()
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    snapToEdge()
                } else {
                    // It was a tap — toggle the panel.
                    togglePanel()
                }
                isDragging = false
                return true
            }
        }
        return false
    }

    // ── Panel logic ─────────────────────────────────────────────────────────────

    private fun togglePanel() {
        if (isPanelVisible) {
            hidePanel()
        } else {
            showPanel()
        }
    }

    private fun showPanel() {
        panelView.visibility = View.VISIBLE
        isPanelVisible = true
        positionPanel()
        safeUpdateLayout(panelView, panelParams)
    }

    private fun hidePanel() {
        panelView.visibility = View.GONE
        isPanelVisible = false
    }

    /**
     * Positions the panel so that:
     *  - Its bottom sits at (buttonCenterY + overlap), where overlap = 0.4 * buttonHeight.
     *    This gives a ~40 % overlap between the button bottom and the panel top.
     *  - The panel grows upward from that point.
     *  - The panel is horizontally centred on the button, clamped to screen bounds.
     *
     * TODO: use ViewTreeObserver for more accurate measurement after layout pass.
     */
    private fun positionPanel() {
        // Measure panel with wrap_content so we know its height.
        panelView.measure(
            View.MeasureSpec.makeMeasureSpec(panelParams.width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val panelHeight = panelView.measuredHeight
        val panelWidth = panelParams.width

        val btnCenterY = buttonParams.y + buttonView.height / 2
        val overlap = (buttonView.height * 0.4).toInt()

        // Panel bottom = buttonCenterY + overlap; panel top = bottom - panelHeight
        val panelBottom = btnCenterY + overlap
        var panelTop = panelBottom - panelHeight

        // Clamp vertically — don't go above the top of the screen.
        panelTop = panelTop.coerceAtLeast(0)
        // Also don't push panel below the screen bottom.
        if (panelTop + panelHeight > screenHeight) {
            panelTop = screenHeight - panelHeight
        }

        // Horizontally: centre on the button, clamped to screen.
        var panelX = buttonParams.x + buttonView.width / 2 - panelWidth / 2
        panelX = panelX.coerceIn(0, (screenWidth - panelWidth).coerceAtLeast(0))

        panelParams.x = panelX
        panelParams.y = panelTop
        safeUpdateLayout(panelView, panelParams)
    }

    // ── Snap to nearest edge ─────────────────────────────────────────────────────

    /**
     * Snaps the button to the left or right screen edge immediately.
     * TODO: replace with a SpringAnimation for a polished feel.
     */
    private fun snapToEdge() {
        val btnCenterX = buttonParams.x + buttonView.width / 2
        buttonParams.x = if (btnCenterX < screenWidth / 2) {
            0
        } else {
            screenWidth - buttonView.width
        }
        safeUpdateLayout(buttonView, buttonParams)

        if (isPanelVisible) {
            positionPanel()
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────

    /** Returns the correct window type for the current API level. */
    private fun overlayType(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    } else {
        @Suppress("DEPRECATION")
        WindowManager.LayoutParams.TYPE_PHONE
    }

    private fun refreshScreenSize() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = wm.currentWindowMetrics
            screenWidth = metrics.bounds.width()
            screenHeight = metrics.bounds.height()
        } else {
            val size = Point()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getSize(size)
            screenWidth = size.x
            screenHeight = size.y
        }
    }

    /**
     * Defensive wrapper around [WindowManager.updateViewLayout].
     * Catches exceptions that can occur if the view was already removed (e.g., during
     * rapid configuration changes or edge-case lifecycle scenarios).
     */
    private fun safeUpdateLayout(view: View, params: WindowManager.LayoutParams) {
        try {
            wm.updateViewLayout(view, params)
        } catch (e: IllegalArgumentException) {
            // View is not attached — ignore; this can happen on destroy.
        }
    }

    private fun safeRemoveView(view: View) {
        try {
            wm.removeView(view)
        } catch (e: Exception) {
            // Already removed or never added.
        }
    }

    // ── Foreground notification (required on Android 8+) ─────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Chesz Overlay",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Chesz floating overlay service"
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Chesz")
            .setContentText("Overlay activo")
            .setSmallIcon(R.drawable.ic_floating_button)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
}
