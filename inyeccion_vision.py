import os

filepath = os.path.expanduser("~/chesz/app/src/main/java/com/chesz/floating/BubbleService.kt")
with open(filepath, "r") as f: code = f.read()

bloque_original = """                            bitmap.copyPixelsFromBuffer(buffer)
                            val cropped = android.graphics.Bitmap.createBitmap(bitmap, 0, 0, safeW, safeH)
                            bitmap.recycle()"""

bloque_nuevo = """                            bitmap.copyPixelsFromBuffer(buffer)
                            
                            // --- INYECCION: VISION DE IA (Recorte y Escala de Grises) ---
                            // 1. Recorte Milimetrico del Tablero (Coord: 0, 463, 708x708)
                            val boardX = 0
                            val boardY = 463
                            val boardSize = 708
                            val recortado = android.graphics.Bitmap.createBitmap(bitmap, boardX, boardY, boardSize, boardSize)
                            bitmap.recycle() // Liberar pantalla completa
                            
                            // 2. Conversion a Escala de Grises (Requisito FrozenGraph 2019)
                            val cropped = android.graphics.Bitmap.createBitmap(boardSize, boardSize, android.graphics.Bitmap.Config.ARGB_8888)
                            val canvas = android.graphics.Canvas(cropped)
                            val paint = android.graphics.Paint()
                            val colorMatrix = android.graphics.ColorMatrix()
                            colorMatrix.setSaturation(0f) // Matar color
                            val filter = android.graphics.ColorMatrixColorFilter(colorMatrix)
                            paint.colorFilter = filter
                            canvas.drawBitmap(recortado, 0f, 0f, paint)
                            recortado.recycle() // Liberar recorte a color
                            // -----------------------------------------------------------"""

if bloque_original in code:
    codigo_reparado = code.replace(bloque_original, bloque_nuevo)
    with open(filepath, "w") as f: f.write(codigo_reparado)
    print("✅ Inyeccion exitosa en BubbleService.kt: Recorte y Escala de Grises.")
else:
    print("❌ Error: Bloque original no encontrado en BubbleService.kt.")
