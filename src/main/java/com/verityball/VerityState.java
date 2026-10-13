package com.verityball;

/** Estados/animaciones que el menú puede activar. */
public enum VerityState {
    IDLE(0, "idle", new String[]{"idle"}, null, false),
    TALK(1, "talk", new String[]{"talk", "yap"}, "talk", false),
    ITS_COMING(2, "its_coming", new String[]{"its_coming", "yap"}, "creepytalk", true),
    DANCE(3, "dance", new String[]{"dance"}, "dance", true),
    FACE1(4, "face1", new String[]{"emote1"}, null, false),
    FACE2(5, "face2", new String[]{"emote2"}, null, false),
    FACE3(6, "face3", new String[]{"emote3"}, null, false);

    public final int id;
    public final String key;
    public final String[] animations;
    /** Nombre del sonido (sounds.json) o null si no tiene. */
    public final String sound;
    public final boolean loopSound;

    VerityState(int id, String key, String[] animations, String sound, boolean loopSound) {
        this.id = id;
        this.key = key;
        this.animations = animations;
        this.sound = sound;
        this.loopSound = loopSound;
    }

    public static VerityState byId(int id) {
        for (VerityState s : values()) if (s.id == id) return s;
        return IDLE;
    }
}
