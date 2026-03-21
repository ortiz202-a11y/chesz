import os

path = "app/src/main/java/com/chesz/floating/BubbleService.kt"
with open(path, "r") as f:
    code = f.read()

target = 'text = "BENCHMARK"'
replacement = 'text = "FEN TEST"'

if target in code:
    code = code.replace(target, replacement)
    with open(path, "w") as f:
        f.write(code)
    print("NOMENCLATURA ACTUALIZADA: BENCHMARK -> FEN TEST.")
else:
    print("ERROR: No se encontro la palabra BENCHMARK en la definicion del boton.")

