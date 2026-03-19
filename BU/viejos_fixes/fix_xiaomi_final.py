import sys

file_path = 'app/src/main/java/com/chesz/floating/BubbleService.kt'
with open(file_path, 'r') as f:
    content = f.read()

# Inyectar delay de 500ms para estabilizar la elevación de permisos en MIUI/HyperOS
old_call = 'runCatching { upgradeToMediaProjection() }'
new_call = 'root.postDelayed({ runCatching { upgradeToMediaProjection() } }, 500)'

if old_call in content:
    content = content.replace(old_call, new_call)
    with open(file_path, 'w') as f:
        f.write(content)
    print("Integridad 1:1: Latencia anti-crash para Xiaomi inyectada con éxito.")
else:
    print("Error: El ADN ya contiene el parche o la función cambió de lugar.")
