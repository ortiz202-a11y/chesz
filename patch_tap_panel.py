#!/usr/bin/env python3
import re, time
from pathlib import Path

ROOT = Path.home() / "chesz"
F = ROOT / "app/src/main/java/com/chesz/floating/BubbleService.kt"
if not F.exists():
    raise SystemExit(f"NO existe: {F}")

src = F.read_text(encoding="utf-8")

# Backup fuera del repo
ts = time.strftime("%Y%m%d_%H%M%S")
bu_dir = ROOT / "BU" / f"patch_backup_{ts}"
bu_dir.mkdir(parents=True, exist_ok=True)
(F.parent / ("BubbleService.kt")).replace(F)  # no-op safety (keeps path)
(bu_dir / "BubbleService.kt").write_text(src, encoding="utf-8")

# 1) En ACTION_MOVE: después de updateViewLayout(bubbleRoot, bubbleLp) llamar updatePanelPositionIfShown()
# (solo si panelShown)
pattern_move = r"""(?s)(MotionEvent\.ACTION_MOVE\s*->\s*\{.*?runCatching\s*\{\s*wm\.updateViewLayout\(bubbleRoot,\s*bubbleLp\)\s*\}\s*)(.*?\n\s*if\s*\(dragging\)\s*\{)"""
m = re.search(pattern_move, src)
if not m:
    raise SystemExit("No pude localizar el bloque ACTION_MOVE con updateViewLayout(bubbleRoot, bubbleLp).")

insert_move = m.group(1) + "\n\n          // Si el panel está abierto, que siga al botón durante el drag\n          if (panelShown) updatePanelPositionIfShown()\n\n          " + m.group(2)
src = re.sub(pattern_move, insert_move, src, count=1)

# 2) En ACTION_UP/ACTION_CANCEL: si NO fue dragging -> togglePanel()
pattern_up = r"""(?s)(MotionEvent\.ACTION_UP,\s*MotionEvent\.ACTION_CANCEL\s*->\s*\{\s*)(.*?)(\s*dragging\s*=\s*false\s*\s*true\s*\})"""
m = re.search(pattern_up, src)
if not m:
    raise SystemExit("No pude localizar el bloque ACTION_UP/ACTION_CANCEL.")

body = m.group(2)

# Ya tiene: if (dragging) { ... } dragging=false true
# Le agregamos el else { togglePanel() }
if "togglePanel()" not in body:
    body_new = re.sub(
        r"""(?s)if\s*\(dragging\)\s*\{(.*?)\}\s*""",
        lambda mm: f"if (dragging) {{{mm.group(1)}}} else {{\n            // TAP (sin drag): alterna Estado A/B\n            togglePanel()\n          }}\n          ",
        body,
        count=1
    )
else:
    body_new = body

src = src[:m.start(2)] + body_new + src[m.end(2):]

# Sanidad simple: llaves balanceadas (mismo criterio que CHECK)
if src.count("{") != src.count("}"):
    raise SystemExit("ABORTADO: llaves desbalanceadas tras el patch.")

F.write_text(src, encoding="utf-8")
print("✅ Patch OK:", F)
print("🧷 Backup fuera del repo:", bu_dir / "BubbleService.kt")
