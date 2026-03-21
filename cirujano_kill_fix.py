import os

path = "app/src/main/java/com/chesz/floating/BubbleService.kt"
with open(path, "r") as f:
    code = f.read()

old_block = """                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    devHandler.removeCallbacks(devRunnable!!) // Cancelar temporizador
                    if (isDeveloperMode) {
                        dragging = false
                        return@setOnTouchListener true // Escudo: Ignorar el tap normal
                    }
                    if (dragging) {
                        if (isOverKillCenter(bubbleCenterX(), bubbleCenterY())) {
                            performKill()
                        } else {
                            setKillHover(false)
                            showKill(false)
                        }
                    } else {
                        val dist = kotlin.math.hypot(e.rawX - bubbleCenterX(), e.rawY - bubbleCenterY())
                        if (dist <= dp(30).toFloat()) togglePanel()
                    }
                    dragging = false
                    true
                }"""

new_block = """                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    devHandler.removeCallbacks(devRunnable!!) // Cancelar temporizador
                    
                    // 1. Siempre procesar el arrastre y apagar el Kill Area primero
                    if (dragging) {
                        if (isOverKillCenter(bubbleCenterX(), bubbleCenterY())) {
                            performKill()
                        } else {
                            setKillHover(false)
                            showKill(false)
                        }
                        dragging = false
                        return@setOnTouchListener true
                    }
                    
                    // 2. Si no fue arrastre, fue un Tap. Aplicar escudo si es Modo Dios.
                    if (isDeveloperMode) {
                        return@setOnTouchListener true // Escudo: Ignorar tap normal
                    } else {
                        val dist = kotlin.math.hypot(e.rawX - bubbleCenterX(), e.rawY - bubbleCenterY())
                        if (dist <= dp(30).toFloat()) togglePanel()
                    }
                    
                    dragging = false
                    true
                }"""

if old_block in code:
    code = code.replace(old_block, new_block)
    with open(path, "w") as f:
        f.write(code)
    print("ORDEN DE PRIORIDAD CORREGIDA: ZONA DE MATAR RESTAURADA Y ESCUDO AJUSTADO.")
else:
    print("ERROR: No se encontro el bloque exacto. Revisa espacios o versiones previas.")

