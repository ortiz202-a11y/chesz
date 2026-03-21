import os, re

path = "app/src/main/java/com/chesz/floating/BubbleService.kt"
with open(path, "r") as f:
    code = f.read()

# Buscar 'isDeveloperMode = true' seguido de 'flashBubbleRed()' e inyectar updatePermUi() en medio
patron = r'(isDeveloperMode\s*=\s*true\s*\n)(\s*)(flashBubbleRed\(\))'
reemplazo = r'\1\2updatePermUi() // Destruir boton de permiso instantaneamente\n\2\3'

if re.search(patron, code):
    code = re.sub(patron, reemplazo, code)
    with open(path, "w") as f:
        f.write(code)
    print("PARCHE REGEX APLICADO: ACTUALIZACION DE UI FORZADA AL ENTRAR A MODO DIOS.")
else:
    print("ERROR: Expresion regular no encontro el bloque de arranque del Modo Dios.")

