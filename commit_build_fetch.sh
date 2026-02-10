#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

OWNER="ortiz202-a11y"
REPO="chesz"
WORKFLOW_FILE="build-apk.yml"
BRANCH="$(git rev-parse --abbrev-ref HEAD)"

APPS_DIR="$HOME/storage/downloads/apps"
mkdir -p "$APPS_DIR"

# Timestamp 3: YYYY-MM-DD_HHMM
TS="$(date '+%Y-%m-%d_%H%M')"

APK_OUT="$APPS_DIR/chesz-debug-${TS}.apk"
APK_LATEST="$APPS_DIR/chesz-debug-latest.apk"

POLL=10

need() { command -v "$1" >/dev/null || { echo "Falta: $1"; exit 1; }; }
need curl
need jq
need unzip
need git

: "${GH_TOKEN:=$(cat ~/.secrets/gh_token 2>/dev/null || true)}"
[ -n "${GH_TOKEN}" ] || { echo "Falta GH_TOKEN (export GH_TOKEN=... o ~/.secrets/gh_token)"; exit 1; }

echo "== Repo: $OWNER/$REPO  Branch: $BRANCH  Workflow: $WORKFLOW_FILE =="
echo "== Salida APK: $APK_OUT =="

echo "== Limpieza: borrando APK previo (latest) =="
rm -f "$APK_LATEST"

echo "== Git status =="
git status -sb

echo "== Commit + Push (si hay cambios staged) =="
# Si no hay nada para commitear, no fallar: solo seguir a build
if git diff --cached --quiet; then
  echo "Nada staged para commit. (OK, continúo a build)"
else
  git commit -m "Fix: drag no se interpreta como tap (no cierra panel al soltar)"
  git push
fi

SHA="$(git rev-parse HEAD)"
echo "HEAD SHA=$SHA"

echo "== Disparando workflow_dispatch en branch: $BRANCH =="
curl -sS -X POST \
  -H "Authorization: Bearer $GH_TOKEN" \
  -H "Accept: application/vnd.github+json" \
  "https://api.github.com/repos/$OWNER/$REPO/actions/workflows/$WORKFLOW_FILE/dispatches" \
  -d "{\"ref\":\"$BRANCH\"}" >/dev/null

echo "OK: workflow disparado. Buscando RUN_ID más reciente de ESTE workflow en esta rama…"

# Buscar el run más reciente del workflow (para esta rama) creado después del dispatch.
# (No usamos SHA porque el dispatch puede reusar el mismo commit.)
RUN_ID=""
for _ in {1..60}; do
  RUN_ID="$(
    curl -sS -H "Authorization: Bearer $GH_TOKEN" -H "Accept: application/vnd.github+json" \
      "https://api.github.com/repos/$OWNER/$REPO/actions/workflows/$WORKFLOW_FILE/runs?branch=$BRANCH&per_page=5" \
    | jq -r '.workflow_runs[0].id'
  )"
  if [ -n "$RUN_ID" ] && [ "$RUN_ID" != "null" ]; then
    break
  fi
  sleep 2
done

[ -n "$RUN_ID" ] && [ "$RUN_ID" != "null" ] || { echo "No pude obtener RUN_ID"; exit 2; }

echo "RUN_ID=$RUN_ID"
echo "== Monitoreando estado cada ${POLL}s =="

while true; do
  JSON="$(
    curl -sS -H "Authorization: Bearer $GH_TOKEN" -H "Accept: application/vnd.github+json" \
      "https://api.github.com/repos/$OWNER/$REPO/actions/runs/$RUN_ID"
  )"

  STATUS="$(echo "$JSON" | jq -r '.status')"
  CONCLUSION="$(echo "$JSON" | jq -r '.conclusion')"

  echo "$(date '+%H:%M:%S')  status=$STATUS  conclusion=$CONCLUSION"

  if [ "$STATUS" = "completed" ]; then
    if [ "$CONCLUSION" != "success" ]; then
      echo "BUILD FALLÓ: conclusion=$CONCLUSION"
      echo "Run: https://github.com/$OWNER/$REPO/actions/runs/$RUN_ID"
      exit 3
    fi
    break
  fi

  sleep "$POLL"
done

echo "== Build OK. Descargando artifacts del run =="

ARTIFACT_ID="$(
  curl -sS -H "Authorization: Bearer $GH_TOKEN" -H "Accept: application/vnd.github+json" \
    "https://api.github.com/repos/$OWNER/$REPO/actions/runs/$RUN_ID/artifacts" \
  | jq -r '.artifacts[0].id'
)"

[ -n "$ARTIFACT_ID" ] && [ "$ARTIFACT_ID" != "null" ] || { echo "No encontré artifacts en RUN_ID=$RUN_ID"; exit 4; }

TMP_DIR="$(mktemp -d)"
ZIP="$TMP_DIR/artifact.zip"

curl -sS -L \
  -H "Authorization: Bearer $GH_TOKEN" \
  -H "Accept: application/vnd.github+json" \
  "https://api.github.com/repos/$OWNER/$REPO/actions/artifacts/$ARTIFACT_ID/zip" \
  -o "$ZIP"

unzip -qo "$ZIP" -d "$TMP_DIR/unz"

APK_PATH="$(find "$TMP_DIR/unz" -type f -name "*.apk" | head -n 1)"
[ -n "$APK_PATH" ] || { echo "No encontré .apk dentro del artifact"; exit 5; }

cp -f "$APK_PATH" "$APK_OUT"
cp -f "$APK_OUT" "$APK_LATEST"
chmod 644 "$APK_OUT" "$APK_LATEST"

echo "✅ APK guardado:"
ls -la "$APK_OUT" "$APK_LATEST"
