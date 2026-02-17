#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

cd ~/chesz || exit 1
export GH_TOKEN="$(cat ~/.secrets/gh_token)"

mkdir -p ~/storage/shared/Download/apps

SHA="$(git rev-parse --short HEAD)"
BRANCH="master"

echo "Buscando run para commit $SHA en $BRANCH..."
echo "-------------------------------------------"

SECONDS_WAITED=0

while true; do
  RUN_JSON="$(
    curl -s -H "Authorization: Bearer $GH_TOKEN" \
    -H "Accept: application/vnd.github+json" \
    "https://api.github.com/repos/ortiz202-a11y/chesz/actions/runs?branch=${BRANCH}&per_page=20"
  )"

  STATUS="$(echo "$RUN_JSON" | jq -r --arg sha "$SHA" '
    .workflow_runs[]
    | select(.head_sha | startswith($sha))
    | .status' | head -n 1)"

  CONCLUSION="$(echo "$RUN_JSON" | jq -r --arg sha "$SHA" '
    .workflow_runs[]
    | select(.head_sha | startswith($sha))
    | .conclusion' | head -n 1)"

  RUN_ID="$(echo "$RUN_JSON" | jq -r --arg sha "$SHA" '
    .workflow_runs[]
    | select(.head_sha | startswith($sha))
    | .id' | head -n 1)"

  if [[ -z "$RUN_ID" ]]; then
    echo "[${SECONDS_WAITED}s] Aún no aparece run para este commit..."
  else
    echo "[${SECONDS_WAITED}s] status=$STATUS  conclusion=$CONCLUSION  run=$RUN_ID"
  fi

  if [[ "$STATUS" == "completed" && "$CONCLUSION" == "success" ]]; then
    echo "-------------------------------------------"
    echo "Run SUCCESS detectado: $RUN_ID"
    break
  fi

  sleep 10
  SECONDS_WAITED=$((SECONDS_WAITED+10))
done

echo "Descargando artifacts..."

mkdir -p ~/BU/gh_apk/$RUN_ID

curl -L --fail \
  -H "Authorization: Bearer $GH_TOKEN" \
  -H "Accept: application/vnd.github+json" \
  "https://api.github.com/repos/ortiz202-a11y/chesz/actions/runs/${RUN_ID}/artifacts" \
  | jq -r '.artifacts[0].archive_download_url' \
  | xargs -I{} curl -L --fail \
      -H "Authorization: Bearer $GH_TOKEN" \
      -o ~/BU/gh_apk/$RUN_ID/artifacts.zip {}

unzip -o ~/BU/gh_apk/$RUN_ID/artifacts.zip -d ~/BU/gh_apk/$RUN_ID >/dev/null

APK_PATH="$(find ~/BU/gh_apk/$RUN_ID -type f -name "*.apk" | head -n 1)"

OUT="$HOME/storage/shared/Download/apps/chesz-${RUN_ID}-${SHA}.apk"

cp -f "$APK_PATH" "$OUT"

echo "-------------------------------------------"
echo "APK listo en:"
ls -la "$OUT"
