#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

cd "$HOME/chesz" || exit 1

# Dependencias
for cmd in curl jq unzip; do
  command -v "$cmd" >/dev/null 2>&1 || { echo "❌ Falta $cmd. Instala: pkg install -y $cmd"; exit 1; }
done

# Token
if [[ -f "$HOME/.secrets/gh_token" ]]; then
  export GH_TOKEN="$(cat "$HOME/.secrets/gh_token")"
fi
: "${GH_TOKEN:?Falta GH_TOKEN (usa ~/.secrets/gh_token o export GH_TOKEN=...)}"

REPO="ortiz202-a11y/chesz"
SHA="$(git rev-parse --short HEAD)"
BRANCH="$(git branch --show-current)"
DEST_DIR="/storage/emulated/0/Download/apps"
mkdir -p "$DEST_DIR"

echo "🛰️ Vigilante: buscando run para SHA=$SHA en branch=$BRANCH"
echo "-------------------------------------------"

SECONDS_WAITED=0
RUN_ID=""
STATUS=""
CONCLUSION=""

while true; do
  RUN_JSON="$(curl -fsSL -H "Authorization: Bearer $GH_TOKEN" \
    -H "Accept: application/vnd.github+json" \
    "https://api.github.com/repos/${REPO}/actions/runs?branch=${BRANCH}&per_page=10")"

  RUN_ID="$(echo "$RUN_JSON" | jq -r --arg sha "$SHA" '.workflow_runs[] | select(.head_sha | startswith($sha)) | .id' | head -n 1)"
  STATUS="$(echo "$RUN_JSON" | jq -r --arg sha "$SHA" '.workflow_runs[] | select(.head_sha | startswith($sha)) | .status' | head -n 1)"
  CONCLUSION="$(echo "$RUN_JSON" | jq -r --arg sha "$SHA" '.workflow_runs[] | select(.head_sha | startswith($sha)) | .conclusion' | head -n 1)"

  if [[ -z "${RUN_ID:-}" ]]; then
    echo "[${SECONDS_WAITED}s] Esperando que GitHub Actions reconozca el commit..."
  else
    echo "[${SECONDS_WAITED}s] Status: $STATUS | Conclusion: $CONCLUSION | Run: $RUN_ID"
  fi

  if [[ "$STATUS" == "completed" && "$CONCLUSION" == "success" ]]; then
    echo "✅ BUILD OK: descargando artifacts del run $RUN_ID"
    break
  elif [[ "$STATUS" == "completed" && "$CONCLUSION" == "failure" ]]; then
    echo "❌ BUILD FAIL. Revisa logs en GitHub Actions."
    exit 1
  fi

  sleep 10
  SECONDS_WAITED=$((SECONDS_WAITED+10))
done

TMP="$HOME/BU/gh_apk/$RUN_ID"
mkdir -p "$TMP"

ART_URL="$(curl -fsSL -H "Authorization: Bearer $GH_TOKEN" \
  "https://api.github.com/repos/${REPO}/actions/runs/${RUN_ID}/artifacts" \
  | jq -r '.artifacts[]?.archive_download_url' | head -n 1)"

if [[ -z "${ART_URL:-}" || "$ART_URL" == "null" ]]; then
  echo "❌ No se encontró artifact para RUN_ID=$RUN_ID"
  exit 1
fi

ZIP="$TMP/artifacts.zip"
curl -fsSL -L -H "Authorization: Bearer $GH_TOKEN" -o "$ZIP" "$ART_URL"
unzip -o "$ZIP" -d "$TMP" >/dev/null

APK_PATH="$(find "$TMP" -type f -name "*.apk" | head -n 1)"
if [[ -z "${APK_PATH:-}" ]]; then
  echo "❌ No se encontró .apk dentro de $TMP"
  exit 1
fi

OUT="$DEST_DIR/chesz-${SHA}.apk"
cp -f "$APK_PATH" "$OUT"
echo "-------------------------------------------"
echo "🚀 APK LISTA: $OUT"
