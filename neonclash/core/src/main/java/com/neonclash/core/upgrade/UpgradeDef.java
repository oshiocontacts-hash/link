package com.neonclash.core.upgrade;

import java.util.Locale;

/**
 * 強化の定義。config の文字列 {@code "id|CATEGORY|RARITY|STAT|value|stackable"} から作る。
 * 例: {@code "gun_dmg_1|WEAPON|COMMON|GUN_DAMAGE|0.10|true"}
 */
public record UpgradeDef(String id, UpgradeCategory category, Rarity rarity, UpgradeStat stat, double value,
                         boolean stackable) {

    public static UpgradeDef parse(String line) {
        String[] p = line.trim().split("\\|");
        if (p.length != 6) {
            throw new IllegalArgumentException("upgrade must have 6 fields (id|CATEGORY|RARITY|STAT|value|stackable): " + line);
        }
        String id = p[0].trim();
        if (id.isEmpty()) {
            throw new IllegalArgumentException("upgrade id is empty: " + line);
        }
        return new UpgradeDef(id,
                UpgradeCategory.valueOf(p[1].trim().toUpperCase(Locale.ROOT)),
                Rarity.valueOf(p[2].trim().toUpperCase(Locale.ROOT)),
                UpgradeStat.valueOf(p[3].trim().toUpperCase(Locale.ROOT)),
                Double.parseDouble(p[4].trim()),
                Boolean.parseBoolean(p[5].trim()));
    }

    public String serialize() {
        return String.join("|", id, category.name(), rarity.name(), stat.name(), Double.toString(value), Boolean.toString(stackable));
    }
}
