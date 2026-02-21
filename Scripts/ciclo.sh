#!/bin/bash
echo "🚀 INICIANDO CICLO DE TRABAJO: CHESZ ENGINE"
echo "🕵️ Ejecutando Fisgón..."
~/chesz/Scripts/fisgon.sh
echo "🛡️ Ejecutando Check de Seguridad..."
~/chesz/Scripts/check.sh
if [ $? -eq 0 ]; then
    echo "✅ VALIDACIÓN EXITOSA. Sincronizando..."
    ~/chesz/Scripts/vigilante.sh
else
    echo "❌ CICLO ABORTADO: Hay errores de sintaxis."
    exit 1
fi
