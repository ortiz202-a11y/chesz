import os

# Configuración de búsqueda
DIRECTORIO_JAVA = "app/src/main/java/com/chesz/"
BUSQUEDA = ">_"

def auditar_prefijos():
    print(f"--- Iniciando auditoría de prefijos '{BUSQUEDA}' ---")
    encontrados = 0
    
    for raiz, dirs, archivos in os.walk(DIRECTORIO_JAVA):
        for archivo in archivos:
            if archivo.endswith(".kt"):
                ruta_completa = os.path.join(raiz, archivo)
                try:
                    with open(ruta_completa, 'r', encoding='utf-8') as f:
                        lineas = f.readlines()
                    
                    for i, linea in enumerate(lineas):
                        if BUSQUEDA in linea:
                            encontrados += 1
                            print(f"{archivo} | L{i+1}: {linea.strip()}")
                except Exception as e:
                    print(f"Error leyendo {archivo}: {e}")
    
    if encontrados == 0:
        print("No se encontraron coincidencias.")
    else:
        print(f"\nSe han detectado {encontrados} líneas con el prefijo.")

if __name__ == "__main__":
    auditar_prefijos()
