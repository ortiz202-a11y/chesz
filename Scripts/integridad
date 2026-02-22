#!/data/data/com.termux/files/usr/bin/bash
# Supervisor de ADN Blindado

PACKAGE_OFICIAL=$(grep "applicationId" ~/chesz/app/build.gradle | head -1 | cut -d"'" -f2)
MANIFEST_PKG=$(grep "package=" ~/chesz/app/src/main/AndroidManifest.xml | cut -d'"' -f2)

echo "🔍 [SUPERVISOR] Validando y Blindando ADN..."

# IF 1: Coherencia de Identidad
if [ "$PACKAGE_OFICIAL" != "$MANIFEST_PKG" ]; then
    echo "⚠️ Discrepancia detectada. Intentando autocuración..."
    sed -i "s/package=\".*\"/package=\"$PACKAGE_OFICIAL\"/" ~/chesz/app/src/main/AndroidManifest.xml
    echo "✅ Manifiesto alineado con Gradle."
fi

# IF 2: Validación de Estructura de Carpetas
EXPECTED_PATH="app/src/main/java/${PACKAGE_OFICIAL//.//}"
if [ ! -d "$HOME/chesz/$EXPECTED_PATH" ]; then
    echo "❌ ERROR CRÍTICO: La estructura de carpetas no coincide con el paquete $PACKAGE_OFICIAL"
    exit 1
fi

# IF 3: Limpieza de Residuos (Elimina archivos que no deberían estar en la raíz)
if [ -f "$HOME/chesz/fisgon.sh" ]; then
    rm "$HOME/chesz/fisgon.sh"
    echo "🧹 Limpiado residuo de fisgon.sh en raíz."
fi

echo "✅ ADN Blindado."
