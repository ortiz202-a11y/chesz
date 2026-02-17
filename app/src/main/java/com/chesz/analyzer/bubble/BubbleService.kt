package com.chesz.analyzer.bubble

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.*
import android.widget.FrameLayout
import android.widget.RelativeLayout
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
            val res = resources
            val pkg = packageName
            
            val layoutId = res.getIdentifier("overlay_root", "layout", pkg)
            root = LayoutInflater.from(this).inflate(layoutId, null)
            
            val container = root!!.findViewById<FrameLayout>(res.getIdentifier("bubbleContainer", "id", pkg))
            val panel = root!!.findViewById<View>(res.getIdentifier("panelBubble", "id", pkg))
            val btnClose = root!!.findViewById<View>(res.getIdentifier("btnClosePanel", "id", pkg))
            
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE
            
            lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.TOP or Gravity.START; x = 0; y = 100 }

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
                            // Lógica Inteligente de Apertura
                            val screenWidth = res.displayMetrics.widthPixels
                            val params = panel.layoutParams as RelativeLayout.LayoutParams
                            val bubbleParams = container.layoutParams as RelativeLayout.LayoutParams

                            if (lp.x > screenWidth / 2) {
                                // Estamos a la derecha: Panel a la IZQUIERDA del botón
                                params.removeRule(RelativeLayout.END_OF)
                                bubbleParams.addRule(RelativeLayout.END_OF, panel.id)
                            } else {
                                // Estamos a la izquierda: Panel a la DERECHA del botón
                                bubbleParams.removeRule(RelativeLayout.END_OF)
                                params.addRule(RelativeLayout.END_OF, container.id)
                            }
                            
                            panel.layoutParams = params
                            container.layoutParams = bubbleParams
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
        root?.let { try { wm?.removeViewImmediate(it) } catch(e: Exception) {} }
    }
}
