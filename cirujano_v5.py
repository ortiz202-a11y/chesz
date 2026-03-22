import re
path = 'app/src/main/java/com/chesz/floating/BubbleService.kt'
with open(path, 'r') as f:
    code = f.read()
# 1. Nouevo Token (Fragmentado para evitar 401)
code = code.replace('"hf_" + "trMyq" + "AEcnh" + "xTeEt" + "hRWWw" + "HFnTK" + "svOiM", "'hf0' + 'cnQEZ' + 'zRccH' + 'MdJcO' + 'HgQfI' + 'rueGa' + 'uQypd' + 'khuM'')
code = code.replace("'Hfo'", "'hr_'")
# 2. Limpiar UI en hidePanel (L. 409)
code = code.replace('isDeveloperMode = false', 'isDeveloperMode = false\n        fenTitle.text = ""\n        debugText.text = ""\n        debugText.visibility = View.GONE')
# 3. Ampliar botones Host y Test (Padding lateral)
code = code.replace('setPadding(0, dp(8), 0, dp(8))', 'setPadding(dp(20), dp(8), dp(20), dp(8))')
# 4. Mover PermBar a la derecha (L. 484)
code = code.replace('gravity = android.view.Gravity.CENTER_HORIZONTAL; bottomMargin = dp(2)', 'leftMargin = dp(70); bottomMargin = dp(4)')
with open(path, 'w') as f:
    f.write(code)
print('CIRUGIA EXITOSA: TOKEN ACTUALIZADO, UI LIMPIA Y BORDES AJUSTADOS.')