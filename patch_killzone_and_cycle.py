#!/usr/bin/env python3
import os, re, sys, subprocess
from pathlib import Path

ROOT = Path.home() / "chesz"
JAVA_DIR = ROOT / "app" / "src" / "main" / "java"
CICLO = ROOT / "Scripts" / "ciclo"

NEW_FUNC = """private fun isOverKillCenter(x: Float, y: Float): Boolean {
  val (cx, cy) = killCenterOnScreen()
  val r = (killLp.width / 2f) * 1.6f
  return hypot(x - cx, y - cy) <= r
}
"""

def die(msg: str, code: int = 1):
    print(f"❌ {msg}")
    sys.exit(code)

def find_bubble_service() -> Path:
    if not JAVA_DIR.is_dir():
        die(f"No existe: {JAVA_DIR}")
    hits = []
    for p in JAVA_DIR.rglob("*.kt"):
        try:
            s = p.read_text(encoding="utf-8", errors="ignore")
        except Exception:
            continue
        if "class BubbleService" in s:
            hits.append(p)
    if not hits:
        die("No encontré ningún archivo con 'class BubbleService' en app/src/main/java")
    # si hay varios, preferimos el que esté en paquete 'floating' o 'bubble'
    def score(p: Path) -> int:
        sp = str(p).lower()
        sc = 0
        if "/floating/" in sp: sc += 3
        if "/bubble/" in sp: sc += 2
        if p.name.lower() == "bubbleservice.kt": sc += 2
        return sc
    hits.sort(key=score, reverse=True)
    return hits[0]

def patch_is_over_kill_center(kotlin_path: Path):
    s = kotlin_path.read_text(encoding="utf-8", errors="ignore")

    # Busca la función isOverKillCenter completa (declaración + cuerpo)
    pattern = re.compile(
        r'private\s+fun\s+isOverKillCenter\s*\(\s*x:\s*Float\s*,\s*y:\s*Float\s*\)\s*:\s*Boolean\s*\{.*?\n\}',
        re.DOTALL
    )
    if not pattern.search(s):
        # fallback: buscar por nombre aunque firma difiera un poco
        pattern2 = re.compile(r'private\s+fun\s+isOverKillCenter\s*\(.*?\)\s*:\s*Boolean\s*\{.*?\n\}', re.DOTALL)
        if not pattern2.search(s):
            die(f"No encontré la función isOverKillCenter(...) en: {kotlin_path}")
        pattern = pattern2

    s2 = pattern.sub(NEW_FUNC.strip(), s, count=1)
    if s2 == s:
        die("No se aplicó ningún cambio (patch no modificó el archivo).")

    # backup
    bak = kotlin_path.with_suffix(kotlin_path.suffix + ".bak_pre_patch")
    bak.write_text(s, encoding="utf-8")
    kotlin_path.write_text(s2, encoding="utf-8")
    print(f"✅ Patch aplicado en: {kotlin_path}")
    print(f"🧷 Backup: {bak}")

def run_cycle():
    if not CICLO.exists():
        die(f"No existe el script ciclo en: {CICLO}")
    # Asegura ejecutable (por si acaso)
    try:
        os.chmod(CICLO, os.stat(CICLO).st_mode | 0o111)
    except Exception:
        pass

    print("🚀 Ejecutando ciclo…")
    # Ejecuta con bash para evitar problemas de shebang
    r = subprocess.run(["bash", str(CICLO)], cwd=str(ROOT))
    if r.returncode != 0:
        die(f"ciclo terminó con error (code={r.returncode})", r.returncode)
    print("✅ ciclo OK")

def main():
    if not ROOT.is_dir():
        die(f"No existe repo en: {ROOT}")
    bs = find_bubble_service()
    patch_is_over_kill_center(bs)
    run_cycle()

if __name__ == "__main__":
    main()
