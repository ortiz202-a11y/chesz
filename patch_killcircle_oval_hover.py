#!/usr/bin/env python3
import re, shutil, subprocess, sys
from pathlib import Path

ROOT = Path.home() / "chesz"
F = ROOT / "app/src/main/java/com/chesz/floating/BubbleService.kt"
RUN_CICLO = "--no-ciclo" not in sys.argv

def die(msg, code=1):
    print(msg, file=sys.stderr)
    raise SystemExit(code)

def backup(p: Path) -> Path:
    bak = p.with_suffix(p.suffix + ".bak_pre_oval")
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
        die(f"No pude cerrar el bloque de {fun_name} (llaves desbalanceadas)")

    indent_match = re.search(r"(?m)^(\s*)private\s+fun\s+" + re.escape(fun_name) + r"\b", src[start:end])
    base_indent = indent_match.group(1) if indent_match else ""

    new_lines = [base_indent + line.rstrip() for line in new_block.strip("\n").splitlines()]
    new_text = "\n".join(new_lines) + "\n"

    return src[:start] + new_text + src[end:]

def ensure_imports(src: str) -> str:
    # Necesitamos ViewOutlineProvider y Outline
    if "android.view.ViewOutlineProvider" in src and "android.graphics.Outline" in src:
        return src
    # Inserta imports cerca de android.view.*
    lines = src.splitlines()
    out = []
    inserted_outline = False
    inserted_provider = False
    for line in lines:
        out.append(line)
        if line.strip() == "import android.view.*":
            if "import android.graphics.Outline" not in src:
                out.append("import android.graphics.Outline")
                inserted_outline = True
            if "import android.view.ViewOutlineProvider" not in src:
                out.append("import android.view.ViewOutlineProvider")
                inserted_provider = True
    if not (inserted_outline or inserted_provider):
        # fallback: agrega al final de imports
        idx = 0
        for i,l in enumerate(out):
            if l.startswith("import "):
                idx = i
        add = []
        if "import android.graphics.Outline" not in src: add.append("import android.graphics.Outline")
        if "import android.view.ViewOutlineProvider" not in src: add.append("import android.view.ViewOutlineProvider")
        out = out[:idx+1] + add + out[idx+1:]
    return "\n".join(out) + ("\n" if not src.endswith("\n") else "")

def ensure_killcircle_property(src: str) -> str:
    if re.search(r"(?m)^\s*private\s+lateinit\s+var\s+killCircle\s*:\s*FrameLayout\s*$", src):
        return src
    m = re.search(r"(?m)^(\s*)private\s+lateinit\s+var\s+killRoot\s*:\s*FrameLayout\s*$", src)
    if not m:
        die("NO encontré killRoot para insertar killCircle.")
    indent = m.group(1)
    insert_line = f"{indent}private lateinit var killCircle: FrameLayout"
    pos = m.end()
    return src[:pos] + "\n" + insert_line + src[pos:]

def main():
    if not F.exists():
        die(f"NO existe: {F}")

    src = F.read_text(encoding="utf-8")
    bak = backup(F)

    src = ensure_imports(src)
    src = ensure_killcircle_property(src)

    new_createKillArea = r"""
private fun createKillArea() {
  // Contenedor (NO se escala)
  killRoot = FrameLayout(this).apply {
    alpha = 1f
    visibility = View.VISIBLE
    clipChildren = false
    clipToPadding = false
    background = null
    setBackgroundColor(0x00000000) // transparente
  }

  val sizePx = dp(100)

  // Círculo real (ESTE se escala) + outline oval para que JAMÁS sea cuadrado
  killCircle = FrameLayout(this).apply {
    background = android.graphics.drawable.GradientDrawable().apply {
      shape = android.graphics.drawable.GradientDrawable.OVAL
      setColor(0xCCFF0000.toInt())
    }
    clipToOutline = true
    outlineProvider = object : ViewOutlineProvider() {
      override fun getOutline(view: View, outline: Outline) {
        outline.setOval(0, 0, view.width, view.height)
      }
    }
    scaleX = 1f
    scaleY = 1f
  }

  val xIcon = ImageView(this).apply {
    setImageResource(android.R.drawable.ic_delete)
    setColorFilter(0xFFFFFFFF.toInt())
    scaleType = ImageView.ScaleType.CENTER_INSIDE
  }

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

    src2 = replace_fun(src, "createKillArea", new_createKillArea)
    src2 = replace_fun(src2, "setKillHover", new_setKillHover)

    if src2.count("{") != src2.count("}"):
        F.write_text(bak.read_text(encoding="utf-8"), encoding="utf-8")
        die(f"ABORTADO: llaves desbalanceadas. Revertí a backup: {bak}")

    F.write_text(src2, encoding="utf-8")
    print(f"✅ Patch aplicado en: {F}")
    print(f"🧷 Backup: {bak}")

    if RUN_CICLO:
        print("🚀 Ejecutando ciclo…")
        p = subprocess.run(["bash", str(ROOT / "Scripts/ciclo")], cwd=str(ROOT))
        raise SystemExit(p.returncode)

if __name__ == "__main__":
    main()
