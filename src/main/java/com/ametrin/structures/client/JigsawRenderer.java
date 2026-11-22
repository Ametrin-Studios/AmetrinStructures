package com.ametrin.structures.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.data.worldgen.Pools;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.JigsawBlockEntity;
import org.jetbrains.annotations.Nullable;

/// Shows a jigsaw's pool above it, like [StructureBlockRenderer] shows the template name.
public class JigsawRenderer implements BlockEntityRenderer<JigsawBlockEntity> {
    public JigsawRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(JigsawBlockEntity jigsaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight, int packedOverlay) {
        var player = Minecraft.getInstance().player;
        if (player == null || !player.canUseGameMasterBlocks() && !player.isSpectator()) {
            return;
        }
        var nameTag = BlockNameTag.of(jigsaw, Items.JIGSAW, () -> pool(jigsaw));
        if (nameTag != null) {
            nameTag.render(poseStack, buffers);
        }
    }

    private static @Nullable Component pool(JigsawBlockEntity jigsaw) {
        return jigsaw.getPool().equals(Pools.EMPTY) ? null : Component.literal(jigsaw.getPool().location().toString());
    }
}
