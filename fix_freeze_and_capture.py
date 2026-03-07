import re

file_path = 'app/src/main/java/com/chesz/floating/BubbleService.kt'
with open(file_path, 'r') as f:
    content = f.read()

# 1. Limpieza de hardware en hilo secundario (Desbloqueo de UI)
safe_reset = """    Thread {
        runCatching { activeMediaProjection?.stop() }
        activeMediaProjection = null
    }.start()"""

# Reemplazo de la lógica vieja por el hilo nuevo
content = re.sub(r'runCatching \{\s*runCatching \{.*?activeMediaProjection = null\s*\}', safe_reset, content, flags=re.DOTALL)

# 2. Validación de Ruta Segura
dir_fix = """          val dir = getExternalFilesDir(null)
          if (dir == null) {
              updateDebug("Err: No Storage")
              return@postDelayed
          }
          if (!dir.exists()) dir.mkdirs()"""

if 'val dir = getExternalFilesDir(null)' not in content:
    content = re.sub(r'val dir = getExternalFilesDir\(null\).*?if \(dir != null\) \{', dir_fix + '\n          if (dir != null) {', content, flags=re.DOTALL)

with open(file_path, 'w') as f:
    f.write(content)
print("Soberanía: Hilos asíncronos inyectados. ADN reparado.")
