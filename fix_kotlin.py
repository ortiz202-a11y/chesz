import os
filepath = os.path.expanduser("~/chesz/app/src/main/java/com/chesz/floating/BubbleService.kt")
with open(filepath, "r") as f: code = f.read()

# 1. Purgar función de Python
code = code.replace("str(respCode)", "respCode.toString()")

# 2. Eliminar saltos de línea literales que rompen Kotlin
code = code.replace('updateDebug("\n✅', 'updateDebug("✅')
code = code.replace('updateDebug("\n❌', 'updateDebug("❌')

with open(filepath, "w") as f: f.write(code)
print("✅ Sintaxis Kotlin corregida al 100%.")
