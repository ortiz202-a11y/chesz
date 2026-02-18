package com.chesz.analyzer.bubble

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.ImageView
import kotlin.math.abs
import kotlin.math.sqrt

class BubbleService : Service() {
    private var wm: WindowManager? = null
    private var bubbleView: View? = null
    private var panelView: View? = null
    private var closeView: View? = null
    
    private lateinit var bubbleLp: WindowManager.LayoutParams
    private lateinit var panelLp: WindowManager.LayoutParams
    private lateinit var closeLp: WindowManager.LayoutParams

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        if (bubbleView == null) setupEverything()
        return START_STICKY
    }

    private fun setupEverything() {
        val inflater = LayoutInflater.from(this)
        val pkg = packageName
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else 2002

        // 1. CIERRE
        closeView = inflater.inflate(resources.getIdentifier("close_target", "layout", pkg), null)
        closeLp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; y = 100 }
        closeView!!.visibility = View.GONE

        // 2. PANEL (Ajuste de coordenadas)
        panelView = inflater.inflate(resources.getIdentifier("overlay_root", "layout", pkg), null)
        panelView!!.findViewById<View>(resources.getIdentifier("bubbleContainer", "id", pkg)).visibility = View.GONE
        panelLp = WindowManager.LayoutParams(
            dpToPx(220), WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }
        panelView!!.visibility = View.GONE

        // 3. BOTÓN (Con limites de pantalla manuales)
        bubbleView = inflater.inflate(resources.getIdentifier("overlay_root", "layout", pkg), null)
        val bubbleImg = bubbleView!!.findViewById<ImageView>(resources.getIdentifier("bubbleContainer", "id", pkg))
        bubbleView!!.findViewById<View>(resources.getIdentifier("panelBubble", "id", pkg)).visibility = View.GONE
        
        bubbleLp = WindowManager.LayoutParams(
            dpToPx(80), dpToPx(80), layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 200; y = 500 }

        wm?.addView(closeView, closeLp)
        wm?.addView(panelView, panelLp)
        wm?.addView(bubbleView, bubbleLp)

        setupLogic(bubbleImg)
    }

    private fun setupLogic(bubble: ImageView) {
        val dm = resources.displayMetrics
        var dX = 0f; var dY = 0f; var oX = 0; var oY = 0
        var mov = false; var startTime = 0L

        bubble.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    dX = e.rawX; dY = e.rawY; oX = bubbleLp.x; oY = bubbleLp.y
                    startTime = System.currentTimeMillis(); mov = false
                    closeView!!.visibility = View.VISIBLE
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val curX = (oX + (e.rawX - dX)).toInt()
                    val curY = (oY + (e.rawY - dY)).toInt()
                    
                    // FRENO: No dejar que el botón desaparezca de la pantalla
                    bubbleLp.x = curX.coerceIn(-dpToPx(20), dm.widthPixels - dpToPx(60))
                    bubbleLp.y = curY.coerceIn(0, dm.heightPixels - dpToPx(100))
                    
                    if (abs(e.rawX - dX) > 10 || abs(e.rawY - dY) > 10) {
                        mov = true
                        wm?.updateViewLayout(bubbleView, bubbleLp)
                        panelView!!.visibility = View.GONE
                    }
                    
                    val dist = calculateDist(bubbleLp.x, bubbleLp.y)
                    if (dist < 250) closeView!!.scaleX = 1.2f else closeView!!.scaleX = 1.0f
                    true
                }
                MotionEvent.ACTION_UP -> {
                    closeView!!.visibility = View.GONE
                    if (calculateDist(bubbleLp.x, bubbleLp.y) < 250) {
                        stopSelf()
                    } else if (!mov && (System.currentTimeMillis() - startTime) < 250) {
                        // TAP: Posicionar panel inteligentemente
                        if (panelView!!.visibility == View.GONE) {
                            // Si el botón está muy a la derecha, mover panel a la izquierda
                            panelLp.x = if (bubbleLp.x > dm.widthPixels / 2) bubbleLp.x - dpToPx(140) else bubbleLp.x
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

    private fun calculateDist(x: Int, y: Int): Double {
        val dm = resources.displayMetrics
        val tx = (dm.widthPixels / 2) - dpToPx(40); val ty = dm.heightPixels - dpToPx(150)
        return sqrt(Math.pow((x - tx).toDouble(), 2.0) + Math.pow((y - ty).toDouble(), 2.0))
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        super.onDestroy()
        try { wm?.removeView(bubbleView); wm?.removeView(panelView); wm?.removeView(closeView) } catch(e: Exception) {}
    }
}
