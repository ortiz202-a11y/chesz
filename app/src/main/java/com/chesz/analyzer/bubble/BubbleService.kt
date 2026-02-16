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
import com.chesz.analyzer.R
import kotlin.math.abs

class BubbleService : Service() {
    private lateinit var wm: WindowManager
    private var bubbleView: View? = null
    private var closeView: View? = null
    private var panelBubble: View? = null
    private lateinit var bubbleLp: WindowManager.LayoutParams

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return START_STICKY }
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (bubbleView == null) {
            createCloseZone()
            createBubble()
        }
        return START_STICKY
    }

    private fun windowType(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE

    private fun baseFlags(): Int = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
                                   WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or 
                                   WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

    private fun createCloseZone() {
        val size = dp(96)
        closeView = FrameLayout(this).apply {
            visibility = View.GONE
            val circle = FrameLayout(context).apply { setBackgroundColor(0xAAFF0000.toInt()) }
            addView(circle, FrameLayout.LayoutParams(size, size, Gravity.CENTER))
        }
        val closeLp = WindowManager.LayoutParams(size, size, windowType(), baseFlags(), PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = dp(28)
        }
        wm.addView(closeView, closeLp)
    }

    private fun createBubble() {
        val root = LayoutInflater.from(this).inflate(R.layout.overlay_root, null) as FrameLayout
        val bubbleContainer = root.findViewById<FrameLayout>(R.id.bubbleContainer)
        panelBubble = root.findViewById<View>(R.id.panelBubble)
        
        panelBubble?.visibility = View.GONE

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

        var downRawX = 0f; var downRawY = 0f
        var downX = 0; var downY = 0
        var moved = false; var downTime = 0L

        bubbleContainer.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downRawX = ev.rawX; downRawY = ev.rawY
                    downX = bubbleLp.x; downY = bubbleLp.y
                    moved = false; downTime = System.currentTimeMillis()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (ev.rawX - downRawX).toInt()
                    val dy = (ev.rawY - downRawY).toInt()
                    if (!moved && (abs(dx) > dp(5) || abs(dy) > dp(5))) {
                        moved = true
                        closeView?.visibility = View.VISIBLE
                    }
                    bubbleLp.x = downX + dx
                    bubbleLp.y = downY + dy
                    
                    val dm = resources.displayMetrics
                    bubbleLp.x = bubbleLp.x.coerceIn(0, dm.widthPixels - dp(80))
                    bubbleLp.y = bubbleLp.y.coerceIn(0, dm.heightPixels - dp(80))
                    
                    wm.updateViewLayout(root, bubbleLp)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    closeView?.visibility = View.GONE
                    if (!moved && (System.currentTimeMillis() - downTime) < 250) {
                        panelBubble?.visibility = if (panelBubble?.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                        wm.updateViewLayout(root, bubbleLp)
                    }
                    true
                }
                else -> false
            }
        }
        bubbleView = root
        wm.addView(root, bubbleLp)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        super.onDestroy()
        try { bubbleView?.let { wm.removeViewImmediate(it) } } catch(e: Exception) {}
        try { closeView?.let { wm.removeViewImmediate(it) } } catch(e: Exception) {}
    }
}
