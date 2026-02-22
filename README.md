# chesz (Android Kotlin)

App Android nativa con overlay (botón flotante) para analizar ajedrez en cualquier app.

## Reglas del proyecto
- Android puro + Kotlin
- 1 módulo: app
- Desarrollo incremental por archivos pequeños
- Pendientes explícitos en docs/PENDING.md
- Cursor: auditor/revisor periódico, no autor masivo

## Build
- Local (Termux): opcional
- Oficial: GitHub Actions (APK)

---

## Protocolo Oficial de Trabajo (v1)

Orden obligatorio:

Fisgón → Check → Commit/Push → Vigilante

### 1. Fisgón (inspección)

Full:

bash Scripts/fisgon.sh

Targets:

export FISGON_TARGETS="app/src/main/AndroidManifest.xml Scripts app/src/main/res/layout"
bash Scripts/fisgon.sh targets

### 2. Check (validación local)

bash Scripts/check.sh

### 3. Commit + Push

git add -A
git commit -m "mensaje claro y específico"
git push

### 4. Vigilante (build oficial)

bash Scripts/vigilante.sh

APK descargado en:

/storage/emulated/0/Download/apps/chesz-<sha>.apk

Reglas:

- No editar AndroidManifest.xml con comandos inseguros.
- No mezclar cambios grandes en un solo commit.
- No ejecutar Vigilante sin push.
- Siempre pasar Check antes de commit.


