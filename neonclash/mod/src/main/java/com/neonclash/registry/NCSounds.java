package com.neonclash.registry;

import com.neonclash.NeonClash;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * 効果音と試合の BGM。中身は tools/sfx/make_sfx.py で合成した ogg（リソースパックで差し替え可能）。
 * 命中音とキル音は別。キル音は連続キルで、命中音は連続命中でピッチが上がる。
 */
public final class NCSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, NeonClash.MODID);

    public static final RegistryObject<SoundEvent> SHOT = register("shot");
    public static final RegistryObject<SoundEvent> SHOT_BEAT = register("shot_beat");
    public static final RegistryObject<SoundEvent> IMPACT = register("impact");
    public static final RegistryObject<SoundEvent> HIT = register("hit");
    public static final RegistryObject<SoundEvent> HIT_HEAD = register("hit_head");
    public static final RegistryObject<SoundEvent> HURT = register("hurt");
    public static final RegistryObject<SoundEvent> KILL = register("kill");
    public static final RegistryObject<SoundEvent> SHATTER = register("shatter");
    public static final RegistryObject<SoundEvent> BLINK = register("blink");
    public static final RegistryObject<SoundEvent> WINDUP = register("windup");
    public static final RegistryObject<SoundEvent> SLASH = register("slash");
    public static final RegistryObject<SoundEvent> SLASH_HIT = register("slash_hit");
    public static final RegistryObject<SoundEvent> PARRY = register("parry");
    public static final RegistryObject<SoundEvent> PARRY_SUCCESS = register("parry_success");
    public static final RegistryObject<SoundEvent> LAST_STAND = register("last_stand");
    public static final RegistryObject<SoundEvent> SLOWMO = register("slowmo");
    public static final RegistryObject<SoundEvent> BEAT = register("beat");
    public static final RegistryObject<SoundEvent> RANK_UP = register("rank_up");
    public static final RegistryObject<SoundEvent> RANK_DOWN = register("rank_down");
    public static final RegistryObject<SoundEvent> ROUND_START = register("round_start");
    public static final RegistryObject<SoundEvent> ROUND_END = register("round_end");
    public static final RegistryObject<SoundEvent> ZONE_WARN = register("zone_warn");
    public static final RegistryObject<SoundEvent> MUSIC_BASE = register("music_base");
    public static final RegistryObject<SoundEvent> MUSIC_DRIVE = register("music_drive");

    /** 着弾音は数が多いので控えめに。 */
    public static final float IMPACT_VOLUME = 0.6f;

    private NCSounds() {
    }

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(NeonClash.id(name)));
    }
}
