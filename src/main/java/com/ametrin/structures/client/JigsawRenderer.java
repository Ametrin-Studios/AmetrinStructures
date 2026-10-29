package com.ametrin.structures.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.data.worldgen.Pools;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.JigsawBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/// Shows a jigsaw's pool above it, like [StructureBlockRenderer] shows the template name.
public class JigsawRenderer implements BlockEntityRenderer<JigsawBlockEntity, JigsawRenderer.State> {
    public JigsawRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(
            JigsawBlockEntity jigsaw, State state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(jigsaw, state, partialTicks, cameraPosition, breakProgress);
        var player = Minecraft.getInstance().player;
        var visible = player != null && (player.canUseGameMasterBlocks() || player.isSpectator());
        state.nameTag = visible ? BlockNameTag.of(jigsaw, Items.JIGSAW, () -> pool(jigsaw)) : null;
    }

    private static @Nullable Component pool(JigsawBlockEntity jigsaw) {
        return jigsaw.getPool().equals(Pools.EMPTY) ? null : Component.literal(jigsaw.getPool().identifier().toString());
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
