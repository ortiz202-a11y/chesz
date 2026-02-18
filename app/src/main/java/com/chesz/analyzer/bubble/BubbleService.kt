package com.chesz.analyzer.bubble

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.*
import android.widget.Button
import com.chesz.analyzer.R

class BubbleService : Service() {
    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var panelView: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        setupBubble()
    }

    private fun setupBubble() {
        bubbleView = View(this).apply {
            setBackgroundResource(android.R.drawable.presence_online)
        }

        val params = WindowManager.LayoutParams(
            150, 150,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 100
        }

        bubbleView?.setOnTouchListener(object : View.OnTouchListener {
            private var lastX = 0; private var lastY = 0
            private var lastTouchX = 0f; private var lastTouchY = 0f
            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        lastX = params.x; lastY = params.y
                        lastTouchX = event.rawX; lastTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = lastX + (event.rawX - lastTouchX).toInt()
                        params.y = lastY + (event.rawY - lastTouchY).toInt()
                        windowManager.updateViewLayout(bubbleView, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (Math.abs(event.rawX - lastTouchX) < 10) showPanel()
                        return true
                    }
                }
                return false
            }
        })
        windowManager.addView(bubbleView, params)
    }

    private fun showPanel() {
        if (panelView != null) return
        val inflater = LayoutInflater.from(this)
        panelView = inflater.inflate(R.layout.panel_layout, null)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }

        panelView?.findViewById<Button>(R.id.btn_close)?.setOnClickListener {
            stopSelf()
        }
        windowManager.addView(panelView, params)
        bubbleView?.visibility = View.GONE
    }

    override fun onDestroy() {
        super.onDestroy()
        bubbleView?.let { if (it.isAttachedToWindow) windowManager.removeView(it) }
        panelView?.let { if (it.isAttachedToWindow) windowManager.removeView(it) }
    }
}
