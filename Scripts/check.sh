#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT="$HOME/chesz"
ICON_SRC="$ROOT/iconos/boton.png"
# El destino se mantiene, pero solo si existe la carpeta drawable
mkdir -p "$ROOT/app/src/main/res/drawable"
ICON_DEST="$ROOT/app/src/main/res/drawable/bubble_icon.png"

# === PROTECCION: prohibir backups/temporales dentro de res/ ===
RES_DIR="$ROOT/app/src/main/res"
if find "$RES_DIR" -type f \( -name "*.bak" -o -name "*.bak_*" -o -name "*.xml.bak*" -o -name "*~" \) -print -quit | grep -q .; then
  echo "❌ [CHECK] Basura detectada dentro de res/ (*.bak*, *~). Borra antes de commit/build:"
  find "$RES_DIR" -type f \( -name "*.bak" -o -name "*.bak_*" -o -name "*.xml.bak*" -o -name "*~" \) -print
  exit 1
fi
# === FIN PROTECCION ===


echo "🛡️ [CHECK] Validando integridad..."

if [[ ! -f "$ICON_SRC" ]]; then
    echo -e "\n❌ [ERROR]: No se encuentra el icono en $ICON_SRC"
    exit 1
fi

if [ ! -f "$ICON_SRC" ]; then echo "❌ ERROR: Icono no encontrado" && exit 1; else cp "$ICON_SRC" "$ICON_DEST" && echo "✅ Icono sincronizado"; fi

# Buscamos cualquier archivo .kt para validar llaves sin importar la carpeta
KOTLIN_FILES=$(find "$ROOT/app/src/main/java" -name "*.kt")
for f in $KOTLIN_FILES; do
    OPEN=$(grep -o "{" "$f" | wc -l)
    CLOSE=$(grep -o "}" "$f" | wc -l)
    if [ "$OPEN" -ne "$CLOSE" ]; then
        echo "❌ Error en $f: Llaves {$OPEN vs }$CLOSE"
        exit 1
    fi
done


# --- PROTECCIÓN: no permitir backups dentro de res/ ---
BAD_RES=$(find "$ROOT/app/src/main/res" -type f \( -name "*.bak" -o -name "*.bak_*" -o -name "*.xml.bak*" -o -name "*~" \) 2>/dev/null || true)
if [[ -n "${BAD_RES:-}" ]]; then
  echo "❌ [CHECK] Basura detectada dentro de app/src/main/res (esto rompe el build):"
  echo "$BAD_RES"
  exit 1
fi
# --- FIN PROTECCIÓN ---

echo "✅ CHECK PASADO: Todo en orden para compilar."

# ===== BLOQUEO_BAK_RES: no permitir basura en res/ =====
if find "$ROOT/app/src/main/res" -type f -name "*bak*" | grep -q .; then
  echo -e "\n❌ [ERROR]: Se detectaron archivos *bak* dentro de app/src/main/res (esto rompe AAPT)."
  echo "Lista:"
  find "$ROOT/app/src/main/res" -type f -name "*bak*" -print
  exit 1
fi
