package com.neonclash.game;

import com.neonclash.core.style.StyleEvent;
import com.neonclash.core.style.StyleMeter;
import com.neonclash.network.NCNetwork;
import com.neonclash.network.S2CFeedbackPacket;
import com.neonclash.registry.NCSounds;

/** スタイルランクの上下と、その通知。 */
public final class StyleService {
    private StyleService() {
    }

    public static StyleMeter.Change apply(Combatant c, StyleEvent... events) {
        StyleMeter.Change change = c.style.apply(events);
        if (change.after() != change.before()) {
            NCNetwork.send(c.entity(), new S2CFeedbackPacket(S2CFeedbackPacket.Kind.RANK, change.rankedUp() ? 1f : 0f, 0, change.after().ordinal()));
            if (change.rankedUp()) {
                HitFeedback.playTo(c.entity(), NCSounds.RANK_UP.get(), 0.8f, 1.0f + change.after().ordinal() * 0.1f);
            }
        }
        return change;
    }
}
