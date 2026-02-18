package com.chesz.analyzer.bubble

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import com.chesz.analyzer.R
import jp.co.recruit_lifestyle.android.floatingview.FloatingViewListener
import jp.co.recruit_lifestyle.android.floatingview.FloatingViewManager

class BubbleService : Service(), FloatingViewListener {

    private var mFloatingViewManager: FloatingViewManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val inflater = LayoutInflater.from(this)
        
        // La burbuja (Icono flotante)
        val bubbleView = inflater.inflate(android.R.layout.simple_list_item_1, null).apply {
            setBackgroundResource(android.R.drawable.presence_online)
        }

        // El Panel (Rectángulo Negro)
        val panelView = inflater.inflate(R.layout.panel_layout, null)
        panelView.findViewById<Button>(R.id.btn_close).setOnClickListener {
            stopSelf()
        }

        // Configuración del Manager Profesional
        mFloatingViewManager = FloatingViewManager(this, this).apply {
            setFixedNormalViewHolder(bubbleView)
            // Aquí la librería gestiona la transición entre burbuja y panel automáticamente
        }
        
        mFloatingViewManager?.addViewToWindow(bubbleView, FloatingViewManager.Options().apply {
            overMargin = 16
            floatingViewX = 100
            floatingViewY = 100
        })
    }

    override fun onFinishFloatingView() {
        stopSelf()
    }

    override fun onTouchFinished(isFinishing: Boolean, x: Int, y: Int) {
        // La librería maneja la física aquí
    }

    override fun onDestroy() {
        mFloatingViewManager?.removeAllViewToWindow()
        super.onDestroy()
    }
}
