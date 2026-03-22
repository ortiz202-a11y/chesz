import os, re

path = "app/src/main/java/com/chesz/floating/BubbleService.kt"
with open(path, "r") as f:
    code = f.read()

# 1. Definir los Drawables (Colores) en el codigo
# Verde original (Borde verde, fondo negro)
# Rojo Neon 85% (Borde rojo #FF0033, fondo rojo traslucido 85% = D9FF0033)

logica_colores = """
                root.post {
                    if (rc == 200 || rc == 503 || rc == 404) {
                        isHostChecked = true
                        // CAMBIO A ROJO NEON (85% opacidad en fondo)
                        val neonRed = android.graphics.drawable.GradientDrawable().apply {
                            setColor(0xD9FF0033.toInt()) 
                            setStroke(dp(2), 0xFFFF0033.toInt())
                            cornerRadius = dp(20).toFloat()
                        }
                        btnPing.background = neonRed
                        btnPing.setTextColor(0xFFFFFFFF.toInt()) // Texto blanco para contraste
                        
                        updateDebug(">_ HOST DETECTADO\\n>_ SEGUNDO TAP PARA REINICIAR.")
                        
                        // RESETEAR A VERDE TRAS 10 SEG
                        root.postDelayed({ 
                            isHostChecked = false 
                            val originalGreen = android.graphics.drawable.GradientDrawable().apply {
                                setColor(0xFF000000.toInt())
                                setStroke(dp(1), 0xFF33FF00.toInt())
                                cornerRadius = dp(20).toFloat()
                            }
                            btnPing.background = originalGreen
                            btnPing.setTextColor(0xFF33FF00.toInt())
                        }, 10000)
                    }
                }"""

# Inyectar el cambio de color dentro de la respuesta del Ping
patron_color = r'root\.post\s*\{\s*if\s*\(rc\s*==\s*200\)\s*\{.*?\}\s*\}'
code = re.sub(patron_color, logica_colores, code, flags=re.DOTALL)

# Asegurar que el segundo tap tambien restaure el color verde
restaurar_verde = """// TAP 2: EJECUCION
            isHostChecked = false
            root.post {
                val originalGreen = android.graphics.drawable.GradientDrawable().apply {
                    setColor(0xFF000000.toInt())
                    setStroke(dp(1), 0xFF33FF00.toInt())
                    cornerRadius = dp(20).toFloat()
                }
                btnPing.background = originalGreen
                btnPing.setTextColor(0xFF33FF00.toInt())
            }"""
code = code.replace("// TAP 2: EJECUCION\n            isHostChecked = false", restaurar_verde)

with open(path, "w") as f:
    f.write(code)

print("CIRUGÍA NEÓN COMPLETADA: BOTÓN DINÁMICO AL 85% CONFIGURADO.")
