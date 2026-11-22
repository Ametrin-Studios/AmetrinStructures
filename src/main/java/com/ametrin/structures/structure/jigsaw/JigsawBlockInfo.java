package com.ametrin.structures.structure.jigsaw;

import net.minecraft.core.registries.Registries;
import net.minecraft.data.worldgen.Pools;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.entity.JigsawBlockEntity;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.jetbrains.annotations.ApiStatus;

import java.util.Objects;

/// A jigsaw block in a template, with its block entity data read, like later versions of vanilla have it.
@ApiStatus.Internal
public record JigsawBlockInfo(StructureTemplate.StructureBlockInfo info, JigsawBlockEntity.JointType jointType, ResourceLocation name, ResourceKey<StructureTemplatePool> pool, ResourceLocation target, int placementPriority, int selectionPriority) {
    private static final ResourceLocation EMPTY_ID = ResourceLocation.withDefaultNamespace("empty");

    public static JigsawBlockInfo of(StructureTemplate.StructureBlockInfo info) {
        var nbt = Objects.requireNonNull(info.nbt(), () -> info + " nbt was null");
        var joint = JigsawBlockEntity.JointType.byName(nbt.getString("joint")).orElseGet(() -> JigsawBlock.getFrontFacing(info.state()).getAxis().isHorizontal() ? JigsawBlockEntity.JointType.ALIGNED : JigsawBlockEntity.JointType.ROLLABLE);
        var pool = nbt.contains("pool") ? ResourceKey.create(Registries.TEMPLATE_POOL, ResourceLocation.parse(nbt.getString("pool"))) : Pools.EMPTY;
        return new JigsawBlockInfo(info, joint, id(nbt, "name"), pool, id(nbt, "target"), nbt.getInt("placement_priority"), nbt.getInt("selection_priority"));
    }

    private static ResourceLocation id(CompoundTag nbt, String key) {
        return nbt.contains(key) ? ResourceLocation.parse(nbt.getString(key)) : EMPTY_ID;
    }

    public JigsawBlockInfo withInfo(StructureTemplate.StructureBlockInfo info) {
        return new JigsawBlockInfo(info, jointType, name, pool, target, placementPriority, selectionPriority);
    }
}
