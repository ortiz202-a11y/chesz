package com.chesz.floating

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.*
import android.widget.FrameLayout

class BubbleService : Service() {
    private lateinit var windowManager: WindowManager
    private lateinit var bubbleView: View
    private lateinit var params: WindowManager.LayoutParams

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        bubbleView = LayoutInflater.from(this).inflate(com.chesz.R.layout.layout_bubble, null)

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 100
            width = (80 * resources.displayMetrics.density).toInt()
            height = (80 * resources.displayMetrics.density).toInt()
        }

        bubbleView.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        params.x = initialX + (event.rawX - initialTouchX).toInt()
                        params.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(bubbleView, params)
                        return true
                    }
                }
                return false
            }
        })

        windowManager.addView(bubbleView, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::bubbleView.isInitialized) windowManager.removeView(bubbleView)
    }
}
