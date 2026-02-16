package com.chesz.analyzer.bubble

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.*
import android.widget.FrameLayout
import android.widget.TextView
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
        if (bubbleView == null) { createCloseZone(); createBubble() }
        return START_STICKY
    }

    private fun windowType(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE

    private fun baseFlags(): Int = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or 
                                   WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or 
                                   WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN

    private fun createCloseZone() {
        val size = (96 * resources.displayMetrics.density).toInt()
        closeView = FrameLayout(this).apply {
            visibility = View.GONE
            setBackgroundColor(0xAAFF0000.toInt())
        }
        val lp = WindowManager.LayoutParams(size, size, windowType(), baseFlags(), PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; y = 100
        }
        wm.addView(closeView, lp)
    }

    private fun createBubble() {
        val root = LayoutInflater.from(this).inflate(R.layout.overlay_root, null) as FrameLayout
        val bubbleContainer = root.findViewById<FrameLayout>(R.id.bubbleContainer)
        panelBubble = root.findViewById<View>(R.id.panelBubble)
        
        root.findViewById<View>(R.id.tapToClose)?.setOnClickListener { 
            panelBubble?.visibility = View.GONE
            wm.updateViewLayout(root, bubbleLp)
        }

        bubbleLp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType(),
            baseFlags(),
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100; y = 100
        }

        var dX = 0f; var dY = 0f; var oX = 0; var oY = 0
        var moved = false; var time = 0L

        bubbleContainer.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dX = ev.rawX; dY = ev.rawY; oX = bubbleLp.x; oY = bubbleLp.y
                    moved = false; time = System.currentTimeMillis(); true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (ev.rawX - dX).toInt(); val dy = (ev.rawY - dY).toInt()
                    if (!moved && (abs(dx) > 10 || abs(dy) > 10)) { moved = true; closeView?.visibility = View.VISIBLE }
                    bubbleLp.x = oX + dx; bubbleLp.y = oY + dy
                    
                    val dm = resources.displayMetrics
                    bubbleLp.x = bubbleLp.x.coerceIn(0, dm.widthPixels - (80 * dm.density).toInt())
                    bubbleLp.y = bubbleLp.y.coerceIn(0, dm.heightPixels - (80 * dm.density).toInt())
                    
                    wm.updateViewLayout(root, bubbleLp); true
                }
                MotionEvent.ACTION_UP -> {
                    closeView?.visibility = View.GONE
                    if (!moved && (System.currentTimeMillis() - time) < 250) {
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

    override fun onDestroy() {
        super.onDestroy()
        try { bubbleView?.let { wm.removeViewImmediate(it) } } catch(_: Exception) {}
        try { closeView?.let { wm.removeViewImmediate(it) } } catch(_: Exception) {}
    }
}
