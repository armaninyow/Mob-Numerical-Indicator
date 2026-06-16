package com.armaninyow.mobnumericalindicator.renderer;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Client-side cache of mob effects received via network packets.
 * Uses game ticks for the countdown so it pauses correctly in singleplayer.
 */
public class ClientEffectCache {

    public static class CachedEffect {
        public final MobEffectInstance instance;
        // Game tick (level.getGameTime()) when the effect was received
        private final long receivedTick;
        // Duration in ticks at the time of receipt
        private final int durationTicks;

        public CachedEffect(MobEffectInstance instance, long currentGameTick) {
            this.instance = instance;
            this.receivedTick = currentGameTick;
            this.durationTicks = instance.getDuration();
        }

        /**
         * Returns remaining seconds, using Math.ceil so 0.2s → 1.
         * Returns 0 if expired (caller should hide the indicator).
         */
        public int getRemainingSeconds(long currentGameTick) {
            long elapsedTicks = currentGameTick - receivedTick;
            long remainingTicks = durationTicks - elapsedTicks;
            if (remainingTicks <= 0) return 0;
            return (int) Math.ceil(remainingTicks / 20.0);
        }
    }

    // entityId -> (effect holder -> cached effect)
    private static final Map<Integer, Map<Holder<MobEffect>, CachedEffect>> CACHE = new HashMap<>();

    public static void onEffectAdded(int entityId, MobEffectInstance instance, long currentGameTick) {
        CACHE.computeIfAbsent(entityId, k -> new HashMap<>())
             .put(instance.getEffect(), new CachedEffect(instance, currentGameTick));
    }

    public static void onEffectRemoved(int entityId, Holder<MobEffect> effect) {
        Map<Holder<MobEffect>, CachedEffect> map = CACHE.get(entityId);
        if (map != null) {
            map.remove(effect);
            if (map.isEmpty()) CACHE.remove(entityId);
        }
    }

    public static void onEntityRemoved(int entityId) {
        CACHE.remove(entityId);
    }

    public static List<CachedEffect> getEffects(int entityId) {
        Map<Holder<MobEffect>, CachedEffect> map = CACHE.get(entityId);
        if (map == null || map.isEmpty()) return Collections.emptyList();
        return new ArrayList<>(map.values());
    }
}