package com.armaninyow.mobnumericalindicator.mixin;

import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;

@Mixin(LivingEntity.class)
public class LivingEntityEffectSyncMixin {

    @Inject(method = "onEffectAdded", at = @At("TAIL"))
    private void onEffectAdded(MobEffectInstance effect, Entity source, CallbackInfo ci) {
        LivingEntity self = (LivingEntity)(Object)this;
        if (self.level() instanceof ServerLevel serverLevel) {
            serverLevel.getChunkSource().sendToTrackingPlayers(
                self, new ClientboundUpdateMobEffectPacket(self.getId(), effect, false)
            );
        }
    }

    @Inject(method = "onEffectUpdated", at = @At("TAIL"))
    private void onEffectUpdated(MobEffectInstance effect, boolean doRefreshAttributes, Entity source, CallbackInfo ci) {
        LivingEntity self = (LivingEntity)(Object)this;
        if (self.level() instanceof ServerLevel serverLevel) {
            serverLevel.getChunkSource().sendToTrackingPlayers(
                self, new ClientboundUpdateMobEffectPacket(self.getId(), effect, false)
            );
        }
    }

    @Inject(method = "onEffectsRemoved", at = @At("TAIL"))
    private void onEffectsRemoved(Collection<MobEffectInstance> effects, CallbackInfo ci) {
        LivingEntity self = (LivingEntity)(Object)this;
        if (self.level() instanceof ServerLevel serverLevel) {
            for (MobEffectInstance effect : effects) {
                serverLevel.getChunkSource().sendToTrackingPlayers(
                    self, new ClientboundRemoveMobEffectPacket(self.getId(), effect.getEffect())
                );
            }
        }
    }
}