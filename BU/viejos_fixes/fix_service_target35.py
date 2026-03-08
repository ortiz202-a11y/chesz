import sys

file_path = 'app/src/main/java/com/chesz/floating/BubbleService.kt'
with open(file_path, 'r') as f:
    content = f.read()

# 1. Cambiar el arranque inicial a 'specialUse' para evitar el crash
old_start_fgs = """    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
      startForeground(1, notif, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
    } else {
      startForeground(1, notif)
    }"""

new_start_fgs = """    if (android.os.Build.VERSION.SDK_INT >= 34) {
      startForeground(1, notif, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
    } else {
      startForeground(1, notif)
    }"""
content = content.replace(old_start_fgs, new_start_fgs)

# 2. Inyectar la función de elevación de privilegio
if "fun upgradeToMediaProjection" not in content:
    upgrade_func = """
  private fun upgradeToMediaProjection() {
    val channelId = "chesz_channel"
    val notif = android.app.Notification.Builder(this, channelId)
      .setContentTitle("Chesz")
      .setContentText("Captura de pantalla habilitada")
      .setSmallIcon(R.drawable.ic_check_green)
      .build()
    if (android.os.Build.VERSION.SDK_INT >= 29) {
      startForeground(1, notif, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
    }
  }
"""
    # Insertar antes de updatePermUi
    content = content.replace("  private fun updatePermUi() {", upgrade_func + "\n  private fun updatePermUi() {")

# 3. Llamar a la elevación cuando se recibe el resultado del permiso
content = content.replace(
    "mpData = intent.getParcelableExtra(\"data\")\n      updatePermUi()",
    "mpData = intent.getParcelableExtra(\"data\")\n      runCatching { upgradeToMediaProjection() }\n      updatePermUi()"
)

with open(file_path, 'w') as f:
    f.write(content)
print("Integridad 1:1: Activación diferida de MediaProjection inyectada.")
