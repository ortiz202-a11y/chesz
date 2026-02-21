#!/bin/bash
# Script Fisgón: Genera un reporte completo del estado actual del proyecto

REPORT_NAME="fisgon.txt"
DESTINATION="~/storage/downloads/$REPORT_NAME"

echo "🔍 El Fisgón está recorriendo el proyecto..."

# Buscar archivos .kt, .xml y build.gradle, ignorando carpetas ocultas
find ~/chesz -maxdepth 10 -not -path '*/.*' -type f \( -name "*.kt" -o -name "*.xml" -o -name "build.gradle" \) \
-exec echo "FILE: {}" \; \
-exec cat {} \; \
-exec echo -e "\n---\n" \; > ~/storage/downloads/fisgon.txt

echo "✅ Reporte generado en: $DESTINATION"
echo "Contenido del reporte:"
cat ~/storage/downloads/fisgon.txt
