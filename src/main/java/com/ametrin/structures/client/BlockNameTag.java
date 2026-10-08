package com.ametrin.structures.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

record BlockNameTag(Component name, int lightCoords) {
    private static final Vec3 ATTACHMENT = new Vec3(0.5, 0.75, 0.5);

    /// Null while it isn't shown or `name` gives null.
    static @Nullable BlockNameTag of(BlockEntity blockEntity, Item item, Supplier<@Nullable Component> name) {
        var pos = blockEntity.getBlockPos();
        if (!isShown(pos, item)) {
            return null;
        }
        var text = name.get();
        if (text == null) {
            return null;
        }
        // Lit like the space the text is in: inside an opaque block, such as a structure block, it's always dark.
        var level = blockEntity.getLevel();
        int light = level != null ? LightCoordsUtil.getLightCoords(level, pos.above()) : LightCoordsUtil.FULL_BRIGHT;
        return new BlockNameTag(text, light);
    }

    private static boolean isShown(BlockPos pos, Item item) {
        var minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.player.isHolding(item)) {
            return true;
        }
        return minecraft.hitResult instanceof BlockHitResult hit
                && hit.getType() == HitResult.Type.BLOCK
                && hit.getBlockPos().equals(pos);
    }

    void submit(PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
        collector.submitNameTag(poseStack, ATTACHMENT, 0, name, true, lightCoords, camera);
    }
}
