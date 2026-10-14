package com.verityball.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** Teclas del mod (solo cliente). Se pueden cambiar en Opciones > Controles. */
public class ModKeys {
    /** Coger / soltar a Verity (por defecto E). */
    public static final KeyMapping PICKUP = new KeyMapping(
            "key.verityball.pickup",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_E,
            "key.categories.verityball");
}
