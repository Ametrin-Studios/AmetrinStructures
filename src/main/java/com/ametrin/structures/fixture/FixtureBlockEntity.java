package com.ametrin.structures.fixture;

import com.ametrin.structures.registry.ASBlockEntities;
import com.ametrin.structures.util.ASNbt;
import com.ametrin.structures.util.PositionHelper;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;

@ApiStatus.Internal
public class FixtureBlockEntity extends BlockEntity {
    public static final String FIXTURES_KEY = "fixtures";
    public static final String CUSTOM_NAME_KEY = "CustomName";
    public static final String USE_GRAVITY_KEY = "use_gravity";
    public static final String MARK_POST_PROCESSING_KEY = "mark_post_processing";
    public static final String OFFSET_KEY = "offset";
    public static final String BECOMES_KEY = "becomes";

    /// How far the action may be from the marker along each axis, so it stays within what the generating chunk can read and write.
    public static final int MAX_OFFSET = 16;
    public static final Codec<Vec3> OFFSET_CODEC = Vec3.CODEC.validate(offset -> isValidOffset(offset)
            ? DataResult.success(offset)
            : DataResult.error(() -> "offset " + offset + " is more than " + MAX_OFFSET + " blocks along an axis"));
    private static final Codec<List<Tag>> DATA_CODEC = ASNbt.TAG_CODEC.listOf();

    // The alternatives exactly as stored, saved back unchanged. Re-encoding a decoded alternative drops
    // values that equal the default, so an explicitly set value would change if the default changes.
    private List<Tag> fixtureData = List.of();
    private List<WeightedFixture> fixtures = List.of();

    private @Nullable Component customName;

    private boolean useGravity;
    private boolean markPostProcessing;
    private Vec3 offset = Vec3.ZERO;
    private Optional<BlockState> becomes = Optional.empty();

    public FixtureBlockEntity(BlockPos pos, BlockState state) {
        super(ASBlockEntities.FIXTURE.get(), pos, state);
    }

    public List<WeightedFixture> fixtures() {
        return fixtures;
    }

    /// The alternatives as stored. A missing field uses the current default.
    public List<Tag> fixtureData() {
        return fixtureData.stream().map(Tag::copy).toList();
    }

    public void setFixtureData(List<? extends Tag> data, HolderLookup.Provider registries) {
        this.fixtureData = data.stream().map(Tag::copy).toList();
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        this.fixtures = fixtureData.stream().map(alternative -> WeightedFixture.decodeLeniently(ops, alternative)).toList();
        setChanged();
    }

    public @Nullable Component customName() {
        return customName;
    }

    public void setCustomName(@Nullable Component customName) {
        this.customName = customName;
        setChanged();
    }

    /// Snap the action position down to the ocean-floor heightmap.
    public boolean useGravity() {
        return useGravity;
    }

    public void setUseGravity(boolean useGravity) {
        this.useGravity = useGravity;
        setChanged();
    }

    /// Flag the target position for chunk post-processing.
    public boolean markPostProcessing() {
        return markPostProcessing;
    }

    public void setMarkPostProcessing(boolean markPostProcessing) {
        this.markPostProcessing = markPostProcessing;
        setChanged();
    }

    /// Where the action happens, from the bottom center of the marker, along the template's axes.
    public Vec3 offset() {
        return offset;
    }

    /// @throws IllegalArgumentException when the offset isn't [valid][#isValidOffset(Vec3)]
    public void setOffset(Vec3 offset) {
        if (!isValidOffset(offset)) {
            throw new IllegalArgumentException("offset " + offset + " is more than " + MAX_OFFSET + " blocks along an axis");
        }
        this.offset = offset;
        setChanged();
    }

    /// Finite and at most [#MAX_OFFSET] along each axis.
    public static boolean isValidOffset(Vec3 offset) {
        return isValidOffset(offset.x) && isValidOffset(offset.y) && isValidOffset(offset.z);
    }

    public static boolean isValidOffset(double offset) {
        return Math.abs(offset) <= MAX_OFFSET;
    }

    /// Unless set, [Blocks#AIR], or [Fluids#WATER] when waterlogged.
    public BlockState becomes() {
        return becomes.orElseGet(() -> getBlockState().getValue(FixtureBlock.WATERLOGGED)
                ? Fluids.WATER.defaultFluidState().createLegacyBlock()
                : Blocks.AIR.defaultBlockState());
    }

    public Optional<BlockState> declaredBecomes() {
        return becomes;
    }

    public void setBecomes(Optional<BlockState> becomes) {
        var changed = !this.becomes.equals(becomes);
        this.becomes = becomes;
        setChanged();
        // Neighbors such as fences connect to the becomes state's support shape.
        if (changed && level != null && !level.isClientSide()) {
            getBlockState().updateNeighbourShapes(level, worldPosition, Block.UPDATE_ALL);
        }
    }

    public ItemStack toItemStack(HolderLookup.Provider registries) {
        ItemStack stack = new ItemStack(getBlockState().getBlock());
        // Like vanilla's pick block with data, so a picked marker and its copy stack.
        var tag = saveCustomOnly(registries);
        removeComponentsFromTag(tag);
        BlockItem.setBlockEntityData(stack, getType(), tag);
        stack.applyComponents(collectComponents());
        return stack;
    }

    // The offset turns with the structure like any other template position. StructureTemplateMixin calls this.
    public void applyStructureRotation(Mirror mirror, Rotation rotation) {
        if (mirror != Mirror.NONE || rotation != Rotation.NONE) {
            offset = PositionHelper.transform(offset, mirror, rotation);
            setChanged();
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        this.fixtureData = ASNbt.read(tag, FIXTURES_KEY, DATA_CODEC, ops).orElseGet(List::of);
        this.fixtures = ASNbt.read(tag, FIXTURES_KEY, WeightedFixture.LIST_CODEC, ops).orElseGet(List::of);
        this.customName = ASNbt.read(tag, CUSTOM_NAME_KEY, ComponentSerialization.CODEC, ops).orElse(null);
        this.useGravity = tag.getBoolean(USE_GRAVITY_KEY);
        this.markPostProcessing = tag.getBoolean(MARK_POST_PROCESSING_KEY);
        // An offset out of range is ignored. The structure check reports it.
        this.offset = ASNbt.read(tag, OFFSET_KEY, OFFSET_CODEC, ops).orElse(Vec3.ZERO);
        this.becomes = ASNbt.read(tag, BECOMES_KEY, BlockState.CODEC, ops);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        var ops = registries.createSerializationContext(NbtOps.INSTANCE);
        ASNbt.store(tag, FIXTURES_KEY, DATA_CODEC, ops, fixtureData);
        if (customName != null) {
            ASNbt.store(tag, CUSTOM_NAME_KEY, ComponentSerialization.CODEC, ops, customName);
        }
        tag.putBoolean(USE_GRAVITY_KEY, useGravity);
        tag.putBoolean(MARK_POST_PROCESSING_KEY, markPostProcessing);
        ASNbt.store(tag, OFFSET_KEY, Vec3.CODEC, ops, offset);
        becomes.ifPresent(state -> ASNbt.store(tag, BECOMES_KEY, BlockState.CODEC, ops, state));
    }

    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        components.set(DataComponents.CUSTOM_NAME, customName);
    }

    // The name is stored as the item's name component instead.
    @Override
    @SuppressWarnings("deprecation")
    public void removeComponentsFromTag(CompoundTag tag) {
        tag.remove(CUSTOM_NAME_KEY);
    }

    @Override
    protected void applyImplicitComponents(DataComponentInput components) {
        super.applyImplicitComponents(components);
        var name = components.get(DataComponents.CUSTOM_NAME);
        if (name != null) {
            this.customName = name;
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveCustomOnly(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
