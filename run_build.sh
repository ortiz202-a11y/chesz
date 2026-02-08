#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

OWNER="ortiz202-a11y"
REPO="chesz"
BRANCH="master"
WF_NAME="Build APK (debug)"

api() { curl -sS -H "Authorization: Bearer $GH_TOKEN" -H "Accept: application/vnd.github+json" "$@"; }

echo "Buscando workflow..."
WF_ID="$(api "https://api.github.com/repos/$OWNER/$REPO/actions/workflows" \
  | jq -r --arg N "$WF_NAME" '.workflows[] | select(.name==$N) | .id' | head -n 1)"

echo "WF_ID=$WF_ID"

echo "Disparando workflow..."
curl -sS -X POST \
  -H "Authorization: Bearer $GH_TOKEN" \
  -H "Accept: application/vnd.github+json" \
  "https://api.github.com/repos/$OWNER/$REPO/actions/workflows/$WF_ID/dispatches" \
  -d "{\"ref\":\"$BRANCH\"}" >/dev/null

sleep 6

RUN_ID="$(api "https://api.github.com/repos/$OWNER/$REPO/actions/workflows/$WF_ID/runs?branch=$BRANCH&per_page=1" \
  | jq -r '.workflow_runs[0].id')"

echo "RUN_ID=$RUN_ID"
echo "Esperando..."

while true; do
  J="$(api "https://api.github.com/repos/$OWNER/$REPO/actions/runs/$RUN_ID")"
  STATUS="$(echo "$J" | jq -r '.status')"
  CONC="$(echo "$J" | jq -r '.conclusion')"
  echo "status=$STATUS conclusion=$CONC"
  [ "$STATUS" = "completed" ] && break
  sleep 8
done

if [ "$CONC" != "success" ]; then
  echo "FALLO. Bajando logs..."
  mkdir -p ~/BU/gh_logs/$RUN_ID
  curl -L --fail \
    -H "Authorization: Bearer $GH_TOKEN" \
    -H "Accept: application/vnd.github+json" \
    "https://api.github.com/repos/$OWNER/$REPO/actions/runs/$RUN_ID/logs" \
    -o ~/BU/gh_logs/$RUN_ID/logs.zip

  unzip -o ~/BU/gh_logs/$RUN_ID/logs.zip -d ~/BU/gh_logs/$RUN_ID >/dev/null
  echo "Logs en ~/BU/gh_logs/$RUN_ID"
  echo "Errores (resumen):"
  grep -RIn "error:|FAILURE:" ~/BU/gh_logs/$RUN_ID | head -n 60
  exit 0
fi

echo "OK. Bajando APK..."
ART_ID="$(api "https://api.github.com/repos/$OWNER/$REPO/actions/runs/$RUN_ID/artifacts" \
  | jq -r '.artifacts[0].id')"

mkdir -p ~/BU/apk && cd ~/BU/apk
curl -L --fail \
  -H "Authorization: Bearer $GH_TOKEN" \
  -H "Accept: application/vnd.github+json" \
  "https://api.github.com/repos/$OWNER/$REPO/actions/artifacts/$ART_ID/zip" \
  -o artifact.zip

unzip -o artifact.zip >/dev/null
APK="$(find . -name '*.apk' | head -n 1)"

termux-setup-storage >/dev/null 2>&1 || true
cp "$APK" ~/storage/shared/Download/chesz-debug.apk

echo "APK listo: Download/chesz-debug.apk"


# ===== Export APK to Download (Termux) =====
APK_SRC="./chesz-debug-apk/chesz-debug-apk/app-debug.apk"
APK_DST="/storage/shared/Download/chesz-debug.apk"

if [ -f "" ]; then
  if [ -d "/storage/shared/Download" ]; then
    cp -f "" ""
    echo "APK copiado a: "
  else
    echo "No existe ~/storage/shared/Download. Ejecuta: termux-setup-storage"
    echo "Luego reintenta ./run_build.sh"
  fi
else
  echo "No encontré el APK en: "
  echo "Busca dónde quedó con: find . -maxdepth 6 -name "*.apk" -print"
fi
