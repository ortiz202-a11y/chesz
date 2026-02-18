package com.chesz.analyzer.bubble

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.ImageView
import com.chesz.analyzer.R
import jp.co.recruit_lifestyle.android.floatingview.FloatingViewListener
import jp.co.recruit_lifestyle.android.floatingview.FloatingViewManager

class BubbleService : Service(), FloatingViewListener {

    private var mFloatingViewManager: FloatingViewManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val inflater = LayoutInflater.from(this)
        
        // Icono de la Burbuja
        val iconView = ImageView(this).apply {
            setImageResource(android.R.drawable.presence_online)
        }

        // Panel de Control
        val panelView = inflater.inflate(R.layout.panel_layout, null)
        panelView.findViewById<Button>(R.id.btn_close).setOnClickListener {
            stopSelf()
        }

        mFloatingViewManager = FloatingViewManager(this, this).apply {
            setFixedNormalViewHolder(iconView)
        }
        
        mFloatingViewManager?.addViewToWindow(iconView, FloatingViewManager.Options().apply {
            overMargin = 16
        })
    }

    override fun onFinishFloatingView() { stopSelf() }
    override fun onTouchFinished(isFinishing: Boolean, x: Int, y: Int) {}

    override fun onDestroy() {
        mFloatingViewManager?.removeAllViewToWindow()
        super.onDestroy()
    }
}
