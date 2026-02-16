package com.chesz.analyzer.bubble

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.*
import android.widget.FrameLayout
import android.widget.TextView
import com.chesz.analyzer.R
import kotlin.math.abs
import kotlin.math.hypot

class BubbleService : Service() {
    private lateinit var wm: WindowManager
    private var bubbleView: View? = null
    private var closeView: View? = null
    private var panelBubble: View? = null
    private lateinit var bubbleLp: WindowManager.LayoutParams
    private lateinit var closeLp: WindowManager.LayoutParams
    private var downRawX = 0f
    private var downRawY = 0f
    private var downX = 0
    private var downY = 0
    private var moved = false
    private var downTime = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) { shutdown(); return START_STICKY }
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (bubbleView == null) { createCloseZone(); createBubble() }
        return START_STICKY
    }

    override fun onDestroy() { super.onDestroy(); removeViews() }

    private fun shutdown() { removeViews(); stopSelf() }

    private fun removeViews() {
        if (!::wm.isInitialized) return
        try { bubbleView?.let { wm.removeViewImmediate(it) } } catch (_: Throwable) {}
        try { closeView?.let { wm.removeViewImmediate(it) } } catch (_: Throwable) {}
        panelBubble = null; bubbleView = null; closeView = null
    }

    private fun windowType(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE

    private fun baseFlags(): Int = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

    private fun createCloseZone() {
        val size = dp(96)
        val root = FrameLayout(this).apply {
            visibility = View.GONE
            addView(FrameLayout(this@BubbleService).apply {
                setBackgroundColor(0xFFE53935.toInt())
                clipToOutline = true
                outlineProvider = object : ViewOutlineProvider() {
                    override fun getOutline(view: View, outline: Outline) { outline.setOval(0, 0, view.width, view.height) }
                }
                addView(TextView(this@BubbleService).apply {
                    text = "X"; textSize = 28f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
                })
            }, FrameLayout.LayoutParams(size, size).apply { gravity = Gravity.CENTER })
        }
        closeLp = WindowManager.LayoutParams(size, size, windowType(), baseFlags(), PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; y = dp(28)
        }
        closeView = root
        wm.addView(root, closeLp)
    }

    private fun showClose(show: Boolean) { closeView?.visibility = if (show) View.VISIBLE else View.GONE }

    private fun isInsideCloseCircle(rawX: Float, rawY: Float): Boolean {
        val close = closeView ?: return false
        if (close.visibility != View.VISIBLE) return false
        val loc = IntArray(2); close.getLocationOnScreen(loc)
        val cx = loc[0] + close.width / 2f
        val cy = loc[1] + close.height / 2f
        return hypot(rawX - cx, rawY - cy) <= (close.width / 2f)
    }

    private fun isPanelOpen(): Boolean = (panelBubble?.visibility == View.VISIBLE)

    private fun openPanel() {
        panelBubble?.visibility = View.VISIBLE
        bubbleView?.let { wm.updateViewLayout(it, bubbleLp) }
    }

    private fun closePanel() {
        panelBubble?.visibility = View.GONE
        bubbleView?.let { wm.updateViewLayout(it, bubbleLp) } // SIN CLAMP = SIN BRINCO
    }

    private fun createBubble() {
        val root = LayoutInflater.from(this).inflate(R.layout.overlay_root, null) as FrameLayout
        val bubbleContainer = root.findViewById<FrameLayout>(R.id.bubbleContainer)
        val panel = root.findViewById<View>(R.id.panelBubble)
        
        panel.setBackgroundColor(0x88000000.toInt())
        panel.visibility = View.GONE

        val dm = resources.displayMetrics
        val pw = (dm.widthPixels * 0.60f).toInt()
        val ph = (dm.heightPixels * 0.30f).toInt()
        panel.layoutParams = FrameLayout.LayoutParams(pw, ph).apply {
            gravity = Gravity.BOTTOM or Gravity.START
            leftMargin = dp(55)
        }

        root.findViewById<View>(R.id.tapToClose).setOnClickListener { closePanel() }

        bubbleLp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType(),
            baseFlags(),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16); y = dp(220)
        }

        bubbleContainer.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = ev.rawX; downRawY = ev.rawY
                    downX = bubbleLp.x; downY = bubbleLp.y
                    moved = false; downTime = System.currentTimeMillis()
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (ev.rawX - downRawX).toInt()
                    val dy = (ev.rawY - downRawY).toInt()
                    if (!moved && (abs(dx) > dp(4) || abs(dy) > dp(4))) { moved = true; showClose(true) }
                    bubbleLp.x = downX + dx
                    bubbleLp.y = downY + dy
                    clampToScreen(bubbleLp, root)
                    wm.updateViewLayout(root, bubbleLp)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved && isInsideCloseCircle(ev.rawX, ev.rawY)) { shutdown() }
                    else if (!moved && (System.currentTimeMillis() - downTime) < 250) {
                        if (isPanelOpen()) closePanel() else openPanel()
                    }
                    showClose(false)
                    true
                }
                else -> false
            }
        }

        panelBubble = panel
        bubbleView = root
        wm.addView(root, bubbleLp)
    }

    private fun clampToScreen(lp: WindowManager.LayoutParams, root: View) {
        val dm = resources.displayMetrics
        val sw = dm.widthPixels
        val sh = dm.heightPixels
        
        // Si panel cerrado, el ancho es solo la burbuja (80dp aprox)
        val vw = if (isPanelOpen()) root.width else dp(80)
        val vh = if (isPanelOpen()) root.height else dp(80)

        lp.x = lp.x.coerceIn(0, sw - dp(80))
        lp.y = lp.y.coerceIn(0, sh - dp(80)) // Límite 0 real arriba
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
