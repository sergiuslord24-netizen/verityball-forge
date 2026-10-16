package com.verityball;

/**
 * Variantes de Verity. Cada una tiene su propia carpeta de texturas: textures/entity/&lt;key&gt;/
 * y pertenece a un tipo de modelo (esfera o humanoide).
 */
public enum VerityVariant {
    VERITY(0, "verity", 0xFBFB00, Type.BALL),
    FALSITY(1, "falsity", 0x2391FF, Type.BALL),
    LOVITY(2, "lovity", 0xFF37BE, Type.BALL),
    MORBOSITY(3, "morbosity", 0x912DD2, Type.BALL),
    PERUANITY(4, "peruanity", 0x8E582D, Type.BALL),
    ECLIPSE(5, "eclipse", 0x2A2A2A, Type.BALL),
    ANGEL(6, "angel", 0xF5F5F5, Type.BALL),
    VERITYEXE(9, "verityexe", 0xC80F0F, Type.BALL),
    FEMVERITY(7, "femverity", 0xE2B13C, Type.FEM),
    FEMVERITY_MORADA(8, "femverity_morada", 0x9B5BE8, Type.FEM);

    /** Tipo de modelo: define el archivo, el tamaño y cómo se sostiene en la mano. */
    public enum Type {
        /** Esfera: models/verity_model.json. Escala 1, en la mano al 50 %, anclada por el centro de la bola. */
        BALL("verity_model", 1.0F, 0.5F, 4.4F / 16.0F, 0.6F, 0.6F),
        /** Humanoide: models/femverity_model.json (47 px de alto -> ~1,8 bloques). En la mano de pie sobre la palma. */
        FEM("femverity_model", 0.6F, 0.2F, 0.0F, 0.6F, 1.8F);

        public final String modelFile;
        /** Escala del modelo (1 = un píxel de Blockbench son 1/16 de bloque). */
        public final float scale;
        /** Escala extra cuando se lleva en la mano. */
        public final float heldScale;
        /** Altura (en bloques, con escala 1) del punto del modelo que se sujeta con la mano. */
        public final float anchorY;
        /** Tamaño de la hitbox. */
        public final float width, height;

        Type(String modelFile, float scale, float heldScale, float anchorY, float width, float height) {
            this.modelFile = modelFile;
            this.scale = scale;
            this.heldScale = heldScale;
            this.anchorY = anchorY;
            this.width = width;
            this.height = height;
        }
    }

    public final int id;
    public final String key;
    /** Color representativo (huevo generador, título del menú). */
    public final int color;
    public final Type type;

    VerityVariant(int id, String key, int color, Type type) {
        this.id = id;
        this.key = key;
        this.color = color;
        this.type = type;
    }

    public boolean isFem() {
        return this.type == Type.FEM;
    }

    public static VerityVariant byId(int id) {
        for (VerityVariant v : values()) if (v.id == id) return v;
        return VERITY;
    }

    public VerityVariant next() {
        VerityVariant[] all = values();
        return all[(this.ordinal() + 1) % all.length];
    }

    public VerityVariant previous() {
        VerityVariant[] all = values();
        return all[(this.ordinal() + all.length - 1) % all.length];
    }
}
