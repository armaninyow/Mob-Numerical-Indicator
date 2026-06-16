package com.armaninyow.mobnumericalindicator.mixin;

import com.armaninyow.mobnumericalindicator.renderer.ClientEffectCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundRemoveMobEffectPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPacketListener.class)
public class ClientPacketListenerMixin {

    @Inject(method = "handleUpdateMobEffect", at = @At("TAIL"))
    private void onUpdateMobEffect(ClientboundUpdateMobEffectPacket packet, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long gameTick = mc.level.getGameTime();
        MobEffectInstance instance = new MobEffectInstance(
            packet.getEffect(),
            packet.getEffectDurationTicks(),
            packet.getEffectAmplifier(),
            packet.isEffectAmbient(),
            packet.isEffectVisible(),
            packet.effectShowsIcon()
        );
        ClientEffectCache.onEffectAdded(packet.getEntityId(), instance, gameTick);
    }

    @Inject(method = "handleRemoveMobEffect", at = @At("TAIL"))
    private void onRemoveMobEffect(ClientboundRemoveMobEffectPacket packet, CallbackInfo ci) {
        ClientEffectCache.onEffectRemoved(packet.entityId(), packet.effect());
    }
}