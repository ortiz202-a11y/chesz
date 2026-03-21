import os

path = "app/src/main/java/com/chesz/floating/BubbleService.kt"
with open(path, "r") as f:
    code = f.read()

# 1. Inyectar variables globales del Modo Dios
var_old = "private var killHovered = false"
var_new = """private var killHovered = false

    // ===== Modo Dios =====
    private var isDeveloperMode = false
    private val devHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var devRunnable: Runnable? = null
    private lateinit var devBar: LinearLayout"""
code = code.replace(var_old, var_new)

# 2. Inyectar el temporizador en ACTION_DOWN
down_old = """                MotionEvent.ACTION_DOWN -> {
                    dragging = false
                    downRawX = e.rawX"""
down_new = """                MotionEvent.ACTION_DOWN -> {
                    dragging = false
                    downRawX = e.rawX
                    
                    // Iniciar temporizador Modo Dios
                    devRunnable = Runnable {
                        isDeveloperMode = true
                        flashBubbleRed() // Feedback visual
                        if (!panelShown) showPanelIfFits()
                        if (this::devBar.isInitialized) devBar.visibility = View.VISIBLE
                        updateDebug(">_ MODO DESARROLLADOR ACTIVO.\\n>_ ESPERANDO ORDENES...")
                    }
                    devHandler.postDelayed(devRunnable!!, 5000)
"""
code = code.replace(down_old, down_new)

# 3. Asesinar el temporizador si el usuario mueve el dedo
move_old = """                    if (!dragging && (abs(dx) + abs(dy) > dp(6))) {
                        dragging = true"""
move_new = """                    if (!dragging && (abs(dx) + abs(dy) > dp(6))) {
                        devHandler.removeCallbacks(devRunnable!!) // Cancelar Modo Dios por arrastre
                        dragging = true"""
code = code.replace(move_old, move_new)

# 4. Asesinar el temporizador al levantar el dedo y bloquear el Tap normal
up_old = """                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {"""
up_new = """                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    devHandler.removeCallbacks(devRunnable!!) // Cancelar temporizador
                    if (isDeveloperMode) {
                        dragging = false
                        return@setOnTouchListener true // Escudo: Ignorar el tap normal
                    }
                    if (dragging) {"""
code = code.replace(up_old, up_new)

# 5. Salir del Modo Dios al ocultar el panel
hide_old = """        if (panelShown) {
            val dm = resources.displayMetrics"""
hide_new = """        isDeveloperMode = false
        if (this::devBar.isInitialized) devBar.visibility = View.GONE
        
        if (panelShown) {
            val dm = resources.displayMetrics"""
code = code.replace(hide_old, hide_new)

# 6. Construir los botones visuales (Estilo Pastilla Hacker)
ui_old = "col.addView(View(this), LinearLayout.LayoutParams(-1, 0, 1f))"
ui_new = """col.addView(View(this), LinearLayout.LayoutParams(-1, 0, 1f))

        // --- BARRA MODO DIOS ---
        devBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            visibility = View.GONE
            setPadding(0, dp(5), 0, 0)
        }
        
        val btnBg = android.graphics.drawable.GradientDrawable().apply {
            setColor(0xFF000000.toInt()) // Fondo Negro
            setStroke(dp(1), 0xFF33FF00.toInt()) // Borde Verde
            cornerRadius = dp(20).toFloat() // Forma Pastilla
        }

        val btnPing = TextView(this).apply {
            text = "PING HOST"
            typeface = customFont
            setTextColor(0xFF33FF00.toInt())
            textSize = 12f
            gravity = android.view.Gravity.CENTER
            background = btnBg
            setPadding(0, dp(8), 0, dp(8))
            setOnClickListener { updateDebug(">_ PING: ENVIANDO PAQUETES A HF...") }
        }

        val btnBench = TextView(this).apply {
            text = "BENCHMARK"
            typeface = customFont
            setTextColor(0xFF33FF00.toInt())
            textSize = 12f
            gravity = android.view.Gravity.CENTER
            background = btnBg
            setPadding(0, dp(8), 0, dp(8))
            setOnClickListener { updateDebug(">_ BENCHMARK: INICIANDO BATERIA ZERO-UI...") }
        }

        devBar.addView(btnPing, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(4) })
        devBar.addView(btnBench, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(4) })
        col.addView(devBar, LinearLayout.LayoutParams(-1, -2))"""
code = code.replace(ui_old, ui_new)

with open(path, "w") as f:
    f.write(code)

print("ARQUITECTURA MODO DIOS INYECTADA CON EXITO.")
