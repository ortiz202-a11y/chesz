import sys

file_path = 'app/src/main/AndroidManifest.xml'
with open(file_path, 'r') as f:
    content = f.read()

# Añadir permiso special_use para el arranque inicial
if "FOREGROUND_SERVICE_SPECIAL_USE" not in content:
    content = content.replace(
        '<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION"/>',
        '<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION"/>\n    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE"/>'
    )

# Cambiar tipo de servicio para permitir múltiples tipos
content = content.replace(
    'android:foregroundServiceType="mediaProjection"',
    'android:foregroundServiceType="mediaProjection|specialUse"'
)

with open(file_path, 'w') as f:
    f.write(content)
print("Integridad 1:1: Manifiesto actualizado para TargetSDK 35.")
