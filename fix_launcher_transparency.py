import sys
import os

file_path = 'app/src/main/AndroidManifest.xml'

with open(file_path, 'r') as f:
    content = f.read()

# Buscamos la MainActivity y cambiamos su tema a uno transparente
old_activity_block = """        <activity
            android:name=".MainActivity"
            android:excludeFromRecents="true"
            android:noHistory="true"
            android:exported="true">"""

new_activity_block = """        <activity
            android:name=".MainActivity"
            android:excludeFromRecents="true"
            android:noHistory="true"
            android:theme="@android:style/Theme.Translucent.NoTitleBar"
            android:exported="true">"""

if old_activity_block in content:
    new_content = content.replace(old_activity_block, new_activity_block)
    with open(file_path, 'w') as f:
        f.write(new_content)
    print("Integridad 1:1: MainActivity ahora es transparente.")
else:
    print("Error: No se pudo localizar el bloque de la MainActivity.")
