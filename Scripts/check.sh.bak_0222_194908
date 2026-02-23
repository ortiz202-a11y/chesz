#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT="$HOME/chesz"
ICON_SRC="$ROOT/iconos/boton.png"
# El destino se mantiene, pero solo si existe la carpeta drawable
mkdir -p "$ROOT/app/src/main/res/drawable"
ICON_DEST="$ROOT/app/src/main/res/drawable/bubble_icon.png"

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

echo "✅ CHECK PASADO: Todo en orden para compilar."
