package com.chesz.analyzer.bubble

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs

class BubbleService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var rootLayout: FrameLayout
    private lateinit var bubble: View
    private lateinit var panel: LinearLayout

    override fun onBind(i: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else 2002

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )

        rootLayout = FrameLayout(this)

        // EL PANEL (Construido programáticamente para evitar errores de XML)
        panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#EE000000"))
            setPadding(40, 40, 40, 40)
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(600, FrameLayout.LayoutParams.WRAP_CONTENT)
            
            val title = TextView(context).apply { 
                text = "CHESZ ANALYZER"
                setTextColor(Color.GREEN)
                textSize = 18f
            }
            val data = TextView(context).apply { 
                text = "\nEsperando datos del análisis..."
                setTextColor(Color.WHITE)
            }
            val closeBtn = Button(context).apply { 
                text = "CERRAR PANEL"
                setOnClickListener { panel.visibility = View.GONE }
            }
            
            addView(title)
            addView(data)
            addView(closeBtn)
        }

        // EL BOTÓN (Círculo sólido para no depender de imágenes)
        bubble = View(this).apply {
            val size = (70 * resources.displayMetrics.density).toInt()
            layoutParams = FrameLayout.LayoutParams(size, size).apply {
                leftMargin = 100
                topMargin = 500
            }
            // Fondo verde circular (programático)
            val shape = android.graphics.drawable.GradientDrawable()
            shape.shape = android.graphics.drawable.GradientDrawable.OVAL
            shape.setColor(Color.GREEN)
            background = shape
        }

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
                        val lp = bubble.layoutParams as FrameLayout.LayoutParams
                        lp.leftMargin = initialX + (event.rawX - initialTouchX).toInt()
                        lp.topMargin = initialY + (event.rawY - initialTouchY).toInt()
                        bubble.layoutParams = lp
                        if (panel.visibility == View.VISIBLE) panel.visibility = View.GONE
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val duration = System.currentTimeMillis() - startTime
                        val dist = abs(event.rawX - initialTouchX) + abs(event.rawY - initialTouchY)
                        
                        if (duration < 200 && dist < 25) {
                            val lp = bubble.layoutParams as FrameLayout.LayoutParams
                            panel.x = lp.leftMargin.toFloat()
                            panel.y = (lp.topMargin + v.height + 10).toFloat()
                            panel.visibility = View.VISIBLE
                        }
                        return true
                    }
                }
                return false
            }
        })

        rootLayout.addView(panel)
        rootLayout.addView(bubble)
        wm.addView(rootLayout, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::rootLayout.isInitialized) try { wm.removeView(rootLayout) } catch(e: Exception) {}
    }
}
