import sys

path = "/data/data/com.termux/files/home/chesz/app/src/main/java/com/chesz/floating/BubbleService.kt"

with open(path, 'r') as f:
    content = f.read()

# 1. Inyectar llamada a la API en el flujo de éxito
old_debug = 'updateDebug("✅ Foto guardada en /Pictures")'
new_debug = 'updateDebug("📡 Enviando a API Soberana..."); sendToCheszEngine(file)'

if old_debug in content:
    content = content.replace(old_debug, new_debug)

# 2. Inyectar la función sendToCheszEngine antes de la última llave de la clase
api_function = """
    private fun sendToCheszEngine(file: java.io.File) {
        Thread {
            try {
                val url = java.net.URL("https://Daxer2-chesz-engine.hf.space/predict")
                val conn = url.openConnection() as java.net.HttpURLConnection
                val boundary = "Boundary-" + System.currentTimeMillis()
                
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")

                conn.outputStream.use { out ->
                    val writer = java.io.PrintWriter(out.writer())
                    writer.println("--$boundary")
                    writer.println("Content-Disposition: form-data; name=\\"file\\"; filename=\\"${file.name}\\"")
                    writer.println("Content-Type: image/png")
                    writer.println()
                    writer.flush()
                    
                    file.inputStream().use { it.copyTo(out) }
                    
                    writer.println()
                    writer.println("--$boundary--")
                    writer.flush()
                }

                if (conn.responseCode == 200) {
                    val response = conn.inputStream.bufferedReader().readText()
                    val fen = response.substringAfter("\\"fen\\":\\"").substringBefore("\\"")
                    updateDebug("✅ FEN: $fen")
                } else {
                    updateDebug("❌ Error API: ${conn.responseCode}")
                }
            } catch (e: Exception) {
                updateDebug("❌ Error Red: ${e.message}")
            }
        }.start()
    }
"""

# Buscamos la última llave de cierre de la clase BubbleService
if content.strip().endswith("}"):
    last_brace_index = content.rfind("}")
    content = content[:last_brace_index] + api_function + "\n" + content[last_brace_index:]

with open(path, 'w') as f:
    f.write(content)
