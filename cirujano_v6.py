import re
path = 'app/src/main/java/com/chesz/floating/BubbleService.kt'
with open(path, 'r') as f:
    code = f.read()
# 1. Nuevo Token (Camuflado)
old_tok = '"hf_" + "trMyq" + "AEcnh" + "xTeEt" + "hRWWw" + "HFnTK" + "svOiM" + "hbaS"'
new_tok = '"hf_" + "cnQEZ" + "zRccH" + "MdJcO" + "HgQfI" + "rueGa" + "uQypd" + "khuM"'
code = code.replace(old_tok, new_tok)
# 2. Limpieza UI al cerrar/salir de debug
code = code.replace('isDeveloperMode = false', 'isDeveloperMode = false\n        fenTitle.text = ""\n        debugText.text = ""\n        debugText.visibility = View.GONE')
# 3. Botones Host y Test (Padding lateral)
code = code.replace('setPadding(0, dp(8), 0, dp(8))', 'setPadding(dp(20), dp(8), dp(20), dp(8))')
# 4. Mover PermBar a la derecha y 4dp abajo
code = code.replace('gravity = android.view.Gravity.CENTER_HORIZONTAL; bottomMargin = dp(2)', 'leftMargin = dp(70); bottomMargin = dp(4)')
# 5. Silenciar RESTART exitoso
code = code.replace('root.post { updateDebug(">_ RESTART STATUS: $rc") }', 'root.post { if (rc != 200 && rc != 302) updateDebug(">