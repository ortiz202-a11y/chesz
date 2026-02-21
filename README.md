# Chesz — Overlay flotante para análisis de partidas de ajedrez

Descripción
- Chesz es una app Android (desarrollo y testing desde móvil) que permite capturar rápidamente el tablero (screenshot), obtener la posición en notación FEN y recibir recomendaciones (AI / servicio web) desde un overlay flotante. El overlay está formado por un botón arrastrable y una burbuja/panel translúcido que funcionan como una sola unidad.

Características principales
- Botón flotante arrastrable que no se sale de la pantalla.
- Al tocar el botón:
  - El botón se vuelve translúcido para tomar la captura (debe mantenerse translúcido durante la captura) y regresa a su color normal cuando la captura y el procesamiento se sincronizan (rápido y sincronizado).
  - Se toma una captura de pantalla y se despliega una burbuja/panel translúcido que muestra progreso y resultados.
- El botón y la burbuja se comportan como una sola unidad al arrastrar.
- Zona de cierre: arrastrar el conjunto sobre un círculo rojo con una X cierra el servicio y limpia recursos.
- Tocar el botón con la burbuja abierta cancela el proceso anterior, borra la captura y vuelve a iniciar el flujo (nueva captura y re-procesado).

Posicionamiento preciso del botón y panel
- Al abrir la burbuja/panel, el botón se fijará en la parte inferior izquierda del contenedor visual, posicionado ligeramente encima del panel: el centro del botón quedará aproximadamente 40% por encima del borde superior del panel (el panel crece hacia arriba desde justo debajo del botón).
- Mientras la burbuja esté abierta, el botón no debe moverse de su lugar salvo durante un arrastre intencional del conjunto.
- Si el botón está muy arriba o a la derecha y el panel no cabe, la UI ajustará temporalmente el contenedor para que el panel sea visible sin empujar el botón fuera de los márgenes. Al cerrar el panel, el botón debe volver a su posición original (el ajuste no debe ser permanente).

Estructura y fases del panel
- El panel (burbuja) tendrá tres zonas:
  1. Barra de estados: indicadores por fases — Sshot / Fen / Ai / Done. Cada indicador se marca cuando su fase finaliza.
  2. Área principal: muestra la recomendación recibida, nombre de la jugada, puntuación y explicación.
  3. Botón inferior: “Cerrar” / “Cancelar”.

Flujo de operación
1. Tap en botón (panel cerrado): el botón se hace translúcido → toma screenshot → abre panel y marca Sshot.
2. Screenshot → enviado a Web API para generar FEN. Al recibir FEN: borrar screenshot (por privacidad) y marcar Fen.
3. FEN → enviado a Web API de análisis (AI). Al recibir resultado: interpretar, mostrar en área principal, marcar Ai → Done.
4. Tap en botón con panel abierto: cancelar procesos en curso, borrar captura, tomar nueva captura y reiniciar flujo.
5. Cerrar panel (botón inferior o drag al círculo rojo): cancelar procesos, borrar captura y restaurar estado del botón.

Permisos y primera ejecución
- Al instalar y abrir la app por primera vez se mostrará una pantalla que pide permiso para “mostrar encima de otras apps” (overlay). Ese será el único permiso solicitado en esa fase. La captura de pantalla (MediaProjection) solicitará consentimiento del sistema únicamente al iniciar la primera captura.
- Tras conceder el permiso overlay, la app mostrará únicamente el botón flotante (sin abrir otra pantalla visible).

Modo de trabajo y CI
- Trabajo móvil-first: no se requiere PC. Todo el desarrollo y pruebas se realizan desde el móvil (Termux y herramientas compatibles).
- Políticas de trabajo: commits atómicos y pushes frecuentes; se baja la APK generada por GitHub Actions y se guarda en /storage/emulated/0/Download/apps para que el usuario la instale y pruebe.
- Iconos (a subir desde móvil cuando estén listos):
  - App icon: /storage/emulated/0/Download/icon.png
  - Bubble/button icon: /storage/emulated/0/Download/bubble_icon.png

API y contrato (ejemplo)
- POST /api/generate-fen
  - Request: { "screenshot": "<base64-img>" }
  - Response: { "fen": "..." }
- POST /api/analyze-fen
  - Request: { "fen": "..." }
  - Response: { "recommendations": [{ "name":"...", "moves":"...", "score":0.9 }], "meta": {...} }

Privacidad
- No almacenar capturas permanentemente. Borrar screenshot tras generar FEN.
- Informar en la app y en este README sobre el envío de capturas a API externa y su uso.

Instalación y pruebas (usuario móvil)
1. Clona el repo en el móvil o usa la app desde GitHub Actions.
2. Concede permiso overlay (Settings → Apps → Special access → Display over other apps).
3. Descarga la APK desde GitHub Actions y colócala en /storage/emulated/0/Download/apps; instala y prueba.

Archivos y estructura que se conservaron en esta rama
- .github/ (workflows y CI)
- scripts/ (scripts necesarios)
- README.md (este documento)
- .gitignore
- LICENSE (si existía)

Contribuir
- Fork → crear rama feature/xxx → PR con descripción. Añadir tests para parsing y UI cuando sea posible.

Licencia
- Añade la licencia que prefieras en LICENSE (por ejemplo MIT). If LICENSE is present it was kept.

--

Notes for maintainers: icons will be added once uploaded from the device. Ensure GitHub Actions workflow produces the APK artifact and the CI is configured to publish artifacts accessible for download.

---

## Feature: Floating Overlay Button & Panel (feature/floating-button)

### How to get the APK from CI

1. Open the repository on GitHub and go to the **Actions** tab.
2. Select the latest **Build Debug APK** workflow run for the `feature/floating-button` branch.
3. Scroll to the bottom of the run page and click the **chesz-debug-apk** artifact to download the ZIP.
4. Unzip the archive and transfer the `app-debug.apk` to your Android device:
   ```
   # From a PC / Termux via ADB
   adb push app-debug.apk /storage/emulated/0/Download/apps/app-debug.apk
   # Or copy the file manually via USB / cloud storage
   ```
5. On the device, install the APK:
   ```
   pm install -r /storage/emulated/0/Download/apps/app-debug.apk
   ```
   Or open the file with a file manager and allow "Install from unknown sources" if prompted.

### First run — granting the overlay permission

1. Launch **Chesz** from the app drawer.
2. Tap **Habilitar overlay**.
3. In the system settings page that opens, enable **"Display over other apps"** for Chesz.
4. Return to the app (back button or recent apps). The MainActivity will detect the permission and start the floating service automatically, then close itself.
5. You should now see the floating button on top of any other app.

### Manual testing checklist

- [ ] **Drag — no jumps**: Touch and drag the floating button; verify it moves smoothly without teleporting.
- [ ] **Snap to edge**: Lift your finger after dragging; the button should snap instantly to the nearest left or right screen edge.
- [ ] **Panel opens upward**: Tap the button (without dragging); the panel should appear above the button with ~40 % overlap under the button bottom.
- [ ] **Panel follows button**: With the panel open, drag the button; verify the panel stays attached and repositions correctly.
- [ ] **Close button works**: Tap **Cerrar** in the panel; the panel should hide and the button should remain visible and interactive.
- [ ] **Button stays in bounds**: Drag the button to all four corners; it must never be pushed off-screen.
- [ ] **Panel stays in bounds**: Open the panel while the button is near the top of the screen; the panel must clamp to the top edge and not overflow.
- [ ] **No crashes**: Perform all of the above steps rapidly; the app must not crash.

### Reporting issues

If a bug is found, create a fix branch (e.g. `fix/floating-ghost`) and open a new PR or add commits to this PR with a clear description of the fix.