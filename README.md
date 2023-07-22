# Causal_Inference
En este repositorio muestro cómo un agente inteligente utiliza a inferencia causal para derivar de ella su comportamiento. El escenario es un entorno de un videojuego FPS (Unreal Tournament 2004) en el que está diseñado un escenario en el que un agente (GuardBOT) se posiciona en una ubicación específica y un agente (CausalBOT) cuya tarea es tomar la posición del GuardBOT sin ser detectado.

## Configuración del entorno
Instalar UT2004
Instalar Pogamut
Crear un proyecto JAVA maven de Pogamut
Incluir CausalBOT y GuardBOT en el proyecto
Descargar CausalBOT_inference.ipynb, Dataset.csv y runcausalbot.jar en el directorio raíz (los he puesto allí por simplicidad)
Instalar los paquetes  

## Data
La data fue recolectada ejecutando 1200 partidas, de las cuales se extrajo la siguiente información:
 'BOT'  : Id del bot de la partida
 'C'    : El camino que recorrió
 'N'    : La cantidad de nodos del camino
 'V'    : La cantidad de nodos que visitó en su recorrido
 'S'    : Indica si el sentido de la vista del GuardBOT está activo
 'L'    : Indica si el sentido de la audición del GuardBOT está activo
 'Y'    : Indica si BOT logró tomar la posición del GuardBOT 
