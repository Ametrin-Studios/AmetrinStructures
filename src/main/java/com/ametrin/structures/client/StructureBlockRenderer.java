package com.ametrin.structures.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.StructureBlockEntity;
import net.minecraft.world.level.block.state.properties.StructureMode;
import org.jetbrains.annotations.Nullable;

/// Vanilla's structure block renderer plus a name tag, like fixtures have. It shows the template name,
/// or the metadata of a data block. Players who can see the bounding box see it while looking at the
/// block or holding a structure block.
public class StructureBlockRenderer extends net.minecraft.client.renderer.blockentity.StructureBlockRenderer {
    public StructureBlockRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(StructureBlockEntity structureBlock, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        super.render(structureBlock, partialTick, poseStack, buffers, packedLight, packedOverlay);
        var player = Minecraft.getInstance().player;
        if (player == null || !player.canUseGameMasterBlocks() && !player.isSpectator()) {
            return;
        }
        var nameTag = BlockNameTag.of(structureBlock, Items.STRUCTURE_BLOCK, () -> name(structureBlock));
        if (nameTag != null) {
            nameTag.render(poseStack, buffers);
        }
    }

    private static @Nullable Component name(StructureBlockEntity structureBlock) {
        var text = structureBlock.getMode() == StructureMode.DATA ? structureBlock.getMetaData() : structureBlock.getStructureName();
        return text.isEmpty() ? null : Component.literal(text);
    }
}
