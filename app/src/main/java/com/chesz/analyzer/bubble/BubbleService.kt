package com.chesz.analyzer.bubble

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.FrameLayout
import kotlin.math.abs

class BubbleService : Service() {
    private var wm: WindowManager? = null
    private var root: View? = null
    private lateinit var lp: WindowManager.LayoutParams

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        if (root == null) {
            val res = resources
            val pkg = packageName
            val layoutId = res.getIdentifier("overlay_root", "layout", pkg)
            root = LayoutInflater.from(this).inflate(layoutId, null)
            
            val container = root!!.findViewById<FrameLayout>(res.getIdentifier("bubbleContainer", "id", pkg))
            val panel = root!!.findViewById<View>(res.getIdentifier("panelBubble", "id", pkg))
            val btnClose = root!!.findViewById<View>(res.getIdentifier("btnClosePanel", "id", pkg))
            
            lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 2038 else 2002,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.TOP or Gravity.START; x = 500; y = 1000 }

            var dX = 0f; var dY = 0f; var oX = 0; var oY = 0; var mov = false

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
                        if (!mov) {
                            // Solo alternamos visibilidad, sin mover reglas de layout
                            panel.visibility = if (panel.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                        }
                        true
                    }
                    else -> false
                }
            }

            btnClose.setOnClickListener { panel.visibility = View.GONE }
            wm?.addView(root, lp)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        root?.let { wm?.removeViewImmediate(it) }
    }
}
