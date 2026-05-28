# ESTADO CHESZ — últimos 5 builds

| # | Commit | Qué funciona | Qué falta / bugs |
|---|---|---|---|
| 5 | `ceb6933` 28/05 | Flujo permisos completo: overlay→AppInfo→A11y→ScreenCapture automático. `BubbleService.isRunning` flag. `noHistory` removido. | Sin probar en dispositivo aún. |
| 4 | `83e1d74` | (fix fix — sin info) | — |
| 3 | `720889b` | Fix varios | — |
| 2 | `a21eb6a` 25/05 | Pipeline A11y→FEN→análisis completo. Fix `onInterrupt` no borra instance. `hasPerm=true`. | — |
| 1 | `a43067a` 24/05 | Overlay flotante, panel, drag, kill area. | Permisos no fluían automático. |

---
**Modo activo:** A11y tree reading (`CAPTURA_FOTO_ENABLED=true` pero A11y es primer intento, MP es fallback)  
**Rama:** `estable-gemini`  
**Pendiente:** Probar flujo permisos en dispositivo · API ChessDb sin implementar
