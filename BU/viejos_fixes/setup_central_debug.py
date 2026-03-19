import re

file_path = 'app/src/main/java/com/chesz/floating/BubbleService.kt'
with open(file_path, 'r') as f:
    content = f.read()

# 1. Declarar la nueva referencia de texto en la clase
if 'private lateinit var debugText: TextView' not in content:
    content = content.replace('private lateinit var permText: TextView', 
                              'private lateinit var permText: TextView\n  private lateinit var debugText: TextView')

# 2. Crear el TextView dentro del buildPanel (en el área central)
debug_init = """    debugText = TextView(this).apply {
      setTextColor(0xFFD1D1D1.toInt())
      textSize = 10f
      gravity = android.view.Gravity.CENTER
      visibility = android.view.View.GONE
    }
    col.addView(debugText)"""

if 'debugText = TextView(this)' not in content:
    content = content.replace('col.addView(title)', 'col.addView(title)\n' + debug_init)

# 3. Función para actualizar el monitoreo en el área central
update_fn = """  private fun updateDebug(msg: String) {
    root.post {
      debugText.visibility = android.view.View.VISIBLE
      debugText.text = msg
    }
  }"""

if 'private fun updateDebug' not in content:
    content = content.replace('private fun takeScreenshotOnce()', update_fn + '\n\n  private fun takeScreenshotOnce()')

with open(file_path, 'w') as f:
    f.write(content)
print("Soberanía: Área central preparada para telemetría de usuario.")
