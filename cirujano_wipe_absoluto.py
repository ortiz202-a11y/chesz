import os

path = "app/src/main/java/com/chesz/floating/BubbleService.kt"
with open(path, "r") as f:
    code = f.read()

# 1. ENTRADA: Titulo Modo Dios y Consola en Silencio Absoluto
old_enter = """                        if (this::devBar.isInitialized) devBar.visibility = View.VISIBLE
                        root.post { fenTitle.text = ">_ MODE DEBUG" }
                        updateDebug(">_ ESPERANDO ORDENES...")"""

new_enter = """                        if (this::devBar.isInitialized) devBar.visibility = View.VISIBLE
                        root.post { 
                            fenTitle.text = ">_ MODE DEBUG" 
                            debugText.text = "" // Consola en silencio
                        }"""

if old_enter in code:
    code = code.replace(old_enter, new_enter)

# 2. SALIDA: Destruccion de Memoria (lastFen) y Limpieza de Consola
old_exit = """        isDeveloperMode = false
        if (this::devBar.isInitialized) devBar.visibility = View.GONE
        updatePermUi() // Restaurar boton de permiso si falta el token
        root.post { fenTitle.text = "" } // Dejar el titulo en blanco"""

new_exit = """        isDeveloperMode = false
        if (this::devBar.isInitialized) devBar.visibility = View.GONE
        lastFen = null // Destruir memoria de la ultima jugada
        updatePermUi() // Restaurar boton de permiso
        root.post { 
            fenTitle.text = "" // Borrar titulo
            debugText.text = "" // Borrar fantasmas de la consola
            debugText.visibility = View.GONE // Ocultar consola
        }"""

if old_exit in code:
    code = code.replace(old_exit, new_exit)

with open(path, "w") as f:
    f.write(code)

print("WIPE ABSOLUTO APLICADO: ENTRADA SILENCIOSA Y DESTRUCCION DE MEMORIA AL SALIR.")
