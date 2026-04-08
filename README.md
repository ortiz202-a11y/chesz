 
===========================================================

CHESZ - ESPECIFICACIÓN MAESTRA COMPLETA (V3)

===========================================================

Estado: DEFINIDO Y BLOQUEADO
Modelo: Determinista Humano
Arquitectura: la Ai sigue ordenes


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

panelW = 60% ancho pantalla
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


Procesar respuesta

Reemplazar contenido panel

Consola → Finalizado


Tap reinicia todo.


---


---

### 🖥️ CONSOLA DE DIAGNÓSTICO (debugText)
El sistema utiliza un área de texto dedicada para informar al usuario en tiempo real sin estados crípticos.

1. **Estado Inicial**: Consola limpia / Esperando.
2. **Proceso de Captura**: ⚙️ Iniciando motor de captura...
3. **Bloqueo de Sistema**: ❌ Android bloqueó el acceso (Falta Permiso).
4. **Fallo Técnico**: ❌ Error: El motor de captura falló al arrancar.
5. **Fallo de Archivos**: 📂 Error: No se pudo guardar la foto en /Pictures.

---

### ⚙️ FLUJO TÉCNICO ACTUALIZADO
0) **Tap = START / RESTART**
   - Se activa el candado lógico de 3 segundos (isCapturing).
   - El panel se mantiene abierto al 20% de altura para visibilidad.
   - Mensaje: Iniciando motor...

1) **Captura → Sshot**
   - Ejecutar MediaProjection (Motor de Android).
   - Captura de Frame mediante ImageReader.
   - Guardado en: /Android/data/com.chesz/files/Pictures/chesz_last.png.

2) **Procesamiento**
   - Se envía la imagen a la API de FEN (Pendiente).
   - Se recibe el análisis de la AI (Pendiente).
   - La Consola informa el éxito o el error específico de la etapa.

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

Consola → Esperando

Mostrar botón “Conceder permiso”

Lanzar flujo sistema y cierra el panel

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
Captura → Procesamiento → Done
Tap reinicia

Sin saltos.
Sin mezcla de estados.
Sin comportamientos mágicos.
Determinista.


--------------------------------------
--------------------------------------


-----------------------------------------------------------
CAPA DE PERMISO – MEDIA PROJECTION (SSHOT)
-----------------------------------------------------------

Filosofía
- El permiso NO es automático.
- El usuario lo otorga desde el panel.
- ZERO-JUMP se mantiene.

Ciclo
1. Usuario toca "Permitir Screenshot" (botón blanco).
2. Panel se cierra.
3. Se solicita permiso mediante CapturePermissionActivity.
4. Al regresar:
   - Si permiso OK → el botón blanco desaparece.
   - El panel queda listo para Screenshot.

Regla de Tap en Burbuja
- Con panel cerrado → abre panel (si cabe).
- Con panel abierto → NO cierra panel.
- Si permiso válido → toma screenshot.
- Tap siempre reinicia ciclo de captura.

Vida del Permiso
- Válido mientras BubbleService esté vivo.
- Si proceso muere o reinicia dispositivo → debe solicitarse nuevamente.

Botón Permitir Screenshot
- Altura: 50dp
- Fondo: Negro
- Corner radius: 12dp
- Asset: permit_icon.png
- Icono: ✓ verde (#22C55E)
- Se oculta automáticamente cuando el permiso es válido.

--------------------------------------------------------------------------------------------------
se esta utlizando la funciom git diff desoues de cada cambio oaea veeificar canbios si no pasa se reviza hasta avanzar de modo que se tenfa rotal control sobre los cambios efecruados


--------------------------------------> 


ya estoy por terminar la conecion con 
el servidor.

En algun momento se podra alterar el tamaño del panel y de la letra pe4o dw momento esta prohibido.


---
# 📜 ADENDA DE SOBERANÍA V3.2 (ACTUALIZACIÓN ESTRICTA)
# Fecha: 2026-03-13 | Estado: BLOQUEADO

## 5.1 REGLAS FÍSICAS DE INTERFAZ (SOBRESCRIBE SECCIONES PREVIAS)
* **Marco Perimetral**: 
    * Grosor: 3dp.
    * Color: Verde Terminal (#33FF00).
    * Forma: Esquinas perfectamente cuadradas (0dp radius).
* **Altura Dinámica**: Se autoriza rango de 15% a 20% de pantalla (para evitar truncado de logs).
* **Anclaje Y**: Se fija rootLp.y = dp(167) de forma determinista.

## 5.2 CONSOLA DEBUG
* **Fuente**: perfect_dos_vga.ttf (#33FF00).
* **Lógica**: GONE por defecto, visible únicamente durante el ciclo de API.

--------------------------------------
SE AGREGO UN MODO DEBUG O MODO DIOS PARA HACER PING Y O REINICIAR EL SERBIDOR 
TAMBIEN UN BOTON PARA PRUEBAS DEL FEN
EXISTE UN BOTON TEST FEN QUE LAMZA UNA SERIE DE PRUEBAS QUE SE COMPARAN CON IMAGENES ALMACENADASCON FEN VERIFICADOS
# CHESZ


## FLUJO DE DESARROLLO CON CLI DE IA

El proyecto se desarrolla desde Termux usando dos CLI de IA instalados localmente.
El principal es **Claude Code** (`claude`), usado para razonamiento, edición de código y commits.
El segundo CLI (Gemini CLI) está instalado y disponible como herramienta auxiliar.
Ambos operan directamente sobre el repo sin entorno gráfico.

---

## Agentes y Templates instalados

Agentes especializados disponibles para usar con Claude Code en este proyecto:

| Agente | Para qué sirve en CHESZ |
|---|---|
| **android-ninja** | Genera y mantiene código Android production-ready: Compose, MVVM, Hilt, Room. Úsalo para scaffolding de nuevas features o revisión de arquitectura. |
| **code-reviewer** | Revisa PRs y diffs buscando bugs, problemas de seguridad y violaciones de las reglas del README. Ideal antes de cada commit importante. |
| **unused-code-cleaner** | Detecta y elimina código muerto: variables sin usar, imports, funciones obsoletas. Útil tras refactors de FenEngine o BubbleService. |
| **performance-profiler** | Analiza cuellos de botella en el procesamiento de imágenes (Canny, template matching, extractSquare). Identifica qué operaciones bloquean el hilo principal. |
| **refactoring-specialist** | Reestructura código complejo manteniendo comportamiento. Úsalo para limpiar `detectPiece`, `resolveByHeight` o la lógica del ciclo de captura. |
| **error-detective** | Investiga bugs específicos rastreando el flujo de ejecución en el código fuente. Úsalo cuando una casilla devuelve la pieza incorrecta y necesitas saber por qué. |
| **debugger** | Analiza logs de runtime e identifica el estado exacto del sistema en el momento del fallo. Complementa a error-detective cuando hay logs disponibles. |
| **test-automator** | Genera tests instrumentados y unitarios para FenEngine, verificando detección de piezas contra FENs conocidos (integra con el botón TEST FEN existente). |
