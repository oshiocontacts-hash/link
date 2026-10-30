package com.neonclash.core.style;

public enum StyleEvent {
    /** 通常キル */
    KILL,
    /** 自分が空中にいる状態でのキル */
    AIR_KILL,
    /** ブリンク直後のキル */
    BLINK_KILL,
    /** 剣でのキル */
    SWORD_KILL,
    /** ビートに乗った射撃・ブリンク */
    ON_BEAT,
    /** パリィ成功 */
    PARRY,
    /** 被弾 */
    DAMAGE_TAKEN,
    /** 死亡 */
    DEATH
}
