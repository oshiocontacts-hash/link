package com.neonclash.core.fx;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;

/** 飛んでいる粒の集まり。上限を超えたら古いものから捨てる。 */
public final class SparkField {
    private final Deque<Spark> sparks = new ArrayDeque<>();

    public void add(Spark s, int maxSparks) {
        sparks.addLast(s);
        while (sparks.size() > maxSparks) {
            sparks.removeFirst();
        }
    }

    public void tick() {
        sparks.removeIf(s -> {
            s.tick();
            return s.dead();
        });
    }

    public Collection<Spark> sparks() {
        return Collections.unmodifiableCollection(sparks);
    }

    public void clear() {
        sparks.clear();
    }
}
