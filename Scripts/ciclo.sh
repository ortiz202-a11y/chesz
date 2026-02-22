#!/data/data/com.termux/files/usr/bin/bash
echo "🚀 INICIANDO CICLO (Modo: Portero + Supervisor ADN)"

# 1. Validación de Seguridad y Coherencia
bash Scripts/check.sh && bash Scripts/validar_adn.sh
if [ $? -ne 0 ]; then
    echo "❌ ABORTANDO: El Supervisor ADN o el Check detectaron inconsistencias."
    exit 1
fi

# 2. Actualizar el Paper (Kanban)
echo "📝 Actualizando PAPER..."

# 3. Sincronización con GitHub (Aquí actúa el Portero)
git add .
git commit -m "Build: Ajustes visuales 60dp y corrección de visibilidad"
git push origin master

# 4. Activar al Vigilante
bash Scripts/vigilante.sh
