package com.chesz.analyzer.bubble

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageView
import kotlin.math.abs

class BubbleService : Service() {
    private lateinit var wm: WindowManager
    private var rootLayout: View? = null
    private lateinit var bubble: ImageView
    private lateinit var panel: View

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        setupMasterOverlay()
    }

    private fun setupMasterOverlay() {
        val inflater = LayoutInflater.from(this)
        val pkg = packageName
        
        // Ventana maestra que cubre TODA la pantalla
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else 2002

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        rootLayout = inflater.inflate(resources.getIdentifier("overlay_master", "layout", pkg), null)
        bubble = rootLayout!!.findViewById(resources.getIdentifier("master_bubble", "id", pkg))
        panel = rootLayout!!.findViewById(resources.getIdentifier("master_panel", "id", pkg))

        // Posición inicial del botón
        val bLp = bubble.layoutParams as FrameLayout.LayoutParams
        bLp.leftMargin = 100
        bLp.topMargin = 500
        bubble.layoutParams = bLp

        bubble.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0; private var initialY = 0
            private var initialTouchX = 0f; private var initialTouchY = 0f
            private var startTime = 0L

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        startTime = System.currentTimeMillis()
                        initialX = (bubble.layoutParams as FrameLayout.LayoutParams).leftMargin
                        initialY = (bubble.layoutParams as FrameLayout.LayoutParams).topMargin
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        
                        val lp = bubble.layoutParams as FrameLayout.LayoutParams
                        lp.leftMargin = initialX + dx
                        lp.topMargin = initialY + dy
                        bubble.layoutParams = lp
                        
                        if (panel.visibility == View.VISIBLE) panel.visibility = View.GONE
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val duration = System.currentTimeMillis() - startTime
                        val moveDist = abs(event.rawX - initialTouchX) + abs(event.rawY - initialTouchY)
                        
                        if (duration < 200 && moveDist < 20) {
                            // ES UN CLICK
                            showPanel()
                        }
                        return true
                    }
                }
                return false
            }
        })

        rootLayout!!.findViewById<View>(resources.getIdentifier("btn_close_panel", "id", pkg)).setOnClickListener {
            panel.visibility = View.GONE
        }

        wm.addView(rootLayout, params)
    }

    private fun showPanel() {
        val bLp = bubble.layoutParams as FrameLayout.LayoutParams
        val pLp = panel.layoutParams as FrameLayout.LayoutParams
        
        // El panel aparece pegado al botón
        pLp.leftMargin = bLp.leftMargin
        pLp.topMargin = bLp.topMargin + bubble.height
        
        panel.layoutParams = pLp
        panel.visibility = View.VISIBLE
    }

    override fun onDestroy() {
        super.onDestroy()
        rootLayout?.let { wm.removeView(it) }
    }
}
