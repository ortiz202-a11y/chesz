#!/data/data/com.termux/files/usr/bin/bash
# Supervisor de Coherencia Chesz

PACKAGE_OFICIAL=$(grep "applicationId" ~/chesz/app/build.gradle | head -1 | cut -d"'" -f2)
MANIFEST_PKG=$(grep "package=" ~/chesz/app/src/main/AndroidManifest.xml | cut -d'"' -f2)

echo "🔍 [SUPERVISOR] Validando coherencia del ADN..."

# 1. [span_0](start_span)[span_1](start_span)Verificar Gradle vs Manifest[span_0](end_span)[span_1](end_span)
if [ "$PACKAGE_OFICIAL" != "$MANIFEST_PKG" ]; then
    echo "❌ ERROR: El ID en Gradle ($PACKAGE_OFICIAL) no coincide con Manifest ($MANIFEST_PKG)"
    exit 1
fi

# 2. [span_2](start_span)[span_3](start_span)Verificar que los archivos Kotlin tengan el package correcto[span_2](end_span)[span_3](end_span)
find ~/chesz/app/src/main/java -name "*.kt" | while read -r file; do
    if ! grep -q "package $PACKAGE_OFICIAL" "$file"; then
        echo "⚠️ ADVERTENCIA: $file tiene un package declarado que no coincide con $PACKAGE_OFICIAL"
        exit 1
    fi
done

echo "✅ Coherencia de identidad verificada con éxito."
