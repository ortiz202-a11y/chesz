#!/data/data/com.termux/files/usr/bin/bash
set -e
cd "$HOME/chesz"

abort() {
  echo -e "\n***********************************************"
  echo -e "❌ ABORTANDO: $1"
  echo -e "***********************************************"
  exit 1
}

echo "🚀 INICIANDO CICLO..."
bash Scripts/check.sh || abort "El CHECK detectó errores."
bash Scripts/fisgon.sh full > /dev/null || abort "El FISGÓN falló."

echo "📡 Subiendo a GitHub..."
if ! git diff --quiet || ! git diff --cached --quiet; then
    git add -A
    git commit -m "chore: update $(date +%T)" || abort "Fallo al crear COMMIT."
    git push || abort "Fallo en el PUSH (revisa internet)."
else
    echo "ℹ️ Sin cambios locales."
fi

bash Scripts/vigilante.sh || abort "El VIGILANTE falló."
echo -e "\n✨ [CICLO COMPLETADO EXITOSAMENTE] ✨"
