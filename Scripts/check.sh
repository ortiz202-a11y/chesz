#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

echo "--- CHECK: SANITY (NO COMMIT / NO PUSH) ---"

ROOT="$HOME/chesz"
GRADLE="$ROOT/app/build.gradle"
BUBBLE="$ROOT/app/src/main/java/com/chesz/analyzer/bubble/BubbleService.kt"

echo "Revisando build.gradle..."
if [[ ! -f "$GRADLE" ]]; then
  echo "❌ ERROR: no existe $GRADLE"
  exit 1
fi

if grep -q "targetCompatibility[[:space:]]*$" "$GRADLE"; then
  echo "❌ ERROR: targetCompatibility vacío o mal formado en build.gradle."
  exit 1
fi

echo "Revisando BubbleService..."
if [[ ! -f "$BUBBLE" ]]; then
  echo "❌ ERROR: no existe $BUBBLE"
  exit 1
fi

# basura común: '=' huérfano al inicio
if grep -q "^[[:space:]]*=" "$BUBBLE"; then
  echo "❌ ERROR: asignaciones huérfanas (basura) detectadas en BubbleService."
  exit 1
fi

# llaves balanceadas (check rápido)
OPEN_BRACES=$(grep -o "{" "$BUBBLE" | wc -l | tr -d ' ')
CLOSE_BRACES=$(grep -o "}" "$BUBBLE" | wc -l | tr -d ' ')

if [[ "$OPEN_BRACES" -ne "$CLOSE_BRACES" ]]; then
  echo "❌ ERROR: llaves desbalanceadas. {=$OPEN_BRACES }=$CLOSE_BRACES"
  exit 1
fi

echo "✅ CHECK OK"
