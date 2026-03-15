import os

filepath = os.path.expanduser("~/chesz/app/src/main/java/com/chesz/floating/BubbleService.kt")
with open(filepath, "r") as f:
    code = f.read()

# 1. Tap block
orig_tap = "                    } else {\n                        togglePanel()\n                    }"
new_tap = "                    } else {\n                        val dist = kotlin.math.hypot(e.rawX - bubbleCentex(), e.rawY - bubbleCenterY())\n                        if (dist <= dp(30).toFloat()) togglePanel()\n                    }"
code = code.replace(orig_tap, new_tap)

# 2. API block
orig_api = "                if (conn.responseCode == 200) {\n                    val response = conn.inputStream.bufferedReader().readText()"
new_api = "                val rC = conn.responseCode\n                val st = if (rC in 200..299) conn.inputStream else conn.errorStream\n                val raw = st?.bufferedReader()?.readText() ?: \"NA\"\n                updateDebug(\"API [\" + rC + \"]: \" + raw)\n                if (rC == 200) {\n                    val response = raw"
code = code.replace(orig_api, new_api)

with open(filepath, "w") as f:
    f.write(code)

print("✅ Inyeccion blindada 100% completada.")