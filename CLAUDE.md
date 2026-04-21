<!-- code-review-graph MCP tools -->
## MCP Tools: code-review-graph

**CRITICAL: This project has a knowledge graph. You MUST ALWAYS use the
code-review-graph MCP tools as your FIRST action before using Grep/Glob/Read
to explore the codebase.** The graph is faster, cheaper (fewer tokens), and gives
you structural context (callers, dependents, test coverage) that file scanning cannot.

**NEVER skip the graph tools. Start with them, then use Grep/Glob/Read only if needed.**

### When to use graph tools FIRST

- **Exploring code**: `semantic_search_nodes` or `query_graph` instead of Grep
- **Understanding impact**: `get_impact_radius` instead of manually tracing imports
- **Code review**: `detect_changes` + `get_review_context` instead of reading entire files
- **Finding relationships**: `query_graph` with callers_of/callees_of/imports_of/tests_for
- **Architecture questions**: `get_architecture_overview` + `list_communities`

Only use Grep/Glob/Read AFTER consulting the graph, and only if the graph doesn't provide what you need.

### Key Tools

| Tool | Use when |
|------|----------|
| `detect_changes` | Reviewing code changes — gives risk-scored analysis |
| `get_review_context` | Need source snippets for review — token-efficient |
| `get_impact_radius` | Understanding blast radius of a change |
| `get_affected_flows` | Finding which execution paths are impacted |
| `query_graph` | Tracing callers, callees, imports, tests, dependencies |
| `semantic_search_nodes` | Finding functions/classes by name or keyword |
| `get_architecture_overview` | Understanding high-level codebase structure |
| `refactor_tool` | Planning renames, finding dead code |

## Si faltan archivos
Revisa primero: `git stash list`. Si muestra algo, corre `git stash pop`.
