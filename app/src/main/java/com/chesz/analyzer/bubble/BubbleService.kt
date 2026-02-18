package com.chesz.analyzer.bubble

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.widget.ImageView
import jp.co.recruit_lifestyle.android.floatingview.FloatingViewListener
import jp.co.recruit_lifestyle.android.floatingview.FloatingViewManager

class BubbleService : Service(), FloatingViewListener {
    private var mFloatingViewManager: FloatingViewManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val iconView = ImageView(this).apply {
            setImageResource(android.R.drawable.presence_online)
        }
        mFloatingViewManager = FloatingViewManager(this, this).apply {
            setFixedNormalViewHolder(iconView)
            val options = FloatingViewManager.Options().apply {
                overMargin = 16
            }
            addViewToWindow(iconView, options)
        }
    }

    override fun onFinishFloatingView() { stopSelf() }
    override fun onTouchFinished(isFinishing: Boolean, x: Int, y: Int) {}
    override fun onDestroy() {
        mFloatingViewManager?.removeAllViewToWindow()
        super.onDestroy()
    }
}
