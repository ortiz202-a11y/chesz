package com.chesz.analyzer.bubble

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.FrameLayout
import kotlin.math.abs
import kotlin.math.sqrt

class BubbleService : Service() {
    private var wm: WindowManager? = null
    private var root: View? = null
    private var closeZone: View? = null
    private lateinit var lp: WindowManager.LayoutParams
    private lateinit var closeLp: WindowManager.LayoutParams

    override fun onBind(i: Intent?): IBinder? = null

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    override fun onStartCommand(i: Intent?, f: Int, s: Int): Int {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val res = resources
        val pkg = packageName

        if (root == null) {
            root = LayoutInflater.from(this).inflate(res.getIdentifier("overlay_root", "layout", pkg), null)
            val container = root!!.findViewById<FrameLayout>(res.getIdentifier("bubbleContainer", "id", pkg))
            val panel = root!!.findViewById<View>(res.getIdentifier("panelBubble", "id", pkg))
            val btnClose = root!!.findViewById<View>(res.getIdentifier("btnClosePanel", "id", pkg))
            
            closeZone = LayoutInflater.from(this).inflate(res.getIdentifier("overlay_close_zone", "layout", pkg), null)
            val closeCircle = closeZone!!.findViewById<View>(res.getIdentifier("closeZoneCircle", "id", pkg))

            lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 2038 else 2002,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply { 
                gravity = Gravity.TOP or Gravity.START
                x = 500; y = 1000 
            }

            closeLp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 2038 else 2002,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            )

            var dX = 0f; var dY = 0f; var oX = 0; var oY = 0; var mov = false

            container.setOnTouchListener { _, e ->
                val sw = res.displayMetrics.widthPixels
                val sh = res.displayMetrics.heightPixels
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { 
                        dX = e.rawX; dY = e.rawY; oX = lp.x; oY = lp.y; mov = false
                        closeCircle.visibility = View.VISIBLE
                        true 
                    }
                    MotionEvent.ACTION_MOVE -> {
                        lp.x = (oX + (e.rawX - dX).toInt()).coerceIn(0, sw - dp(80))
                        lp.y = (oY + (e.rawY - dY).toInt()).coerceIn(0, sh - dp(80))
                        if (abs(e.rawX - dX) > 15 || abs(e.rawY - dY) > 15) mov = true
                        wm?.updateViewLayout(root, lp)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        closeCircle.visibility = View.GONE
                        val distToClose = sqrt(Math.pow((lp.x + dp(40) - sw/2).toDouble(), 2.0) + Math.pow((lp.y + dp(40) - (sh - dp(90))).toDouble(), 2.0))
                        if (distToClose < dp(100)) { stopSelf() } 
                        else if (!mov) { panel.visibility = if (panel.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
                        true
                    }
                    else -> false
                }
            }
            btnClose.setOnClickListener { panel.visibility = View.GONE }
            wm?.addView(closeZone, closeLp)
            wm?.addView(root, lp)
        }
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        root?.let { wm?.removeViewImmediate(it) }
        closeZone?.let { wm?.removeViewImmediate(it) }
    }
}
