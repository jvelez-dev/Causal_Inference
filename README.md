# Causal_Inference
En este repositorio muestro cómo un agente inteligente utiliza a inferencia causal para derivar de ella su comportamiento. El escenario es un entorno de un videojuego FPS (Unreal Tournament 2004) en el que está diseñado un escenario (TestEnvironment) en el que un agente (GuardBOT) se posiciona en una ubicación específica y un agente (CausalBOT) cuya tarea es tomar la posición del GuardBOT sin ser detectado.

## Configuración del entorno
1. Instalación de software
* Instalar UT2004
* Instalar Pogamut
* itertools
* networkx 
* causalnex
* pgmpy

2. Crear un proyecto JAVA maven de Pogamut
* Incluir CausalBOT y GuardBOT en el proyecto

3. Descargar CausalBOT_inference.ipynb, Dataset.csv y runcausalbot.jar en el directorio raíz (los he puesto allí por simplicidad)

## Data
La data fue recolectada ejecutando 1200 partidas, de las cuales se extrajo la siguiente información:
* 'BOT'  : Id del bot de la partida
* 'C'    : El camino que recorrió
* 'N'    : La cantidad de nodos del camino
* 'V'    : La cantidad de nodos que visitó en su recorrido
* 'S'    : Indica si el sentido de la vista del GuardBOT está activo
* 'L'    : Indica si el sentido de la audición del GuardBOT está activo
* 'Y'    : Indica si BOT logró tomar la posición del GuardBOT

Cada sección del notebook, después de ejecutarse, irá construyendo paso a paso el proceso de inferencia causal. Cuando este termine se lanzará el CausalBOT pora la eecución de la tarea por el camino que presenta mejores probabilidades de éxito, opcionalmente, si se quiere visualizar la ejecución de la tarea, deberá tenerse previamente abierto UT2004 y el server en ejecución.
