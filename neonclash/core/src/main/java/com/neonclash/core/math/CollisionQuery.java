package com.neonclash.core.math;

/** 「この箱の空間は空いているか」を答えるワールド側の問い合わせ。 */
@FunctionalInterface
public interface CollisionQuery {
    boolean isFree(Box box);
}
