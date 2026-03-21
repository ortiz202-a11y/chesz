import os, re

path = "app/src/main/java/com/chesz/floating/BubbleService.kt"
with open(path, "r") as f:
    code = f.read()

# 1. Ocultar Boton Permitir estrictamente si esta en Modo Dios
new_perm = """    private fun updatePermUi() {
        if (!this::permBar.isInitialized) return
        permBar.visibility = if (isDeveloperMode) View.GONE else if (mpData != null) View.GONE else View.VISIBLE
    }"""
code = re.sub(r'    private fun updatePermUi\(\) \{.*?\n    \}', new_perm, code, flags=re.DOTALL)

# 2. Liberar Consola: Maximo 15 lineas, AutoSize de 13 a 6
new_debug = """    private fun updateDebug(msg: String) {
        root.post {
            debugText.visibility = View.VISIBLE
            debugText.maxLines = 15
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                debugText.setAutoSizeTextTypeUniformWithConfiguration(6, 13, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
            }
            debugText.text = msg
        }
    }"""
code = re.sub(r'    private fun updateDebug\(msg: String\) \{.*?\n    \}', new_debug, code, flags=re.DOTALL)

# 3. Empujar botones Dev 45dp a la derecha para esquivar la burbuja
code = re.sub(
    r'col\.addView\(devBar,\s*LinearLayout\.LayoutParams\(-1,\s*-2\)\)(?:\.apply\s*\{[^}]+\})?',
    r'col.addView(devBar, LinearLayout.LayoutParams(-1, -2).apply { leftMargin = dp(45); bottomMargin = dp(5) })',
    code
)

with open(path, "w") as f:
    f.write(code)

print("MODO DIOS 100% ESTERILIZADO: PERMISO OCULTO, TEXTO AUTO-AJUSTABLE (13->6) Y BOTONES ESQUIVANDO BURBUJA (45dp).")
