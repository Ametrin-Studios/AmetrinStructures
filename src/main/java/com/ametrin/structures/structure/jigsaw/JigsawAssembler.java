package com.ametrin.structures.structure.jigsaw;

import com.ametrin.structures.util.ASLog;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.data.worldgen.Pools;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.block.JigsawBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pools.DimensionPadding;
import net.minecraft.world.level.levelgen.structure.pools.EmptyPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.LiquidSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.apache.commons.lang3.mutable.MutableObject;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

/// Grows a layout from its start piece the way vanilla's `JigsawPlacement` does, drawing from the random in the same order, and enforces [CountedPoolElement] counts.
///
/// When the layout ends with an element below its minimum, it undoes everything back to the latest jigsaw that could still attach that element, attaches it there and grows on from that point.
/// Every change to the layout is recorded so it can be undone.
final class JigsawAssembler {
    private static final int UNSET = Integer.MIN_VALUE;

    private final Function<ResourceKey<StructureTemplatePool>, Optional<? extends Holder<StructureTemplatePool>>> pools;
    private final StructureTemplateManager templates;
    private final ChunkGenerator generator;
    private final LevelHeightAccessor heightAccessor;
    private final RandomState randomState;
    private final RandomSource random;
    private final int maxDepth;
    private final boolean expansionHack;
    private final PoolAliasLookup aliases;
    private final LiquidSettings liquidSettings;
    private final int maxBacktracks;

    private final List<Runnable> undo = new ArrayList<>();
    private final List<Node> placed = new ArrayList<>();
    private final List<Decision> decisions = new ArrayList<>();
    // Vanilla visits the highest priority first, in the order they were placed.
    private final NavigableMap<Integer, ArrayDeque<Node>> queue = new TreeMap<>(Comparator.reverseOrder());
    private final Map<CountedPoolElement, Integer> counts = new HashMap<>();
    // Only grows, so a requirement stays once the layout reached it, even if that part is undone.
    private final Set<CountedPoolElement> required = new LinkedHashSet<>();
    private @Nullable Visit visit;
    private VoxelShape blocked = Shapes.empty();

    /// @param pools looks up the pools jigsaws point to, after `aliases`
    JigsawAssembler(Structure.GenerationContext context, Function<ResourceKey<StructureTemplatePool>, Optional<? extends Holder<StructureTemplatePool>>> pools, int maxDepth, boolean expansionHack, PoolAliasLookup aliases, LiquidSettings liquidSettings, int maxBacktracks) {
        this.pools = pools;
        this.templates = context.structureTemplateManager();
        this.generator = context.chunkGenerator();
        this.heightAccessor = context.heightAccessor();
        this.randomState = context.randomState();
        this.random = context.random();
        this.maxDepth = maxDepth;
        this.expansionHack = expansionHack;
        this.aliases = aliases;
        this.liquidSettings = liquidSettings;
        this.maxBacktracks = maxBacktracks;
    }

    /// The space pieces can take: the box `maxDistance` around the center, within the world's height and `padding`, without the start piece.
    static VoxelShape freeSpace(BoundingBox start, int centerY, JigsawStructure.MaxDistance maxDistance, LevelHeightAccessor heightAccessor, DimensionPadding padding) {
        int centerX = (start.maxX() + start.minX()) / 2;
        int centerZ = (start.maxZ() + start.minZ()) / 2;
        var space = new AABB(
                centerX - maxDistance.horizontal(),
                Math.max(centerY - maxDistance.vertical(), heightAccessor.getMinY() + padding.bottom()),
                centerZ - maxDistance.horizontal(),
                centerX + maxDistance.horizontal() + 1,
                Math.min(centerY + maxDistance.vertical() + 1, heightAccessor.getMaxY() + 1 - padding.top()),
                centerZ + maxDistance.horizontal() + 1);
        return Shapes.join(Shapes.create(space), Shapes.create(AABB.of(start)), BooleanOp.ONLY_FIRST);
    }

    /// Grows the layout from `start`, which is already in place.
    ///
    /// @param blocked taken by pieces outside the layout, which even pieces attached inside another one stay out of
    /// @return every piece in the order it was placed, `start` first, or empty if the counts can't be met
    Optional<List<PoolElementStructurePiece>> assemble(PoolElementStructurePiece start, VoxelShape free, VoxelShape blocked) {
        this.blocked = blocked;
        var root = new Node(start, new MutableObject<>(Shapes.join(free, blocked, BooleanOp.ONLY_FIRST)), 0);
        placed.add(root);
        visit(root);
        int backtracks = 0;
        while (true) {
            if (step()) {
                continue;
            }
            var missing = required.stream().filter(element -> count(element) < element.minCount()).findFirst();
            if (missing.isEmpty()) {
                break;
            }
            if (backtracks++ >= maxBacktracks || !backtrack(missing.get())) {
                return Optional.empty();
            }
        }
        for (var node : placed) {
            node.junctions.forEach(node.piece::addJunction);
        }
        return Optional.of(placed.stream().map(Node::piece).toList());
    }

    // Decides the next jigsaw of the piece being visited, or starts visiting the next piece. False once there's nothing left.
    private boolean step() {
        var current = visit;
        if (current != null && current.next < current.jigsaws.size()) {
            var jigsaw = current.jigsaws.get(current.next++);
            undo.add(() -> current.next--);
            decide(current.node, jigsaw);
            return true;
        }
        for (var pieces : queue.values()) {
            if (!pieces.isEmpty()) {
                var node = pieces.removeFirst();
                undo.add(() -> pieces.addFirst(node));
                visit(node);
                return true;
            }
        }
        return false;
    }

    private void visit(Node node) {
        var previous = visit;
        visit = new Visit(node, node.piece.getElement().getShuffledJigsawBlocks(templates, node.piece.getPosition(), node.piece.getRotation(), random));
        undo.add(() -> visit = previous);
    }

    private void decide(Node source, StructureTemplate.JigsawBlockInfo jigsaw) {
        var poolKey = aliases.lookup(jigsaw.pool());
        var pool = pools.apply(poolKey);
        if (pool.isEmpty() || pool.get().value().size() == 0 && !pool.get().is(Pools.EMPTY)) {
            ASLog.warn("Empty or non-existent pool: {}", poolKey.identifier());
            return;
        }
        var fallback = pool.get().value().getFallback();
        if (fallback.value().size() == 0 && !fallback.is(Pools.EMPTY)) {
            ASLog.warn("Empty or non-existent fallback pool: {}", fallback.unwrapKey().map(key -> key.identifier().toString()).orElse("<unregistered>"));
            return;
        }

        var direction = JigsawBlock.getFrontFacing(jigsaw.info().state());
        var target = jigsaw.info().pos().relative(direction);
        MutableObject<VoxelShape> free;
        if (source.piece.getBoundingBox().isInside(target)) {
            free = source.inside;
            if (free.get() == null) {
                free.setValue(Shapes.join(Shapes.create(AABB.of(source.piece.getBoundingBox())), blocked, BooleanOp.ONLY_FIRST));
                undo.add(() -> source.inside.setValue(null));
            }
        } else {
            free = source.free;
        }

        var elements = new ArrayList<StructurePoolElement>();
        if (source.depth != maxDepth) {
            elements.addAll(pool.get().value().getShuffledTemplates(random));
        }
        elements.addAll(fallback.value().getShuffledTemplates(random));
        for (var element : elements) {
            if (element instanceof CountedPoolElement counted && counted.minCount() > 0) {
                required.add(counted);
            }
        }

        var decision = new Decision(source, jigsaw, direction, target, free, elements);
        decisions.add(decision);
        undo.add(decisions::removeLast);
        decision.mark = undo.size();
        decision.placeNext(_ -> true, false);
    }

    private boolean backtrack(CountedPoolElement missing) {
        for (int i = decisions.size() - 1; i >= 0; i--) {
            var decision = decisions.get(i);
            // Undoing the element's own placement to place it again elsewhere doesn't add one.
            if (decision.attached == missing || !decision.mayStillAttach(missing)) {
                continue;
            }
            undoTo(decision.mark);
            // Past an empty element too: another draw from the pool could have reached the element.
            if (decision.placeNext(element -> element == missing, true)) {
                return true;
            }
        }
        return false;
    }

    private void undoTo(int mark) {
        while (undo.size() > mark) {
            undo.removeLast().run();
        }
    }

    private int count(CountedPoolElement element) {
        return counts.getOrDefault(element, 0);
    }

    private boolean isFull(StructurePoolElement element) {
        return element instanceof CountedPoolElement counted && counted.maxCount().filter(max -> count(counted) >= max).isPresent();
    }

    private void place(Decision decision, Node node, JigsawJunction sourceJunction, JigsawJunction targetJunction) {
        var free = decision.free;
        var previousFree = free.get();
        free.setValue(Shapes.joinUnoptimized(previousFree, Shapes.create(AABB.of(node.piece.getBoundingBox())), BooleanOp.ONLY_FIRST));
        undo.add(() -> free.setValue(previousFree));

        var sourceJunctions = decision.source.junctions;
        sourceJunctions.add(sourceJunction);
        undo.add(sourceJunctions::removeLast);
        node.junctions.add(targetJunction);

        placed.add(node);
        undo.add(placed::removeLast);
        decision.attached = node.piece.getElement();
        undo.add(() -> decision.attached = null);
        if (node.piece.getElement() instanceof CountedPoolElement counted) {
            counts.merge(counted, 1, Integer::sum);
            undo.add(() -> counts.merge(counted, -1, Integer::sum));
        }
        if (node.depth <= maxDepth) {
            var pieces = queue.computeIfAbsent(decision.jigsaw.placementPriority(), _ -> new ArrayDeque<>());
            pieces.addLast(node);
            undo.add(pieces::removeLast);
        }
    }

    /// @param free   the space for pieces attached outside this one
    /// @param inside the space for pieces attached inside this one, created when the first one is tried
    private record Node(PoolElementStructurePiece piece, MutableObject<VoxelShape> free, MutableObject<VoxelShape> inside, int depth, List<JigsawJunction> junctions) {
        Node(PoolElementStructurePiece piece, MutableObject<VoxelShape> free, int depth) {
            this(piece, free, new MutableObject<>(), depth, new ArrayList<>());
        }
    }

    private static final class Visit {
        private final Node node;
        private final List<StructureTemplate.JigsawBlockInfo> jigsaws;
        private int next;

        private Visit(Node node, List<StructureTemplate.JigsawBlockInfo> jigsaws) {
            this.node = node;
            this.jigsaws = jigsaws;
        }
    }

    /// One jigsaw and the candidates it goes through: each element, in each rotation, by each of its jigsaws.
    /// Its position among them isn't undone, so going back to it continues with the next candidate.
    private final class Decision {
        private final Node source;
        private final StructureTemplate.JigsawBlockInfo jigsaw;
        private final Direction direction;
        private final BlockPos target;
        private final MutableObject<VoxelShape> free;
        private final List<StructurePoolElement> elements;
        private final boolean sourceRigid;
        private final int sourceBoxY;
        private final int localY;
        private int baseHeight = UNSET;
        private int mark;
        private @Nullable StructurePoolElement attached;

        private int element = -1;
        private @Nullable List<Rotation> rotations;
        private int rotation;
        private @Nullable List<StructureTemplate.JigsawBlockInfo> targets;
        private int targetIndex;
        private int expandTo = UNSET;

        private Decision(Node source, StructureTemplate.JigsawBlockInfo jigsaw, Direction direction, BlockPos target, MutableObject<VoxelShape> free, List<StructurePoolElement> elements) {
            this.source = source;
            this.jigsaw = jigsaw;
            this.direction = direction;
            this.target = target;
            this.free = free;
            this.elements = elements;
            this.sourceRigid = source.piece.getElement().getProjection() == StructureTemplatePool.Projection.RIGID;
            this.sourceBoxY = source.piece.getBoundingBox().minY();
            this.localY = jigsaw.info().pos().getY() - sourceBoxY;
        }

        boolean mayStillAttach(StructurePoolElement wanted) {
            for (int i = Math.max(element, 0); i < elements.size(); i++) {
                if (elements.get(i) == wanted) {
                    return true;
                }
            }
            return false;
        }

        /// Places the next candidate that fits, skipping elements `wanted` rejects and those at their maximum.
        /// Like vanilla, an empty element ends the candidates, unless `pastEmpty`.
        boolean placeNext(Predicate<StructurePoolElement> wanted, boolean pastEmpty) {
            if (element >= 0 && !wanted.test(current())) {
                rotations = null;
                targets = null;
            }
            while (true) {
                if (targets != null) {
                    while (targetIndex < targets.size()) {
                        var candidate = targets.get(targetIndex++);
                        if (JigsawBlock.canAttach(jigsaw, candidate) && tryPlace(candidate)) {
                            return true;
                        }
                    }
                    targets = null;
                }
                if (rotations != null && rotation < rotations.size()) {
                    targets = current().getShuffledJigsawBlocks(templates, BlockPos.ZERO, rotations.get(rotation++), random);
                    targetIndex = 0;
                    expandTo = UNSET;
                    continue;
                }
                rotations = null;
                if (element + 1 >= elements.size()) {
                    return false;
                }
                var next = elements.get(element + 1);
                if (next == EmptyPoolElement.INSTANCE && !pastEmpty) {
                    return false;
                }
                element++;
                if (next != EmptyPoolElement.INSTANCE && wanted.test(next) && !isFull(next)) {
                    rotations = Rotation.getShuffled(random);
                    rotation = 0;
                }
            }
        }

        private StructurePoolElement current() {
            return elements.get(element);
        }

        private Rotation currentRotation() {
            return Objects.requireNonNull(rotations).get(rotation - 1);
        }

        private boolean tryPlace(StructureTemplate.JigsawBlockInfo targetJigsaw) {
            var targetElement = current();
            var targetRotation = currentRotation();
            var targetLocal = targetJigsaw.info().pos();
            var rawPosition = target.subtract(targetLocal);
            var rawBox = targetElement.getBoundingBox(templates, rawPosition, targetRotation);
            var targetProjection = targetElement.getProjection();
            boolean targetRigid = targetProjection == StructureTemplatePool.Projection.RIGID;
            int targetLocalY = targetLocal.getY();
            int deltaY = localY - targetLocalY + direction.getStepY();
            int targetBoxY = sourceRigid && targetRigid ? sourceBoxY + deltaY : baseHeight() - targetLocalY;
            int yOffset = targetBoxY - rawBox.minY();
            var moved = rawBox.moved(0, yOffset, 0);
            var position = rawPosition.offset(0, yOffset, 0);
            int expand = expandTo(targetElement, targetRotation);
            var box = expand > 0
                    ? new BoundingBox(moved.minX(), moved.minY(), moved.minZ(), moved.maxX(), Math.max(moved.maxY(), moved.minY() + Math.max(expand + 1, moved.getYSpan() - 1)), moved.maxZ())
                    : moved;
            if (Shapes.joinIsNotEmpty(free.get(), Shapes.create(AABB.of(box).deflate(0.25)), BooleanOp.ONLY_SECOND)) {
                return false;
            }

            int sourceDelta = source.piece.getGroundLevelDelta();
            int targetDelta = targetRigid ? sourceDelta - deltaY : targetElement.getGroundLevelDelta();
            var piece = new PoolElementStructurePiece(templates, targetElement, position, targetDelta, targetRotation, box, liquidSettings);
            int junctionY = sourceRigid ? sourceBoxY + localY : targetRigid ? targetBoxY + targetLocalY : baseHeight() + deltaY / 2;
            var jigsawPos = jigsaw.info().pos();
            place(this, new Node(piece, free, source.depth + 1),
                    new JigsawJunction(target.getX(), junctionY - localY + sourceDelta, target.getZ(), deltaY, targetProjection),
                    new JigsawJunction(jigsawPos.getX(), junctionY - targetLocalY + targetDelta, jigsawPos.getZ(), -deltaY, source.piece.getElement().getProjection()));
            return true;
        }

        private int baseHeight() {
            if (baseHeight == UNSET) {
                var pos = jigsaw.info().pos();
                baseHeight = generator.getFirstFreeHeight(pos.getX(), pos.getZ(), Heightmap.Types.WORLD_SURFACE_WG, heightAccessor, randomState);
            }
            return baseHeight;
        }

        // Flat pieces reserve room above them for the tallest piece their inward jigsaws can attach, so village streets can have houses inside their box.
        private int expandTo(StructurePoolElement element, Rotation rotation) {
            if (expandTo != UNSET) {
                return expandTo;
            }
            expandTo = 0;
            var box = element.getBoundingBox(templates, BlockPos.ZERO, rotation);
            if (expansionHack && box.getYSpan() <= 16) {
                for (var child : Objects.requireNonNull(targets)) {
                    if (box.isInside(child.info().pos().relative(JigsawBlock.getFrontFacing(child.info().state())))) {
                        var childPool = pools.apply(aliases.lookup(child.pool()));
                        int poolSize = childPool.map(pool -> pool.value().getMaxSize(templates)).orElse(0);
                        int fallbackSize = childPool.map(pool -> pool.value().getFallback().value().getMaxSize(templates)).orElse(0);
                        expandTo = Math.max(expandTo, Math.max(poolSize, fallbackSize));
                    }
                }
            }
            return expandTo;
        }
    }
}
