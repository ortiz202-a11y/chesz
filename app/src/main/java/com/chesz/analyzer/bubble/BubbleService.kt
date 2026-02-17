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
    private lateinit var bubbleLp: WindowManager.LayoutParams
    private var panelView: View? = null
    private lateinit var panelLp: WindowManager.LayoutParams
    private var closeView: View? = null
    private lateinit var closeLp: WindowManager.LayoutParams

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        if (bubbleView == null) setupViews()
        return START_STICKY
    }

    private fun setupViews() {
        val inflater = LayoutInflater.from(this)
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else 2002
        val pkg = packageName

        // Capa 1: El Botón (80dp exactos, sin marcos)
        bubbleView = inflater.inflate(resources.getIdentifier("overlay_root", "layout", pkg), null)
        val bubbleImg = bubbleView!!.findViewById<ImageView>(resources.getIdentifier("bubbleContainer", "id", pkg))
        bubbleView!!.findViewById<View>(resources.getIdentifier("panelBubble", "id", pkg)).visibility = View.GONE

        bubbleLp = WindowManager.LayoutParams(
            dpToPx(80), dpToPx(80), layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 500; y = 1000 }

        // Capa 2: El Panel (Capa separada = 0 brincos)
        panelView = inflater.inflate(resources.getIdentifier("overlay_root", "layout", pkg), null)
        val actualPanel = panelView!!.findViewById<View>(resources.getIdentifier("panelBubble", "id", pkg))
        panelView!!.findViewById<View>(resources.getIdentifier("bubbleContainer", "id", pkg)).visibility = View.GONE
        
        panelLp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 430; y = 1085 }
        panelView!!.visibility = View.GONE

        // Capa 3: El Cierre (La X blanca)
        closeView = inflater.inflate(resources.getIdentifier("close_target", "layout", pkg), null)
        closeLp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag, WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; y = 100 }
        closeView!!.visibility = View.GONE

        wm?.addView(closeView, closeLp)
        wm?.addView(panelView, panelLp)
        wm?.addView(bubbleView, bubbleLp)

        setupInteractions(bubbleImg, actualPanel)
    }

    private fun setupInteractions(bubble: ImageView, panel: View) {
        var dX = 0f; var dY = 0f; var oX = 0; var oY = 0; var mov = false
        val pkg = packageName

        bubble.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    dX = e.rawX; dY = e.rawY; oX = bubbleLp.x; oY = bubbleLp.y; mov = false
                    closeView!!.visibility = View.VISIBLE
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    bubbleLp.x = oX + (e.rawX - dX).toInt()
                    bubbleLp.y = oY + (e.rawY - dY).toInt()
                    
                    if (panelView!!.visibility == View.VISIBLE) {
                        panelLp.x = bubbleLp.x - dpToPx(70)
                        panelLp.y = bubbleLp.y + dpToPx(85)
                        wm?.updateViewLayout(panelView, panelLp)
                    }

                    val dist = calculateDistToClose(bubbleLp.x, bubbleLp.y)
                    val circle = closeView!!.findViewById<View>(resources.getIdentifier("closeCircle", "id", pkg))
                    val p = circle.layoutParams
                    if (dist < 250) { p.width = dpToPx(130); p.height = dpToPx(130) } 
                    else { p.width = dpToPx(110); p.height = dpToPx(110) }
                    circle.layoutParams = p

                    if (abs(e.rawX - dX) > 15 || abs(e.rawY - dY) > 15) mov = true
                    wm?.updateViewLayout(bubbleView, bubbleLp)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    closeView!!.visibility = View.GONE
                    if (calculateDistToClose(bubbleLp.x, bubbleLp.y) < 250) {
                        stopSelf()
                    } else if (!mov) {
                        if (panelView!!.visibility == View.GONE) {
                            panelLp.x = bubbleLp.x - dpToPx(70)
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
        panelView!!.findViewById<View>(resources.getIdentifier("btnClosePanel", "id", pkg)).setOnClickListener {
            panelView!!.visibility = View.GONE
        }
    }

    private fun calculateDistToClose(x: Int, y: Int): Double {
        val dm = resources.displayMetrics
        val targetX = (dm.widthPixels / 2) - dpToPx(40)
        val targetY = dm.heightPixels - dpToPx(150)
        return sqrt(Math.pow((x - targetX).toDouble(), 2.0) + Math.pow((y - targetY).toDouble(), 2.0))
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        super.onDestroy()
        bubbleView?.let { try { wm?.removeViewImmediate(it) } catch(e: Exception) {} }
        panelView?.let { try { wm?.removeViewImmediate(it) } catch(e: Exception) {} }
        closeView?.let { try { wm?.removeViewImmediate(it) } catch(e: Exception) {} }
    }
}
