===========================================================
        CHESZ - ESPECIFICACIÓN MAESTRA COMPLETA (V2)
===========================================================
Estado: DEFINIDO Y BLOQUEADO | Modelo: Determinista Humano
Arquitectura: ZERO-JUMP PURO
-----------------------------------------------------------

0. PROPÓSITO DEL DOCUMENTO
Este documento define el comportamiento oficial del overlay de CHESZ.
Garantiza continuidad técnica sin rediseños improvisados ni lógica
"mágica".

-----------------------------------------------------------
1. PRINCIPIO FILOSÓFICO CENTRAL
-----------------------------------------------------------
- EL USUARIO ES UN SER HUMANO.
- Si algo no cabe, el usuario debe mover el botón.
- SI NO CABE -> NO ABRE.
- No existen ajustes invisibles.

-----------------------------------------------------------
2. IDENTIDAD VISUAL Y RECURSOS
-----------------------------------------------------------
- Launcher: ~/chesz/iconos/launcher.png
- Botón Overlay: ~/chesz/iconos/boton.png (60dp)
- Calidad: Imágenes limpias, sin marcos blancos o negros.

-----------------------------------------------------------
3. ARQUITECTURA DE OVERLAY (ZERO-JUMP PURO)
-----------------------------------------------------------

EXISTE UN SOLO OVERLAY PRINCIPAL:

- root (WindowManager overlay)
- Tamaño FIJO: 60dp x 60dp
- Posición FIJA al hacer toggle
- Nunca se redimensiona al abrir panel
- Nunca se mueve automáticamente

El panel:
- Vive dentro del root
- Se posiciona con márgenes internos
- Puede extenderse visualmente hacia arriba
- NO modifica rootLp.x
- NO modifica rootLp.y
- NO modifica rootLp.width
- NO modifica rootLp.height

Resultado:
El botón nunca salta.
El botón nunca parpadea.
El overlay nunca cambia de tamaño al abrir panel.

-----------------------------------------------------------
4. REGLA DE APERTURA (VALIDACIÓN PROYECTADA)
-----------------------------------------------------------

Antes de mostrar el panel se calcula un rectángulo PROYECTADO
(sin mover el overlay real).

Dimensiones:
    panelW = 60% del ancho de pantalla
    panelH = 25% del alto de pantalla
    bw = 60dp
    bh = 60dp

Rectángulo proyectado:
    projectedLeft   = buttonX
    projectedTop    = buttonY - (panelH - bh)
    projectedRight  = buttonX + (panelW + bw/2)
    projectedBottom = buttonY + bh

Si el rectángulo proyectado sale de pantalla:

    - El botón se vuelve ROJO temporalmente.
    - El panel NO abre.

El usuario debe mover el botón manualmente.

-----------------------------------------------------------
5. ESTADOS
-----------------------------------------------------------

ESTADO A – BOTÓN SOLO
- Botón circular 60dp.
- Arrastre libre.
- Tap inicia captura + expansión a Estado B.

ESTADO B – BOTÓN + PANEL
- Panel aparece a la derecha.
- Solapamiento 50%.
- Ancho 60% pantalla.
- Alto 25% pantalla.
- Fondo negro translúcido.
- Texto gris.
- Botón Close interno.

-----------------------------------------------------------
6. KILL SWITCH
-----------------------------------------------------------

Overlay independiente inferior:
- Zona roja circular con X blanca.
- Efecto succión.
- Vibración.
- Destruye servicio.

-----------------------------------------------------------
7. FLUJO DE DESARROLLO (REMOTO)
-----------------------------------------------------------

Scripts oficiales:

- check.sh
- ciclo.sh
- vigilante.sh
- fisgon.sh (full | targets)

Compilación exclusivamente vía GitHub Actions.

-----------------------------------------------------------
HOJA DE RUTA
-----------------------------------------------------------

1) Limpieza técnica
2) Botón flotante estable
3) Kill switch
4) Geometría panel validada
5) Maquetación
6) Integración FEN/API

===========================================================
