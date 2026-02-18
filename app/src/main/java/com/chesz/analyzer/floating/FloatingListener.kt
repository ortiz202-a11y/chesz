package com.chesz.analyzer.floating

interface FloatingListener {
    fun onFinishFloatingView()
    fun onTouchMove(x: Int, y: Int)
}
