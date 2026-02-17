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
    private var closeView: View? = null
    private lateinit var lp: WindowManager.LayoutParams
    private lateinit var closeLp: WindowManager.LayoutParams

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        if (root == null) setupViews()
        return START_STICKY
    }

    private fun setupViews() {
        val res = resources
        val pkg = packageName
        
        // Setup Bubble
        root = LayoutInflater.from(this).inflate(res.getIdentifier("overlay_root", "layout", pkg), null)
        val container = root!!.findViewById<FrameLayout>(res.getIdentifier("bubbleContainer", "id", pkg))
        val panel = root!!.findViewById<View>(res.getIdentifier("panelBubble", "id", pkg))
        val btnClose = root!!.findViewById<View>(res.getIdentifier("btnClosePanel", "id", pkg))

        lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 2038 else 2002,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 500; y = 1000 }

        // Setup Close Target (Círculo Rojo)
        closeView = LayoutInflater.from(this).inflate(res.getIdentifier("close_target", "layout", pkg), null)
        val closeCircle = closeView!!.findViewById<View>(res.getIdentifier("closeCircle", "id", pkg))
        closeLp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 2038 else 2002,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; y = 100 }
        closeView!!.visibility = View.GONE
        wm?.addView(closeView, closeLp)

        var dX = 0f; var dY = 0f; var oX = 0; var oY = 0; var mov = false

        container.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    dX = e.rawX; dY = e.rawY; oX = lp.x; oY = lp.y; mov = false
                    closeView!!.visibility = View.VISIBLE
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = oX + (e.rawX - dX).toInt()
                    lp.y = oY + (e.rawY - dY).toInt()
                    
                    // Detectar si está sobre el círculo de cierre
                    val dist = calculateDistToClose(lp.x, lp.y)
                    val params = closeCircle.layoutParams
                    if (dist < 250) {
                        params.width = dpToPx(130); params.height = dpToPx(130)
                    } else {
                        params.width = dpToPx(110); params.height = dpToPx(110)
                    }
                    closeCircle.layoutParams = params
                    
                    if (abs(e.rawX - dX) > 15 || abs(e.rawY - dY) > 15) mov = true
                    wm?.updateViewLayout(root, lp)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    closeView!!.visibility = View.GONE
                    if (calculateDistToClose(lp.x, lp.y) < 250) {
                        stopSelf() // CIERRA TODO EL SERVICIO
                    } else if (!mov) {
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

    private fun calculateDistToClose(x: Int, y: Int): Double {
        val screenWidth = resources.displayMetrics.widthPixels
        val screenHeight = resources.displayMetrics.heightPixels
        val targetX = screenWidth / 2
        val targetY = screenHeight - 200
        return sqrt(Math.pow((x - targetX).toDouble(), 2.0) + Math.pow((y - targetY).toDouble(), 2.0))
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        super.onDestroy()
        root?.let { wm?.removeViewImmediate(it) }
        closeView?.let { wm?.removeViewImmediate(it) }
    }
}
