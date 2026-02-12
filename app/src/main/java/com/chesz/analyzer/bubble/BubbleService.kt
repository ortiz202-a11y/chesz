package com.chesz.analyzer.bubble

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.*
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import kotlin.math.abs

class BubbleService : Service() {

  private lateinit var wm: WindowManager
  private var bubbleView: View? = null
  private var closeView: View? = null

  // Params burbuja
  private lateinit var bubbleLp: WindowManager.LayoutParams
  private lateinit var closeLp: WindowManager.LayoutParams

  // Touch tracking
  private var downRawX = 0f
  private var downRawY = 0f
  private var downX = 0
  private var downY = 0
  private var moved = false
  private var downTime = 0L

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (!Settings.canDrawOverlays(this)) {
      stopSelf()
      return START_NOT_STICKY
    }
    wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    if (bubbleView == null) {
      createCloseZone()
      createBubble()
    }
    return START_STICKY
  }

  override fun onDestroy() {
    super.onDestroy()
    removeViews()
  }

  private fun removeViews() {
    try { bubbleView?.let { wm.removeView(it) } } catch (_: Throwable) {}
    try { closeView?.let { wm.removeView(it) } } catch (_: Throwable) {}
    bubbleView = null
    closeView = null
  }

  private fun windowType(): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    else
      @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
  }

  private fun baseFlags(): Int {
    return (WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
  }

  private fun createCloseZone() {
    val root = FrameLayout(this).apply {
      // “X roja” inferior
      setBackgroundColor(0x55FF0000) // rojo semitransparente
      visibility = View.GONE
      // Texto X grande
      addView(TextView(this@BubbleService).apply {
        text = "X"
        textSize = 32f
        setTextColor(0xFFFFFFFF.toInt())
        gravity = Gravity.CENTER
        layoutParams = FrameLayout.LayoutParams(
          FrameLayout.LayoutParams.MATCH_PARENT,
          FrameLayout.LayoutParams.MATCH_PARENT
        )
      })
    }

    closeLp = WindowManager.LayoutParams(
      WindowManager.LayoutParams.MATCH_PARENT,
      dp(120),
      windowType(),
      baseFlags(),
      PixelFormat.TRANSLUCENT
    ).apply {
      gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
      x = 0
      y = 0
    }

    closeView = root
    wm.addView(root, closeLp)
  }

  private fun createBubble() {
    val bubble = ImageView(this).apply {
      // Usa tu icono (ya lo tienes como adaptive foreground)
      setImageResource(resources.getIdentifier("ic_launcher_foreground", "drawable", packageName))
      scaleType = ImageView.ScaleType.CENTER_CROP
      // Fondo transparente, sin marco
      setBackgroundColor(0x00000000)
    }

    val container = FrameLayout(this).apply {
      setPadding(0, 0, 0, 0)
      addView(bubble, FrameLayout.LayoutParams(dp(56), dp(56)).apply {
        gravity = Gravity.CENTER
      })
    }

    bubbleLp = WindowManager.LayoutParams(
      dp(56),
      dp(56),
      windowType(),
      baseFlags(),
      PixelFormat.TRANSLUCENT
    ).apply {
      gravity = Gravity.TOP or Gravity.START
      x = dp(16)
      y = dp(220)
    }

    container.setOnTouchListener { v, ev ->
      when (ev.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
          downRawX = ev.rawX
          downRawY = ev.rawY
          downX = bubbleLp.x
          downY = bubbleLp.y
          moved = false
          downTime = System.currentTimeMillis()
          showClose(true)
          true
        }

        MotionEvent.ACTION_MOVE -> {
          val dx = (ev.rawX - downRawX).toInt()
          val dy = (ev.rawY - downRawY).toInt()
          if (abs(dx) > dp(3) || abs(dy) > dp(3)) moved = true
          bubbleLp.x = downX + dx
          bubbleLp.y = downY + dy
          wm.updateViewLayout(v, bubbleLp)

          // Si entra a la zona X roja, marca visual (opcional)
          true
        }

        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
          val elapsed = System.currentTimeMillis() - downTime
          val isTap = (!moved && elapsed < 250)

          if (isOverCloseZone(v)) {
            // Drag a X: cerrar servicio completo
            stopSelf()
          } else if (isTap) {
            // Tap: cerrar burbuja + limpiar memoria
            clearSession()
            stopSelf()
          } else {
            // Si no cerró, ocultar zona X
            showClose(false)
          }
          true
        }

        else -> false
      }
    }

    bubbleView = container
    wm.addView(container, bubbleLp)
  }

  private fun showClose(show: Boolean) {
    closeView?.visibility = if (show) View.VISIBLE else View.GONE
  }

  private fun isOverCloseZone(bubble: View): Boolean {
    val close = closeView ?: return false
    if (close.visibility != View.VISIBLE) return false

    val rBubble = Rect()
    val rClose = Rect()

    bubble.getGlobalVisibleRect(rBubble)
    close.getGlobalVisibleRect(rClose)

    // Intersección suficiente
    return Rect.intersects(rBubble, rClose)
  }

  private fun clearSession() {
    // “Limpia memoria” (sin inventar lógica de motor):
    // - cacheDir
    // - filesDir (solo cache; no borramos todo el app data)
    try { cacheDir?.deleteRecursively() } catch (_: Throwable) {}
    // Prefs de sesión si existen
    try {
      getSharedPreferences("chesz_session", MODE_PRIVATE).edit().clear().apply()
    } catch (_: Throwable) {}
  }

  private fun dp(v: Int): Int {
    val d = resources.displayMetrics.density
    return (v * d).toInt()
  }
}
