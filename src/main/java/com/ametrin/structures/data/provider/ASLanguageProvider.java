package com.ametrin.structures.data.provider;

import com.ametrin.structures.AmetrinStructures;
import com.ametrin.structures.client.ASClientConfig;
import com.ametrin.structures.client.FixtureScreen;
import com.ametrin.structures.client.FixtureTooltip;
import com.ametrin.structures.client.compat.SpreadWaypointsClient;
import com.ametrin.structures.debug.*;
import com.ametrin.structures.foam.FoamPresets;
import com.ametrin.structures.foam.FoamSpread;
import com.ametrin.structures.foam.FoamSpreadBehavior;
import com.ametrin.structures.foam.FoamSpreadRestriction;
import com.ametrin.structures.registry.ASBlocks;
import com.ametrin.structures.registry.ASFoamSpreadBehaviors;
import com.ametrin.structures.registry.ASFoamSpreadRestrictions;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

public class ASLanguageProvider extends LanguageProvider {
    public ASLanguageProvider(PackOutput output) {
        super(output, AmetrinStructures.MOD_ID, "en_us");
    }

    @Override
    protected void addTranslations() {
        blocksAndItems();
        foam();
        fixtureScreen();
        add(ASClientConfig.STRUCTURE_BLOCK_NAME_TAGS_KEY, "Structure Block Name Tags");
        add(ASClientConfig.STRUCTURE_BLOCK_NAME_TAGS_KEY + ".tooltip", "Shows a structure block's template name above it. Turn this off if another mod replaces the structure block's renderer.");
        reports();
        lootStats();
        pieces();
        spread();
        check();
        structureBlocks();
    }

    private void blocksAndItems() {
        addBlock(ASBlocks.FOAM, "Foam");
        addBlock(ASBlocks.FOAM_SPREADER, "Foam Spreader");
        addBlock(ASBlocks.FIXTURE, "Fixture");
        // `indoor_orthogonal` → `Indoor Orthogonal Foam`
        for (var preset : FoamPresets.ALL) {
            var words = Arrays.stream(preset.name().split("_")).map(word -> word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1));
            add(preset.translationKey(), words.collect(Collectors.joining(" ")) + " Foam");
        }
    }

    private void foam() {
        add(FoamSpread.Keys.FILL, "Hold %s while placing to fill the space:");
        add(FoamSpread.Keys.UNBOUNDED, "Never stops, only walls contain it");
        add(FoamSpreadBehavior.descriptionKey(ASFoamSpreadBehaviors.FACES.getId()), "Spreads to the six adjacent blocks");
        add(FoamSpreadBehavior.descriptionKey(ASFoamSpreadBehaviors.EDGES.getId()), "Spreads diagonally across block edges");
        add(FoamSpreadBehavior.descriptionKey(ASFoamSpreadBehaviors.FACES_AND_EDGES.getId()), "Spreads to adjacent blocks and across block edges");
        add(FoamSpreadBehavior.descriptionKey(ASFoamSpreadBehaviors.PLANAR.getId()), "Spreads flat, across the plane you are looking at");
        add(FoamSpreadRestriction.descriptionKey(ASFoamSpreadRestrictions.MAX_DISTANCE.getId()), "Stops %s blocks from where it was placed");
        add(FoamSpreadRestriction.descriptionKey(ASFoamSpreadRestrictions.REQUIRES_SHELTER.getId()), "Only spreads under a roof");
        add(FoamSpreadRestriction.descriptionKey(ASFoamSpreadRestrictions.STACK_COUNT_DISTANCE.getId()), "Reaches as many blocks as the stack has items");
    }

    private void fixtureScreen() {
        add(FixtureTooltip.Keys.HOLD_SHIFT, "%s alternatives, hold Shift to list them");
        add(FixtureTooltip.Keys.CHANCE, ", runs %s of the time");

        add(FixtureScreen.Keys.FIXTURE, "Fixture");
        add(FixtureScreen.Keys.PAGE_FIXTURES, "Fixtures");
        add(FixtureScreen.Keys.PAGE_SETTINGS, "Settings");
        add(FixtureScreen.Keys.ALTERNATIVES, "Alternative %s of %s");
        add(FixtureScreen.Keys.PREVIOUS, "Previous alternative");
        add(FixtureScreen.Keys.NEXT, "Next alternative");
        add(FixtureScreen.Keys.ADD, "Add a copy of this alternative");
        add(FixtureScreen.Keys.REMOVE, "Remove this alternative");
        add(FixtureScreen.Keys.TYPE, "Type");
        add(FixtureScreen.Keys.WEIGHT, "Weight");
        add(FixtureScreen.Keys.GENERATION_CHANCE, "Generation chance");
        add(FixtureScreen.Keys.GENERATION_CHANCE_TOOLTIP, "The chance, from 0 to 1, that this alternative runs once it's drawn. Blank for always.");
        add(FixtureScreen.Keys.CONDITIONS, "Conditions");
        add(FixtureScreen.Keys.CONDITIONS_TOOLTIP, "Conditions as SNBT that must all pass where the fixture acts, or this alternative isn't drawn. Takes biomes, dimensions, heights and NeoForge conditions like neoforge:mod_loaded. Blank for always.");
        add(FixtureScreen.Keys.PARAMETERS, "Parameters");
        add(FixtureScreen.Keys.REQUIRED, "Required");
        add(FixtureScreen.Keys.EXAMPLE, "e.g. %s");
        add(FixtureScreen.Keys.NONE, "None");
        add(FixtureScreen.Keys.CUSTOM_NAME, "Name");
        add(FixtureScreen.Keys.OFFSET, "Offset");
        add(FixtureScreen.Keys.OFFSET_TOOLTIP, "Where the fixture acts, from the bottom center of the marker, along the template's axes. At most %s blocks along each axis.");
        add(FixtureScreen.Keys.BECOMES, "Becomes");
        add(FixtureScreen.Keys.BECOMES_HINT, "air, or water if waterlogged");
        add(FixtureScreen.Keys.USE_GRAVITY, "Snap to ground");
        add(FixtureScreen.Keys.MARK_POST_PROCESSING, "Mark for post-processing");
        add(FixtureScreen.Keys.GENERATE, "Generate now");
        add(FixtureScreen.Keys.GENERATE_TOOLTIP, "Saves, then runs this marker in place. The marker goes back into your inventory first.");
    }

    private void reports() {
        add(ChatReport.Keys.LINE, " %s × %s (%s%%)");
        add(ChatReport.Keys.MORE, " [+%s more]");
        add(ChatReport.Keys.GONE, "That report is no longer kept, run the command again");
        add(ChatReport.Keys.COPY, "[copy]");
        add(ChatReport.Keys.COPY_HOVER, "Copy as tab-separated values, e.g. for a spreadsheet");
    }

    private void lootStats() {
        add(LootStatsCommand.Keys.SUMMARY, "%s opened %s times with luck %s, %s%% empty");
        add(LootStatsCommand.Keys.RANGE_CHEST, "%s to %s per chest");
        add(LootStatsCommand.Keys.RANGE_STRUCTURE, "%s to %s per structure");
        add(LootStatsCommand.Keys.FAILED, "The loot stats report failed: %s");
        add(LootStatsCommand.Keys.STRUCTURE_STARTED, "Looking for %s layouts of %s within %s chunks…");
        add(LootStatsCommand.Keys.NO_LAYOUTS, "%s doesn't generate within %s chunks");
        add(LootStatsCommand.Keys.STRUCTURE_SUMMARY, "%s: %s layouts within %s chunks with luck %s, %s%% without loot");
        add(LootStatsCommand.Keys.STRUCTURE_MIX, " Layouts per structure: %s");
        add(LootStatsCommand.Keys.SOURCES, " %s loot sources, hover to list them");
        add(LootStatsCommand.Keys.CODE_PIECES, " Not counted: %s pieces per structure that place their contents in code");
        add(LootStatsCommand.Keys.DATA_MARKERS, " Not counted: %s data markers per structure, which the pieces turn into containers or entities in code");
    }

    private void pieces() {
        add(PiecesCommand.Keys.STARTED, "Laying out %2$s at %1$s random seeds…");
        add(PiecesCommand.Keys.FAILED, "The pieces report failed: %s");
        add(PiecesCommand.Keys.NONE, "%s laid out no pieces in %s tries");
        add(PiecesCommand.Keys.SUMMARY, "%s: %s layouts at random seeds, %s%% laid out no pieces");
        add(PiecesCommand.Keys.COUNT, " %s pieces per layout, %s to %s");
        add(PiecesCommand.Keys.SIZE, " %s × %s × %s blocks on average, up to %s × %s × %s");
        add(PiecesCommand.Keys.OPEN_JIGSAWS, " %s open jigsaws per layout");
        add(PiecesCommand.Keys.RANGE, "%s to %s per layout");

        add(GalleryCommand.Keys.STARTED, "Laying out %2$s at %1$s random seeds…");
        add(GalleryCommand.Keys.FAILED, "The gallery failed: %s");
        add(GalleryCommand.Keys.PLACED, "Placed %s layouts of %s in rows of %s");
    }

    private void spread() {
        add(SpreadCommand.Keys.STARTED, "Checking where %s would generate within %s chunks…");
        add(SpreadCommand.Keys.FAILED, "The spread report failed: %s");
        add(SpreadCommand.Keys.NO_SET, "%s is in no structure set of this dimension, so it never generates here");
        add(SpreadCommand.Keys.SET_UNUSED, "%s isn't a structure set of this dimension, so it never generates here");
        add(SpreadCommand.Keys.INVALID_SET, "There is no structure set \"%s\"");
        add(SpreadCommand.Keys.INVALID_PLACEMENT, "Not a valid placement: %s");
        add(SpreadCommand.Keys.RINGS_PLACEMENT, "Concentric rings are worked out when the world loads, so only a set's own can be reported");
        add(SpreadCommand.Keys.PLACEMENT, "Placement %s");
        add(SpreadCommand.Keys.WITH_PLACEMENT, "%s with placement %s");
        add(SpreadCommand.Keys.PLACEMENT_STARTED, "Checking which chunks %s picks within %s chunks…");
        add(SpreadCommand.Keys.CANDIDATES, "%s picks %s chunks within %s chunks");
        add(SpreadCommand.Keys.SUMMARY, "%s within %s chunks: %s candidate chunks, %s generate (%s%%)");
        add(SpreadCommand.Keys.LIMITED, " Stopped after the nearest %s");
        add(SpreadCommand.Keys.GENERATED, " %s: %s");
        add(SpreadCommand.Keys.REJECTED, " %s × %s");
        add(SpreadCommand.Keys.HEIGHTS, " Start height: average %s, lowest %s, highest %s");
        add(SpreadCommand.Keys.SPACING, " Distance to the nearest other: average %s, closest %s blocks");
        add(SpreadCommand.Keys.NEAREST, " Nearest: %s blocks away at %s");
        add(SpreadCommand.Keys.VISIT_HINT, " [Visit them, nearest first]");
        add(SpreadCommand.Keys.OUTSIDE_BIOME_HIDDEN, " %s rejected spots outside its biomes aren't waypoints, add \"all\" to show them");
        add(SpreadCommand.Keys.WAYPOINTS_LIMITED, " Waypoints show the nearest %s of each kind");
        add(SpreadCommand.Keys.CLEARED, "Removed the spread waypoints");
        add(SpreadCommand.Keys.TIMING, " Time for %s: %s over %s tries, %s per try");
        add(SpreadCommand.Keys.TIMING_STEPS, "  %s");
        add(SpreadCommand.Keys.TIMING_STEP, "%s %s (%s%%)");
        add(SpreadCommand.Keys.TIMING_LIMITED, " Times show the %s slowest structures");
        add(SpreadCommand.Keys.VISIT_NONE, "Nothing to visit, run /ametrin structures spread and find some spots first");
        add(SpreadCommand.Keys.VISIT_ARRIVED, "%s of %s: %s at %s, %s blocks");
        add(SpreadCommand.Keys.VISIT_ARRIVED_CANDIDATE, "%s of %s: picked chunk at %s");

        reason(StructureSpread.Reason.BIOME, "wrong biome at the start", "biome");
        reason(StructureSpread.Reason.NO_PIECES, "no pieces", "no pieces");
        reason(StructureSpread.Reason.NO_START_HEIGHT, "empty start height range", "start height");
        reason(StructureSpread.Reason.filtered(""), "filter %s", "%s");
        reason(StructureSpread.Reason.takenBy(""), "taken by %s from the same set", "taken by %s");
        reason(StructureSpread.Reason.NO_GENERATION_POINT, "no generation point or wrong biome", "no generation point");
        reason(StructureSpread.Reason.NOT_PICKED_FIRST, "another structure of the set was picked first", "not picked first");

        add(StructureSpread.Step.BIOME_PRECHECK.translationKey(), "biome pre-check");
        add(StructureSpread.Step.PIECES.translationKey(), "pieces");
        add(StructureSpread.Step.START_HEIGHT.translationKey(), "start height");
        add(StructureSpread.Step.filter("").translationKey(), "filter %s");
    }

    /// @param waypoint the shorter label of a rejected spot's waypoint
    private void reason(StructureSpread.Reason reason, String report, String waypoint) {
        add(reason.translationKey(), report);
        add(SpreadWaypointsClient.rejectedKey(reason.key()), waypoint);
    }

    private void check() {
        add(CheckCommand.Keys.STARTED, "Checking %s structures…");
        add(CheckCommand.Keys.NONE, "There are no structures in the namespace %s");
        add(CheckCommand.Keys.FAILED, "The structure check failed: %s");
        add(CheckCommand.Keys.CLEAN, "Checked %s structures and %s templates: no problems found");
        add(CheckCommand.Keys.SUMMARY, "Checked %s structures and %s templates: %s problems");
        add(CheckCommand.Keys.SKIPPED, " Skipped %s structures of types it can't look into: %s");
        add(CheckCommand.Keys.PROBLEM, " %s: %s");
        add(CheckCommand.Keys.MORE, " …and %s more, all listed in the log");

        add(StructureCheck.Problem.Subject.STRUCTURE.translationKey(), "Structure %s");
        add(StructureCheck.Problem.Subject.TEMPLATE.translationKey(), "Template %s");
        add(StructureCheck.Problem.Subject.FIXTURE_PRESET.translationKey(), "Fixture preset %s");
        add(StructureCheck.Keys.AT, "at %s");
        add(StructureCheck.Keys.ALTERNATIVE, "alternative %s");
        add(StructureCheck.Keys.MISSING_TEMPLATE, "template %s doesn't exist");
        add(StructureCheck.Keys.MISSING_POOL, "jigsaw %s in %s names pool %s, which doesn't exist");
        add(StructureCheck.Keys.EMPTY_POOL, "jigsaw %s in %s names pool %s, which is empty");
        add(StructureCheck.Keys.EMPTY_FALLBACK, "pool %s falls back to %s, which is empty");
        add(StructureCheck.Keys.UNMATCHED_JIGSAW, "jigsaw %s in %s targets %s, but no jigsaw in pool %s or its fallback has that name");
        add(StructureCheck.Keys.MISFACING_JIGSAW, "jigsaw %s in %s targets %s, but every jigsaw of that name in pool %s faces the wrong way");
        add(StructureCheck.Keys.START_JIGSAW, "no template in the start pool %s has a jigsaw named %s");
        add(StructureCheck.Keys.MISSING_LOOT_TABLE, "container %s names loot table %s, which doesn't exist");
        add(StructureCheck.Keys.UNREADABLE_FIXTURE, "fixture %s doesn't read: %s");
        add(StructureCheck.Keys.MISSING_REFERENCE, "fixture %s names %s %s, which doesn't exist");
        add(StructureCheck.Keys.FIXTURE_OFFSET, "fixture %s is offset %s, more than %s blocks along an axis, so it acts at the marker");
    }

    private void structureBlocks() {
        add(SaveStructuresCommand.Keys.SAVED, "Saved %s structures within %s blocks");
        add(SaveStructuresCommand.Keys.FAILED, "Could not save: %s");
        add(ExportTemplatesCommand.Keys.EXPORTED, "Exported %s new and %s changed templates to the resources, %s were unchanged");
        add(ExportTemplatesCommand.Keys.NONE, "This world has no saved templates to export");
        add(ExportTemplatesCommand.Keys.SKIPPED, "Skipped namespaces the resources have no data folder for: %s");
        add(ExportTemplatesCommand.Keys.FAILED, "Could not export the templates: %s");
    }
}
