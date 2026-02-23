#!/usr/bin/env python3
# patch_panel_stateB.py
# - Agrega panel Estado B (60% ancho, 25% alto)
# - Solape: panel a la derecha, invadiendo 50% del botón
# - Regla: si el ROOT se sale -> NO abre + flash rojo
# - No toca kill switch; solo reposiciona panel si está abierto
# - Backups van a ~/chesz/BU (no .bak en res/)

import re, sys, time, shutil, subprocess
from pathlib import Path

ROOT = Path.home() / "chesz"
F = ROOT / "app/src/main/java/com/chesz/floating/BubbleService.kt"

RUN_CICLO = True
if "--no-ciclo" in sys.argv:
    RUN_CICLO = False

def die(msg, code=1):
    print(msg, file=sys.stderr)
    raise SystemExit(code)

def read(p: Path) -> str:
    return p.read_text(encoding="utf-8")

def write(p: Path, s: str):
    p.write_text(s, encoding="utf-8")

def backup_file(p: Path) -> Path:
    bu_dir = ROOT / "BU"
    bu_dir.mkdir(parents=True, exist_ok=True)
    ts = time.strftime("%Y%m%d_%H%M%S")
    bak = bu_dir / f"BubbleService.kt.bak_stateB_{ts}"
    shutil.copy2(p, bak)
    return bak

def ensure_import(src: str, imp: str) -> str:
    if imp in src:
        return src
    # inserta después del package + imports existentes
    m = re.search(r"(?m)^(import .+\n)+", src)
    if m:
        ins_at = m.end()
        return src[:ins_at] + imp + "\n" + src[ins_at:]
    # si no hay bloque imports claro, inserta tras package
    m = re.search(r"(?m)^package .+\n", src)
    if not m:
        die("No encontré package para insertar import.")
    ins_at = m.end()
    return src[:ins_at] + "\n" + imp + "\n" + src[ins_at:]

def insert_after(src: str, needle: str, block: str, once=True) -> str:
    idx = src.find(needle)
    if idx == -1:
        die(f"No encontré el marcador para insertar: {needle}")
    idx2 = idx + len(needle)
    return src[:idx2] + block + src[idx2:]

def insert_before(src: str, needle: str, block: str) -> str:
    idx = src.find(needle)
    if idx == -1:
        die(f"No encontré el marcador para insertar antes de: {needle}")
    return src[:idx] + block + src[idx:]

def has(src: str, pat: str) -> bool:
    return re.search(pat, src, flags=re.M) is not None

def main():
    if not F.exists():
        die(f"NO existe: {F}")

    src = read(F)
    bak = backup_file(F)

    # Imports necesarios (si faltan)
    src = ensure_import(src, "import android.graphics.Color")
    src = ensure_import(src, "import android.widget.TextView")

    # 1) Agregar fields del panel (si no existen)
    if not has(src, r"private lateinit var panelRoot: FrameLayout"):
        # insertarlos después del bloque Bubble fields (después de bubbleLp)
        m = re.search(r"(?m)^\s*private lateinit var bubbleLp: WindowManager\.LayoutParams\s*\n", src)
        if not m:
            die("No encontré bubbleLp para insertar fields del panel.")
        insert_pos = m.end()
        block = (
            "\n"
            "  // Panel (Estado B)\n"
            "  private lateinit var panelRoot: FrameLayout\n"
            "  private lateinit var panelLp: WindowManager.LayoutParams\n"
            "  private var panelShown = false\n"
            "\n"
        )
        src = src[:insert_pos] + block + src[insert_pos:]

    # 2) Asegurar createPanel() llamado en onCreate()
    # buscamos onCreate() y después de createKillArea()
    if "createPanel()" not in src:
        m = re.search(r"(?m)^\s*createKillArea\(\)\s*\n", src)
        if not m:
            die("No encontré llamada a createKillArea() en onCreate().")
        src = src[:m.end()] + "      createPanel()\n" + src[m.end():]

    # 3) Insertar funciones del panel (si no existen)
    if "private fun createPanel()" not in src:
        # insertarlas antes de createKillArea() (orden limpio)
        m = re.search(r"(?m)^\s*private fun createKillArea\(\)\s*\{", src)
        if not m:
            die("No encontré createKillArea() para insertar funciones del panel antes.")
        block = r'''
  // ---------- Panel (Estado B) ----------
  private fun createPanel() {
    // Panel simple (negro translúcido + textos grises + botón Close)
    panelRoot = FrameLayout(this).apply {
      setBackgroundColor(0xCC000000.toInt())
      alpha = 1f
    }

    val content = FrameLayout(this).apply {
      setPadding(dp(10), dp(10), dp(10), dp(10))
    }

    fun mkLine(t: String): TextView = TextView(this).apply {
      text = t
      setTextColor(0xFFB0B0B0.toInt())
      textSize = 12f
    }

    // Texto de prueba del README (lo dejamos fijo por ahora)
    val t1 = mkLine("Apertura italiana boca")
    val t2 = mkLine("Defensa nórdica Ase 12 100%")
    val t3 = mkLine("Defensa Nápoles variante coaboanca termux ase 10 90%")
    val t4 = mkLine("Defensa fuck becerro asesino papaya sangrienta 80%")

    // Close minimalista (vuelve a Estado A)
    val close = TextView(this).apply {
      text = "Close"
      gravity = Gravity.CENTER
      setTextColor(Color.WHITE)
      textSize = 12f
      setBackgroundColor(0x66000000)
      setPadding(0, dp(6), 0, dp(6))
      setOnClickListener { hidePanel() }
    }

    // Layout vertical simple (sin XML)
    val col = android.widget.LinearLayout(this).apply {
      orientation = android.widget.LinearLayout.VERTICAL
    }
    col.addView(t1)
    col.addView(space(dp(6)))
    col.addView(t2)
    col.addView(space(dp(6)))
    col.addView(t3)
    col.addView(space(dp(6)))
    col.addView(t4)
    col.addView(space(dp(10)))
    col.addView(close, android.widget.LinearLayout.LayoutParams(
      android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
      android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
    ))

    content.addView(col)
    panelRoot.addView(content)

    // size: 60% x 25% de pantalla
    val dm = resources.displayMetrics
    val panelW = (dm.widthPixels * 0.60f).toInt()
    val panelH = (dm.heightPixels * 0.25f).toInt()

    panelLp = WindowManager.LayoutParams(
      panelW,
      panelH,
      overlayType(),
      WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
      PixelFormat.TRANSLUCENT
    ).apply {
      gravity = Gravity.TOP or Gravity.START
      x = 0
      y = 0
    }
  }

  private fun space(h: Int): View =
    View(this).apply { layoutParams = FrameLayout.LayoutParams(1, h) }

  private fun togglePanel() {
    if (panelShown) hidePanel() else showPanelIfFits()
  }

  private fun showPanelIfFits() {
    // Calcula geometría según README:
    // rootX = buttonX
    // rootY = buttonY - (panelH - buttonH)
    // rootW = panelW + (buttonW/2)
    // rootH = panelH
    val dm = resources.displayMetrics
    val panelW = panelLp.width
    val panelH = panelLp.height
    val btnW = if (bubbleRoot.width > 0) bubbleRoot.width else dp(60)
    val btnH = if (bubbleRoot.height > 0) bubbleRoot.height else dp(60)

    val rootX = bubbleLp.x
    val rootY = bubbleLp.y - (panelH - btnH)
    val rootW = panelW + (btnW / 2)
    val rootH = panelH

    val fits = rootX >= 0 &&
               rootY >= 0 &&
               (rootX + rootW) <= dm.widthPixels &&
               (rootY + rootH) <= dm.heightPixels

    if (!fits) {
      flashBubbleRed()
      return
    }

    // Posición del panel: a la derecha, solapando 50% del botón
    panelLp.x = bubbleLp.x + (btnW / 2)
    panelLp.y = bubbleLp.y - (panelH - btnH)

    if (!panelShown) {
      runCatching { wm.addView(panelRoot, panelLp) }
      panelShown = true
    } else {
      runCatching { wm.updateViewLayout(panelRoot, panelLp) }
    }
  }

  private fun hidePanel() {
    if (!panelShown) return
    runCatching { wm.removeViewImmediate(panelRoot) }
    panelShown = false
  }

  private fun updatePanelPositionIfShown() {
    if (!panelShown) return
    // Solo reposiciona con la misma regla (sin revalidar "fits")
    val panelH = panelLp.height
    val btnW = if (bubbleRoot.width > 0) bubbleRoot.width else dp(60)
    val btnH = if (bubbleRoot.height > 0) bubbleRoot.height else dp(60)
    panelLp.x = bubbleLp.x + (btnW / 2)
    panelLp.y = bubbleLp.y - (panelH - btnH)
    runCatching { wm.updateViewLayout(panelRoot, panelLp) }
  }

  private fun flashBubbleRed() {
    // feedback simple: tinte rojo 220ms
    runCatching {
      bubbleIcon.setColorFilter(0xFFFF3333.toInt())
      bubbleIcon.postDelayed({ runCatching { bubbleIcon.clearColorFilter() } }, 220)
    }
  }
'''
        src = src[:m.start()] + block + "\n" + src[m.start():]

    # 4) Modificar el ACTION_UP: tap -> togglePanel()
    # Buscamos el comentario "tap normal" o el bloque else de shouldKill
    # En tu archivo actual ya existe "tap normal (no hace nada)".
    if "togglePanel()" not in src:
        # inserta dentro del else { } del ACTION_UP cuando NO mata
        # Heurística: reemplazar la línea comentada.
        src2 = re.sub(
            r"(?m)^\s*//\s*tap normal.*$",
            "              togglePanel()",
            src
        )
        if src2 == src:
            # fallback: buscar "else {" inmediato en shouldKill
            die("No pude insertar togglePanel(): no encontré el comentario 'tap normal'.")
        src = src2

    # 5) En ACTION_MOVE, si panelShown -> updatePanelPositionIfShown()
    if "updatePanelPositionIfShown()" not in src:
        # ya está la función, pero falta llamada: buscamos wm.updateViewLayout(bubbleRoot, bubbleLp)
        m = re.search(r"(?m)^\s*wm\.updateViewLayout\(bubbleRoot,\s*bubbleLp\)\s*$", src)
        if not m:
            die("No encontré wm.updateViewLayout(bubbleRoot, bubbleLp) en ACTION_MOVE.")
        src = src[:m.end()] + "\n\n            updatePanelPositionIfShown()" + src[m.end():]

    # 6) Al destruir servicio, remover panel si está visible
    if "if (panelShown)" not in src:
        m = re.search(r"(?m)^\s*override fun onDestroy\(\)\s*\{", src)
        if not m:
            die("No encontré onDestroy() para agregar cleanup de panel.")
        # insertamos tras super.onDestroy()
        m2 = re.search(r"(?m)^\s*super\.onDestroy\(\)\s*$", src[m.end():])
        if not m2:
            die("No encontré super.onDestroy() dentro de onDestroy().")
        pos = m.end() + m2.end()
        src = src[:pos] + "\n      runCatching { if (panelShown) wm.removeViewImmediate(panelRoot) }\n      panelShown = false\n" + src[pos:]

    # Sanidad llaves (aprox)
    if src.count("{") != src.count("}"):
        write(F, read(bak))
        die(f"ABORTADO: llaves desbalanceadas. Revertí a backup: {bak}")

    write(F, src)
    print(f"✅ Patch Estado B aplicado en: {F}")
    print(f"🧷 Backup (fuera del repo): {bak}")

    if RUN_CICLO:
        print("🚀 Ejecutando ciclo…")
        p = subprocess.run(["bash", str(ROOT / "Scripts/ciclo")], cwd=str(ROOT))
        if p.returncode != 0:
            die(f"❌ ciclo terminó con error (code={p.returncode})", p.returncode)
        print("✅ ciclo OK")

if __name__ == "__main__":
    main()
