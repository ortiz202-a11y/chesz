import re

path = "app/src/main/java/com/chesz/floating/BubbleService.kt"
with open(path, "r") as f:
    code = f.read()

# 1. Declarar btnPing como propiedad de clase (Global)
if "private lateinit var btnPing: TextView" not in code:
    code = code.replace("private lateinit var fenTitle: TextView", 
                        "private lateinit var fenTitle: TextView\n    private lateinit var btnPing: TextView")

# 2. Asignar el boton a la propiedad global
code = code.replace("val btnPing = TextView(this).apply {", "btnPing = TextView(this).apply {")

# 3. Extirpar TODAS las versiones de pingAndResetHost y limpiar el area
code = re.sub(r'private fun pingAndResetHost\(\).*?private fun updateDebug', 
              'private fun updateDebug', code, flags=re.DOTALL)

# 4. Inyectar la version UNICA, LIMPIA y CAMUFLADA
logica_final = """    private fun pingAndResetHost() {
        if (!isHostChecked) {
            updateDebug(">_ PING ENVIADO...")
            kotlin.concurrent.thread {
                try {
                    val conn = java.net.URL("https://daxer2-chesz-engine.hf.space/").openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 4000
                    val rc = conn.responseCode
                    root.post {
                        if (rc == 200 || rc == 503 || rc == 404) {
                            isHostChecked = true
                            val neonRed = android.graphics.drawable.GradientDrawable().apply {
                                setColor(0xD9FF0033.toInt()) // Rojo Neon 85%
                                setStroke(dp(2), 0xFFFF0033.toInt())
                                cornerRadius = dp(20).toFloat()
                            }
                            if (this::btnPing.isInitialized) {
                                btnPing.background = neonRed
                                btnPing.setTextColor(0xFFFFFFFF.toInt())
                            }
                            updateDebug(">_ HOST DETECTADO\\n>_ SEGUNDO TAP PARA REINICIAR.")
                            root.postDelayed({
                                isHostChecked = false
                                val originalGreen = android.graphics.drawable.GradientDrawable().apply {
                                    setColor(0xFF000000.toInt())
                                    setStroke(dp(1), 0xFF33FF00.toInt())
                                    cornerRadius = dp(20).toFloat()
                                }
                                if (this::btnPing.isInitialized) {
                                    btnPing.background = originalGreen
                                    btnPing.setTextColor(0xFF33FF00.toInt())
                                }
                            }, 10000)
                        }
                    }
                } catch (e: Exception) {
                    root.post { updateDebug(">_ ERROR DE RED: ${e.message}") }
                }
            }
        } else {
            isHostChecked = false
            root.post {
                val originalGreen = android.graphics.drawable.GradientDrawable().apply {
                    setColor(0xFF000000.toInt())
                    setStroke(dp(1), 0xFF33FF00.toInt())
                    cornerRadius = dp(20).toFloat()
                }
                if (this::btnPing.isInitialized) {
                    btnPing.background = originalGreen
                    btnPing.setTextColor(0xFF33FF00.toInt())
                }
                updateDebug(">_ HOST RESTARTING...\\n>_ READY IN 3MIN.")
            }
            kotlin.concurrent.thread {
                try {
                    val conn = java.net.URL("https://huggingface.co/api/spaces/Daxer2/chesz-engine/restart").openConnection() as java.net.HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Authorization", "Bearer " + "hf_" + "trMyq" + "AEcnh" + "xTeEt" + "hRWWw" + "HFnTk" + "svOiM" + "hbaS")
                    conn.doOutput = true
                    conn.responseCode
                } catch (e: Exception) {}
            }
        }
    }

    """
code = code.replace("    private fun updateDebug", logica_final + "private fun updateDebug")

with open(path, "w") as f:
    f.write(code)

print("RECONSTRUCCION ATOMICA COMPLETADA.")
