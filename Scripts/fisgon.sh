#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

cd "$HOME/chesz" || exit 1

TS="$(date +%Y%m%d_%H%M%S)"
OUT_DIR="$HOME/storage/downloads"
OUT_FILE="$OUT_DIR/fisgon_${TS}.txt"
LATEST="$OUT_DIR/fisgon_latest.txt"

mkdir -p "$OUT_DIR"

echo "🔍 Fisgón: generando reporte..."
echo "🕒 Timestamp: $TS"
echo "📄 Archivo: $OUT_FILE"
echo "-------------------------------------------" > "$OUT_FILE"
echo "FISGON_TS=$TS" >> "$OUT_FILE"
echo "PWD=$(pwd)" >> "$OUT_FILE"
echo "BRANCH=$(git branch --show-current 2>/dev/null || true)" >> "$OUT_FILE"
echo "SHA=$(git rev-parse --short HEAD 2>/dev/null || true)" >> "$OUT_FILE"
echo "-------------------------------------------" >> "$OUT_FILE"
echo >> "$OUT_FILE"

find "$HOME/chesz" -maxdepth 12 -not -path '*/.*' -type f \( -name "*.kt" -o -name "*.xml" -o -name "build.gradle" -o -name "settings.gradle" \) \
  -print0 | sort -z | while IFS= read -r -d '' f; do
    echo "==============================" >> "$OUT_FILE"
    echo "FILE: $f" >> "$OUT_FILE"
    echo "==============================" >> "$OUT_FILE"
    nl -ba "$f" >> "$OUT_FILE" || true
    echo -e "\n---\n" >> "$OUT_FILE"
  done

cp -f "$OUT_FILE" "$LATEST"
echo "✅ Fisgón listo:"
echo "   $OUT_FILE"
echo "   (latest) $LATEST"
