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
import com.chesz.analyzer.R
import kotlin.math.abs

class BubbleService : Service() {
    private var wm: WindowManager? = null
    private var bubbleView: View? = null
    private lateinit var bubbleLp: WindowManager.LayoutParams

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return START_STICKY }
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        if (bubbleView == null) createBubble()
        return START_STICKY
    }

    private fun createBubble() {
        val root = LayoutInflater.from(this).inflate(R.layout.overlay_root, null) as FrameLayout
        val bubbleContainer = root.findViewById<FrameLayout>(R.id.bubbleContainer)
        val panel = root.findViewById<View>(R.id.panelBubble)
        
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
            
        bubbleLp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { 
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 100 
        }

        var dX = 0f; var dY = 0f; var oX = 0; var oY = 0
        var moved = false; var time = 0L

        bubbleContainer.setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dX = ev.rawX; dY = ev.rawY; oX = bubbleLp.x; oY = bubbleLp.y
                    moved = false; time = System.currentTimeMillis()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (ev.rawX - dX).toInt()
                    val dy = (ev.rawY - dY).toInt()
                    if (!moved && (abs(dx) > 10 || abs(dy) > 10)) moved = true
                    
                    bubbleLp.x = oX + dx
                    bubbleLp.y = oY + dy
                    
                    val dm = resources.displayMetrics
                    val den = dm.density
                    val limitX = dm.widthPixels - (65 * den).toInt()
                    val limitY = dm.heightPixels - (65 * den).toInt()
                    
                    bubbleLp.x = bubbleLp.x.coerceIn(0, limitX)
                    bubbleLp.y = bubbleLp.y.coerceIn(-100, limitY)
                    
                    wm?.updateViewLayout(root, bubbleLp)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved && (System.currentTimeMillis() - time) < 250) {
                        panel.visibility = if (panel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                        wm?.updateViewLayout(root, bubbleLp)
                    }
                    true
                }
                else -> false
            }
        }
        bubbleView = root
        wm?.addView(root, bubbleLp)
    }

    override fun onDestroy() {
        super.onDestroy()
        bubbleView?.let { try { wm?.removeViewImmediate(it) } catch(e: Exception) {} }
    }
}
