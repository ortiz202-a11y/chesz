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
    private var root: View? = null
    private lateinit var lp: WindowManager.LayoutParams

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int {
        if (!Settings.canDrawOverlays(this)) return START_NOT_STICKY
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        if (root == null) {
            root = LayoutInflater.from(this).inflate(R.layout.overlay_root, null)
            val container = root!!.findViewById<FrameLayout>(R.id.bubbleContainer)
            val panel = root!!.findViewById<View>(R.id.panelBubble)
            
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
            
            lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.TOP or Gravity.START; x = 0; y = 100 }

            var dX = 0f; var dY = 0f; var oX = 0; var oY = 0
            var mov = false

            container.setOnTouchListener { _, e ->
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { dX = e.rawX; dY = e.rawY; oX = lp.x; oY = lp.y; mov = false; true }
                    MotionEvent.ACTION_MOVE -> {
                        lp.x = oX + (e.rawX - dX).toInt()
                        lp.y = oY + (e.rawY - dY).toInt()
                        if (abs(e.rawX - dX) > 10 || abs(e.rawY - dY) > 10) mov = true
                        wm?.updateViewLayout(root, lp); true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!mov) panel.visibility = if (panel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                        true
                    }
                    else -> false
                }
            }
            wm?.addView(root, lp)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        root?.let { wm?.removeViewImmediate(it) }
    }
}
