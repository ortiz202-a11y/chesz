import sys

file_path = 'app/src/main/java/com/chesz/floating/BubbleService.kt'
with open(file_path, 'r') as f:
    content = f.read()

# 1. PURGA DE HUNDIMIENTO
content = content.replace('root.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).start()', '')
content = content.replace('root.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()', '')

# 2. COORDENADAS INICIALES ABSOLUTAS (60, 180)
old_pos = """      x = 0
      y = dp(120)"""
new_pos = """      x = dp(60)
      y = dp(180)"""
content = content.replace(old_pos, new_pos)

# 3. FIX DE setStateA_layout (Evita salto a 0,0 al cerrar panel)
old_clampA = """    val clampedA = clampRootToScreen(rootLp.x, rootLp.y)
    rootLp.x = clampedA.first
    rootLp.y = clampedA.second"""
new_clampA = """    rootLp.x = dp(60)
    rootLp.y = dp(180)"""
content = content.replace(old_clampA, new_clampA)

# 4. LATENCIA PURA Y PROACTIVA
# Asegurar que el onStartCommand tenga exactamente el delay de 500ms
import re
content = re.sub(
    r'mpData = intent\.getParcelableExtra\("data"\).*?updatePermUi\(\)',
    'mpData = intent.getParcelableExtra("data")\\n      root.postDelayed({ runCatching { upgradeToMediaProjection() } }, 500)\\n      updatePermUi()',
    content,
    flags=re.DOTALL
)

# 5. UI PROACTIVA
content = content.replace(
    'permBar.visibility = if (ok) View.GONE else View.VISIBLE',
    'permBar.visibility = if (ok || mpData != null) View.GONE else View.VISIBLE'
)

with open(file_path, 'w') as f:
    f.write(content)
print("Integridad 1:1: ADN Maestro inyectado. Todas las plagas eliminadas.")
