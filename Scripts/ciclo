#!/data/data/com.termux/files/usr/bin/bash
# Motor Chesz v2.0 - Blindado

cd "$HOME/chesz"
echo "🚀 INICIANDO CICLO BLINDADO..."

# 1. Validaciones encadenadas
bash Scripts/check.sh && bash Scripts/integridad || {
bash Scripts/check.sh || { echo "❌ FASE DE VALIDACIÓN FALLIDA. Revisa paper" && echo "- [ ] ⚠️ FALLO: Check detenido ($(date "+%H:%M"))" >> paper; exit 1; }
    exit 1
}

# 2. Gestión de Cambios (Solo hace commit si hay cambios reales)
if [[ -n $(git status -s) ]]; then
    echo "📝 Cambios detectados. Registrando en PAPER..."
    git add .
    git commit -m "Build: $(date '+%Y-%m-%d %H:%M:%S')"
    
    echo "📡 Subiendo a GitHub..."
    if git push origin master; then
        echo "✅ Subida exitosa. Activando Vigilante..."
        bash Scripts/vigilante.sh || echo "⚠️ El Vigilante reportó un problema en el build."
    else
        echo "❌ Error en el Push. Revisa conexión."
        exit 1
    fi
else
    echo "ℹ️ Sin cambios. Ejecutando Vigilante para verificar último build..."
    bash Scripts/vigilante.sh
fi

echo "✨ PROCESO FINALIZADO."
