package com.neonclash.game;

import com.neonclash.match.MatchRegistry;
import com.neonclash.network.C2SActionPacket;
import net.minecraft.server.level.ServerPlayer;

/** クライアントのキー入力パケットをサーバー側の判定に振り分ける。 */
public final class ActionHandler {
    private ActionHandler() {
    }

    public static void handle(ServerPlayer player, C2SActionPacket.Action action, int arg) {
        if (!player.isAlive() || player.isSpectator()) {
            return;
        }
        Combatant c = Combatants.getOrCreate(player);
        if (c.eliminated) {
            return;
        }
        switch (action) {
            case BLINK -> BlinkService.tryBlink(c);
            case PARRY -> SkillService.tryParry(c);
            case SWORD -> SwordService.tryStartWindup(c);
            case CHOOSE_UPGRADE -> MatchRegistry.findFor(player.getUUID()).ifPresent(m -> m.choose(player.getUUID(), arg));
        }
    }
}
