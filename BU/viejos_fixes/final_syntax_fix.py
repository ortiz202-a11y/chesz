import re

file_path = 'app/src/main/java/com/chesz/floating/BubbleService.kt'
with open(file_path, 'r') as f:
    content = f.read()

# 1. Corregir el doble cierre al final de la clase (llaves huérfanas)
content = content.strip()
if content.count('}') > content.count('{'):
    content = re.sub(r'\}\s*\}\s*\}$', '  }\n}', content)

# 2. Re-inyectar el bloqueo anti-spam en el lugar legal (ACTION_UP)
pattern = r'MotionEvent\.ACTION_UP, MotionEvent\.ACTION_CANCEL -> \{(.*?)\}'
def add_spam_block(match):
    inner = match.group(1)
    if 'togglePanel()' in inner and 'panelTitle.text' not in inner:
        return 'MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {\n          if (panelTitle.text == "Sshot/") return@setOnTouchListener true\n' + inner + '}'
    return match.group(0)

content = re.sub(pattern, add_spam_block, content, flags=re.DOTALL)

with open(file_path, 'w') as f:
    f.write(content)
print("Soberanía: Llaves balanceadas y bloqueo anti-spam legalizado.")
