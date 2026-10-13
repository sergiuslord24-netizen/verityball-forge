# Verity Ball

Mod para **Minecraft 1.20.1 (Forge 47.3.0)**. Agrega a Verity como mob quieto: clic derecho abre un menu con Hablar, It's Coming, Bailar, Caras 1/2/3, Arcoiris y Detener. Todo se sincroniza entre jugadores (todos necesitan el mod).

Modelo y animaciones: RoseRocket.

## Variantes

Verity, Falsity, Lovity, Morbosity y Peruanity (mismo modelo y animaciones, distinto color y caras).

- **Huevos generadores:** cada variante tiene su huevo en la pestana de huevos del creativo.
- **Menu:** clic derecho sobre ella y pulsa el boton **Variante** para cambiarla al instante.
- **Comando:** `/summon verityball:verity ~ ~ ~ {VerityVariant:2}` (0 Verity, 1 Falsity, 2 Lovity, 3 Morbosity, 4 Peruanity).

## Obtener el .jar

1. Ve a la pestana **Actions** de este repositorio.
2. Abre la ultima ejecucion de **Compilar mod** (si no hay, pulsa *Run workflow*).
3. Abajo, en **Artifacts**, descarga `verityball-jar`. Dentro esta `verityball-1.0.0.jar`.
4. Copia el `.jar` a la carpeta `mods` de tu Minecraft con Forge 1.20.1.

## Sonidos

Pon tus `.ogg` en `src/main/resources/assets/verityball/sounds/` antes de compilar:
`talk.ogg`, `creepytalk.ogg`, `dance.ogg`. Si falta alguno, el mod funciona igual, solo sin ese sonido.

## Compilar en tu PC

Necesitas Java 17 y Gradle 8.x:

```
gradle build
```

El resultado queda en `build/libs/verityball-1.0.0.jar`.

## Ajustes

- Tamano de Verity: `VerityRenderer.java` -> `SCALE`.
- Modelo y animaciones: `assets/verityball/models/verity_model.json`.
