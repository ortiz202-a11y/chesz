#!/data/data/com.termux/files/usr/bin/bash

mkdir -p "$HOME/.gemini_pro"
PROJ="$HOME/chesz"
HIST="$HOME/.gemini_pro/history.txt"
CTX="$HOME/.gemini_pro/contexto.txt"
ERR="$HOME/.gemini_pro/error.log"
MODEL_FILE="$HOME/.gemini_pro/modelo_actual.txt"
MEM="$HOME/.gemini_pro/memoria.json"

touch $HIST

MODELOS=(
    "gemini-3.1-pro-preview"
    "gemini-3-flash-preview"
    "gemini-3.1-flash-lite-preview"
    "gemini-2.5-pro"
    "gemini-2.5-flash"
    "gemini-2.5-flash-lite"
)

if [ -f "$MODEL_FILE" ]; then
    MODELO_ACTUAL=$(cat "$MODEL_FILE")
else
    MODELO_ACTUAL="${MODELOS[0]}"
    echo "$MODELO_ACTUAL" > "$MODEL_FILE"
fi

MCP_INIT='{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2024-11-05","capabilities":{},"clientInfo":{"name":"cge","version":"1.0"}}}'
MCP_READY='{"jsonrpc":"2.0","method":"notifications/initialized","params":{}}'

echo -e "\e[33m$MODELO_ACTUAL\e[0m"
echo -e "\e[94mComandos: fix | diff | analyze | commit | raw | modelo | Sin comando → IA directa\e[0m"
echo -e "\e[90m(Ctrl+C para salir)\e[0m"

CUOTA_AGOTADA=()

gemini_call() {
    local prompt="$1"
    local system="$2"
    local output=""
    local idx=0

    for i in "${!MODELOS[@]}"; do
        [ "${MODELOS[$i]}" = "$MODELO_ACTUAL" ] && idx=$i && break
    done

    while [ $idx -lt ${#MODELOS[@]} ]; do
        local modelo="${MODELOS[$idx]}"
        local raw
        raw=$(echo "$prompt" | gemini --model "$modelo" -p "${system:-responde}" 2>"$ERR")

        if grep -q "quota\|QuotaError\|429\|exhausted" "$ERR" 2>/dev/null; then
            CUOTA_AGOTADA+=("$modelo")
            echo -e "\e[31m⚠ Cuota agotada en $modelo\e[0m" >&2
            echo -e "\e[33mElige modelo:\e[0m" >&2
            for i in "${!MODELOS[@]}"; do
                local m="${MODELOS[$i]}"
                local agotado=false
                for x in "${CUOTA_AGOTADA[@]}"; do [ "$x" = "$m" ] && agotado=true && break; done
                if [ "$m" = "$MODELO_ACTUAL" ]; then
                    echo -e "  \e[90m$((i+1)). $m (actual)\e[0m" >&2
                elif $agotado; then
                    echo -e "  \e[31m$((i+1)). $m ✖ cuota\e[0m" >&2
                else
                    echo -e "  $((i+1)). $m" >&2
                fi
            done
            echo -ne "\e[36m¿Cambiar a cuál? (número o enter para cancelar): \e[0m" >&2
            read sel < /dev/tty
            if [[ "$sel" =~ ^[1-6]$ ]]; then
                local nuevo="${MODELOS[$((sel-1))]}"
                local ya_agotado=false
                for x in "${CUOTA_AGOTADA[@]}"; do [ "$x" = "$nuevo" ] && ya_agotado=true && break; done
                if $ya_agotado; then
                    echo -e "\e[31m✖ Ese modelo ya tiene cuota agotada, elige otro\e[0m" >&2
                else
                    MODELO_ACTUAL="$nuevo"
                    echo "$MODELO_ACTUAL" > "$MODEL_FILE"
                    echo -e "\e[32m✔ Cambiado a $MODELO_ACTUAL\e[0m" >&2
                    idx=$((sel-1))
                fi
            else
                echo -e "\e[31m✖ Cancelado\e[0m" >&2
                return 1
            fi
        else
            echo "$raw"
            return 0
        fi
    done
    return 1

}

buscar_en_grafo() {
    local query="$1"
    local MCP_SEARCH='{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"semantic_search_nodes_tool","arguments":{"query":"'"$query"'","limit":3}}}'
    printf '%s\n%s\n%s\n' "$MCP_INIT" "$MCP_READY" "$MCP_SEARCH" \
        | code-review-graph serve 2>/dev/null \
        | tail -1
}

extraer_campo() {
    local json="$1"
    local campo="$2"
    echo "$json" | python3 -c "
import sys,json
d=json.load(sys.stdin)
r=json.loads(d['result']['content'][0]['text'])['results']
hits = [x for x in r if '/BU/' not in x.get('file_path','')]
print(hits[0]['$campo'] if hits else '')
" 2>/dev/null
}

es_correccion() {
    local instruccion="$1"
    echo "$instruccion" | python3 -c "
import sys
t=sys.stdin.read().lower()
palabras=['no era','no es ese','el otro','incorrecto','equivocado','otro candidato','cambia el','prueba de nuevo','intenta de nuevo','no ese']
print('si' if any(p in t for p in palabras) else 'no')
"
}

modo_cirujano() {
    local instruccion="$1"
    local archivo=""
    local referencia=""
    local linea_ini=""
    local linea_fin=""
    local analisis=""

    # Verificar si es una corrección y hay memoria
    if [ -f "$MEM" ] && [ "$(es_correccion "$instruccion")" = "si" ]; then
        echo -e "\e[33m🧠 Retomando contexto anterior...\e[0m"
        archivo=$(python3 -c "import json; d=json.load(open('$MEM')); print(d.get('archivo',''))" 2>/dev/null)
        referencia=$(python3 -c "import json; d=json.load(open('$MEM')); print(d.get('referencia',''))" 2>/dev/null)
        analisis=$(python3 -c "import json; d=json.load(open('$MEM')); print(d.get('analisis',''))" 2>/dev/null)

        if [ -n "$archivo" ] && [ -n "$analisis" ]; then
            echo -e "\e[33m🎯 Archivo: $(basename $archivo)\e[0m"
            # Mostrar candidatos de nuevo para que elija
            local num_candidatos=$(echo "$analisis" | python3 -c "

import sys,json,re
t=sys.stdin.read()
m=re.search(r'\{.*\}',t,re.DOTALL)
if m:
    d=json.loads(m.group())
    print(len(d.get('candidatos',[])))
else:
    print(0)
" 2>/dev/null)
            echo -e "\e[33m🔍 Candidatos anteriores:\e[0m"
            echo "$analisis" | python3 -c "
import sys,json,re; t=sys.stdin.read()
m=re.search(r'\{.*\}',t,re.DOTALL)
if m:
    d=json.loads(m.group())
    for i,c in enumerate(d['candidatos']):
        print(f'\n  {i+1}. {c[\"descripcion\"]}')
        print(f'  Buscar: {c.get(\"texto_buscar\",\"\")}')
        print(f'  {c.get(\"codigo\",\"\")}')
" 2>/dev/null
            echo -e "\e[94m  r. Reformular   c. Cancelar\e[0m"
            echo -ne "\e[36mElige: \e[0m"
            read opcion < /dev/tty

            if [ "$opcion" = "c" ]; then
                echo -e "\e[33m✖ Cancelado\e[0m"; return
            elif [ "$opcion" = "r" ]; then
                echo -ne "\e[36mAgrega más detalle: \e[0m"
                read detalle < /dev/tty
                modo_cirujano "$instruccion — $detalle"; return
            elif [[ "$opcion" =~ ^[0-9]+$ ]] && [ "$opcion" -ge 1 ] && [ "$opcion" -le "$num_candidatos" ]; then
                linea_ini=$(echo "$analisis" | python3 -c "

import sys,json,re; t=sys.stdin.read()
m=re.search(r'\{.*\}',t,re.DOTALL)
if m:
    d=json.loads(m.group()); print(d['candidatos'][$((opcion-1))].get('texto_buscar',''))
" 2>/dev/null)
                linea_fin=$(echo "$analisis" | python3 -c "
import sys,json,re; t=sys.stdin.read()
m=re.search(r'\{.*\}',t,re.DOTALL)
if m:
    d=json.loads(m.group()); print(d['candidatos'][$((opcion-1))].get('texto_buscar',''))
" 2>/dev/null)
            else
                echo -e "\e[31m✖ Opción inválida\e[0m"; return
            fi

            linea_ini=$(grep -Fn "$linea_ini" "$archivo" 2>/dev/null | head -1 | cut -d: -f1)
            [ -z "$linea_ini" ] && echo -e "\e[31m✖ No se encontró el bloque\e[0m" && return
            linea_fin=$((linea_ini + 25))
            local codigo_actual=$(sed -n "${linea_ini},${linea_fin}p" "$archivo")
            echo -e "\e[33m✏️  Generando código...\e[0m"
            local t3=$(date +%s)
            local codigo_nuevo=$(gemini_call "Eres cirujano de código.
Tu única tarea es modificar EXACTAMENTE el siguiente bloque de código según la instrucción.
No toques nada fuera de este bloque. No agregues elementos nuevos. Solo modifica lo que la instrucción pide.

Código a modificar:
$codigo_actual

Valores de referencia extraídos del código real:
$referencia

Instrucción original: $(python3 -c "import json; d=json.load(open('$MEM')); print(d.get('instruccion',''))" 2>/dev/null)

Devuelve SOLO el bloque modificado, listo para reemplazar línea por línea. Sin explicaciones. Sin backticks." \
"devuelve solo el código modificado" "no")
            local t4=$(date +%s)
            echo -e "\e[96m⏱ generación: $(( t4 - t3 ))s\e[0m"

            if [ -z "$codigo_nuevo" ]; then
                echo -e "\e[31m✖ No se pudo generar código nuevo\e[0m"; return
            fi

            codigo_nuevo=$(echo "$codigo_nuevo" | python3 -c "
import sys,re
lines = sys.stdin.read().splitlines()
start = 0
for i,l in enumerate(lines):
    if re.match(r'^[\s]*(val |var |fun |if |btn|//|/\*|\}|\{|[a-z][a-zA-Z]*\s*[=.(])', l):
        start = i
        break
print('\n'.join(lines[start:]))
")

            echo -e "\e[33m👀 Preview:\e[0m"
            echo -e "\e[31m--- ANTES ---\e[0m"
            echo "$codigo_actual"
            echo -e "\e[32m+++ DESPUÉS +++\e[0m"
            echo "$codigo_nuevo"
            echo ""
            echo -ne "\e[36m¿Aplicar? (y/n): \e[0m"
            read confirm < /dev/tty

            if [[ "$confirm" == "y" || "$confirm" == "Y" ]]; then
                local tmp=$(mktemp)
                head -n $((linea_ini-1)) "$archivo" > "$tmp"
                echo "$codigo_nuevo" >> "$tmp"
                tail -n +$((linea_fin+1)) "$archivo" >> "$tmp"
                mv "$tmp" "$archivo"
                echo -e "\e[32m✔ Cambio aplicado\e[0m"
                rm -f "$MEM"
            else
                echo -e "\e[33m✖ Cancelado\e[0m"
            fi
            return
        fi
    fi

    # Flujo normal — análisis completo
    echo -e "\e[33m🔎 [GRAFO] Buscando archivo...\e[0m"
    local palabras=$(echo "$instruccion" | tr ' ' '\n' | awk 'length>3')
    for palabra in $palabras; do
        local r=$(buscar_en_grafo "$palabra")
        local f=$(extraer_campo "$r" "file_path")
        if [ -n "$f" ]; then
            archivo="$f"
            echo -e "\e[33m🎯 Archivo: $(basename $f)\e[0m"
            break
        fi
    done

    if [ -z "$archivo" ]; then
        echo -e "\e[31m✖ El grafo no encontró ningún archivo relacionado\e[0m"
        return
    fi

    local contenido_completo=$(cat "$archivo")
    local total=$(wc -l < "$archivo")

    echo -e "\e[33m🚀 Paso 1/2: Analizando código...\e[0m"
    local t1=$(date +%s)
    analisis=$(gemini_call "Eres experto en código.

Este es el archivo completo $archivo ($total líneas):
$contenido_completo

Instrucción del usuario: $instruccion

Analiza la instrucción y el archivo:
1. Si la instrucción menciona un elemento de referencia (algo de donde copiar valores, estilos, comportamiento), encuéntralo en el código y extrae sus atributos relevantes tal como aparecen literalmente en el archivo.
2. Encuentra todos los lugares del código donde se debe aplicar el cambio. Pueden ser 1 o varios candidatos.
3. Para cada candidato explica: para qué sirve ese bloque, qué color/estilo tiene actualmente y qué color/estilo tendrá después del cambio.

Responde SOLO con este JSON sin texto adicional ni backticks:
{
  \"referencia\": \"atributos literales del elemento de referencia extraídos del código, o vacío si no hay referencia\",
  \"candidatos\": [
    {
      \"texto_buscar_b64\": \"primeras 2-3 líneas únicas del bloque en base64\",
      \"lineas\": numero_de_lineas_del_bloque_completo,
      \"para_que\": \"para qué sirve este bloque en una línea\",
      \"color_actual\": \"color/estilo actual en palabras simples\",
      \"color_nuevo\": \"color/estilo que tendrá después del cambio\"
    }
  ]
}" \
"responde solo con JSON" "no")
    local t2=$(date +%s)
    echo -e "\e[96m⏱ análisis: $(( t2 - t1 ))s\e[0m"

    local ATMP=$(mktemp)
    printf '%s' "$analisis" > "$ATMP"

    referencia=$(python3 - "$ATMP" <<'PYEOF'
import sys,json,re
t=open(sys.argv[1]).read()
m=re.search(r'\{.*\}',t,re.DOTALL)
if m:
    d=json.loads(m.group())
    print(d.get('referencia',''))
PYEOF
)

    local num_candidatos=$(python3 - "$ATMP" <<'PYEOF'
import sys,json,re
t=open(sys.argv[1]).read()
m=re.search(r'\{.*\}',t,re.DOTALL)
if m:
    d=json.loads(m.group())
    print(len(d.get('candidatos',[])))
else:
    print(0)
PYEOF
)

    if [ -z "$num_candidatos" ] || [ "$num_candidatos" -eq 0 ]; then
        echo -e "\e[31m✖ No encontré elementos relacionados\e[0m"
        echo -ne "\e[36m  r. Reformular | c. Cancelar: \e[0m"
        read opcion < /dev/tty
        rm -f "$ATMP"
        if [ "$opcion" = "r" ]; then
            echo -ne "\e[36mAgrega más detalle: \e[0m"
            read detalle < /dev/tty
            modo_cirujano "$instruccion — $detalle"
        fi
        return
    fi

    # Guardar memoria
    python3 - "$ATMP" "$archivo" "$instruccion" "$MEM" <<'PYEOF'
import sys,json,re
t=open(sys.argv[1]).read()
m=re.search(r'\{.*\}',t,re.DOTALL)
d=json.loads(m.group()) if m else {}
mem={'archivo':sys.argv[2],'referencia':d.get('referencia',''),'analisis':t,'instruccion':sys.argv[3]}
json.dump(mem,open(sys.argv[4],'w'))
PYEOF

    [ -n "$referencia" ] && echo -e "\e[32m📌 Referencia: $referencia\e[0m"

    local texto_buscar=""
    if [ "$num_candidatos" -eq 1 ]; then
        texto_buscar=$(python3 - "$ATMP" <<'PYEOF'
import sys,json,re
t=open(sys.argv[1]).read()
m=re.search(r'\{.*\}',t,re.DOTALL)
if m:
    d=json.loads(m.group()); print(d['candidatos'][0].get('texto_buscar',''))
PYEOF
)
        local desc=$(python3 - "$ATMP" <<'PYEOF'
import sys,json,re
t=open(sys.argv[1]).read()
m=re.search(r'\{.*\}',t,re.DOTALL)
if m:
    d=json.loads(m.group()); print(d['candidatos'][0].get('descripcion',''))
PYEOF
)
        local codigo_preview=$(python3 - "$ATMP" <<'PYEOF'
import sys,json,re
t=open(sys.argv[1]).read()
m=re.search(r'\{.*\}',t,re.DOTALL)
if m:
    d=json.loads(m.group()); print(d['candidatos'][0].get('codigo',''))
PYEOF
)
        echo -e "\e[32m📍 $desc\e[0m"
        echo -e "\e[90m$codigo_preview\e[0m"
    else
        echo -e "\e[33m🔍 Encontré $num_candidatos candidatos:\e[0m"
        python3 - "$ATMP" <<'PYEOF'
import sys,json,re
try:
    t=open(sys.argv[1]).read()
    m=re.search(r'\{.*\}',t,re.DOTALL)
    if not m:
        print("  [error: JSON no encontrado en respuesta]", file=sys.stderr); sys.exit(1)
    d=json.loads(m.group())
    candidatos=d.get('candidatos',[])
    for i,c in enumerate(candidatos):
        print(f'\n  \033[36m{i+1}\033[0m. {c.get("para_que", c.get("descripcion","sin descripción"))}')
        print(f'  Antes:  {c.get("color_actual","")}')
        print(f'  Después:{c.get("color_nuevo","")}')
except Exception as e:
    print(f"  [error: {e}]", file=sys.stderr)
PYEOF
        local nums_candidatos=$(python3 - "$ATMP" <<'PYEOF'
import sys,json,re
t=open(sys.argv[1]).read()
m=re.search(r'\{.*\}',t,re.DOTALL)
if m:
    d=json.loads(m.group())
    n=len(d.get('candidatos',[]))
    print('/'.join(str(i+1) for i in range(n)))
PYEOF
)
        echo -e "\e[94m  r. Reformular   c. Cancelar\e[0m"
        echo -ne "\e[36mElige ($nums_candidatos): \e[0m"
        read opcion < /dev/tty

        if [ "$opcion" = "c" ]; then
            echo -e "\e[33m✖ Cancelado\e[0m"; rm -f "$ATMP"; return
        elif [ "$opcion" = "r" ]; then
            echo -ne "\e[36mAgrega más detalle: \e[0m"
            read detalle < /dev/tty
            rm -f "$ATMP"
            modo_cirujano "$instruccion — $detalle"; return
        elif [[ "$opcion" =~ ^[0-9]+$ ]] && [ "$opcion" -ge 1 ] && [ "$opcion" -le "$num_candidatos" ]; then
            texto_buscar=$(python3 - "$ATMP" "$((opcion-1))" <<'PYEOF'
import sys,json,re,base64
t=open(sys.argv[1]).read()
m=re.search(r'\{.*\}',t,re.DOTALL)
if m:
    d=json.loads(m.group())
    c=d['candidatos'][int(sys.argv[2])]
    b64=c.get('texto_buscar_b64','')
    print(base64.b64decode(b64).decode('utf-8') if b64 else c.get('texto_buscar',''))
PYEOF
)
            num_lineas=$(python3 - "$ATMP" "$((opcion-1))" <<'PYEOF'
import sys,json,re
t=open(sys.argv[1]).read()
m=re.search(r'\{.*\}',t,re.DOTALL)
if m:
    d=json.loads(m.group()); print(d['candidatos'][int(sys.argv[2])].get('lineas',25))
PYEOF
)
        else
            echo -e "\e[31m✖ Opción inválida\e[0m"; rm -f "$ATMP"; return
        fi
    fi

    rm -f "$ATMP"
    linea_ini=$(grep -Fn "$texto_buscar" "$archivo" 2>/dev/null | head -1 | cut -d: -f1)
    [ -z "$linea_ini" ] && echo -e "\e[31m✖ No se encontró el bloque en el archivo\e[0m" && return
    local num_lineas=${num_lineas:-25}
    linea_fin=$((linea_ini + num_lineas - 1))
    local codigo_actual=$(sed -n "${linea_ini},${linea_fin}p" "$archivo")

    echo -e "\e[33m✏️  Paso 2/2: Generando código...\e[0m"
    local t3=$(date +%s)
    local codigo_nuevo=$(gemini_call "Eres cirujano de código.
Tu única tarea es modificar EXACTAMENTE el siguiente bloque de código según la instrucción.
No toques nada fuera de este bloque. No agregues elementos nuevos. Solo modifica lo que la instrucción pide.

Código a modificar:
$codigo_actual

Valores de referencia extraídos del código real:
$referencia

Instrucción: $instruccion

Devuelve SOLO el bloque modificado codificado en base64 en una sola línea. Sin texto adicional." \
"devuelve solo base64 del código modificado" "no")
    local t4=$(date +%s)
    echo -e "\e[96m⏱ generación: $(( t4 - t3 ))s\e[0m"

    if [ -z "$codigo_nuevo" ]; then
        echo -e "\e[31m✖ No se pudo generar código nuevo\e[0m"; return
    fi

    # Decodificar base64 — si falla, usar limpieza de texto plano
    codigo_nuevo=$(echo "$codigo_nuevo" | tr -d '\n' | python3 -c "
import sys,base64,re
raw=sys.stdin.read().strip()
m=re.search(r'[A-Za-z0-9+/]{20,}={0,2}', raw)
if m:
    try:
        print(base64.b64decode(m.group()).decode('utf-8')); sys.exit(0)
    except: pass
lines=raw.splitlines()
start=0
for i,l in enumerate(lines):
    if re.match(r'^[\s]*(val |var |fun |if |btn|//|/\*|\}|\{|[a-z][a-zA-Z]*\s*[=.(])',l):
        start=i; break
print('\n'.join(lines[start:]))
")

    echo -e "\e[33m👀 Preview:\e[0m"
    echo -e "\e[31m--- ANTES ---\e[0m"
    echo "$codigo_actual"
    echo -e "\e[32m+++ DESPUÉS +++\e[0m"
    echo "$codigo_nuevo"
    echo ""
    echo -ne "\e[36m¿Aplicar? (y/n): \e[0m"
    read confirm < /dev/tty

    if [[ "$confirm" == "y" || "$confirm" == "Y" ]]; then
        local tmp=$(mktemp)
        head -n $((linea_ini-1)) "$archivo" > "$tmp"
        echo "$codigo_nuevo" >> "$tmp"
        tail -n +$((linea_fin+1)) "$archivo" >> "$tmp"
        mv "$tmp" "$archivo"
        echo -e "\e[32m✔ Cambio aplicado\e[0m"
        rm -f "$MEM"
    else
        echo -e "\e[33m✖ Cancelado\e[0m"
    fi

}

trap 'echo -e "\n\e[90mLimpiando sesión...\e[0m"; rm -f "$MEM"; exit 0' INT TERM

while true; do
    echo ""
    echo -ne "\e[36m💡> \e[0m"
    read input

    cmd=$(echo "$input" | tr '[:upper:]' '[:lower:]' | awk '{print $1}')
    rest=$(echo "$input" | cut -d' ' -f2-)
    [ "$rest" = "$input" ] && rest=""

    case "$cmd" in
    fix|diff|analyze|commit|raw|modelo) ;;
    *) cmd="normal"; rest="$input" ;;
    esac

    APPLY_MODE=false

    case "$cmd" in
    fix)
        echo -e "\e[35m[FIX]\e[0m"
        modo_cirujano "$rest"
        ;;
    diff)
        echo -e "\e[35m[DIFF]\e[0m"
        APPLY_MODE=true
        prompt="Genera un parche tipo unified diff (.patch) mínimo y quirúrgico. SOLO devuelve diff válido:\n$rest"
        ;;
    analyze)
        echo -e "\e[35m[ANALYZE]\e[0m"
        prompt="Analiza este problema de código y explica la causa raíz sin proponer cambios innecesarios: $rest"
        ;;
    commit)
        echo -e "\e[35m[COMMIT]\e[0m"
        prompt="Genera un mensaje de commit claro y profesional basado en este cambio: $rest"
        ;;
    raw)
        echo -e "\e[35m[RAW]\e[0m"
        prompt="$rest"
        ;;
    modelo)
        echo -e "\e[35m[modelo actual: $MODELO_ACTUAL]\e[0m"
        echo "Disponibles:"
        for i in "${!MODELOS[@]}"; do
            [ "${MODELOS[$i]}" = "$MODELO_ACTUAL" ] \
                && echo -e "  \e[32m→ $((i+1)). ${MODELOS[$i]} ✔\e[0m" \
                || echo "     $((i+1)). ${MODELOS[$i]}"
        done
        echo -ne "\e[36m¿Cambiar? (número o enter para no): \e[0m"
        read sel < /dev/tty
        if [[ "$sel" =~ ^[1-6]$ ]]; then
            MODELO_ACTUAL="${MODELOS[$((sel-1))]}"
            echo "$MODELO_ACTUAL" > "$MODEL_FILE"
            echo -e "\e[32m✔ Modelo cambiado a $MODELO_ACTUAL\e[0m"
        fi
        continue
        ;;
    *)
        prompt="$input"
        ;;
    esac

    echo "USER: $prompt" >> $HIST

    FULL_PROMPT="$(cat $CTX 2>/dev/null)

[HISTORIAL]
$(tail -n 20 $HIST)

[INSTRUCCIÓN ACTUAL]
$prompt

[RESPUESTA]
Responde estrictamente según el modo activo."

    if [ "$cmd" != "fix" ]; then
        echo -e "\e[33m⏳ pensando...\e[0m"
        t1=$(date +%s)
        response=$(gemini_call "$FULL_PROMPT" "responde" "no")
        t2=$(date +%s)
        echo -e "\e[32m$response\e[0m"
        echo -e "\e[96m⏱ $(( t2 - t1 ))s\e[0m"
        echo "AI: $response" >> $HIST
    fi

    if [ "$APPLY_MODE" = true ]; then
        PATCH_FILE="$HOME/.gemini_pro/auto_patch_$(date +%s).patch"
        CLEAN_DIFF=$(echo "$response" | sed 's/```[a-z]*//g' | sed -n '/^--- /,$p')
        if [ -z "$CLEAN_DIFF" ]; then
            echo -e "\e[31m✖ no se generó diff válido\e[0m"
        else
            echo "$CLEAN_DIFF" > "$PATCH_FILE"
            echo -e "\e[33m👀 vista previa:\e[0m"
            cat "$PATCH_FILE"
            echo ""
            patch --dry-run -p1 < "$PATCH_FILE" > /dev/null 2>&1
            if [ $? -ne 0 ]; then
                echo -e "\e[31m✖ patch inválido\e[0m"
            else
                echo -e "\e[32m✔ patch válido\e[0m"
                echo -ne "\e[36m¿Aplicar patch? (y/n): \e[0m"
                read confirm < /dev/tty
                if [[ "$confirm" == "y" || "$confirm" == "Y" ]]; then
                    patch -p1 < "$PATCH_FILE" 2>/dev/null
                    if [ $? -eq 0 ]; then
                        echo -e "\e[32m✔ patch aplicado\e[0m"
                    else
                        echo -e "\e[31m✖ error al aplicar\e[0m"
                    fi
                else
                    echo -e "\e[33m✖ cancelado\e[0m"
                fi
            fi
        fi
    fi

done
