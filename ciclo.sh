#!/data/data/com.termux/files/usr/bin/bash
set -e

cd "$HOME/chesz"

abort() {
  echo -e "\n***********************************************"
  echo -e "❌ ABORTANDO: $1"
  echo -e "***********************************************"
  exit 1
}

echo "🚀 INICIANDO CICLO CHESZ..."

bash Scripts/check.sh || abort "El CHECK detectó errores estructurales."
bash Scripts/fisgon.sh full > /dev/null || abort "El FISGÓN falló al auditar."

echo "📡 Sincronizando con GitHub..."
if ! git diff --quiet || ! git diff --cached --quiet; then
    git add -A
    git commit -m "chore: auto-update $(date +%T)" || abort "Fallo al crear el COMMIT."
    git push || abort "Fallo en el PUSH. Revisa conexión o conflictos."
    echo "✅ Cambios subidos."
else
    echo "ℹ️  Sin cambios locales."
fi

bash Scripts/vigilante.sh || abort "El VIGILANTE no pudo obtener el APK."

echo -e "\n✨ [CICLO COMPLETADO EXITOSAMENTE] ✨"
