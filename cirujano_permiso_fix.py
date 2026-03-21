import os

path = "app/src/main/java/com/chesz/floating/BubbleService.kt"
with open(path, "r") as f:
    code = f.read()

target = """                        devRunnable = Runnable {
                            isDeveloperMode = true
                            flashBubbleRed() // Feedback visual"""

replacement = """                        devRunnable = Runnable {
                            isDeveloperMode = true
                            updatePermUi() // Destruir boton de permiso instantaneamente
                            flashBubbleRed() // Feedback visual"""

if target in code:
    code = code.replace(target, replacement)
    with open(path, "w") as f:
        f.write(code)
    print("PARCHE APLICADO: ACTUALIZACION DE UI FORZADA AL ENTRAR A MODO DIOS.")
else:
    print("ERROR: Bloque no encontrado.")

