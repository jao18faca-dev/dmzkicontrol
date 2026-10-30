package com.dmzkicontrol.client.scene;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** Tecla para pular a cutscene de transformacao (padrao: Enter). */
public final class SceneKeys {
    public static final KeyMapping SKIP = new KeyMapping("key.dmzkicontrol.skip_scene", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_ENTER, "key.categories.dmzkicontrol");
    private static boolean wasDown;

    private SceneKeys() {}

    /** Le a tecla direto do teclado (a cena solta todas as teclas a cada tick para travar o personagem). */
    public static void clientTickStart() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.screen != null || !ScenePlayback.isLocalActive()) {
            wasDown = false;
            return;
        }
        InputConstants.Key key = SKIP.getKey();
        boolean down = key.getType() == InputConstants.Type.KEYSYM && key.getValue() != InputConstants.UNKNOWN.getValue()
                && InputConstants.isKeyDown(mc.getWindow().getWindow(), key.getValue());
        if (down && !wasDown) ScenePlayback.skipLocal();
        wasDown = down;
    }
}
