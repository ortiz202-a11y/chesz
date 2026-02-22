#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

cd "$HOME/chesz" || exit 1

MODE="${1:-full}"              # full | targets
TS="$(date +%Y%m%d_%H%M%S)"
OUT_DIR="$HOME/storage/downloads"
OUT_FILE="$OUT_DIR/fisgon_${MODE}_${TS}.txt"
LATEST="$OUT_DIR/fisgon_latest.txt"

mkdir -p "$OUT_DIR"

echo "🔍 Fisgón: generando reporte..."
echo "🧩 Mode: $MODE"
echo "🕒 Timestamp: $TS"
echo "📄 Archivo: $OUT_FILE"

{
  echo "-------------------------------------------"
  echo "FISGON_MODE=$MODE"
  echo "FISGON_TS=$TS"
  echo "PWD=$(pwd)"
  echo "BRANCH=$(git branch --show-current 2>/dev/null || true)"
  echo "SHA=$(git rev-parse --short HEAD 2>/dev/null || true)"
  echo "-------------------------------------------"
  echo
} > "$OUT_FILE"

append_file() {
  local f="$1"
  if [[ ! -f "$f" ]]; then
    {
      echo "=============================="
      echo "FILE: $f"
      echo "=============================="
      echo "⚠️  NO EXISTE"
      echo
      echo "---"
      echo
    } >> "$OUT_FILE"
    return 0
  fi

  {
    echo "=============================="
    echo "FILE: $f"
    echo "=============================="
    nl -ba "$f" || true
    echo
    echo "---"
    echo
  } >> "$OUT_FILE"
}

emit_by_find() {
  local base="$1"
  local maxdepth="${2:-12}"
  find "$base" -maxdepth "$maxdepth" -not -path '*/.*' -type f \
    \( -name "*.kt" -o -name "*.java" -o -name "*.xml" -o -name "*.gradle" -o -name "settings.gradle" -o -name "*.sh" \) \
    -print0 | sort -z | while IFS= read -r -d '' f; do
      append_file "$f"
    done
}

case "$MODE" in
  full)
    emit_by_find "$HOME/chesz" 12
    ;;

  targets)
    : "${FISGON_TARGETS:?Falta FISGON_TARGETS (ej: 'app/src/main/AndroidManifest.xml Scripts app/src/main/res/layout/overlay_*.xml')}"
    shopt -s nullglob globstar

    expanded=()
    # OJO: FISGON_TARGETS se expande como palabras (separa por espacios)
    for t in $FISGON_TARGETS; do
      matches=( $t )
      if (( ${#matches[@]} == 0 )); then
        {
          echo "=============================="
          echo "TARGET: $t"
          echo "=============================="
          echo "⚠️  SIN MATCH"
          echo
          echo "---"
          echo
        } >> "$OUT_FILE"
        continue
      fi
      for m in "${matches[@]}"; do expanded+=( "$m" ); done
    done

    for p in "${expanded[@]}"; do
      if [[ -d "$p" ]]; then
        emit_by_find "$p" 20
      else
        append_file "$p"
      fi
    done
    ;;

  *)
    echo "❌ Modo inválido: $MODE (usa: full | targets)"
    exit 1
    ;;
esac

cp -f "$OUT_FILE" "$LATEST"
echo "✅ Fisgón listo:"
echo "   $OUT_FILE"
echo "   (latest) $LATEST"
