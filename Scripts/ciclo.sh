#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

cd "$HOME/chesz" || exit 1

echo "==========================================="
echo "🚀 CICLO: CHESZ"
echo "==========================================="
echo "🧭 Branch: $(git branch --show-current)"
echo "🧾 SHA:    $(git rev-parse --short HEAD)"
echo

echo "== 1) STATUS =="
git status -sb || true
echo

echo "== 2) FISGÓN (full) =="
"$HOME/chesz/Scripts/fisgon.sh" full
echo

echo "== 3) CHECK =="
"$HOME/chesz/Scripts/check.sh"
echo

echo "== 4) COMMIT+PUSH (si hay cambios) =="
if ! git diff --quiet || ! git diff --cached --quiet; then
  echo "📝 Hay cambios. Preparando commit..."
  git add -A

  MSG="${CICLO_MSG:-"chore(ciclo): update"}"
  git commit -m "$MSG"
  git push

  echo "✅ Push OK."
else
  echo "ℹ️ No hay cambios para commitear."
fi
echo

echo "== 5) VIGILANTE =="
"$HOME/chesz/Scripts/vigilante.sh"
echo
echo "✅ CICLO COMPLETO"
