===========================================================
        CHESZ - ESPECIFICACIÓN MAESTRA COMPLETA (V1)
===========================================================
Estado: DEFINIDO Y BLOQUEADO | Modelo: Determinista Humano
-----------------------------------------------------------

0. PROPÓSITO DEL DOCUMENTO
Este documento define el comportamiento oficial del overlay de CHESZ. 
Garantiza que cualquier persona (o IA) pueda continuar el proyecto 
siguiendo una ruta clara de acciones y protecciones, evitando 
comportamientos "mágicos" o rediseños improvisados.

1. PRINCIPIO FILOSÓFICO CENTRAL
- EL USUARIO ES UN SER HUMANO: No necesita ajustes invisibles. Si 
  algo no cabe, el usuario debe moverlo.
- SI NO CABE -> NO ABRE: Comportamiento lógico y consistente.

2. IDENTIDAD VISUAL Y RECURSOS
- Launcher: ~/chesz/iconos/launcher.png
- Botón Overlay: ~/chesz/iconos/boton.png (80dp)
- Calidad: Imágenes limpias, sin marcos blancos o negros.

3. ESTADOS Y GEOMETRÍA DEL OVERLAY
ESTADO A – BOTÓN SOLO:
- Botón circular (80dp). Arrastrable 100% (sin magnetismo).
- Tap: Inicia captura y expansión al Estado B.

ESTADO B – BOTÓN + PANEL:
- Panel abre a la derecha del botón. 
- Solapamiento: 50% del botón invade el panel (ancla inferior izq).
- Dimensiones: Ancho 60% / Alto 25% de la pantalla.
- Apariencia: Fondo negro translúcido, letras grises.

4. REGLA DE APERTURA (LÓGICA DETERMINISTA)
Cálculo de posición del Root (Contenedor):
   rootX = buttonX
   rootY = buttonY - (panelH - buttonH)
   rootW = panelW + (buttonW / 2)
   rootH = panelH
- REGLA: Si el Root se sale de la pantalla, el botón cambia a ROJO 
  temporalmente y el panel NO abre.

5. FLUJO DE ANÁLISIS Y SERVICIOS WEB
1. Tap en Botón -> Transparencia 100%.
2. Screenshot -> Envío a servicio web (FEN/Motor de Ajedrez).
3. Restauración -> Botón recupera imagen normal.
4. Indicadores de Proceso (Top Panel):
   Sshot/ -> Sshot/Fen -> Sshot/Fen/Ai -> Sshot/Fen/Ai/Done
5. Área Central: Resultados de táctica/apertura.
6. Área Inferior: Botón "Close" minimalista (vuelve a Estado A).

6. KILL SWITCH (CIERRE)
Arrastre a área roja con "X" blanca (abajo centro). 
Efecto succión (crece el círculo) + Vibración -> Destruye servicio.

7. ARQUITECTURA DE SCRIPTS Y FLUJO DE TRABAJO (Scripts/)
El desarrollo es estrictamente remoto (Compilación en GitHub):

- check.sh: Validación local rápida (Manifest, IDs, sintaxis, iconos).
- ciclo.sh: Automatización Total. Ejecuta: Check -> Git Push -> Vigilante.
- vigilante.sh: Escucha a GitHub Actions. Cuando el Build termina, 
  descarga el APK automáticamente en /storage/emulated/0/Download/apps/.
- fisgon.sh: Auditoría de código. 
    - `fisgon.sh full`: Escanea todo el proyecto.
    - `fisgon.sh targets`: Escanea solo archivos críticos editados.

-----------------------------------------------------------
            🗺️ HOJA DE RUTA DE CONSTRUCCIÓN
-----------------------------------------------------------

PASO 1: LIMPIEZA TÉCNICA Y AUDITORÍA
- Revisar historial de archivos. Borrar basura (archivos .bak, .tmp).
- Limpiar AndroidManifest y build.gradle de residuos antiguos.

PASO 2: ESTRUCTURA DE CARPETAS Y RECURSOS
- Crear esqueleto oficial. Unificar iconos en ~/chesz/iconos/.

PASO 3: BOTÓN FLOTANTE (ESTADO A)
- Implementar BubbleService.kt (80dp) con arrastre libre.
- Proceso: Modificar -> bash Scripts/ciclo.sh -> Probar APK.

PASO 4: ÁREA DE CIERRE (KILL SWITCH)
- Implementar zona de succión roja, colisión y vibración.

PASO 5: GEOMETRÍA DEL PANEL Y LÓGICA DE ERROR
- Programar expansión y validación de bordes (Botón Rojo si no cabe).

PASO 6: MAQUETACIÓN DEL PANEL (ESTADO B)
- Definir áreas y botones internos. Inyectar Texto de Prueba:
  * Apertura italiana boca
  * Defensa nórdica Ase 12 100%
  * Defensa Nápoles variante coaboanca termux ase 10 90%
  * Defensa fuck becerro asesino papaya sangrienta 80%

PASO 7: INTEGRACIÓN FINAL
- Screenshot transparente, borrado de foto tras FEN y conexión API.

===========================================================
