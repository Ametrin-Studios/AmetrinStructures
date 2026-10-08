package com.ametrin.structures.client;

import com.ametrin.structures.fixture.FixtureBlockEntity;
import com.ametrin.structures.registry.ASItems;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/// Shows a fixture's name above it, like an entity's name tag, while it's looked at or while the player holds a fixture.
public class FixtureRenderer implements BlockEntityRenderer<FixtureBlockEntity, FixtureRenderer.State> {
    public FixtureRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(
            FixtureBlockEntity marker, State state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(marker, state, partialTicks, cameraPosition, breakProgress);
        state.nameTag = BlockNameTag.of(marker, ASItems.FIXTURE.get(), marker::customName, cameraPosition);
    }

    @Override
    public void submit(State state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.nameTag != null) {
            state.nameTag.submit(poseStack, collector, camera);
        }
    }

    public static final class State extends BlockEntityRenderState {
        private @Nullable BlockNameTag nameTag;
    }
}
