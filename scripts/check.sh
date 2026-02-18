#!/bin/bash
echo "--- ANALIZADOR DE SINTAXIS PROFESIONAL ---"

# 1. Validar el archivo de configuración (Gradle) para evitar líneas vacías
echo "Revisando build.gradle..."
if grep -q "targetCompatibility[[:space:]]*$" ~/chesz/app/build.gradle; then
    echo "❌ ERROR: targetCompatibility está vacío o mal formado en build.gradle."
    exit 1
fi

# 2. Validar sintaxis de Kotlin (Verificar que no haya líneas truncadas)
echo "Revisando estructura de BubbleService..."
# Verificamos que no haya signos '=' huérfanos al inicio de línea (basura común)
if grep -q "^[[:space:]]*=" ~/chesz/app/src/main/java/com/chesz/analyzer/floating/BubbleService.kt; then
    echo "❌ ERROR: Se detectaron asignaciones huérfanas (basura) en BubbleService."
    exit 1
fi

# 3. Verificación de llaves cerradas
OPEN_BRACES=$(grep -o "{" ~/chesz/app/src/main/java/com/chesz/analyzer/floating/BubbleService.kt | wc -l)
CLOSE_BRACES=$(grep -o "}" ~/chesz/app/src/main/java/com/chesz/analyzer/floating/BubbleService.kt | wc -l)

if [ "$OPEN_BRACES" -eq "$CLOSE_BRACES" ]; then
    echo "✅ SINTAXIS ESTRUCTURAL OK. Procediendo..."
    cd ~/chesz
    git add .
    git commit -m "Auto-fix: Sintaxis validada estructuralmente"
    git push origin master
    ~/chesz/scripts/vigilante.sh
else
    echo "❌ ERROR: Llaves desbalanceadas. El código está incompleto."
    exit 1
fi
