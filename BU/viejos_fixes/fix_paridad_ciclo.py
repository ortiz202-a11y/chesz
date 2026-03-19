import os

def aplicar_bloqueo_paridad():
    file_path = os.path.expanduser("~/Scripts_chesz/ciclo.sh")
    
    nuevo_ciclo = [
        "#!/data/data/com.termux/files/usr/bin/bash\n",
        "set -euo pipefail\n",
        "\n",
        "CD_DIR=\"$HOME/chesz\"\n",
        "SCRIPTS_DIR=\"$HOME/Scripts_chesz\"\n",
        "PAPER=\"$CD_DIR/paper\"\n",
        "\n",
        "cd \"$CD_DIR\"\n",
        "BRANCH=$(git branch --show-current)\n",
        "\n",
        "echo \"🚀 INICIANDO CICLO EN RAMA: $BRANCH...\"\n",
        "\n",
        "# 1. Limpieza de Paper\n",
        "if [[ -f \"$PAPER\" ]]; then\n",
        "    sed -i '/\\[x\\].*([0-9]\\{2\\}:[0-9]\\{2\\})/d' \"$PAPER\"\n",
        "fi\n",
        "\n",
        "# 2. Validaciones estrictas con BLOQUEO DE PARIDAD\n",
        "bash \"$SCRIPTS_DIR/check.sh\" && bash \"$SCRIPTS_DIR/integridad.sh\" || {\n",
        "    echo \"- [ ] ⚠️ FALLO: Detenido por Check/ADN ($(date '+%H:%M'))\" >> \"$PAPER\"\n",
        "    echo \"❌ [ERROR CRÍTICO] ADN o Sintaxis corrupta.\"\n",
        "    echo \"🛑 BLOQUEO DE SEGURIDAD: Abortando para evitar descarga de APK vieja.\"\n",
        "    exit 1\n",
        "}\n",
        "\n",
        "# 3. Subida y Vigilante (Solo si el paso anterior fue PERFECTO)\n",
        "if [[ -n $(git status -s) ]]; then\n",
        "    git add .\n",
        "    git commit -m \"${1:-Build: $(date '+%Y-%m-%d %H:%M:%S')}\"\n",
        "    if git push origin \"$BRANCH\"; then\n",
        "        echo \"✅ Subida exitosa. Esperando build real...\"\n",
        "        sleep 5\n",
        "        bash \"$SCRIPTS_DIR/vigilante.sh\"\n",
        "    else\n",
        "        echo \"❌ Error en el Push. No se descargará nada.\"\n",
        "        exit 1\n",
        "    fi\n",
        "else\n",
        "    echo \"❌ ERROR: No hay cambios nuevos. Ciclo abortado.\"\n",
        "    exit 1\n",
        "fi\n"
    ]

    try:
        # Escritura literal para integridad 1:1
        with open(file_path, 'w') as f:
            f.writelines(nuevo_ciclo)
        
        # Restaurar permisos de ejecución
        os.chmod(file_path, 0o744)
        print(f"\n✅ BLOQUEO DE PARIDAD APLICADO: {file_path}")
        print("🛡️ Vigilante bloqueado ante fallos de ADN/Sintaxis.")
        
    except Exception as e:
        print(f"\n❌ ERROR CRÍTICO: {e}")

if __name__ == '__main__':
    aplicar_bloqueo_paridad()
