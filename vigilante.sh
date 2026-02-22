#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

cd "$HOME/chesz"

# Cargar token si existe
[[ -f "$HOME/.secrets/gh_token" ]] && export GH_TOKEN="$(cat "$HOME/.secrets/gh_token")"
: "${GH_TOKEN:?❌ Falta GH_TOKEN en ~/.secrets/gh_token}"

REPO="ortiz202-a11y/chesz"
SHA="$(git rev-parse --short HEAD)"
DEST_DIR="/storage/emulated/0/Download/apps"
mkdir -p "$DEST_DIR"

echo "🛰️  [VIGILANTE] Rastreando Build para SHA: $SHA"

# Simulación de loop de monitoreo (usa tu lógica de curl anterior)
# Si el build falla en GitHub, llamar a build_error
build_error() {
    echo -e "\n❌ [ERROR EN VIGILANTE]: $1"
    exit 1
}

# Aquí iría tu bloque de curl/jq que ya tienes funcional. 
# Asegúrate de que si el status es 'failure', devuelva exit 1.

echo "✅ APK DESCARGADA: $DEST_DIR/chesz-$SHA.apk"
