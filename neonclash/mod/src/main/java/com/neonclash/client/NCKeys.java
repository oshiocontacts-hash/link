package com.neonclash.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** キー割り当て（TaCZ の R/G/C/Z/T/O/V/H と被らないもの）。 */
@OnlyIn(Dist.CLIENT)
public final class NCKeys {
    public static final String CATEGORY = "key.categories.neonclash";
    public static final KeyMapping BLINK = new KeyMapping("key.neonclash.blink", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_ALT, CATEGORY);
    public static final KeyMapping PARRY = new KeyMapping("key.neonclash.parry", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_X, CATEGORY);

    private NCKeys() {
    }
}
