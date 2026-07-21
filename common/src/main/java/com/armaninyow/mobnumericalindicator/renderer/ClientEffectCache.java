package com.armaninyow.mobnumericalindicator.renderer;

import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClientEffectCache {

    public static class CachedEffect {
        public final MobEffectInstance instance;
        private final long receivedTick;
        private final int durationTicks;

        public CachedEffect(MobEffectInstance instance, long currentGameTick) {
            this.instance = instance;
            this.receivedTick = currentGameTick;
            this.durationTicks = instance.getDuration();
        }

        public int getRemainingSeconds(long currentGameTick) {
            long elapsedTicks = currentGameTick - receivedTick;
            long remainingTicks = durationTicks - elapsedTicks;
            if (remainingTicks <= 0) return 0;
            return (int) Math.ceil(remainingTicks / 20.0);
        }
    }

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