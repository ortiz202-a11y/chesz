from pathlib import Path
import re, sys

p = Path("app/src/main/java/com/chesz/floating/BubbleService.kt")
s = p.read_text(encoding="utf-8")

orig = s

# 1) ACTION_DOWN: NO mostrar kill aquí (solo al iniciar drag real)
s = re.sub(r'^\s*showKill\(true\)\s*\n', '', s, flags=re.M)

# 2) ACTION_MOVE: activar dragging con umbral + mostrar kill SOLO cuando empieza el drag
# Reemplaza la línea "if (!dragging && (...)) dragging = true" por bloque con showKill(true)
s = re.sub(
    r'if\s*\(\s*!dragging\s*&&\s*\(kotlin\.math\.abs\(dx\)\s*\+\s*kotlin\.math\.abs\(dy\)\s*>\s*dp\(6\)\)\s*\)\s*dragging\s*=\s*true',
    'if (!dragging && (kotlin.math.abs(dx) + kotlin.math.abs(dy) > dp(6))) {\n'
    '              dragging = true\n'
    '              showKill(true)\n'
    '            }',
    s
)

# 3) ACTION_MOVE: hover usando rawX/rawY (coordenadas del dedo), no bubbleCenterX/Y
s = re.sub(
    r'val over = isOverKillCenter\(bubbleCenterX\(\), bubbleCenterY\(\)\)',
    'val over = dragging && isOverKillCenter(e.rawX, e.rawY)',
    s
)

# 4) ACTION_UP: shouldKill usando rawX/rawY + quitar vibración (ya no la quieres)
s = re.sub(
    r'val shouldKill = dragging && isOverKillCenter\(bubbleCenterX\(\), bubbleCenterY\(\)\)',
    'val shouldKill = dragging && isOverKillCenter(e.rawX, e.rawY)',
    s
)
s = re.sub(r'^\s*vibrateKill\(\)\s*\n', '', s, flags=re.M)

# 5) Hover: hacerlo OBVIO (más grande)
s = re.sub(
    r'val target = if \(hover\) 1\.25f else 1\.0f',
    'val target = if (hover) 1.60f else 1.0f',
    s
)

# 6) Radio de detección: ampliar (más tolerancia)
s = re.sub(
    r'val r = \(killLp\.width / 2f\) \* 1\.(05|6)f',
    'val r = (killLp.width / 2f) * 1.35f',
    s
)

if s == orig:
    print("⚠️ No hubo cambios (los patrones no matchearon). Revisa que el archivo sea el esperado.")
    sys.exit(2)

# sanity: no dejar backups dentro del repo (tu CHECK odia *.bak* en res/, pero aquí es .kt: ok)
p.write_text(s, encoding="utf-8")
print(f"✅ Patch aplicado: {p}")
