#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

REPORT_NAME="fisgon.txt"
DEST_DIR="$HOME/storage/shared/Download"
OUT="$DEST_DIR/$REPORT_NAME"

mkdir -p "$DEST_DIR"

echo "🔍 El Fisgón está recorriendo el proyecto..."
find "$HOME/chesz" -maxdepth 10 -not -path '*/.*' -type f \( -name "*.kt" -o -name "*.xml" -o -name "build.gradle" -o -name "AndroidManifest.xml" \) \
  -exec echo "FILE: {}" \; \
  -exec cat {} \; \
  -exec printf "\n---\n\n" \; > "$OUT"

echo "✅ Reporte generado en: $OUT"
echo "Contenido del reporte (primeras líneas):"
head -n 40 "$OUT" || true
