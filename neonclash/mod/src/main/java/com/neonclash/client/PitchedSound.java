package com.neonclash.client;

import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;

/** 元の音をそのまま使い、ピッチだけ掛け算する。 */
@OnlyIn(Dist.CLIENT)
public final class PitchedSound implements SoundInstance {
    private final SoundInstance inner;
    private final float pitchMul;

    public PitchedSound(SoundInstance inner, float pitchMul) {
        this.inner = inner;
        this.pitchMul = pitchMul;
    }

    @Override
    public ResourceLocation getLocation() {
        return inner.getLocation();
    }

    @Nullable
    @Override
    public WeighedSoundEvents resolve(SoundManager manager) {
        return inner.resolve(manager);
    }

    @Override
    public Sound getSound() {
        return inner.getSound();
    }

    @Override
    public SoundSource getSource() {
        return inner.getSource();
    }

    @Override
    public boolean isLooping() {
        return inner.isLooping();
    }

    @Override
    public boolean isRelative() {
        return inner.isRelative();
    }

    @Override
    public int getDelay() {
        return inner.getDelay();
    }

    @Override
    public float getVolume() {
        return inner.getVolume();
    }

    @Override
    public float getPitch() {
        return inner.getPitch() * pitchMul;
    }

    @Override
    public double getX() {
        return inner.getX();
    }

    @Override
    public double getY() {
        return inner.getY();
    }

    @Override
    public double getZ() {
        return inner.getZ();
    }

    @Override
    public Attenuation getAttenuation() {
        return inner.getAttenuation();
    }

    @Override
    public boolean canStartSilent() {
        return inner.canStartSilent();
    }

    @Override
    public boolean canPlaySound() {
        return inner.canPlaySound();
    }

    @Override
    public CompletableFuture<AudioStream> getStream(SoundBufferLibrary library, Sound sound, boolean looping) {
        return inner.getStream(library, sound, looping);
    }
}
