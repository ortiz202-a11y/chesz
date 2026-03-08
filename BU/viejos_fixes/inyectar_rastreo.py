import re

file_path = 'app/src/main/java/com/chesz/floating/BubbleService.kt'
with open(file_path, 'r') as f:
    content = f.read()

# 1. Monitoreo de inicialización de MediaProjection
content = content.replace('panelTitle.text = "Sshot/"', 'updateDebug("Step 1: Init...")')
content = content.replace('activeMediaProjection = mgr.getMediaProjection(rc, data)', 
                          'activeMediaProjection = mgr.getMediaProjection(rc, data)\n        updateDebug("Step 1: OK")')

# 2. Monitoreo de creación de Pantalla Virtual (Hardware)
content = content.replace('val vd = mp.createVirtualDisplay', 
                          'updateDebug("Step 2: VirtualDisplay...")\n      val vd = mp.createVirtualDisplay')

# 3. Monitoreo de recepción del Buffer de Imagen
content = content.replace('val image = reader.acquireLatestImage()', 
                          'updateDebug("Step 3: Recibiendo Buffer...")\n            val image = reader.acquireLatestImage()')

# 4. Monitoreo de Proceso de Escritura (Disco)
content = content.replace('val dir = getExternalFilesDir', 
                          'updateDebug("Step 4: Escribiendo PNG...")\n          val dir = getExternalFilesDir')

# 5. Finalización exitosa
content = content.replace('panelTitle.text = "Sshot/"', 'updateDebug("Sshot/ OK")')

# 6. Reporte de error real en el área central
content = content.replace('panelTitle.text = "Sshot/Err"', 'updateDebug("Err: ${it.javaClass.simpleName}")')

with open(file_path, 'w') as f:
    f.write(content)
print("Soberanía: Telemetría inyectada en el área central del panel.")
