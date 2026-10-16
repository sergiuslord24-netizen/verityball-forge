package com.verityball;

/** Estados/animaciones que el menú puede activar. Cada tipo de modelo usa sus propias animaciones. */
public enum VerityState {
    //      id key           animaciones de la ESFERA               animaciones HUMANOIDE        sonido       bucle
    IDLE(0, "idle", new String[]{"idle"}, new String[]{}, null, false),
    TALK(1, "talk", new String[]{"talk", "yap"}, new String[]{"verity_talk"}, "talk", false),
    ITS_COMING(2, "its_coming", new String[]{"its_coming", "yap"}, new String[]{}, "creepytalk", true),
    DANCE(3, "dance", new String[]{"dance"}, new String[]{}, "dance", true),
    FACE1(4, "face1", new String[]{"emote1"}, new String[]{}, null, false),
    FACE2(5, "face2", new String[]{"emote2"}, new String[]{}, null, false),
    FACE3(6, "face3", new String[]{"emote3"}, new String[]{}, null, false),
    THINK(7, "think", new String[]{}, new String[]{"verity_think"}, null, false),
    SIT(8, "sit", new String[]{}, new String[]{"verity_sit"}, null, false),
    SNAP(9, "snap", new String[]{}, new String[]{"verity_snap"}, null, false),
    /** Cara de muerto (ojos XX). */
    DEAD(10, "dead", new String[]{"dead"}, new String[]{}, null, false),
    /** Boca de payaso: solo existe para Morbosity. */
    CLOWN(11, "clown", new String[]{"clown"}, new String[]{}, null, false);

    public final int id;
    public final String key;
    private final String[] ballAnimations;
    private final String[] femAnimations;
    /** Nombre del sonido (sounds.json) o null si no tiene. */
    public final String sound;
    public final boolean loopSound;

    VerityState(int id, String key, String[] ballAnimations, String[] femAnimations, String sound, boolean loopSound) {
        this.id = id;
        this.key = key;
        this.ballAnimations = ballAnimations;
        this.femAnimations = femAnimations;
        this.sound = sound;
        this.loopSound = loopSound;
    }

    /** Animaciones que se reproducen en este estado para el tipo de modelo dado. */
    public String[] animationsFor(VerityVariant.Type type) {
        return type == VerityVariant.Type.FEM ? femAnimations : ballAnimations;
    }

    /** ¿Existe este estado para ese tipo de modelo? (IDLE existe en todos.) */
    public boolean isAvailableFor(VerityVariant.Type type) {
        if (this == IDLE) return true;
        return animationsFor(type).length > 0;
    }

    /** ¿Existe este estado para esa variante? (CLOWN solo para Morbosity.) */
    public boolean isAvailableFor(VerityVariant variant) {
        if (this == CLOWN && variant != VerityVariant.MORBOSITY) return false;
        return isAvailableFor(variant.type);
    }

    public static VerityState byId(int id) {
        for (VerityState s : values()) if (s.id == id) return s;
        return IDLE;
    }
}
