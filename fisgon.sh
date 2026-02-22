#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

cd "$HOME/chesz" || exit 1

MODE="${1:-full}"
TS="$(date +%Y%m%d_%H%M%S)"
OUT_DIR="$HOME/storage/downloads"
OUT_FILE="$OUT_DIR/fisgon_${MODE}_${TS}.txt"
LATEST="$OUT_DIR/fisgon_latest.txt"

mkdir -p "$OUT_DIR"

echo "🔍 [FISGÓN] Generando reporte..."

{
  echo "--- FISGON REPORT ---"
  echo "SHA: $(git rev-parse --short HEAD 2>/dev/null || echo 'N/A')"
  echo "---------------------"
} > "$OUT_FILE"

# Lógica de escaneo (Mantenemos tu find original aquí)
find . -maxdepth 10 -not -path '*/.*' -type f \( -name "*.kt" -o -name "*.xml" -o -name "*.gradle" -o -name "*.sh" \) -exec echo "FILE: {}" \; -exec nl -ba {} \; >> "$OUT_FILE" 2>/dev/null

cp -f "$OUT_FILE" "$LATEST"
echo "✅ FISGÓN: Reporte en $LATEST"
