#!/usr/bin/env python3
import re
import shutil
import subprocess
import sys
from pathlib import Path

ROOT = Path.home() / "chesz"
F = ROOT / "app/src/main/java/com/chesz/floating/BubbleService.kt"

RUN_CICLO = "--no-ciclo" not in sys.argv

def die(msg: str, code: int = 1):
    print(msg, file=sys.stderr)
    raise SystemExit(code)

def read_text(p: Path) -> str:
    return p.read_text(encoding="utf-8")

def write_text(p: Path, s: str):
    p.write_text(s, encoding="utf-8")

def backup_file(p: Path) -> Path:
    bak = p.with_suffix(p.suffix + ".bak_pre_killcircle")
    shutil.copy2(p, bak)
    return bak

def replace_fun(src: str, fun_name: str, new_block: str) -> str:
    m = re.search(rf"(?m)^\s*private\s+fun\s+{re.escape(fun_name)}\s*\(", src)
    if not m:
        die(f"NO ENCONTRÉ: private fun {fun_name}(")

    start = m.start()
    brace_open = src.find("{", m.end())
    if brace_open == -1:
        die(f"No pude encontrar '{{' de {fun_name}")

    i = brace_open
    depth = 0
    in_str = False
    esc = False
    while i < len(src):
        ch = src[i]
        if in_str:
            if esc:
                esc = False
            elif ch == "\\":
                esc = True
            elif ch == '"':
                in_str = False
        else:
            if ch == '"':
                in_str = True
            elif ch == "{":
                depth += 1
            elif ch == "}":
                depth -= 1
                if depth == 0:
                    end = i + 1
                    break
        i += 1
    else:
        die(f"No pude cerrar el bloque de {fun_name}")

    indent_match = re.search(r"(?m)^(\s*)private\s+fun\s+" + re.escape(fun_name) + r"\b", src[start:end])
    base_indent = indent_match.group(1) if indent_match else ""

    new_lines = []
    for line in new_block.strip("\n").splitlines():
        new_lines.append(base_indent + line.rstrip())
    new_text = "\n".join(new_lines) + "\n"

    return src[:start] + new_text + src[end:]

def ensure_killcircle_property(src: str) -> str:
    if re.search(r"(?m)^\s*private\s+lateinit\s+var\s+killCircle\s*:\s*FrameLayout\s*$", src):
        return src

    m1 = re.search(r"(?m)^(\s*)private\s+lateinit\s+var\s+killRoot\s*:\s*FrameLayout\s*$", src)
    if m1:
        indent = m1.group(1)
        line = f"{indent}private lateinit var killCircle: FrameLayout"
        return src[:m1.end()] + "\n" + line + src[m1.end():]

    m2 = re.search(r"(?m)^(\s*)private\s+lateinit\s+var\s+killLp\s*:\s*WindowManager\.LayoutParams\s*$", src)
    if m2:
        indent = m2.group(1)
        line = f"{indent}private lateinit var killCircle: FrameLayout"
        return src[:m2.start()] + line + "\n" + src[m2.start():]

    die("NO pude insertar killCircle (no encontré killRoot ni killLp).")

def main():
    if not F.exists():
        die(f"NO existe: {F}")

    src = read_text(F)
    bak = backup_file(F)

    src = ensure_killcircle_property(src)

    new_createKillArea = r"""
private fun createKillArea() {
  // Contenedor (NO se escala)
  killRoot = FrameLayout(this).apply {
    alpha = 1f
    scaleX = 1f
    scaleY = 1f
    visibility = View.VISIBLE
    clipChildren = false
    clipToPadding = false
    setBackgroundColor(0x00000000) // transparente
  }

  val sizePx = dp(100)

  // Círculo real (ESTE se escala)
  killCircle = FrameLayout(this).apply {
    background = android.graphics.drawable.GradientDrawable().apply {
      shape = android.graphics.drawable.GradientDrawable.OVAL
      setColor(0xCCFF0000.toInt())
    }
    scaleX = 1f
    scaleY = 1f
  }

  // Ícono X
  val xIcon = ImageView(this).apply {
    setImageResource(android.R.drawable.ic_delete)
    setColorFilter(0xFFFFFFFF.toInt())
    scaleType = ImageView.ScaleType.CENTER_INSIDE
  }

  // Estructura: killRoot -> killCircle -> xIcon
  killRoot.addView(
    killCircle,
    FrameLayout.LayoutParams(sizePx, sizePx, Gravity.CENTER)
  )
  killCircle.addView(
    xIcon,
    FrameLayout.LayoutParams(dp(44), dp(44), Gravity.CENTER)
  )

  killLp = WindowManager.LayoutParams(
    sizePx,
    sizePx,
    overlayType(),
    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
      or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
      or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
    PixelFormat.TRANSLUCENT
  ).apply {
    gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
    x = 0
    y = dp(40)
  }
}
"""

    new_setKillHover = r"""
private fun setKillHover(hover: Boolean) {
  val target = if (hover) 1.40f else 1.0f
  killCircle.animate().cancel()
  killCircle.animate().scaleX(target).scaleY(target).setDuration(90).start()
}
"""

    src = replace_fun(src, "createKillArea", new_createKillArea)
    src = replace_fun(src, "setKillHover", new_setKillHover)

    opens = src.count("{")
    closes = src.count("}")
    if opens != closes:
        write_text(F, read_text(bak))
        die(f"ABORTADO: llaves desbalanceadas ({{={opens} }}={closes}). Revertí a backup: {bak}")

    write_text(F, src)

    print(f"✅ Patch aplicado en: {F}")
    print(f"🧷 Backup: {bak}")

    if RUN_CICLO:
        print("🚀 Ejecutando ciclo…")
        p = subprocess.run(["bash", str(ROOT / "Scripts/ciclo")], cwd=str(ROOT))
        if p.returncode != 0:
            die(f"❌ ciclo terminó con error (code={p.returncode})", p.returncode)
        print("✅ ciclo OK")

if __name__ == "__main__":
    main()
