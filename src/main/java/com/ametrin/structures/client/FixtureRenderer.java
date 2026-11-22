package com.ametrin.structures.client;

import com.ametrin.structures.fixture.FixtureBlockEntity;
import com.ametrin.structures.registry.ASItems;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

/// Shows a fixture's name above it, like an entity's name tag, while it's looked at or while the player holds a fixture.
public class FixtureRenderer implements BlockEntityRenderer<FixtureBlockEntity> {
    public FixtureRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(FixtureBlockEntity marker, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        var nameTag = BlockNameTag.of(marker, ASItems.FIXTURE.get(), marker::customName);
        if (nameTag != null) {
            nameTag.render(poseStack, buffers);
        }
    }
}
