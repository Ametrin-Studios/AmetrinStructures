package com.ametrin.structures.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityWithBoundingBoxRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityWithBoundingBoxRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.state.properties.StructureMode;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/// Vanilla's structure block renderer plus a name tag, like fixtures have. It shows the template name,
/// or the metadata of a data block. Players who can see the bounding box see it while looking at the
/// block or holding a structure block.
public class StructureBlockRenderer extends BlockEntityWithBoundingBoxRenderer<StructureBlockEntity> {
    @Override
    public BlockEntityWithBoundingBoxRenderState createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(
            StructureBlockEntity structureBlock, BlockEntityWithBoundingBoxRenderState state, float partialTicks, Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress) {
        super.extractRenderState(structureBlock, state, partialTicks, cameraPosition, breakProgress);
        if (state instanceof State named) {
            named.nameTag = state.isVisible ? BlockNameTag.of(structureBlock, Items.STRUCTURE_BLOCK, () -> name(structureBlock), cameraPosition) : null;
        }
    }

    private static @Nullable Component name(StructureBlockEntity structureBlock) {
        var text = structureBlock.getMode() == StructureMode.DATA ? structureBlock.getMetaData() : structureBlock.getStructureName();
        return text.isEmpty() ? null : Component.literal(text);
    }

    @Override
    public void submit(BlockEntityWithBoundingBoxRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        super.submit(state, poseStack, collector, camera);
        if (state instanceof State named && named.nameTag != null) {
            named.nameTag.submit(poseStack, collector, camera);
        }
    }

    public static final class State extends BlockEntityWithBoundingBoxRenderState {
        private @Nullable BlockNameTag nameTag;
    }
}
