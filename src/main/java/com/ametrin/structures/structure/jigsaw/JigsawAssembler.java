package com.ametrin.structures.structure.jigsaw;

import com.ametrin.structures.structure.filter.PlacementFilter;
import com.ametrin.structures.structure.simple.PieceSource;
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
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.DimensionPadding;
import net.minecraft.world.level.levelgen.structure.pools.EmptyPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.JigsawJunction;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.pools.alias.PoolAliasLookup;
import net.minecraft.world.level.levelgen.structure.structures.JigsawStructure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.apache.commons.lang3.mutable.MutableObject;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

/// Grows a layout from its start piece the way vanilla's `JigsawPlacement` does, drawing from the random in the same order, and keeps [ConditionalPoolElement]s within their counts.
///
/// While an element is below its soft minimum, jigsaws that can't lead to it wait until the rest of the layout is done, so other pieces don't take its space first.
/// If the layout still ends with too few, it undoes part of the layout to make room for the element and grows the rest back around it.
/// It never undoes pieces that another element needs for its soft minimum, except to reach a minimum, which may take others down to theirs.
///
/// Pieces hold the element itself rather than its [ConditionalPoolElement], and are created with the structure's settings.
final class JigsawAssembler {
    private static final int UNSET = Integer.MIN_VALUE;

    private final Function<ResourceKey<StructureTemplatePool>, Optional<? extends Holder<StructureTemplatePool>>> pools;
    private final StructureTemplateManager templates;
    private final RandomSource random;
    private final int maxDepth;
    private final boolean expansionHack;
    private final PoolAliasLookup aliases;
    private final PieceSource.Context context;
    private final int maxBacktracks;

    // Every change to the layout, so it can be undone.
    private final List<Runnable> undo = new ArrayList<>();
    private final List<Node> placed = new ArrayList<>();
    private final List<Decision> decisions = new ArrayList<>();
    // Vanilla visits the highest priority first, in the order they were placed.
    private final NavigableMap<Integer, ArrayDeque<Node>> queue = new TreeMap<>(Comparator.reverseOrder());
    private final Map<ConditionalPoolElement, Integer> counts = new HashMap<>();
    // Elements with a soft minimum that the layout reached. Only grows, so a soft minimum stays once reached, even if that part is undone.
    private final Set<ConditionalPoolElement> softMinimums = new LinkedHashSet<>();
    private @Nullable Visit visit;
    private List<BoundingBox> blocked = List.of();
    private Map<ConditionalPoolElement, Set<Holder<StructureTemplatePool>>> leadingPools = Map.of();
    private final ArrayDeque<Deferred> deferred = new ArrayDeque<>();
    private boolean deferring;
    // Jigsaws a backtrack attached ahead of their turn, so they're skipped when it comes.
    private final Set<Attached> attachedEarly = new HashSet<>();
    // Only for listing jigsaws, so the layout's own random isn't drawn from. Layouts run on several threads, so each has its own.
    private final RandomSource listing = RandomSource.create(0);

    /// @param pools looks up the pools jigsaws point to, after `aliases`
    JigsawAssembler(PieceSource.Context context, Function<ResourceKey<StructureTemplatePool>, Optional<? extends Holder<StructureTemplatePool>>> pools, int maxDepth, boolean expansionHack, PoolAliasLookup aliases, int maxBacktracks) {
        var generation = context.generation();
        this.pools = pools;
        this.templates = generation.structureTemplateManager();
        this.random = generation.random();
        this.maxDepth = maxDepth;
        this.expansionHack = expansionHack;
        this.aliases = aliases;
        this.context = context;
        this.maxBacktracks = maxBacktracks;
    }

    /// A piece for `element` with the structure's settings, see [StructureElements].
    static PoolElementStructurePiece createPiece(PieceSource.Context context, StructurePoolElement element, BlockPos position, int groundLevelDelta, Rotation rotation, BoundingBox box) {
        var inner = element instanceof ConditionalPoolElement counted ? counted.element() : element;
        var placed = StructureElements.withStructureSettings(inner, context.processors(), context.foundation());
        return new PoolElementStructurePiece(context.generation().structureTemplateManager(), placed, position, groundLevelDelta, rotation, box, context.liquidSettings());
    }

    /// The space pieces can take: the box `maxDistance` around the center, within the world's height and `padding`, without the start piece.
    static Space freeSpace(BoundingBox start, int centerY, JigsawStructure.MaxDistance maxDistance, LevelHeightAccessor heightAccessor, DimensionPadding padding) {
        int centerX = (start.maxX() + start.minX()) / 2;
        int centerZ = (start.maxZ() + start.minZ()) / 2;
        int horizontal = maxDistance.horizontal();
        int minY = Math.max(centerY - maxDistance.vertical(), heightAccessor.getMinY() + padding.bottom());
        int maxY = Math.min(centerY + maxDistance.vertical(), heightAccessor.getMaxY() - padding.top());
        var bounds = minY <= maxY ? new BoundingBox(centerX - horizontal, minY, centerZ - horizontal, centerX + horizontal, maxY, centerZ + horizontal) : null;
        return new Space(bounds, List.of(start));
    }

    /// Grows the layout from `start`, which is already in place.
    ///
    /// @param element the pool element `start` was created from
    /// @param blocked taken by pieces outside the layout, which even pieces attached inside another one stay out of
    /// @return every piece in the order it was placed, `start` first, or empty if a minimum can't be met
    Optional<List<PoolElementStructurePiece>> assemble(PoolElementStructurePiece start, StructurePoolElement element, Space free, List<BoundingBox> blocked) {
        this.blocked = blocked;
        free.take(blocked);
        var root = new Node(start, element, null, free, 0);
        placed.add(root);
        if (element instanceof ConditionalPoolElement counted) {
            counts.put(counted, 1);
        }
        visit(root);
        leadingPools = leadingPools(Objects.requireNonNull(visit).jigsaws);
        deferring = !leadingPools.isEmpty();
        int backtracks = 0;
        var givenUp = new HashSet<ConditionalPoolElement>();
        while (true) {
            if (step()) {
                continue;
            }
            // Elements below their minimum first, since without them the structure doesn't generate.
            var missing = softMinimums.stream().filter(counted -> count(counted) < counted.softMinCount() && !givenUp.contains(counted)).min(Comparator.comparing(counted -> count(counted) >= counted.minCount()));
            if (missing.isEmpty()) {
                break;
            }
            if (backtracks++ < maxBacktracks && backtrack(missing.get())) {
                continue;
            }
            // A failed backtrack may have undone part of the layout, so it grows on from there before it's done.
            givenUp.add(missing.get());
        }
        // Backtracking for one element can take away another's, so the minimums are only checked at the end.
        if (softMinimums.stream().anyMatch(counted -> count(counted) < counted.minCount())) {
            return Optional.empty();
        }
        for (var node : placed) {
            node.junctions.forEach(node.piece::addJunction);
        }
        return Optional.of(placed.stream().map(Node::piece).toList());
    }

    // Decides the next jigsaw of the piece being visited, starts visiting the next piece, or decides the next jigsaw that waited. False once there's nothing left.
    private boolean step() {
        var current = visit;
        if (current != null && current.next < current.jigsaws.size()) {
            var jigsaw = current.jigsaws.get(current.next++);
            undo.add(() -> current.next--);
            if (attachedEarly.contains(new Attached(current.node, jigsaw.info().pos()))) {
                return true;
            }
            if (deferring && !leadsToMissing(jigsaw)) {
                deferred.addLast(new Deferred(current.node, jigsaw));
                undo.add(deferred::removeLast);
            } else {
                decide(current.node, jigsaw);
            }
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
        if (!deferred.isEmpty()) {
            if (deferring) {
                deferring = false;
                undo.add(() -> deferring = true);
            }
            var next = deferred.removeFirst();
            undo.add(() -> deferred.addFirst(next));
            if (!attachedEarly.contains(new Attached(next.node, next.jigsaw.info().pos()))) {
                decide(next.node, next.jigsaw);
            }
            return true;
        }
        return false;
    }

    // Whether the jigsaw's pool can lead to an element below its soft minimum. Jigsaws that point to an unknown pool don't wait.
    private boolean leadsToMissing(StructureTemplate.JigsawBlockInfo jigsaw) {
        var pool = pools.apply(aliases.lookup(jigsaw.pool()));
        if (pool.isEmpty()) {
            return true;
        }
        var missing = false;
        for (var entry : leadingPools.entrySet()) {
            if (count(entry.getKey()) < entry.getKey().softMinCount()) {
                if (entry.getValue().contains(pool.get())) {
                    return true;
                }
                missing = true;
            }
        }
        return !missing;
    }

    /// For each element with a soft minimum that the jigsaws can lead to, the pools that lead to it: through their elements, their fallback and the pools their elements' jigsaws point to.
    private Map<ConditionalPoolElement, Set<Holder<StructureTemplatePool>>> leadingPools(List<StructureTemplate.JigsawBlockInfo> jigsaws) {
        var parents = new HashMap<Holder<StructureTemplatePool>, Set<Holder<StructureTemplatePool>>>();
        var containing = new HashMap<ConditionalPoolElement, Set<Holder<StructureTemplatePool>>>();
        var visited = new HashSet<Holder<StructureTemplatePool>>();
        var queue = new ArrayDeque<Holder<StructureTemplatePool>>();
        jigsaws.forEach(jigsaw -> pools.apply(aliases.lookup(jigsaw.pool())).ifPresent(queue::add));
        while (!queue.isEmpty()) {
            var pool = queue.poll();
            if (!visited.add(pool)) {
                continue;
            }
            var children = new ArrayList<Holder<StructureTemplatePool>>();
            children.add(pool.value().getFallback());
            for (var entry : pool.value().getTemplates()) {
                var element = entry.getFirst();
                if (element instanceof ConditionalPoolElement counted && counted.softMinCount() > 0) {
                    containing.computeIfAbsent(counted, _ -> new HashSet<>()).add(pool);
                }
                Jigsaws.pools(element, templates, listing).forEach(key -> pools.apply(aliases.lookup(key)).ifPresent(children::add));
            }
            for (var child : children) {
                parents.computeIfAbsent(child, _ -> new HashSet<>()).add(pool);
                queue.add(child);
            }
        }

        var leading = new HashMap<ConditionalPoolElement, Set<Holder<StructureTemplatePool>>>();
        containing.forEach((element, holders) -> {
            var found = new HashSet<>(holders);
            var back = new ArrayDeque<>(holders);
            while (!back.isEmpty()) {
                for (var parent : parents.getOrDefault(back.poll(), Set.of())) {
                    if (found.add(parent)) {
                        back.add(parent);
                    }
                }
            }
            leading.put(element, found);
        });
        return leading;
    }

    private void visit(Node node) {
        var previous = visit;
        visit = new Visit(node, Jigsaws.shuffled(node.piece.getElement(), templates, node.piece.getPosition(), node.piece.getRotation(), random));
        undo.add(() -> visit = previous);
    }

    private void decide(Node source, StructureTemplate.JigsawBlockInfo jigsaw) {
        var decision = createDecision(source, jigsaw);
        if (decision != null) {
            decision.placeNext(_ -> true, false);
        }
    }

    private @Nullable Decision createDecision(Node source, StructureTemplate.JigsawBlockInfo jigsaw) {
        var poolKey = aliases.lookup(jigsaw.pool());
        var pool = pools.apply(poolKey);
        if (pool.isEmpty() || pool.get().value().size() == 0 && !pool.get().is(Pools.EMPTY)) {
            ASLog.warn("Empty or non-existent pool: {}", poolKey.identifier());
            return null;
        }
        var fallback = pool.get().value().getFallback();
        if (fallback.value().size() == 0 && !fallback.is(Pools.EMPTY)) {
            ASLog.warn("Empty or non-existent fallback pool: {}", fallback.unwrapKey().map(key -> key.identifier().toString()).orElse("<unregistered>"));
            return null;
        }

        var direction = JigsawBlock.getFrontFacing(jigsaw.info().state());
        var target = jigsaw.info().pos().relative(direction);
        Space free;
        if (source.piece.getBoundingBox().isInside(target)) {
            free = source.inside.get();
            if (free == null) {
                free = new Space(source.piece.getBoundingBox(), blocked);
                source.inside.setValue(free);
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
            if (element instanceof ConditionalPoolElement counted && counted.softMinCount() > 0) {
                softMinimums.add(counted);
            }
        }

        var decision = new Decision(source, jigsaw, direction, target, free, elements);
        decisions.add(decision);
        undo.add(decisions::removeLast);
        decision.mark = undo.size();
        return decision;
    }

    // First looks for jigsaws that could attach the element if the pieces in its way were gone, and takes the earliest, so the most of the layout grows back around
    // the element, which leaves room for more of it. If each would undo one of the element's own pieces, it goes back to the latest jigsaw that could still attach it
    // instead, so the regrowth can find room for both. Neither undoes so far that another element drops below its soft minimum, so elements don't keep taking each other's place.
    private boolean backtrack(ConditionalPoolElement missing) {
        return makeRoom(missing) || retryEarlierDecisions(missing);
    }

    // Only undoes once it found a jigsaw where the element fits afterwards, so when there's none the layout stays as it is.
    private boolean makeRoom(ConditionalPoolElement missing) {
        // Undoing one of the element's own pieces to place it elsewhere doesn't add one.
        int keep = keptBefore(missing, false);
        Decision best = null;
        int bestMark = Integer.MAX_VALUE;
        for (var decision : decisions) {
            int mark = decision.undoMarkFor(missing);
            if (mark > keep && mark < bestMark) {
                best = decision;
                bestMark = mark;
            }
        }
        if (best == null) {
            return false;
        }
        int undoMark = bestMark;
        var blocker = decisions.stream().filter(decision -> decision.mark == undoMark).findFirst().orElseThrow();
        undoTo(undoMark);
        if (blocker == best) {
            best.restart();
            // Past an empty element too: another draw from the pool could have reached the element.
            return best.placeNext(element -> element == missing, true);
        }
        // The jigsaw's own decision is undone, so it's decided again here, ahead of its turn, and the decision that placed the first piece in the way goes on with its next candidate.
        var attached = new Attached(best.source, best.jigsaw.info().pos());
        attachedEarly.add(attached);
        undo.add(() -> attachedEarly.remove(attached));
        var again = Objects.requireNonNull(createDecision(best.source, best.jigsaw));
        var placedMissing = again.placeNext(element -> element == missing, true);
        blocker.placeNext(_ -> true, false);
        return placedMissing;
    }

    private boolean retryEarlierDecisions(ConditionalPoolElement missing) {
        int keep = keptBefore(missing, true);
        Decision earliest = null;
        for (int i = decisions.size() - 1; i >= 0 && decisions.get(i).mark > keep; i--) {
            var decision = decisions.get(i);
            if (decision.attached == missing || !decision.mayStillAttach(missing)) {
                continue;
            }
            undoTo(decision.mark);
            // Past an empty element too: another draw from the pool could have reached the element.
            if (decision.placeNext(element -> element == missing, true)) {
                return true;
            }
            earliest = decision;
        }
        // The later decisions it tried were undone and get decided again, but this one stays decided, so it starts over with all of its candidates.
        if (earliest != null) {
            earliest.restart();
            earliest.placeNext(_ -> true, false);
        }
        return false;
    }

    /// The latest mark a backtrack for `missing` must not undo to, so every other element with a soft minimum keeps as many pieces as that needs.
    /// While `missing` is below its minimum, the others only keep as many as their minimum needs. An element below what it keeps keeps every piece.
    ///
    /// @param movesOwn whether `missing` may lose its own pieces, otherwise it keeps all of them
    private int keptBefore(ConditionalPoolElement missing, boolean movesOwn) {
        boolean belowMinimum = count(missing) < missing.minCount();
        int keep = -1;
        for (var element : softMinimums) {
            if (element == missing && movesOwn) {
                continue;
            }
            int needed = element == missing ? Integer.MAX_VALUE : belowMinimum ? element.minCount() : element.softMinCount();
            int spare = Math.max(count(element) - needed, 0);
            var marks = placed.stream().filter(node -> node.element == element).mapToInt(Node::placedAt).boxed().sorted(Comparator.reverseOrder()).toList();
            // Undoing to a mark removes the pieces placed at or after it, latest first.
            if (spare < marks.size()) {
                keep = Math.max(keep, marks.get(spare));
            }
        }
        return keep;
    }

    private void undoTo(int mark) {
        while (undo.size() > mark) {
            undo.removeLast().run();
        }
    }

    private int count(ConditionalPoolElement element) {
        return counts.getOrDefault(element, 0);
    }

    private static boolean allowsDepth(StructurePoolElement element, int depth) {
        return !(element instanceof ConditionalPoolElement conditional) || conditional.allowsDepth(depth);
    }

    private boolean isFull(StructurePoolElement element) {
        return element instanceof ConditionalPoolElement counted && counted.maxCount().filter(max -> count(counted) >= max).isPresent();
    }

    private void place(Decision decision, Node node, JigsawJunction sourceJunction, JigsawJunction targetJunction) {
        var taken = decision.free.taken;
        taken.add(new Taken(node.piece.getBoundingBox(), node));
        undo.add(taken::removeLast);

        var sourceJunctions = decision.source.junctions;
        sourceJunctions.add(sourceJunction);
        undo.add(sourceJunctions::removeLast);
        node.junctions.add(targetJunction);

        placed.add(node);
        undo.add(placed::removeLast);
        decision.attached = node.element;
        undo.add(() -> decision.attached = null);
        if (node.element instanceof ConditionalPoolElement counted) {
            counts.merge(counted, 1, Integer::sum);
            undo.add(() -> counts.merge(counted, -1, Integer::sum));
        }
        if (node.depth <= maxDepth) {
            var pieces = queue.computeIfAbsent(decision.jigsaw.placementPriority(), _ -> new ArrayDeque<>());
            pieces.addLast(node);
            undo.add(pieces::removeLast);
        }
    }

    /// @param element  the pool element the piece was created from, which the counts go by
    /// @param decision the decision that placed it, none for the start piece
    /// @param free     the space for pieces attached outside this one
    /// @param inside   the space for pieces attached inside this one, created when the first one is tried
    private record Node(PoolElementStructurePiece piece, StructurePoolElement element, @Nullable Decision decision, Space free, MutableObject<@Nullable Space> inside, int depth, List<JigsawJunction> junctions) {
        Node(PoolElementStructurePiece piece, StructurePoolElement element, @Nullable Decision decision, Space free, int depth) {
            this(piece, element, decision, free, new MutableObject<>(), depth, new ArrayList<>());
        }

        // Before every decision, so nothing undoes it.
        int placedAt() {
            return decision == null ? -1 : decision.mark;
        }
    }

    /// @param node the piece that took it, none for the start piece and pieces outside the layout, which can't be undone
    private record Taken(BoundingBox box, @Nullable Node node) {}

    private record Attached(Node source, BlockPos jigsaw) {}

    /// Where pieces can go: inside the bounds, if there are any, and clear of the taken boxes.
    static final class Space {
        private final @Nullable BoundingBox bounds;
        private final List<Taken> taken = new ArrayList<>();

        Space(@Nullable BoundingBox bounds, List<BoundingBox> taken) {
            this.bounds = bounds;
            take(taken);
        }

        private void take(List<BoundingBox> boxes) {
            boxes.forEach(box -> taken.add(new Taken(box, null)));
        }

        private boolean fits(BoundingBox box) {
            return within(box) && taken.stream().noneMatch(other -> other.box.intersects(box));
        }

        private boolean within(BoundingBox box) {
            return bounds != null && box.minX() >= bounds.minX() && box.minY() >= bounds.minY() && box.minZ() >= bounds.minZ() && box.maxX() <= bounds.maxX() && box.maxY() <= bounds.maxY() && box.maxZ() <= bounds.maxZ();
        }
    }

    private record Deferred(Node node, StructureTemplate.JigsawBlockInfo jigsaw) {}

    /// @param boxY   the bottom of the attached piece's box
    /// @param deltaY how much higher the attached piece's box starts than the source's, when both are rigid
    private record Placement(BlockPos position, BoundingBox box, int boxY, int deltaY) {}

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
        private final Space free;
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
        private int expandTo;

        private Decision(Node source, StructureTemplate.JigsawBlockInfo jigsaw, Direction direction, BlockPos target, Space free, List<StructurePoolElement> elements) {
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

        /// The undo mark to go back to so that `wanted` can attach here, or -1 if it can't. Undoing to it removes every piece in the element's way.
        /// It can't take away the source piece, or pieces placed before it, the start piece or pieces outside the layout.
        int undoMarkFor(StructurePoolElement wanted) {
            if (attached == wanted || !elements.contains(wanted) || !allowsDepth(wanted, source.depth + 1)) {
                return -1;
            }
            int best = -1;
            for (var rotation : Rotation.values()) {
                var candidates = Jigsaws.shuffled(wanted, templates, BlockPos.ZERO, rotation, listing);
                int expand = expansion(wanted, rotation, candidates);
                for (var candidate : candidates) {
                    if (!JigsawBlock.canAttach(jigsaw, candidate)) {
                        continue;
                    }
                    var placement = placement(wanted, rotation, candidate, expand);
                    if (!free.within(placement.box) || !passesFilters(wanted, placement)) {
                        continue;
                    }
                    int needed = mark;
                    for (var taken : free.taken) {
                        if (!taken.box.intersects(placement.box)) {
                            continue;
                        }
                        if (taken.node == null || taken.node.placedAt() <= source.placedAt()) {
                            needed = -1;
                            break;
                        }
                        needed = Math.min(needed, taken.node.placedAt());
                    }
                    best = Math.max(best, needed);
                }
            }
            return best;
        }

        void restart() {
            element = -1;
            rotations = null;
            targets = null;
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
                    targets = Jigsaws.shuffled(current(), templates, BlockPos.ZERO, rotations.get(rotation++), random);
                    targetIndex = 0;
                    expandTo = expansion(current(), currentRotation(), targets);
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
                if (next != EmptyPoolElement.INSTANCE && wanted.test(next) && !isFull(next) && allowsDepth(next, source.depth + 1)) {
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
            var placement = placement(targetElement, targetRotation, targetJigsaw, expandTo);
            // Filters on the terrain cost more, so they come last.
            if (!free.fits(placement.box) || !passesFilters(targetElement, placement)) {
                return false;
            }

            var targetProjection = targetElement.getProjection();
            boolean targetRigid = targetProjection == StructureTemplatePool.Projection.RIGID;
            int targetLocalY = targetJigsaw.info().pos().getY();
            int deltaY = placement.deltaY;
            int sourceDelta = source.piece.getGroundLevelDelta();
            int targetDelta = targetRigid ? sourceDelta - deltaY : targetElement.getGroundLevelDelta();
            var piece = createPiece(context, targetElement, placement.position, targetDelta, targetRotation, placement.box);
            int junctionY = sourceRigid ? sourceBoxY + localY : targetRigid ? placement.boxY + targetLocalY : baseHeight() + deltaY / 2;
            var jigsawPos = jigsaw.info().pos();
            place(this, new Node(piece, targetElement, this, free, source.depth + 1),
                    new JigsawJunction(target.getX(), junctionY - localY + sourceDelta, target.getZ(), deltaY, targetProjection),
                    new JigsawJunction(jigsawPos.getX(), junctionY - targetLocalY + targetDelta, jigsawPos.getZ(), -deltaY, source.piece.getElement().getProjection()));
            return true;
        }

        // Where `element` goes when it attaches here by `targetJigsaw`.
        private Placement placement(StructurePoolElement element, Rotation rotation, StructureTemplate.JigsawBlockInfo targetJigsaw, int expand) {
            var targetLocal = targetJigsaw.info().pos();
            var rawPosition = target.subtract(targetLocal);
            var rawBox = element.getBoundingBox(templates, rawPosition, rotation);
            boolean targetRigid = element.getProjection() == StructureTemplatePool.Projection.RIGID;
            int deltaY = localY - targetLocal.getY() + direction.getStepY();
            int boxY = sourceRigid && targetRigid ? sourceBoxY + deltaY : baseHeight() - targetLocal.getY();
            int yOffset = boxY - rawBox.minY();
            var moved = rawBox.moved(0, yOffset, 0);
            var box = expand > 0
                    ? new BoundingBox(moved.minX(), moved.minY(), moved.minZ(), moved.maxX(), Math.max(moved.maxY(), moved.minY() + Math.max(expand + 1, moved.getYSpan() - 1)), moved.maxZ())
                    : moved;
            return new Placement(rawPosition.offset(0, yOffset, 0), box, boxY, deltaY);
        }

        private boolean passesFilters(StructurePoolElement element, Placement placement) {
            return !(element instanceof ConditionalPoolElement conditional) || conditional.passesFilters(new PlacementFilter.Context(context.generation(), placement.position, placement.box, context.terrain()));
        }

        private int baseHeight() {
            if (baseHeight == UNSET) {
                var pos = jigsaw.info().pos();
                baseHeight = context.terrain().surfaceHeight(Heightmap.Types.WORLD_SURFACE_WG, pos.getX(), pos.getZ());
            }
            return baseHeight;
        }

        // Flat pieces reserve room above them for the tallest piece their inward jigsaws can attach, so village streets can have houses inside their box.
        private int expansion(StructurePoolElement element, Rotation rotation, List<StructureTemplate.JigsawBlockInfo> jigsaws) {
            int expand = 0;
            var box = element.getBoundingBox(templates, BlockPos.ZERO, rotation);
            if (expansionHack && box.getYSpan() <= 16) {
                for (var child : jigsaws) {
                    if (box.isInside(child.info().pos().relative(JigsawBlock.getFrontFacing(child.info().state())))) {
                        var childPool = pools.apply(aliases.lookup(child.pool()));
                        int poolSize = childPool.map(pool -> pool.value().getMaxSize(templates)).orElse(0);
                        int fallbackSize = childPool.map(pool -> pool.value().getFallback().value().getMaxSize(templates)).orElse(0);
                        expand = Math.max(expand, Math.max(poolSize, fallbackSize));
                    }
                }
            }
            return expand;
        }
    }
}
