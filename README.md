# Verity Ball

Mod para **Minecraft 1.20.1 (Forge 47.3.0)**. Agrega a Verity como mob quieto: clic derecho abre un menu con Hablar, It's Coming, Bailar, Caras 1/2/3, Arcoiris y Detener. Todo se sincroniza entre jugadores (todos necesitan el mod).

Modelo y animaciones: RoseRocket.

## Variantes

Verity, Falsity, Lovity, Morbosity, Peruanity, **Verity Eclipse** (negra) y **Verity Ángel** (blanca con halo 3D), **VERITY.EXE** (roja, demonio), **Mexicanity** (amarilla con sombrero mexicano), más las humanoides **FemVerity**, **FemVerity Morada** y **FemVerity Rosa** (con vestido).
En el menú (clic derecho): gestos, **Seguirme** (camina tras ti / se para al desactivarlo), **Alejarse** (se aleja de ti), **Mirarme** (te sigue con la mirada), Arcoíris y el gesto **Muerto** (ojos XX) en las esferas.

- **Huevos generadores:** cada variante tiene su huevo en la pestana de huevos del creativo.
- **Menu:** clic derecho sobre ella y pulsa el boton **Variante** para cambiarla al instante.
- **Comando:** `/summon verityball:verity ~ ~ ~ {VerityVariant:2}` (0 Verity, 1 Falsity, 2 Lovity, 3 Morbosity, 4 Peruanity, 5 Eclipse, 6 Ángel, 7 FemVerity, 8 FemVerity Morada, 11 FemVerity Rosa, 9 VERITY.EXE, 10 Mexicanity).

## Obtener el .jar

1. Ve a la pestana **Actions** de este repositorio.
2. Abre la ultima ejecucion de **Compilar mod** (si no hay, pulsa *Run workflow*).
3. Abajo, en **Artifacts**, descarga `verityball-jar`. Dentro esta `verityball-1.1.0.jar`.
4. Copia el `.jar` a la carpeta `mods` de tu Minecraft con Forge 1.20.1.

## Cogerlas en la mano

Apunta a una Verity (hasta 5 bloques) y pulsa **E** (se puede cambiar en Opciones > Controles) para llevarla en la mano.
Pulsa **E** otra vez para soltarla donde apuntas. Un aviso en pantalla te recuerda la tecla.
Si la tecla de coger es la misma que la del inventario, apuntando a una Verity gana coger; mira a otro lado para abrir el inventario.

## Mirada

Todas las Verity giran la cara hacia ti (y la inclinan hacia tus ojos) mientras estes a menos de 32 bloques.

## Peruanity

Lleva su chullo (gorro de lana peruano) con orejeras, trenzas, borlas y pompon.

## Sonidos

Pon tus `.ogg` en `src/main/resources/assets/verityball/sounds/` antes de compilar:
`talk.ogg`, `creepytalk.ogg`, `dance.ogg`. Si falta alguno, el mod funciona igual, solo sin ese sonido.

## Compilar en tu PC

Necesitas Java 17 y Gradle 8.x:

```
gradle build
```

El resultado queda en `build/libs/verityball-1.1.0.jar`.

## Ajustes

- Tamano de Verity: `VerityRenderer.java` -> `SCALE`.
- Modelo y animaciones: `assets/verityball/models/verity_model.json`.
