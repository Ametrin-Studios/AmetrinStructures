package com.ametrin.structures.network;

import com.ametrin.structures.AmetrinStructures;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.TeamColor;
import org.jetbrains.annotations.ApiStatus;

import java.util.List;
import java.util.Optional;

// Bump [#PROTOCOL_VERSION] whenever a wire shape changes.
@ApiStatus.Internal
public final class ASPayloads {
    public static final String PROTOCOL_VERSION = "6";

    private ASPayloads() {}

    /// Client to server: which keys does this registry hold?
    ///
    /// The authoring GUI asks the server rather than reading client-side registries, because the server may have datapacks the client does not.
    public record RequestRegistryKeys(Identifier registry) implements CustomPacketPayload {
        public static final Type<RequestRegistryKeys> TYPE =
                new Type<>(AmetrinStructures.locate("request_registry_keys"));

        /// Not a registry: asks for the structure template ids the server can load
        public static final Identifier STRUCTURE_TEMPLATES = AmetrinStructures.locate("structure_template");

        public static final StreamCodec<ByteBuf, RequestRegistryKeys> STREAM_CODEC = Identifier.STREAM_CODEC.map(RequestRegistryKeys::new, RequestRegistryKeys::registry);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /// Server to client: the answer to [RequestRegistryKeys].
    public record SendRegistryKeys(Identifier registry, List<Identifier> keys) implements CustomPacketPayload {
        public static final Type<SendRegistryKeys> TYPE = new Type<>(AmetrinStructures.locate("send_registry_keys"));

        public static final StreamCodec<ByteBuf, SendRegistryKeys> STREAM_CODEC =
                StreamCodec.composite(
                        Identifier.STREAM_CODEC,
                        SendRegistryKeys::registry,
                        Identifier.STREAM_CODEC.apply(ByteBufCodecs.list()),
                        SendRegistryKeys::keys,
                        SendRegistryKeys::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /// Client to server: run a marker now, to test a fixture from its screen. Sent after [UpdateFixture], so it runs with the latest edits.
    public record GenerateFixture(BlockPos pos) implements CustomPacketPayload {
        public static final Type<GenerateFixture> TYPE = new Type<>(AmetrinStructures.locate("generate_fixture"));

        public static final StreamCodec<ByteBuf, GenerateFixture> STREAM_CODEC =
                BlockPos.STREAM_CODEC.map(GenerateFixture::new, GenerateFixture::pos);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /// Client to server: commit the authoring screen's changes.
    // One packet for the whole marker, so an edit can't be partially applied. The alternatives are sent as
    // stored, so a blank field stays out and uses its default.
    public record UpdateFixture(
            BlockPos pos,
            List<Tag> fixtures,
            Optional<Component> customName,
            boolean useGravity,
            boolean markPostProcessing,
            Vec3 offset,
            Optional<BlockState> becomes)
            implements CustomPacketPayload {

        public static final Type<UpdateFixture> TYPE = new Type<>(AmetrinStructures.locate("update_fixture"));

        public static final Codec<UpdateFixture> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                        BlockPos.CODEC.fieldOf("pos").forGetter(UpdateFixture::pos),
                        ExtraCodecs.NBT.listOf().fieldOf("fixtures").forGetter(UpdateFixture::fixtures),
                        ComponentSerialization.CODEC
                                .optionalFieldOf("custom_name")
                                .forGetter(UpdateFixture::customName),
                        Codec.BOOL.fieldOf("use_gravity").forGetter(UpdateFixture::useGravity),
                        Codec.BOOL.fieldOf("mark_post_processing").forGetter(UpdateFixture::markPostProcessing),
                        Vec3.CODEC.fieldOf("offset").forGetter(UpdateFixture::offset),
                        BlockState.CODEC.optionalFieldOf("becomes").forGetter(UpdateFixture::becomes))
                .apply(instance, UpdateFixture::new));

        public static final StreamCodec<RegistryFriendlyByteBuf, UpdateFixture> STREAM_CODEC =
                ByteBufCodecs.fromCodecWithRegistries(CODEC);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /// Server to client: the spots of a spread report, for a map mod to show as waypoints. Earlier
    /// waypoints of the same report are replaced, those of others kept for comparison.
    ///
    /// @param report     the structure, structure tag or structure set reported on
    /// @param replaceAll whether to drop every earlier spread waypoint first, so rejected spots are not mixed up with another report's
    public record SpreadWaypoints(
            Identifier report,
            ResourceKey<Level> dimension,
            TeamColor color,
            boolean replaceAll,
            List<FoundSpot> found,
            List<RejectedSpot> rejected)
            implements CustomPacketPayload {

        public static final Type<SpreadWaypoints> TYPE = new Type<>(AmetrinStructures.locate("spread_waypoints"));

        public static final StreamCodec<ByteBuf, SpreadWaypoints> STREAM_CODEC = StreamCodec.composite(
                Identifier.STREAM_CODEC, SpreadWaypoints::report,
                ResourceKey.streamCodec(Registries.DIMENSION), SpreadWaypoints::dimension,
                TeamColor.STREAM_CODEC, SpreadWaypoints::color,
                ByteBufCodecs.BOOL, SpreadWaypoints::replaceAll,
                FoundSpot.STREAM_CODEC.apply(ByteBufCodecs.list()), SpreadWaypoints::found,
                RejectedSpot.STREAM_CODEC.apply(ByteBufCodecs.list()), SpreadWaypoints::rejected,
                SpreadWaypoints::new);

        /// @param structure the structure that generates at `position`
        public record FoundSpot(BlockPos position, Identifier structure) {
            public static final StreamCodec<ByteBuf, FoundSpot> STREAM_CODEC = StreamCodec.composite(
                    BlockPos.STREAM_CODEC, FoundSpot::position,
                    Identifier.STREAM_CODEC, FoundSpot::structure,
                    FoundSpot::new);
        }

        /// @param reason   why it failed, the key of `waypoint.ametrin_structures.rejected.<reason>`
        /// @param argument the filter or structure the reason names, or empty
        public record RejectedSpot(BlockPos position, String reason, String argument) {
            public static final StreamCodec<ByteBuf, RejectedSpot> STREAM_CODEC = StreamCodec.composite(
                    BlockPos.STREAM_CODEC, RejectedSpot::position,
                    ByteBufCodecs.STRING_UTF8, RejectedSpot::reason,
                    ByteBufCodecs.STRING_UTF8, RejectedSpot::argument,
                    RejectedSpot::new);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /// Server to client: remove every spread report waypoint.
    public record ClearSpreadWaypoints() implements CustomPacketPayload {
        public static final ClearSpreadWaypoints INSTANCE = new ClearSpreadWaypoints();
        public static final Type<ClearSpreadWaypoints> TYPE = new Type<>(AmetrinStructures.locate("clear_spread_waypoints"));
        public static final StreamCodec<ByteBuf, ClearSpreadWaypoints> STREAM_CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
