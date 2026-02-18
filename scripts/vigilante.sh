#!/bin/bash
# ==========================================
# VIGILANTE V3: REPORTE DEL CADENERO
# ==========================================

# Obtener el último commit local
LAST_COMMIT=$(git rev-parse HEAD)
SHORT_SHA=${LAST_COMMIT:0:7}

echo "🔍 Vigilante: Buscando reporte para el commit [$SHORT_SHA]..."

# Consultar el estado del último flujo en GitHub
# Usamos 'gh run list' para obtener los datos del último proceso
RUN_DATA=$(gh run list --limit 1 --json conclusion,databaseId,status,displayTitle)
STATUS=$(echo $RUN_DATA | jq -r '.[0].status')
CONCLUSION=$(echo $RUN_DATA | jq -r '.[0].conclusion')
RUN_ID=$(echo $RUN_DATA | jq -r '.[0].databaseId')

echo "-------------------------------------------"
echo "ESTADO: $STATUS"

if [ "$STATUS" != "completed" ]; then
    echo "⏳ El proceso sigue en la fila. Espera un momento..."
else
    if [ "$CONCLUSION" == "success" ]; then
        echo "✅ EL CADENERO DIO PASO: Compilación exitosa."
        echo "Ya puedes descargar el APK desde GitHub."
    else
        echo "❌ EL CADENERO DICE: Falla en la compilación."
        echo "--- ANALIZANDO LA RAZÓN DEL FALLO (LOGS) ---"
        echo ""
        # Extraer las líneas de error específicas del log de GitHub
        gh run view $RUN_ID --log-failed | grep -E "Error:|error:|E/" | tail -n 10
        echo ""
        echo "-------------------------------------------"
        echo "💡 Copia lo de arriba y dáselo a Gemini para el diagnóstico."
    fi
fi
