import os

path = "app/src/main/java/com/chesz/floating/BubbleService.kt"
with open(path, "r") as f:
    code = f.read()

old_block = """            maxLines = 2
            gravity = android.view.Gravity.CENTER
            setLineSpacing(0f, 0.9f)
            setPadding(dp(5), dp(5), dp(21), 0)"""

new_block = """            maxLines = 2
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                setAutoSizeTextTypeUniformWithConfiguration(7, 11, 1, android.util.TypedValue.COMPLEX_UNIT_SP)
            }
            gravity = android.view.Gravity.CENTER
            setLineSpacing(0f, 0.9f)
            setPadding(dp(3), dp(4), dp(19), 0)"""

if old_block in code:
    code = code.replace(old_block, new_block)
    with open(path, "w") as f:
        f.write(code)
    print("CIRUGIA APLICADA: PADDING (3, 4, 19, 0) Y AUTO-SIZE INYECTADO.")
else:
    print("ERROR: No se encontro el bloque exacto en BubbleService.kt.")

