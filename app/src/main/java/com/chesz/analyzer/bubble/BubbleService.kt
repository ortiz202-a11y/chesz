package com.chesz.analyzer.bubble

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Matrix
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import com.chesz.analyzer.R
import kotlin.math.abs
import kotlin.math.hypot

class BubbleService : Service() {
    private lateinit var wm: WindowManager
    private var bubbleView: View? = null
    private var closeView: View? = null

    // Paso 1: overlay único (root) + panel como hijo
    private var panelBubble: View? = null

    private lateinit var bubbleLp: WindowManager.LayoutParams
    private lateinit var closeLp: WindowManager.LayoutParams

    private var downRawX = 0f
    private var downRawY = 0f
    private var downX = 0
    private var downY = 0
    private var moved = false
    private var downTime = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (!Settings.canDrawOverlays(this)) {
            shutdown()
            return START_STICKY
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

    private fun shutdown() {
        removeViews()
        stopSelf()
    }

    private fun removeViews() {
        if (!::wm.isInitialized) return

        try {
            bubbleView?.let { wm.removeViewImmediate(it) }
        } catch (_: Throwable) {
        }

        try {
            closeView?.let { wm.removeViewImmediate(it) }
        } catch (_: Throwable) {
        }

        panelBubble = null
        bubbleView = null
        closeView = null
    }

    private fun windowType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

    private fun baseFlags(): Int =
        (
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        )

    // =========================
    // CLOSE ZONE (X)
    // =========================
    private fun createCloseZone() {
        val size = dp(96)

        val root =
            FrameLayout(this).apply {
                visibility = View.GONE
                setBackgroundColor(0x00000000)

                addView(
                    FrameLayout(this@BubbleService).apply {
                        setBackgroundColor(0xFFE53935.toInt()) // fondo oscuro
                        clipToOutline = true
                        outlineProvider =
                            object : ViewOutlineProvider() {
                                override fun getOutline(
                                    view: View,
                                    outline: Outline,
                                ) {
                                    outline.setOval(0, 0, view.width, view.height)
                                }
                            }

                        addView(
                            TextView(this@BubbleService).apply {
                                text = "X"
                                textSize = 28f
                                setTextColor(0xFFFFFFFF.toInt())
                                gravity = Gravity.CENTER
                                layoutParams =
                                    FrameLayout.LayoutParams(
                                        FrameLayout.LayoutParams.MATCH_PARENT,
                                        FrameLayout.LayoutParams.MATCH_PARENT,
                                    )
                            },
                        )
                    },
                    FrameLayout.LayoutParams(size, size).apply { gravity = Gravity.CENTER },
                )
            }

        closeLp =
            WindowManager
                .LayoutParams(
                    size,
                    size,
                    windowType(),
                    baseFlags(),
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                    y = dp(28)
                }

        closeView = root
        wm.addView(root, closeLp)
    }

    private fun showClose(show: Boolean) {
        closeView?.visibility = if (show) View.VISIBLE else View.GONE
    }

    // hit circular REAL (no rectángulo)
    private fun isInsideCloseCircle(
        rawX: Float,
        rawY: Float,
    ): Boolean {
        val close = closeView ?: return false
        if (close.visibility != View.VISIBLE) return false

        val loc = IntArray(2)
        close.getLocationOnScreen(loc)
        val cx = loc[0] + close.width / 2f
        val cy = loc[1] + close.height / 2f
        val r = close.width.coerceAtMost(close.height) / 2f

        return hypot(rawX - cx, rawY - cy) <= r
    }

    // =========================
    // PANEL (burbuja desplegada)
    // =========================
    private fun isPanelOpen(): Boolean = (panelBubble?.visibility == View.VISIBLE)

    private fun openPanel() {
          panelBubble?.visibility = View.VISIBLE
          val root = bubbleView
          if (root != null && ::bubbleLp.isInitialized) {
              // Post eliminado
          }
      }

    private fun closePanel() {
          panelBubble?.visibility = View.GONE
          val root = bubbleView
          if (root != null && ::bubbleLp.isInitialized) {
              // Eliminamos el clamp aquí para que NO brinque
              wm.updateViewLayout(root, bubbleLp)
          }
      }
          }
      }

    // =========================
    // BUBBLE
    // =========================
    private fun createBubble() {
        // Overlay único (root) desde XML
        val root = LayoutInflater.from(this).inflate(R.layout.overlay_root, null) as FrameLayout

        // Burbuja (icono) dentro del contenedor
        val bubbleContainer = root.findViewById<FrameLayout>(R.id.bubbleContainer)
          // Layout MÁSTER: burbuja abajo-izquierda
          (bubbleContainer.layoutParams as FrameLayout.LayoutParams).apply {
              gravity = Gravity.BOTTOM or Gravity.START
              leftMargin = 0
              bottomMargin = 0
              bubbleContainer.layoutParams = this
          }

        val iconView =
            CircleCropView(this).apply {
                setImageResource(R.drawable.bubble_icon)
            }
        bubbleContainer.addView(
            iconView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )

        // Panel como hijo del mismo root (negro translúcido, 60% x 30%)
        val panel = root.findViewById<View>(R.id.panelBubble)
        panel.setBackgroundColor(0x88000000.toInt()) // negro translúcido
        panel.visibility = View.GONE

        val dm = resources.displayMetrics
        val pw = (dm.widthPixels * 0.60f).toInt()
        val ph = (dm.heightPixels * 0.30f).toInt()
        val plp =
              FrameLayout.LayoutParams(pw, ph).apply {
                  // Layout MÁSTER: panel abajo-izquierda, corrido a la derecha con overlap
                  gravity = Gravity.BOTTOM or Gravity.START
                  leftMargin = dp(55) // panel pegado; burbuja sale 55dp
                  bottomMargin = 0
              }
          panel.layoutParams = plp

        // Tap to Close (cierra solo el panel)
        root.findViewById<View>(R.id.tapToClose).setOnClickListener {
            closePanel()
        }

        // Drag SOLO desde la burbuja, pero mueve TODO el root (overlay único)
        bubbleLp =
            WindowManager
                .LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    windowType(),
                    baseFlags(),
                    PixelFormat.TRANSLUCENT,
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    x = dp(16)
                    y = dp(220)
                }

        bubbleContainer.setOnTouchListener { _, ev ->
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
                    updateOverlayLayoutClamped(root, bubbleLp)
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val elapsed = System.currentTimeMillis() - downTime
                    val isTap = (!moved && elapsed < 250)

                    if (moved && isInsideCloseCircle(ev.rawX, ev.rawY)) {
                        shutdown()
                    } else if (isTap) {
                        if (isPanelOpen()) closePanel() else openPanel()
                        showClose(false)
                    } else {
                        showClose(false)
                    }
                    true
                }

                else -> {
                    false
                }
            }
        }

        // Guardar refs
        panelBubble = panel
        bubbleView = root

        wm.addView(root, bubbleLp)

        // Post eliminado
    }

    // =========================
    // CLAMP overlay to screen
    // =========================
    private fun getScreenSizePx(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = wm.currentWindowMetrics
            val b = metrics.bounds
            Pair(b.width(), b.height())
        } else {
            @Suppress("DEPRECATION")
            val dm = resources.displayMetrics
            Pair(dm.widthPixels, dm.heightPixels)
        }
    }

      private fun effectiveOverlaySizePx(root: View): Pair<Int, Int> {
          // Panel abierto => tamaño real = root completo (botón + panel)
          if (isPanelOpen()) {
              val w = if (root.width > 0) root.width else root.measuredWidth
              val h = if (root.height > 0) root.height else root.measuredHeight
              return Pair(w, h)
          }

          // Panel cerrado => clamp SOLO por tamaño del botón (evita margen lateral por panel)
          val bubble = root.findViewById<View>(R.id.bubbleContainer)
          val w = if (bubble.width > 0) bubble.width else bubble.measuredWidth
          val h = if (bubble.height > 0) bubble.height else bubble.measuredHeight
          return Pair(w, h)
      }


private fun clampToScreen(lp: WindowManager.LayoutParams, overlayView: View) {
    val (sw, sh) = getScreenSizePx()
    val size = effectiveOverlaySizePx(overlayView)
    val vw = size.first
    val vh = size.second
    
    // ELIMINAMOS EL MARCO FANTASMA: 
    // Si el panel está cerrado, el ancho real para chocar con la pared es solo el de la burbuja
    val maxX = if (isPanelOpen()) (sw - vw) else (sw - dp(80)) 
    val maxY = (sh - vh).coerceAtLeast(0)

    // LÍMITE SUPERIOR FORZADO A 0
    lp.x = lp.x.coerceIn(0, sw - dp(80)) 
    lp.y = lp.y.coerceIn(0, sh - dp(80))
}


    private fun updateOverlayLayoutClamped(root: View, lp: WindowManager.LayoutParams) {
        clampToScreen(lp, root)
        wm.updateViewLayout(root, lp)
    }


    private fun dp(v: Int): Int {
        val d = resources.displayMetrics.density
        return (v * d).toInt()
    }

    // =========================
    // CircleCrop real (shader)
    // =========================
    private class CircleCropView(
        ctx: Context,
    ) : View(ctx) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var shader: BitmapShader? = null
        private var bitmap: Bitmap? = null

        fun setImageResource(resId: Int) {
            val d = context.resources.getDrawable(resId, context.theme)
            bitmap = drawableToBitmap(d)
            rebuildShader()
            invalidate()
        }

        override fun onSizeChanged(
            w: Int,
            h: Int,
            oldw: Int,
            oldh: Int,
        ) {
            super.onSizeChanged(w, h, oldw, oldh)
            rebuildShader()
        }

        private fun rebuildShader() {
            val b = bitmap ?: return
            if (width <= 0 || height <= 0) return

            shader = BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)

            val m = Matrix()
            val scale = maxOf(width.toFloat() / b.width, height.toFloat() / b.height)
            val dx = (width - b.width * scale) * 0.5f
            val dy = (height - b.height * scale) * 0.5f
            m.setScale(scale, scale)
            m.postTranslate(dx, dy)
            shader!!.setLocalMatrix(m)

            paint.shader = shader
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val r = minOf(width, height) * 0.5f
            canvas.drawCircle(width * 0.5f, height * 0.5f, r, paint)
        }

        private fun drawableToBitmap(d: android.graphics.drawable.Drawable): Bitmap {
            val w = if (d.intrinsicWidth > 0) d.intrinsicWidth else 512
            val h = if (d.intrinsicHeight > 0) d.intrinsicHeight else 512
            val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(b)
            d.setBounds(0, 0, c.width, c.height)
            d.draw(c)
            return b
        }
    }

    // fondo redondeado simple (sin libs)
    private class RoundRectDrawable(
        private val color: Int,
        private val radius: Float,
    ) : android.graphics.drawable.Drawable() {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = this@RoundRectDrawable.color }
        private val r = RectF()

        override fun draw(canvas: Canvas) {
            r.set(bounds)
            canvas.drawRoundRect(r, radius, radius, p)
        }

        override fun setAlpha(alpha: Int) {
            p.alpha = alpha
        }

        override fun setColorFilter(colorFilter: ColorFilter?) {
            p.colorFilter = colorFilter
        }

        override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
    }
}
