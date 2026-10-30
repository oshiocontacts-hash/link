package com.neonclash.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.neonclash.NeonClash;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;

/**
 * スロー中の彩度低下。assets/neonclash/shaders/post/slowmo.json（バニラの color_convolve）を
 * GUI 描画の直前に画面へ掛ける。彩度の値は JSON 側（リソースパックで差し替え可能）。
 */
@OnlyIn(Dist.CLIENT)
public final class SlowMoShader {
    private static final ResourceLocation ID = NeonClash.id("shaders/post/slowmo.json");
    @Nullable
    private static PostChain chain;
    private static int width = -1, height = -1;
    private static boolean failed;

    private SlowMoShader() {
    }

    static void process(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (failed) return;
        try {
            if (chain == null) {
                chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), mc.getMainRenderTarget(), ID);
                width = height = -1;
            }
            int w = mc.getWindow().getWidth(), h = mc.getWindow().getHeight();
            if (w != width || h != height) {
                chain.resize(w, h);
                width = w;
                height = h;
            }
            chain.process(partialTick);
            mc.getMainRenderTarget().bindWrite(true);
            RenderSystem.clear(256, Minecraft.ON_OSX); // GUI 用に深度をクリア
        } catch (Exception e) {
            NeonClash.LOGGER.warn("slow-mo shader disabled: {}", e.toString());
            failed = true;
            close();
        }
    }

    static void close() {
        if (chain != null) {
            chain.close();
            chain = null;
        }
    }
}
