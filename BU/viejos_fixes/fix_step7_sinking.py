import sys
import os

file_path = 'app/src/main/java/com/chesz/floating/BubbleService.kt'

with open(file_path, 'r') as f:
    lines = f.readlines()

new_lines = []
skip_next = 0

# Animación auxiliar
animate_down = "view.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).start()"
animate_up = "view.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start()"

for i, line in enumerate(lines):
    if skip_next > 0:
        skip_next -= 1
        continue

    # 1. Aplicar a la Burbuja Principal (root) en createRootOverlay
    if "root.setOnTouchListener {" in line:
        new_lines.append(line)
        new_lines.append(f"          view -> {animate_down}\n")
        new_lines.append(f"          root.setOnTouchListener {{ view, event ->\n")
        new_lines.append(f"            if (event.action == android.view.MotionEvent.ACTION_UP || event.action == android.view.MotionEvent.ACTION_CANCEL) {{ {animate_up} }}\n")
        skip_next = 1

    # 2. Aplicar al botón Pedir Permiso (permBar) en buildPanel
    elif "permBar.setOnClickListener {" in line:
        new_lines.append(f"    permBar.setOnTouchListener {{ view, event ->\n")
        new_lines.append(f"      if (event.action == android.view.MotionEvent.ACTION_DOWN) {{ {animate_down} }}\n")
        new_lines.append(f"      if (event.action == android.view.MotionEvent.ACTION_UP || event.action == android.view.MotionEvent.ACTION_CANCEL) {{ {animate_up} }}\n")
        new_lines.append(f"      false // Permite que setOnClickListener siga funcionando\n")
        new_lines.append(f"    }}\n")
        new_lines.append(line)

    # 3. Aplicar al botón Close (close) en buildPanel
    elif "close.setOnClickListener {" in line:
        new_lines.append(f"    close.setOnTouchListener {{ view, event ->\n")
        new_lines.append(f"      if (event.action == android.view.MotionEvent.ACTION_DOWN) {{ {animate_down} }}\n")
        new_lines.append(f"      if (event.action == android.view.MotionEvent.ACTION_UP || event.action == android.view.MotionEvent.ACTION_CANCEL) {{ {animate_up} }}\n")
        new_lines.append(f"      false\n")
        new_lines.append(f"    }}\n")
        new_lines.append(line)

    else:
        new_lines.append(line)

with open(file_path, 'w') as f:
    f.writelines(new_lines)

print("Integridad 1:1: Efecto hundimiento (Sinking) inyectado en 3 botones.")
