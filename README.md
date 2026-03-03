Perfecto.
Aquí tienes el README completamente formateado, coherente y listo para guardar, respetando tu especificación maestra y agregando la implementación Sshot/Fen/Ai/Done como bloque formal dentro del documento.

Puedes reemplazar tu README completo por esto.


---

===========================================================

CHESZ - ESPECIFICACIÓN MAESTRA COMPLETA (V3)

===========================================================

Estado: DEFINIDO Y BLOQUEADO
Modelo: Determinista Humano
Arquitectura: ZERO-JUMP PURO


---

0. PROPÓSITO DEL DOCUMENTO

Este documento define el comportamiento oficial del overlay de CHESZ.

Garantiza continuidad técnica sin rediseños improvisados, sin lógica mágica, sin comportamientos ocultos.

Todo comportamiento debe ser explícito, proyectado y validado antes de ejecutarse.


---

1. PRINCIPIO FILOSÓFICO CENTRAL

EL USUARIO ES UN SER HUMANO.

Si algo no cabe, el usuario debe mover el botón.

SI NO CABE → NO ABRE.

No existen ajustes invisibles.

No existen reposicionamientos automáticos.

No existe lógica correctiva silenciosa.



---

2. IDENTIDAD VISUAL Y RECURSOS

Launcher: ~/chesz/iconos/launcher.png

Botón Overlay: ~/chesz/iconos/boton.png

Tamaño botón: 60dp

Sin marcos blancos.

Sin marcos negros.

Imagen limpia, ajustada, centrada.



---

3. ARQUITECTURA DE OVERLAY (ZERO-JUMP PURO)

Overlay principal único

Existe UN SOLO overlay principal:

root (WindowManager overlay)

Tamaño FIJO: 60dp x 60dp

Posición FIJA al hacer toggle

Nunca se redimensiona

Nunca se mueve automáticamente


Panel

Vive dentro del root

Se posiciona con márgenes internos

Puede extenderse visualmente hacia arriba

NO modifica:

rootLp.x

rootLp.y

rootLp.width

rootLp.height



Resultado obligatorio

El botón nunca salta

El botón nunca parpadea

El overlay nunca cambia tamaño al abrir panel



---

4. REGLA DE APERTURA (VALIDACIÓN PROYECTADA)

Antes de mostrar el panel se calcula un rectángulo PROYECTADO.

Sin mover overlay real.

Dimensiones

panelW = 55% ancho pantalla
panelH = 15% alto pantalla
bw = 60dp
bh = 60dp

Rectángulo proyectado

projectedLeft   = buttonX
projectedTop    = buttonY - (panelH - bh)
projectedRight  = buttonX + (panelW + bw/2)
projectedBottom = buttonY + bh

Si el rectángulo sale de pantalla

El botón se vuelve rojo temporalmente

El panel NO abre

El usuario debe mover el botón manualmente


Nunca se ajusta automáticamente.


---

5. ESTADOS VISUALES

ESTADO A – BOTÓN SOLO

Botón circular 60dp

Arrastre libre

Tap inicia ciclo Sshot

Sin panel visible


ESTADO B – BOTÓN + PANEL

Panel aparece a la derecha

Solapamiento 50%

Ancho: 55% pantalla

Alto: 15% pantalla

Fondo negro translúcido

Texto gris

Botón Close interno



---

6. KILL SWITCH

Overlay independiente inferior:

Zona roja circular

X blanca centrada

Efecto succión

Vibración

Destruye servicio completamente



---

7. IMPLEMENTACIÓN Sshot/Fen/Ai/Done

Objetivo

Implementar ciclo completo del botón flotante para:

1. Capturar pantalla (Sshot)


2. Obtener FEN desde Web API


3. Enviar FEN a AI


4. Renderizar resultados




---

Regla Principal del Ciclo

El panel se mantiene abierto durante todo el ciclo

El tap siempre reinicia el proceso

No se cierra/abre panel para reiniciar

Toda respuesta vieja se ignora mediante runId



---

Estados del Title

Estado	Significado

Sshot	Captura solicitada
Sshot/Fen	Screenshot enviado a FEN API
Sshot/Fen/Ai	FEN recibido, enviado a AI
Sshot/Fen/Ai/Done	Resultado AI renderizado



---

Flujo Técnico Completo

0) Tap = START / RESTART

En cualquier estado:

0.1 Cancelación dura

Cancelar captura

Cancelar FEN API

Cancelar AI API

Incrementar currentRunId

Limpiar UI

Mantener panel abierto si ya lo estaba


0.2 Inicio inmediato

Asegurar panel visible

Title → Sshot

Iniciar captura



---

1) Captura → Sshot

Ejecutar MediaProjection

Guardar screenshot temporal


Si falla → error captura
Si OK → paso 2


---

2) FEN API → Sshot/Fen

Cambiar title

Enviar screenshot


Si falla → error FEN
Si OK → paso 3


---

3) Recibir FEN → borrar screenshot → AI → Sshot/Fen/Ai

Regla:

El screenshot solo se borra cuando se recibe FEN.

Guardar FEN

Borrar imagen

Enviar FEN a AI



---

4) Recibir AI → Render → Done

Procesar respuesta

Reemplazar contenido panel

Title → Done


Tap reinicia todo.


---

Control de Concurrencia

Cada ciclo:

currentRunId++

Cada callback:

if (runId != currentRunId) return

Evita:

Respuestas viejas

Estados corruptos

Mezcla de ciclos



---

Permisos

Overlay

Si no existe permiso:

Mostrar Activity

No iniciar ciclo


Captura (MediaProjection)

Si no existe permiso:

Abrir panel

Title → Sshot

Mostrar botón “Conceder permiso”

Lanzar flujo sistema

Tras conceder → siguiente tap inicia ciclo



---

Manejo de Errores

Error Captura

Mostrar mensaje

Panel abierto

Tap reinicia


Error FEN

Mostrar mensaje

Screenshot no se borra

Tap reinicia


Error AI

Mostrar mensaje

Screenshot ya borrado

Tap reinicia



---

8. ORDEN DE IMPLEMENTACIÓN

FASE 1 – Permisos + Screenshot

1. Verificar Overlay


2. Crear CapturePermissionActivity


3. Integrar MediaProjection


4. Implementar startCycle()


5. Implementar captureScreenshot()




---

FASE 2 – FEN API

Subir screenshot

Recibir FEN

Borrar screenshot



---

FASE 3 – AI API

Enviar FEN

Recibir análisis

Renderizar

Done



---

FASE 4 – Pruebas de Estrés

Tap rápido repetido

Cancelaciones simultáneas

Validación runId

Sin ghost

Sin saltos

Sin estados cruzados



---

RESULTADO FINAL ESPERADO

Tap →
Sshot →
Sshot/Fen →
Sshot/Fen/Ai →
Sshot/Fen/Ai/Done →
Tap reinicia

Sin saltos.
Sin mezcla de estados.
Sin comportamientos mágicos.
Determinista.


--------------------------------------
--------------------------------------
