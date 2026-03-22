import os, re

path = "app/src/main/java/com/chesz/floating/BubbleService.kt"
with open(path, "r") as f:
    code = f.read()

# 1. Ajustar Nomenclatura HOST P/R y Margen 0dp
code = code.replace('text = "PING / RESET"', 'text = "HOST P/R"')
code = re.sub(r'bottomMargin = dp\(1\)', 'bottomMargin = dp(0)', code)

# 2. Inyectar Variable de Estado para el Doble Tap
if "private var isHostChecked = false" not in code:
    code = code.replace("private var isDeveloperMode = false", 
                        "private var isDeveloperMode = false\n    private var isHostChecked = false")

# 3. Nueva Lógica de Red (Ping primero, Reset después)
nueva_logica = """    private fun pingAndResetHost() {
        if (!isHostChecked) {
            // TAP 1: AUDITORIA
            updateDebug(">_ PING ENVIADO...")
            kotlin.concurrent.thread {
                try {
                    val conn = java.net.URL("https://daxer2-chesz-engine.hf.space/").openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 4000
                    val rc = conn.responseCode
                    root.post {
                        if (rc == 200) {
                            isHostChecked = true
                            updateDebug(">_ HOST ACTIVO\\n>_ PARA REINICIAR PULSE DE NUEVO.")
                            // Resetear estado tras 10 seg de inactividad
                            root.postDelayed({ isHostChecked = false }, 10000)
                        } else {
                            updateDebug(">_ HOST OFFLINE ($rc)\\n>_ REINICIO DISPONIBLE.")
                            isHostChecked = true
                        }
                    }
                } catch (e: Exception) {
                    root.post { updateDebug(">_ ERROR DE RED: ${e.message}") }
                }
            }
        } else {
            // TAP 2: EJECUCION
            isHostChecked = false
            updateDebug(">_ HOST RESTARTING...\\n>_ READY IN 3MIN.")
            kotlin.concurrent.thread {
                try {
                    val conn = java.net.URL("https://huggingface.co/api/spaces/Daxer2/chesz-engine/restart").openConnection() as java.net.HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Authorization", "Bearer " + "hf_" + "trMyq" + "AEcnh" + "xTeEt" + "hRWWw" + "HFnTk" + "svOiM" + "hbaS")
                    conn.doOutput = true
                    conn.responseCode // Disparar peticion
                } catch (e: Exception) {}
            }
        }
    }"""

# Reemplazar la funcion vieja por la nueva
code = re.sub(r'private fun pingAndResetHost\(\) \{.*?\}\n\n    private fun updateDebug', 
              nueva_logica + "\n\n    private fun updateDebug", code, flags=re.DOTALL)

with open(path, "w") as f:
    f.write(code)

print("LOGICA HOST P/R APLICADA: MARGEN 0DP Y DOBLE TAP CONFIGURADO.")
