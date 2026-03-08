import sys

file_path = 'app/src/main/java/com/chesz/floating/BubbleService.kt'
with open(file_path, 'r') as f:
    lines = f.readlines()

# 1. SANEAMIENTO DE COORDENADAS (ELIMINAR BRINCO)
# [span_0](start_span)[span_1](start_span)Basado en el Fisgón L275-276[span_0](end_span)[span_1](end_span)
lines[274] = "    // rootLp.x = dp(60) // ELIMINADO: Evita brinco\n"
lines[275] = "    // rootLp.y = dp(180) // ELIMINADO: Mantiene posición\n"

# 2. RECENTRAR KILL AREA
# [span_2](start_span)[span_3](start_span)Basado en el Fisgón L597[span_2](end_span)[span_3](end_span)
lines[596] = "      x = 0 // CORREGIDO: Centro matemático\n"

# 3. BLINDAJE DE CAPTURA (CALLBACK OBLIGATORIO)
# Inyección antes de createVirtualDisplay L734
callback_injection = "    mp.registerCallback(object : android.media.projection.MediaProjection.Callback() {}, null)\n"
lines.insert(733, callback_injection)

# 4. OPTIMIZACIÓN DE UI (PERSISTENCIA)
for i, line in enumerate(lines):
    if "permBar.visibility = if (ok || mpData != null)" in line:
        lines[i] = "    permBar.visibility = if (mpData != null) View.GONE else View.VISIBLE\n"

with open(file_path, 'w') as f:
    f.writelines(lines)
print("Soberanía: ADN Saneado correctamente.")
