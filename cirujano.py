import os

path = "/data/data/com.termux/files/home/chesz/app/src/main/java/com/chesz/floating/BubbleService.kt"

with open(path, "r") as f:
    code = f.read()

# 1. PARCHE DE RED: Absorber texto completo en lugar de esperar salto de linea
old_net = """                    val reader = stream?.bufferedReader()
                    var linea: String?
                    while (reader?.readLine().also { linea = it } != null) {"""

new_net = """                    val respuesta = stream?.bufferedReader()?.use { it.readText() } ?: "{}"
                    if (respuesta.isNotBlank()) {
                        val json = JSONObject(respuesta) //"""

code = code.replace(old_net, new_net)

# 2. PARCHE DE VISION: Restaurar la limpieza de Stride (Padding) antes del recorte Photopea
old_vis = """                            bitmap.copyPixelsFromBuffer(buffer)

                            // --- INYECCION: VISION DE IA (Recorte y Escala de Grises) ---
                            // 1. Recorte Definitivo Photopea (Coord: 0, 458, 720x720)
                            val boardX = 0
                            val boardY = 458
                            val boardSize = 720

                            val safeCropW = if (boardX + boardSize > bitmap.width) bitmap.width - boardX else boardSize
                            val safeCropH = if (boardY + boardSize > bitmap.height) bitmap.height - boardY else boardSize

                            val recortado = android.graphics.Bitmap.createBitmap(
                                bitmap,
                                boardX, boardY, safeCropW, safeCropH
                            )
                            bitmap.recycle() // Liberar pantalla completa"""

new_vis = """                            bitmap.copyPixelsFromBuffer(buffer)
                            val croppedLimpio = android.graphics.Bitmap.createBitmap(bitmap, 0, 0, safeW, safeH)
                            bitmap.recycle()

                            // --- INYECCION: VISION DE IA (Recorte y Escala de Grises) ---
                            // 1. Recorte Definitivo Photopea (Coord: 0, 458, 720x720)
                            val boardX = 0
                            val boardY = 458
                            val boardSize = 720

                            val safeCropW = if (boardX + boardSize > croppedLimpio.width) croppedLimpio.width - boardX else boardSize
                            val safeCropH = if (boardY + boardSize > croppedLimpio.height) croppedLimpio.height - boardY else boardSize

                            val recortado = android.graphics.Bitmap.createBitmap(
                                croppedLimpio,
                                boardX, boardY, safeCropW, safeCropH
                            )
                            croppedLimpio.recycle() // Liberar pantalla completa"""

code = code.replace(old_vis, new_vis)

with open(path, "w") as f:
    f.write(code)

print("CIRUGIA DE RED Y VISION COMPLETADA.")
