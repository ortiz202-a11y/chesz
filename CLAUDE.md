<!-- code-review-graph MCP tools -->
## 🧠 Grafo de código (MCP)

### ⚡ Regla de eficiencia

**✅ USA EL GRAFO** cuando necesites explorar/buscar:
- "¿Dónde está X?" → `semantic_search_nodes` o `query_graph` (evita Grep en todo el repo)
- "¿Qué afecta si cambio Y?" → `get_impact_radius` (evita leer 10+ archivos)
- "¿Quién llama a Z?" → `query_graph` con callers_of (evita rastreo manual)

**❌ USA READ DIRECTO** cuando ya tengas el path exacto:
- Si sabes: `app/src/main/java/X.kt` línea 123
- Read directo = **1 llamada, mínimo tokens**
- Grafo + Read = **2 llamadas, más tokens** ❌

### Cuándo usar el grafo PRIMERO

- **Explorar código**: `semantic_search_nodes` o `query_graph` en lugar de Grep
- **Entender impacto**: `get_impact_radius` en lugar de rastrear imports manualmente
- **Revisión de código**: `detect_changes` + `get_review_context` en lugar de leer archivos enteros
- **Relaciones**: `query_graph` con callers_of/callees_of/imports_of/tests_for
- **Arquitectura**: `get_architecture_overview` + `list_communities`

### Herramientas clave

| Herramienta | Cuándo usarla |
|---|---|
| `detect_changes` | Revisar cambios — análisis con puntuación de riesgo |
| `get_review_context` | Obtener fragmentos de código — eficiente en tokens |
| `get_impact_radius` | Entender el impacto de un cambio |
| `get_affected_flows` | Ver qué flujos de ejecución se afectan |
| `query_graph` | Rastrear callers, callees, imports, tests, dependencias |
| `semantic_search_nodes` | Encontrar funciones/clases por **lenguaje natural** (embeddings Gemini activos) |
| `get_architecture_overview` | Entender la estructura general del proyecto |
| `refactor_tool` | Planear renombrados, encontrar código muerto |

### 🔍 Búsqueda semántica (lenguaje natural)
`semantic_search_nodes` usa embeddings de **Gemini** (no sentence-transformers — no compila en Termux).
- Busca con frases en español: `"mover pieza"`, `"guardar favorito"`, `"notificación flotante"`
- Si se reconstruye el grafo (`code-review-graph build`), re-embedear con:
  ```
  GOOGLE_API_KEY=<key> python3 -c "from code_review_graph.tools.docs import embed_graph; embed_graph()"
  ```
- Key configurada en `/data/data/com.termux/files/home/chesz/.mcp.json`

## 🤖 Agentes y Skills
Para elegir el mejor sin gastar tokens: lee `~/.claude/INDEX.md`

## 🚨 Si faltan archivos
Revisa primero: `git stash list`. Si muestra algo, corre `git stash pop`.
