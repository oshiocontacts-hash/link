package com.neonclash.core.upgrade;

/** 強化が変える値。加算で積み上がる（倍率系は「+0.1 = 10% 増」）。 */
public enum UpgradeStat {
    /** 銃ダメージ倍率 +x */
    GUN_DAMAGE,
    /** ビート命中時の追加ダメージ倍率 +x */
    BEAT_DAMAGE,
    /** 剣の溜め時間 -x（割合） */
    SWORD_WINDUP,
    /** 剣の射程 +x ブロック */
    SWORD_RANGE,
    /** 移動速度 +x（割合） */
    MOVE_SPEED,
    /** ジャンプ力 +x（割合） */
    JUMP,
    /** ブリンク距離 +x ブロック */
    BLINK_DISTANCE,
    /** ブリンクのクールダウン -x（割合） */
    BLINK_COOLDOWN,
    /** ブリンク最大チャージ +x */
    BLINK_CHARGES,
    /** パリィ受付 +x tick */
    PARRY_WINDOW,
    /** キル時回復 +x */
    KILL_HEAL,
    /** ラストスタンドのスロー時間 +x tick */
    LAST_STAND_DURATION,
    /** ラストスタンド発動後の体力 +x */
    LAST_STAND_HEALTH
}
