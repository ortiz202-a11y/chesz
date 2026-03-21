import os, re

path = "app/src/main/java/com/chesz/floating/BubbleService.kt"
with open(path, "r") as f:
    code = f.read()

# 1. Secuestrar el Title al entrar al Modo Dios
old_enter = """                        if (this::devBar.isInitialized) devBar.visibility = View.VISIBLE
                        updateDebug(">_ MODO DESARROLLADOR ACTIVO.\\n>_ ESPERANDO ORDENES...")"""
new_enter = """                        if (this::devBar.isInitialized) devBar.visibility = View.VISIBLE
                        root.post { fenTitle.text = ">_ MODE DEBUG" }
                        updateDebug(">_ ESPERANDO ORDENES...")"""
if old_enter in code:
    code = code.replace(old_enter, new_enter)

# 2. Limpieza total al salir del Modo Dios (Wipe absoluto)
old_exit = """        isDeveloperMode = false
        if (this::devBar.isInitialized) devBar.visibility = View.GONE
        updatePermUi() // Restaurar permiso"""
new_exit = """        isDeveloperMode = false
        if (this::devBar.isInitialized) devBar.visibility = View.GONE
        lastFen = null // Borrar memoria del ultimo escaneo
        updatePermUi() // Restaurar boton de permiso si falta el token
        root.post { fenTitle.text = "" } // Dejar el titulo en blanco"""
if old_exit in code:
    code = code.replace(old_exit, new_exit)

# 3. Ajustar el margen inferior de la barra de botones a 1dp y mover a la derecha
code = re.sub(
    r'col\.addView\(devBar,\s*LinearLayout\.LayoutParams\(-1,\s*-2\)(?:\.apply\s*\{[^}]+\})?\)',
    r'col.addView(devBar, LinearLayout.LayoutParams(-1, -2).apply { leftMargin = dp(45); bottomMargin = dp(1) })',
    code
)

with open(path, "w") as f:
    f.write(code)

print("CIRUGIA APLICADA: LIMPIEZA TOTAL AL SALIR Y MARGEN AJUSTADO A 1DP.")
