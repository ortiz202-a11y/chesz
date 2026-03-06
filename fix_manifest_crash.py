import sys

file_path = 'app/src/main/AndroidManifest.xml'
with open(file_path, 'r') as f:
    content = f.read()

if "android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" not in content:
    content = content.replace(
        '<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>',
        '<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>\n    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION"/>'
    )

if 'android:foregroundServiceType="mediaProjection"' not in content:
    content = content.replace(
        '<service\n            android:name=".floating.BubbleService"\n            android:enabled="true"',
        '<service\n            android:name=".floating.BubbleService"\n            android:foregroundServiceType="mediaProjection"\n            android:enabled="true"'
    )

with open(file_path, 'w') as f:
    f.write(content)
print("Integridad 1:1: Manifiesto preparado para MediaProjection.")
