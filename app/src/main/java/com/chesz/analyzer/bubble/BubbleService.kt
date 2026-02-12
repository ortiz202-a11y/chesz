package com.chesz.analyzer.bubble

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
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

  private lateinit var bubbleLp: WindowManager.LayoutParams
  private lateinit var closeLp: WindowManager.LayoutParams

  private var downRawX = 0f
  private var downRawY = 0f
  private var downX = 0
  private var downY = 0
  private var moved = false
  private var downTime = 0L

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    if (!Settings.canDrawOverlays(this)) {
      shutdown()
      return START_NOT_STICKY
    }
    wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    if (bubbleView == null) {
      createCloseZone()
      createBubble()
    }
    // ✅ IMPORTANTE: no “revivir” el servicio después de cerrarlo
    return START_NOT_STICKY
  }

  override fun onDestroy() {
    super.onDestroy()
    removeViews()
  }

  private fun shutdown() {
    // ✅ Cierra TODO: vistas + servicio
    removeViews()
    stopSelf()
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

  // X chica (círculo)
  private fun createCloseZone() {
    val size = dp(84)

    val root = FrameLayout(this).apply {
      visibility = View.GONE
      setBackgroundColor(0x00000000)
      addView(FrameLayout(this@BubbleService).apply {
        setBackgroundColor(0xCCFF0000.toInt())
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
          override fun getOutline(view: View, outline: android.graphics.Outline) {
            outline.setOval(0, 0, view.width, view.height)
          }
        }
        addView(TextView(this@BubbleService).apply {
          text = "X"
          textSize = 28f
          setTextColor(0xFFFFFFFF.toInt())
          gravity = Gravity.CENTER
          layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
          )
        })
      }, FrameLayout.LayoutParams(size, size).apply { gravity = Gravity.CENTER })
    }

    closeLp = WindowManager.LayoutParams(
      size,
      size,
      windowType(),
      baseFlags(),
      PixelFormat.TRANSLUCENT
    ).apply {
      gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
      y = dp(28)
    }

    closeView = root
    wm.addView(root, closeLp)
  }

  // ✅ Burbuja: CÍRCULO REAL sin marco blanco
  private fun createBubble() {
    val icon = ImageView(this).apply {
      setImageResource(resources.getIdentifier("ic_launcher_foreground", "drawable", packageName))
      // ✅ llena el círculo, recorta si hace falta
      scaleType = ImageView.ScaleType.CENTER_CROP
      setBackgroundColor(0x00000000)
    }

    val container = FrameLayout(this).apply {
      // ✅ máscara circular
      clipToOutline = true
      outlineProvider = object : ViewOutlineProvider() {
        override fun getOutline(view: View, outline: android.graphics.Outline) {
          outline.setOval(0, 0, view.width, view.height)
        }
      }
      background = null
      setPadding(0, 0, 0, 0)

      addView(icon, FrameLayout.LayoutParams(
        FrameLayout.LayoutParams.MATCH_PARENT,
        FrameLayout.LayoutParams.MATCH_PARENT
      ))
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
          showClose(false)
          true
        }

        MotionEvent.ACTION_MOVE -> {
          val dx = (ev.rawX - downRawX).toInt()
          val dy = (ev.rawY - downRawY).toInt()

          if (!moved && (abs(dx) > dp(4) || abs(dy) > dp(4))) {
            moved = true
            showClose(true)
          }

          bubbleLp.x = downX + dx
          bubbleLp.y = downY + dy
          wm.updateViewLayout(v, bubbleLp)
          true
        }

        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
          val elapsed = System.currentTimeMillis() - downTime
          val isTap = (!moved && elapsed < 250)

          if (moved && isInsideClose(ev.rawX, ev.rawY)) {
            // ✅ Drag a X: cerrar TODO
            shutdown()
          } else if (isTap) {
            // ✅ Tap: cerrar TODO
            clearSession()
            shutdown()
          } else {
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

  private fun isInsideClose(rawX: Float, rawY: Float): Boolean {
    val close = closeView ?: return false
    if (close.visibility != View.VISIBLE) return false

    val loc = IntArray(2)
    close.getLocationOnScreen(loc)
    val left = loc[0]
    val top = loc[1]
    val right = left + close.width
    val bottom = top + close.height

    return rawX >= left && rawX <= right && rawY >= top && rawY <= bottom
  }

  private fun clearSession() {
    try { cacheDir?.deleteRecursively() } catch (_: Throwable) {}
    try { getSharedPreferences("chesz_session", MODE_PRIVATE).edit().clear().apply() } catch (_: Throwable) {}
  }

  private fun dp(v: Int): Int {
    val d = resources.displayMetrics.density
    return (v * d).toInt()
  }
}
