import re, os

file_path = os.path.expanduser("~/chesz/app/src/main/java/com/chesz/floating/BubbleService.kt")
with open(file_path, "r") as f: code = f.read()

# 1. Inyección de dependencias (Si no existen)
if "import org.json.JSONObject" not in code:
    code = "import org.json.JSONObject\nimport android.os.Handler\nimport android.os.Looper\n" + code

# 2. Localizar el bloque de respuesta HTTP 200
pattern = re.compile(r'(if\s*\(conn\.responseCode\s*==\s*200\)\s*\{)(.*?)(\n\s*\})', re.DOTALL)

# 3. Tu lógica de intercambio adaptada para ser 100% segura en hilos de Android
nueva_logica = """
                    val response = conn.inputStream.bufferedReader().readText()
                    val json = JSONObject(response)
                    val fen = json.optString("fen", "")

                    if (esFenValido64(fen)) {
                        updateDebug("✅ FEN ok")
                        
                        // 1. Mostrar ChessDb inmediatamente
                        val chessdbData = json.optString("chessdb", "Sin datos")
                        updateDebug("📡 ChessDb: $chessdbData")
                        
                        // 2. Temporizador de 10 segundos (Usando Handler para evitar nulos)
                        Handler(Looper.getMainLooper()).postDelayed({
                            val stockfishData = json.optString("stockfish", "Esperando...")
                            updateDebug("📡 Stockfish: $stockfishData")
                        }, 10000)
                    } else {
                        updateDebug("❌ FEN CORRUPTO")
                    }"""

# 4. Sustitución y guardado
nuevo_codigo = pattern.sub(r'\1' + nueva_logica + r'\3', code, count=1)

with open(file_path, "w") as f: f.write(nuevo_codigo)
print("✅ CIRUGÍA APLICADA: Temporizador de 10s y parseo JSON integrados.")
