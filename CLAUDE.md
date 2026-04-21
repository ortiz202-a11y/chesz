<!-- code-review-graph MCP tools -->
## 🧠 Grafo de código (MCP)

**CRÍTICO: Este proyecto tiene un grafo de dependencias. SIEMPRE úsalo PRIMERO antes de Grep/Glob/Read.**
El grafo es más rápido, más barato (menos tokens) y da contexto estructural que el escaneo de archivos no puede dar.

**NUNCA omitas el grafo. Empieza con él, luego usa Grep/Glob/Read solo si es necesario.**

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
| `semantic_search_nodes` | Encontrar funciones/clases por nombre o concepto |
| `get_architecture_overview` | Entender la estructura general del proyecto |
| `refactor_tool` | Planear renombrados, encontrar código muerto |

## 🤖 Agentes y Skills
Para elegir el mejor sin gastar tokens: lee `~/.claude/INDEX.md`

## 🚨 Si faltan archivos
Revisa primero: `git stash list`. Si muestra algo, corre `git stash pop`.
