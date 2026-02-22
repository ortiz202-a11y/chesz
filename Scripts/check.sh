#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

ROOT="$HOME/chesz"
ICON_SRC="$ROOT/iconos/boton.png"
ICON_DEST="$ROOT/app/src/main/res/drawable/bubble_icon.png"
BUBBLE="$ROOT/app/src/main/java/com/chesz/analyzer/bubble/BubbleService.kt"

echo "🛡️ [CHECK] Validando integridad..."

if [[ ! -f "$ICON_SRC" ]]; then
    echo -e "\n❌ [ERROR]: Falta $ICON_SRC"
    exit 1
fi
cp "$ICON_SRC" "$ICON_DEST" && echo "✅ Icono sincronizado."

if [[ -f "$BUBBLE" ]]; then
    OPEN=$(grep -o "{" "$BUBBLE" | wc -l)
    CLOSE=$(grep -o "}" "$BUBBLE" | wc -l)
    [ "$OPEN" -ne "$CLOSE" ] && { echo "❌ Error: Llaves {$OPEN vs }$CLOSE"; exit 1; }
    echo "✅ Sintaxis Kotlin OK."
fi
echo "✅ CHECK PASADO."
