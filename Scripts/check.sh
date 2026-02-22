#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail

echo "--- CHECK: SANITY (NO COMMIT / NO PUSH) ---"

ROOT="$HOME/chesz"
GRADLE="$ROOT/app/build.gradle"
MANIFEST="$ROOT/app/src/main/AndroidManifest.xml"
BUBBLE="$ROOT/app/src/main/java/com/chesz/analyzer/bubble/BubbleService.kt"
OVERLAY_PANEL="$ROOT/app/src/main/res/layout/overlay_panel.xml"
OVERLAY_ROOT="$ROOT/app/src/main/res/layout/overlay_root.xml"
OVERLAY_BTN_XML="$ROOT/app/src/main/res/drawable/overlay_btn_icon.xml"
BUBBLE_ICON="$ROOT/app/src/main/res/drawable/bubble_icon.png"

die() { echo "❌ $*"; exit 1; }
ok()  { echo "✅ $*"; }
require_file() { [[ -f "$1" ]] || die "No existe: $1"; }

echo "1) build.gradle"
require_file "$GRADLE"
if grep -q "targetCompatibility[[:space:]]*$" "$GRADLE"; then
  die "targetCompatibility vacío o mal formado en app/build.gradle"
fi
ok "Gradle OK"

echo "2) AndroidManifest"
require_file "$MANIFEST"
grep -q 'android.permission.SYSTEM_ALERT_WINDOW' "$MANIFEST" || die "Falta permiso SYSTEM_ALERT_WINDOW"
grep -q 'BubbleService' "$MANIFEST" || die "No encuentro BubbleService en AndroidManifest.xml"
ok "Manifest OK"

echo "3) BubbleService.kt (sanity)"
require_file "$BUBBLE"
if grep -q "^[[:space:]]*=" "$BUBBLE"; then
  die "Asignaciones huérfanas '=' detectadas en BubbleService"
fi
OPEN_BRACES=$(grep -o "{" "$BUBBLE" | wc -l | tr -d ' ')
CLOSE_BRACES=$(grep -o "}" "$BUBBLE" | wc -l | tr -d ' ')
[[ "$OPEN_BRACES" -eq "$CLOSE_BRACES" ]] || die "Llaves desbalanceadas en BubbleService: {=$OPEN_BRACES }=$CLOSE_BRACES"
ok "BubbleService OK"

echo "4) Layouts requeridos por BubbleService"
require_file "$OVERLAY_ROOT"
require_file "$OVERLAY_PANEL"
grep -q 'android:id="@+id/panelContainer"' "$OVERLAY_ROOT" || die "Falta id @+id/panelContainer en overlay_root.xml"
grep -q 'android:id="@+id/floatingButtonContainer"' "$OVERLAY_ROOT" || die "Falta id @+id/floatingButtonContainer en overlay_root.xml"
ok "overlay_root IDs OK"

echo "5) Detectar ids corruptos (tipo 2787id/...)"
# detecta: android:id="...2787id/..." o android:id="@+id/2787id/..."
if grep -RInE 'android:id="[^"]*[0-9]+id/' "$ROOT/app/src/main/res" >/dev/null 2>&1; then
  echo "---- ids corruptos detectados ----"
  grep -RInE 'android:id="[^"]*[0-9]+id/' "$ROOT/app/src/main/res" || true
  die "Hay android:id corruptos"
fi
ok "No hay android:id corruptos"

echo "6) Recursos del icono overlay"
require_file "$OVERLAY_BTN_XML"
require_file "$BUBBLE_ICON"
if ! grep -q 'android:src="@drawable/bubble_icon"' "$OVERLAY_BTN_XML"; then
  echo "---- overlay_btn_icon.xml ----"
  sed -n '1,40p' "$OVERLAY_BTN_XML" || true
  die "overlay_btn_icon.xml debe apuntar a @drawable/bubble_icon"
fi
ok "Icono overlay OK"

echo "✅ CHECK OK"
