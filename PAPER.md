# 📋 ESTADO ACTUAL — CHESZ (mayo 2026)

## 🟢 Qué funciona hoy (rama `estable-gemini`)

| Componente | Estado |
|---|---|
| Overlay flotante (BubbleService) | ✅ Inicia, se posiciona, muestra panel |
| Permiso A11y detectado (`hasPerm=true`) | ✅ |
| Lectura del tablero via A11y (`ChessboardA11yService`) | ✅ Conecta — **ver fix abajo** |
| Envío FEN → BubbleService → análisis | ✅ Pipeline completo (tras fix) |
| Captura por foto (`CAPTURA_FOTO_ENABLED`) | ❌ Desactivado (`false`) — modo A11y activo |
| Overlay mostrando resultado tras leer | ⚠️ Pendiente verificar tras fix |

## 🔴 Bug crítico corregido (2026-05-25)

**`onInterrupt()` borraba `instance` → overlay se quedaba en "LEYENDO" para siempre**

- **Síntoma:** `lastFen=null` en todos los `togglePanel`. El overlay mostraba "LEYENDO" pero nunca mostraba posición ni análisis. Sin logs de A11y tras `onServiceConnected`.
- **Causa raíz:** `ChessboardA11yService.onInterrupt()` hacía `instance = null`. Android llama `onInterrupt()` al cambiar de foco/overlay — el servicio seguía vivo pero `requestImmediateRead()` se convertía en no-op silencioso.
- **Fix:** Quitar `instance = null` de `onInterrupt()`. Agregar log en `requestImmediateRead()` para detectar instancia nula.
- **Archivo:** `app/src/main/java/com/chesz/floating/ChessboardA11yService.kt`

---

# 📋 PAPER CHESZ - KANBAN (FASE API)

## ✅ TERMINADO

## 🛠️ PENDIENTES
- [ ] Implementar la llamada a la API de ChessDb desde el servidor.
- [ ] Parsear la respuesta (Extraer Jugada y Apertura).
- [ ] Enviar el texto final a la Consola Debug de la App (#33FF00).
# 📋 PAPER CHESZ

## 🛠️ EN PROCESO
- [x] Validar botón de 60dp y visibilidad (excludeFromRecents)
- [ ] Probar autocuración del Supervisor de ADN

## ✅ TERMINADO
- [x] Identidad com.chesz unificada
- [x] Launcher: Se migró de CENTER_CROP a FIT_XY para forzar la expansión total de la imagen 

- [ ] ⚠️ FALLO: Detenido por Check/ADN (19:33)
- [ ] ⚠️ FALLO: Detenido por Check/ADN (23:21)
- [ ] ⚠️ FALLO: Detenido por Check/ADN (23:24)

- [ ] PENDIENTE: Regla absoluta Estado B: ¿rootLp.y/size puede ajustarse determinísticamente o root queda anclado al botón y panel se dibuja sin mover y?
- [ ] ⚠️ FALLO: Detenido por Check/ADN (21:22)
