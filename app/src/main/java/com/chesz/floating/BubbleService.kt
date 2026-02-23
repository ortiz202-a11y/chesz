package com.chesz.floating

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import com.chesz.R
import kotlin.math.abs
import kotlin.math.hypot

class BubbleService : Service() {

  private lateinit var wm: WindowManager

  // Bubble
  private lateinit var bubbleRoot: FrameLayout
  private lateinit var bubbleIcon: ImageView
  private lateinit var bubbleLp: WindowManager.LayoutParams

  // Kill area
  private lateinit var killRoot: FrameLayout
  private lateinit var killLp: WindowManager.LayoutParams
  private var killShown = false
  private var killHover = false

  // Drag state
  private var downRawX = 0f
  private var downRawY = 0f
  private var startX = 0
  private var startY = 0
  private var dragging = false

  override fun onBind(intent: Intent?): IBinder? = null

  override fun onCreate() {
    super.onCreate()
    wm = getSystemService(WINDOW_SERVICE) as WindowManager
    createBubble()
    createKillArea()
  }

  override fun onDestroy() {
    super.onDestroy()
    runCatching { wm.removeViewImmediate(bubbleRoot) }
    runCatching { if (killShown) wm.removeViewImmediate(killRoot) }
    killShown = false
  }

  private fun overlayType(): Int {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
      WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    else
      @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
  }

  private fun createBubble() {
    bubbleRoot = FrameLayout(this)
    bubbleIcon = ImageView(this).apply {
      setImageResource(R.drawable.bubble_icon)
      scaleType = ImageView.ScaleType.CENTER_CROP
      adjustViewBounds = true
    }

    val sizePx = dp(60)
    bubbleRoot.addView(
      bubbleIcon,
      FrameLayout.LayoutParams(sizePx, sizePx)
    )

    bubbleLp = WindowManager.LayoutParams(
      WindowManager.LayoutParams.WRAP_CONTENT,
      WindowManager.LayoutParams.WRAP_CONTENT,
      overlayType(),
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
      PixelFormat.TRANSLUCENT
    ).apply {
      gravity = Gravity.TOP or Gravity.START
      x = 0
      y = dp(120)
    }

    bubbleRoot.setOnTouchListener { _, e ->
      when (e.actionMasked) {
        MotionEvent.ACTION_DOWN -> {
          dragging = false
          downRawX = e.rawX
          downRawY = e.rawY
          startX = bubbleLp.x
          startY = bubbleLp.y
          true
        }

        MotionEvent.ACTION_MOVE -> {
          val dx = (e.rawX - downRawX).toInt()
          val dy = (e.rawY - downRawY).toInt()

          // umbral: aquí nace el DRAG real → recién ahí mostramos kill
          if (!dragging && (abs(dx) + abs(dy) > dp(6))) {
            dragging = true
            showKill(true)
          }

          bubbleLp.x = startX + dx
          bubbleLp.y = startY + dy
          runCatching { wm.updateViewLayout(bubbleRoot, bubbleLp) }

          if (dragging) {
            val over = isOverKillCenter(bubbleCenterX(), bubbleCenterY())
            setKillHover(over)
          }
          true
        }

        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
          if (dragging) {
            val shouldKill = isOverKillCenter(bubbleCenterX(), bubbleCenterY())
            if (shouldKill) {
              performKill()
            } else {
              setKillHover(false)
              showKill(false)
            }
          }
          dragging = false
          true
        }

        else -> false
      }
    }

    wm.addView(bubbleRoot, bubbleLp)
  }

  // ---------- Kill Area (standard: bottom center) ----------
  private fun createKillArea() {
    killRoot = FrameLayout(this).apply {
      // círculo rojo (simple y estable)
      background = android.graphics.drawable.GradientDrawable().apply {
        shape = android.graphics.drawable.GradientDrawable.OVAL
        setColor(0xCCFF0000.toInt())
      }
      alpha = 1f
      scaleX = 1f
      scaleY = 1f
      visibility = View.VISIBLE
    }

    val xIcon = ImageView(this).apply {
      setImageResource(android.R.drawable.ic_delete)
      setColorFilter(0xFFFFFFFF.toInt())
      scaleType = ImageView.ScaleType.CENTER_INSIDE
    }

    val sizePx = dp(100)
    killRoot.addView(
      xIcon,
      FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER)
    )

    killLp = WindowManager.LayoutParams(
      sizePx,
      sizePx,
      overlayType(),
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
          or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
          or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
      PixelFormat.TRANSLUCENT
    ).apply {
      gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
      x = 0
      y = dp(40) // margen desde abajo
    }
  }

  private fun showKill(show: Boolean) {
    if (show) {
      if (!killShown) {
        runCatching { wm.addView(killRoot, killLp) }
        killShown = true
      }
    } else {
      if (!killShown) return
      runCatching { wm.removeViewImmediate(killRoot) }
      killShown = false
    }
  }

  private fun setKillHover(hover: Boolean) {
      val target = if (hover) 1.30f else 1.0f
      killRoot.animate().cancel()
      killRoot.animate().scaleX(target).scaleY(target).setDuration(90).start()
    }

  private fun bubbleCenterX(): Float {
      val loc = IntArray(2)
      bubbleRoot.getLocationOnScreen(loc)
      val w = if (bubbleRoot.width > 0) bubbleRoot.width else dp(60)
      return loc[0] + (w / 2f)
    }

    private fun bubbleCenterY(): Float {
      val loc = IntArray(2)
      bubbleRoot.getLocationOnScreen(loc)
      val h = if (bubbleRoot.height > 0) bubbleRoot.height else dp(60)
      return loc[1] + (h / 2f)
    }

  private fun killCenterOnScreen(): Pair<Float, Float> {
    val size = Point()
    @Suppress("DEPRECATION")
    wm.defaultDisplay.getSize(size)

    val cx = size.x / 2f
    val cy = size.y - killLp.y - (killLp.height / 2f)
    return cx to cy
  }

  private fun isOverKillCenter(x: Float, y: Float): Boolean {
      if (!killShown) return false

      val loc = IntArray(2)
      killRoot.getLocationOnScreen(loc)
      val left = loc[0]
      val top = loc[1]
      val right = left + killRoot.width
      val bottom = top + killRoot.height

      // margen extra para que "encima" sea fácil de activar (tuneable)
      val pad = dp(18)

      return (x >= (left - pad) && x <= (right + pad) &&
              y >= (top - pad) && y <= (bottom + pad))
    }

  private fun performKill() {
    // cierre determinista (sin depender de animaciones)
    runCatching { wm.removeViewImmediate(bubbleRoot) }
    runCatching { if (killShown) wm.removeViewImmediate(killRoot) }
    killShown = false
    stopSelf()
  }

  private fun dp(v: Int): Int {
    val d = resources.displayMetrics.density
    return (v * d).toInt()
  }
}
