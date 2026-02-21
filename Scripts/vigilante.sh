#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

cd ~/chesz || exit 1
# Usamos tu método de token seguro
export GH_TOKEN="$(cat ~/.secrets/gh_token)"

mkdir -p /storage/emulated/0/Download/apps
SHA="$(git rev-parse --short HEAD)"
BRANCH="master"

echo "🛰️ Vigilante Avanzado: Rastreado commit $SHA..."
echo "-------------------------------------------"

SECONDS_WAITED=0
while true; do
    RUN_JSON="$(curl -s -H "Authorization: Bearer $GH_TOKEN" \
        -H "Accept: application/vnd.github+json" \
        "https://api.github.com/repos/ortiz202-a11y/chesz/actions/runs?branch=${BRANCH}&per_page=5")"

    # Extraemos info del proceso que coincide con nuestro SHA
    STATUS="$(echo "$RUN_JSON" | jq -r --arg sha "$SHA" '.workflow_runs[] | select(.head_sha | startswith($sha)) | .status' | head -n 1)"
    CONCLUSION="$(echo "$RUN_JSON" | jq -r --arg sha "$SHA" '.workflow_runs[] | select(.head_sha | startswith($sha)) | .conclusion' | head -n 1)"
    RUN_ID="$(echo "$RUN_JSON" | jq -r --arg sha "$SHA" '.workflow_runs[] | select(.head_sha | startswith($sha)) | .id' | head -n 1)"

    if [[ -z "$RUN_ID" ]]; then
        echo "[${SECONDS_WAITED}s] Esperando que la nube reconozca el commit..."
    else
        echo "[${SECONDS_WAITED}s] Status: $STATUS | Conclusion: $CONCLUSION"
    fi

    if [[ "$STATUS" == "completed" && "$CONCLUSION" == "success" ]]; then
        echo "-------------------------------------------"
        echo "✅ COMPILACIÓN EXITOSA: Descargando $RUN_ID"
        break
    elif [[ "$STATUS" == "completed" && "$CONCLUSION" == "failure" ]]; then
        echo "❌ ERROR EN LA NUBE. Revisa los logs."
        exit 1
    fi

    sleep 10
    SECONDS_WAITED=$((SECONDS_WAITED+10))
done

# Proceso de descarga y renombrado original
mkdir -p ~/BU/gh_apk/$RUN_ID
curl -L -H "Authorization: Bearer $GH_TOKEN" \
    "https://api.github.com/repos/ortiz202-a11y/chesz/actions/runs/${RUN_ID}/artifacts" \
    | jq -r '.artifacts[0].archive_download_url' \
    | xargs -I{} curl -L -H "Authorization: Bearer $GH_TOKEN" -o ~/BU/gh_apk/$RUN_ID/artifacts.zip {}

unzip -o ~/BU/gh_apk/$RUN_ID/artifacts.zip -d ~/BU/gh_apk/$RUN_ID >/dev/null
APK_PATH="$(find ~/BU/gh_apk/$RUN_ID -type f -name "*.apk" | head -n 1)"
OUT="/storage/emulated/0/Download/apps/chesz-${SHA}.apk"

cp -f "$APK_PATH" "$OUT"
echo "-------------------------------------------"
echo "🚀 APK LISTA: $OUT"
