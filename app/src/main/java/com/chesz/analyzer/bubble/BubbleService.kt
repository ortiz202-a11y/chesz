package com.chesz.analyzer.bubble

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.ImageView
import kotlin.math.abs

class BubbleService : Service() {
    private var wm: WindowManager? = null
    
    private var bubbleView: View? = null
    private lateinit var bubbleLp: WindowManager.LayoutParams
    
    private var panelView: View? = null
    private lateinit var panelLp: WindowManager.LayoutParams

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        if (bubbleView == null) setupViews()
        return START_STICKY
    }

    private fun setupViews() {
        val inflater = LayoutInflater.from(this)
        val pkg = packageName
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else 2002

        // --- VENTANA 1: EL BOTÓN (Solo 80dp) ---
        bubbleView = inflater.inflate(resources.getIdentifier("overlay_root", "layout", pkg), null)
        val bubbleImg = bubbleView!!.findViewById<ImageView>(resources.getIdentifier("bubbleContainer", "id", pkg))
        bubbleView!!.findViewById<View>(resources.getIdentifier("panelBubble", "id", pkg)).visibility = View.GONE

        bubbleLp = WindowManager.LayoutParams(
            dpToPx(80), dpToPx(80), layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { 
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 500 
        }

        // --- VENTANA 2: EL PANEL (Independiente) ---
        panelView = inflater.inflate(resources.getIdentifier("overlay_root", "layout", pkg), null)
        panelView!!.findViewById<View>(resources.getIdentifier("bubbleContainer", "id", pkg)).visibility = View.GONE
        val actualPanel = panelView!!.findViewById<View>(resources.getIdentifier("panelBubble", "id", pkg))
        
        panelLp = WindowManager.LayoutParams(
            dpToPx(220), WindowManager.LayoutParams.WRAP_CONTENT, layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { 
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 600
        }
        panelView!!.visibility = View.GONE

        wm?.addView(panelView, panelLp)
        wm?.addView(bubbleView, bubbleLp)

        setupInteractions(bubbleImg)
    }

    private fun setupInteractions(bubble: ImageView) {
        var dX = 0f; var dY = 0f; var oX = 0; var oY = 0
        var mov = false
        var startTime = 0L

        bubble.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    dX = e.rawX; dY = e.rawY; oX = bubbleLp.x; oY = bubbleLp.y
                    mov = false
                    startTime = System.currentTimeMillis()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaX = e.rawX - dX
                    val deltaY = e.rawY - dY
                    if (abs(deltaX) > 15 || abs(deltaY) > 15) {
                        mov = true
                        bubbleLp.x = oX + deltaX.toInt()
                        bubbleLp.y = oY + deltaY.toInt()
                        wm?.updateViewLayout(bubbleView, bubbleLp)
                        if (panelView?.visibility == View.VISIBLE) panelView!!.visibility = View.GONE
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val duration = System.currentTimeMillis() - startTime
                    if (!mov && duration < 200) {
                        // ES UN TAP: Toggle Panel
                        if (panelView!!.visibility == View.GONE) {
                            panelLp.x = bubbleLp.x
                            panelLp.y = bubbleLp.y + dpToPx(85)
                            wm?.updateViewLayout(panelView, panelLp)
                            panelView!!.visibility = View.VISIBLE
                        } else {
                            panelView!!.visibility = View.GONE
                        }
                    }
                    true
                }
                else -> false
            }
        }
        
        panelView!!.findViewById<View>(resources.getIdentifier("btnClosePanel", "id", packageName)).setOnClickListener {
            panelView!!.visibility = View.GONE
        }
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        super.onDestroy()
        try { wm?.removeView(bubbleView); wm?.removeView(panelView) } catch(e: Exception) {}
    }
}
