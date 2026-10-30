package com.neonclash.registry;

import com.neonclash.NeonClash;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * 効果音。中身は assets/neonclash/sounds.json でバニラの音を参照している（リソースパックで差し替え可能）。
 * 命中音とキル音は別。キル音は連続キルでピッチが上がる。
 */
public final class NCSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, NeonClash.MODID);

    public static final RegistryObject<SoundEvent> HIT = register("hit");
    public static final RegistryObject<SoundEvent> KILL = register("kill");
    public static final RegistryObject<SoundEvent> BLINK = register("blink");
    public static final RegistryObject<SoundEvent> WINDUP = register("windup");
    public static final RegistryObject<SoundEvent> SLASH = register("slash");
    public static final RegistryObject<SoundEvent> PARRY = register("parry");
    public static final RegistryObject<SoundEvent> PARRY_SUCCESS = register("parry_success");
    public static final RegistryObject<SoundEvent> LAST_STAND = register("last_stand");
    public static final RegistryObject<SoundEvent> SLOWMO = register("slowmo");
    public static final RegistryObject<SoundEvent> BEAT = register("beat");
    public static final RegistryObject<SoundEvent> RANK_UP = register("rank_up");
    public static final RegistryObject<SoundEvent> ROUND_START = register("round_start");
    public static final RegistryObject<SoundEvent> ROUND_END = register("round_end");

    private NCSounds() {
    }

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(NeonClash.id(name)));
    }
}
