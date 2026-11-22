package com.ametrin.structures.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

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
        int light = level != null ? LevelRenderer.getLightColor(level, pos.above()) : 0xF000F0;
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

    // Like an entity's name tag that isn't sneaking.
    void render(PoseStack poseStack, MultiBufferSource buffers) {
        var minecraft = Minecraft.getInstance();
        var font = minecraft.font;
        poseStack.pushPose();
        poseStack.translate(ATTACHMENT.x, ATTACHMENT.y + 0.5, ATTACHMENT.z);
        poseStack.mulPose(minecraft.getEntityRenderDispatcher().cameraOrientation());
        poseStack.scale(0.025F, -0.025F, 0.025F);
        var pose = poseStack.last().pose();
        int background = (int) (minecraft.options.getBackgroundOpacity(0.25F) * 255.0F) << 24;
        float x = -font.width(name) / 2.0F;
        font.drawInBatch(name, x, 0, 0x20FFFFFF, false, pose, buffers, Font.DisplayMode.SEE_THROUGH, background, lightCoords);
        font.drawInBatch(name, x, 0, -1, false, pose, buffers, Font.DisplayMode.NORMAL, 0, lightCoords);
        poseStack.popPose();
    }
}
