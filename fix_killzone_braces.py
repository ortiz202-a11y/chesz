#!/usr/bin/env python3
from pathlib import Path
import re, sys, subprocess, os

ROOT = Path.home() / "chesz"
KOT = ROOT / "app/src/main/java/com/chesz/floating/BubbleService.kt"
BAK = KOT.with_suffix(KOT.suffix + ".bak_pre_patch")

def die(msg, code=1):
    print(f"❌ {msg}")
    sys.exit(code)

def run(cmd):
    r = subprocess.run(cmd, cwd=str(ROOT))
    if r.returncode != 0:
        die(f"Falló: {' '.join(cmd)} (code={r.returncode})", r.returncode)

def restore_backup():
    if not BAK.exists():
        die(f"No existe backup: {BAK}")
    KOT.write_text(BAK.read_text(encoding="utf-8", errors="ignore"), encoding="utf-8")
    print(f"✅ Restaurado desde backup: {BAK}")

def patch_radius_multiplier():
    s = KOT.read_text(encoding="utf-8", errors="ignore")

    # Localiza el bloque de isOverKillCenter(...) sin reescribirlo completo:
    #  - buscamos la línea del radio dentro del bloque y solo cambiamos el multiplicador.
    # Acepta variantes: * 1.05f / 1.1f / etc
    block_re = re.compile(
        r'(private\s+fun\s+isOverKillCenter\s*\(.*?\)\s*:\s*Boolean\s*\{)(.*?)(\n\})',
        re.DOTALL
    )
    m = block_re.search(s)
    if not m:
        die("No encontré el bloque isOverKillCenter(...) para parchear.")

    head, body, tail = m.group(1), m.group(2), m.group(3)

    # Reemplaza SOLO el multiplicador final del radio:
    # Ej: val r = (killLp.width / 2f) * 1.05f  ->  * 1.6f
    body2, n = re.subn(
        r'(\bval\s+r\s*=\s*\(\s*killLp\.width\s*/\s*2f\s*\)\s*\*\s*)([0-9]+(?:\.[0-9]+)?f)',
        r'\g<1>1.6f',
        body,
        count=1
    )

    if n == 0:
        # fallback: si la expresión del radio es distinta, intentamos cambiar cualquier "* Xf" en la línea que contenga "val r" y "killLp.width"
        lines = body.splitlines(True)
        changed = False
        for i, ln in enumerate(lines):
            if "val r" in ln and "killLp.width" in ln:
                ln2, n2 = re.subn(r'\*\s*[0-9]+(?:\.[0-9]+)?f', '* 1.6f', ln, count=1)
                if n2:
                    lines[i] = ln2
                    changed = True
                    break
        if not changed:
            die("Encontré isOverKillCenter, pero no pude localizar la línea del radio para cambiarla.")
        body2 = "".join(lines)

    s2 = s[:m.start()] + head + body2 + tail + s[m.end():]

    # sanity: conteo de llaves debe quedar igual que el backup (o al menos balanceado)
    openb = s2.count("{")
    closeb = s2.count("}")
    if openb != closeb:
        die(f"Patch dejó llaves desbalanceadas: {{={openb} }}={closeb}. No escribo cambios.")

    # guarda
    KOT.write_text(s2, encoding="utf-8")
    print("✅ Patch OK: multiplicador del radio cambiado a 1.6f (sin tocar llaves).")

def main():
    if not KOT.exists():
        die(f"No existe: {KOT}")
    restore_backup()
    patch_radius_multiplier()

    # corre ciclo
    ciclo = ROOT / "Scripts/ciclo"
    if not ciclo.exists():
        die(f"No existe: {ciclo}")
    try:
        os.chmod(ciclo, os.stat(ciclo).st_mode | 0o111)
    except Exception:
        pass

    print("🚀 Ejecutando ciclo…")
    run(["bash", str(ciclo)])
    print("✅ ciclo OK")

if __name__ == "__main__":
    main()
