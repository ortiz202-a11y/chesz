#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

REPO="ortiz202-a11y/chesz"
WORKFLOW="build-apk.yml"
ART_DIR="$HOME/BU/gh_artifacts_tmp"
APK_DIR="/storage/emulated/0/Download/apps"

termux-setup-storage >/dev/null 2>&1 || true
mkdir -p "$APK_DIR"

if ! command -v gh >/dev/null 2>&1; then
  pkg update -y >/dev/null 2>&1 || true
  pkg install -y gh >/dev/null 2>&1 || { echo "No se pudo instalar gh"; exit 1; }
fi

echo "Buscando último run SUCCESS..."
RUN_ID="$(gh run list --repo "$REPO" --workflow "$WORKFLOW" --limit 20 2>/dev/null \
  | awk '$0 ~ /success/ {print $1; exit}')"

[ -n "$RUN_ID" ] || { echo "No encontré run exitoso."; exit 1; }

echo "Usando RUN_ID: $RUN_ID"

rm -rf "$ART_DIR" >/dev/null 2>&1 || true
mkdir -p "$ART_DIR"

gh run download --repo "$REPO" "$RUN_ID" --dir "$ART_DIR" || { echo "Fallo descarga"; exit 1; }

find "$ART_DIR" -name "*.zip" -exec unzip -o {} -d "$ART_DIR" \; >/dev/null 2>&1 || true

echo "Copiando APK(s)..."
FOUND=0
while IFS= read -r -d '' apk; do
  cp -f "$apk" "$APK_DIR/"
  echo "Copiado: $(basename "$apk")"
  FOUND=1
done < <(find "$ART_DIR" -type f -name "*.apk" -print0)

[ "$FOUND" -eq 1 ] || { echo "No se encontraron APKs."; exit 1; }

echo "Listo. APK(s) en:"
ls -la "$APK_DIR"
