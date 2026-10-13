package com.verityball;

/** Variantes de Verity. Cada una tiene su propia carpeta de texturas: textures/entity/&lt;key&gt;/ */
public enum VerityVariant {
    VERITY(0, "verity", 0xFBFB00),
    FALSITY(1, "falsity", 0x2391FF),
    LOVITY(2, "lovity", 0xFF37BE),
    MORBOSITY(3, "morbosity", 0x912DD2),
    PERUANITY(4, "peruanity", 0x8E582D);

    public final int id;
    public final String key;
    /** Color de la esfera (se usa para el huevo generador). */
    public final int color;

    VerityVariant(int id, String key, int color) {
        this.id = id;
        this.key = key;
        this.color = color;
    }

    public static VerityVariant byId(int id) {
        for (VerityVariant v : values()) if (v.id == id) return v;
        return VERITY;
    }

    public VerityVariant next() {
        VerityVariant[] all = values();
        return all[(this.ordinal() + 1) % all.length];
    }
}
