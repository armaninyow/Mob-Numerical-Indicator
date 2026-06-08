package com.armaninyow.mobnumericalindicator.mixin;

import com.armaninyow.mobnumericalindicator.renderer.EntityRenderCache;
import com.armaninyow.mobnumericalindicator.renderer.MobIndicatorRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {

    @Inject(
            method = "render(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("TAIL")
    )
    private void onRender(LivingEntityRenderState state, PoseStack poseStack,
                          MultiBufferSource bufferSource, int packedLight, CallbackInfo ci) {
        // Retrieve and consume the snapshot we stashed in extractRenderState
        EntityRenderCache.Snapshot snapshot = EntityRenderCache.take(state);
        if (snapshot == null) return;
        MobIndicatorRenderer.render(snapshot, poseStack, bufferSource, packedLight);
    }
}