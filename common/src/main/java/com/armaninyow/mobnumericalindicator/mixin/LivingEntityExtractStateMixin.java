package com.armaninyow.mobnumericalindicator.mixin;

import com.armaninyow.mobnumericalindicator.renderer.EntityRenderCache;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityExtractStateMixin<T extends LivingEntity, S extends LivingEntityRenderState> {

    @Inject(
            method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL")
    )
    private void onExtractRenderState(T entity, S reusedState, float partialTick, CallbackInfo ci) {
        EntityRenderCache.put(reusedState, new EntityRenderCache.Snapshot(entity, partialTick));
    }
}