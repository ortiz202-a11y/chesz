#!/bin/bash
# ==========================================
# VIGILANTE V4: EL CADENERO SINCERO
# ==========================================

echo "🔍 Vigilante: Consultando el estado en la nube..."

# Obtener datos del último proceso
RUN_DATA=$(gh run list --limit 1 --json conclusion,databaseId,status)
STATUS=$(echo $RUN_DATA | jq -r '.[0].status')
CONCLUSION=$(echo $RUN_DATA | jq -r '.[0].conclusion')
RUN_ID=$(echo $RUN_DATA | jq -r '.[0].databaseId')

if [ "$STATUS" != "completed" ]; then
    echo "⏳ ESTADO: $STATUS... El Cadenero aún está procesando. Reintenta en 10s."
else
    if [ "$CONCLUSION" == "success" ]; then
        echo "✅ EL CADENERO DIO PASO: ¡Compilación Exitosa!"
    else
        echo "❌ EL CADENERO DICE: Falla detectada."
        echo "--- REPORTE TÉCNICO DEL FALLO ---"
        # Aquí implementamos tu idea: Ver el log de lo que falló
        gh run view $RUN_ID --log-failed
        echo "--------------------------------"
        echo "💡 Gemini: Analiza el error de arriba para el Paso 5.1."
    fi
fi
