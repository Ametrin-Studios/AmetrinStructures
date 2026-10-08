package com.ametrin.structures.client;

import com.ametrin.structures.fixture.*;
import com.ametrin.structures.network.ASPayloads;
import com.ametrin.structures.registry.ASFixtures;
import com.ametrin.structures.registry.ASRegistries;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.serialization.DynamicOps;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.tabs.MenuTabBar;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.layouts.FrameLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.Layout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/// Authoring screen for a fixture block. Only Cancel discards edits.
public class FixtureScreen extends Screen {
    private static final int MAX_ROW_WIDTH = 520;
    private static final int ROW_HEIGHT = 24;
    private static final int MAX_LABEL_WIDTH = 140;
    private static final int WIDGET_HEIGHT = 20;
    private static final int SMALL_BUTTON = 20;
    private static final int GAP = 4;
    private static final int FOOTER_BUTTON_WIDTH = 100;
    private static final int INVALID_COLOR = 0xFFFF5555;

    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    private final TabManager tabManager = new TabManager(this::addRenderableWidget, this::removeWidget, this::onTabSelected, _ -> {});
    private @Nullable MenuTabBar tabNavigationBar;
    private int tabIndex;
    private final List<ListTab> tabs = new ArrayList<>();

    private final BlockPos pos;
    private final List<Alternative> alternatives = new ArrayList<>();
    private final List<Identifier> availableTypes;
    private int selected;
    private boolean rebuildFixtures;
    private boolean refocusType;
    private @Nullable EditBox typeBox;
    private @Nullable Row typeRow;

    private String customName;
    private boolean useGravity;
    private boolean markPostProcessing;
    private final double[] offset;
    private String becomes;

    public static void open(FixtureBlockEntity marker) {
        Minecraft.getInstance().gui.setScreen(new FixtureScreen(marker));
    }

    private FixtureScreen(FixtureBlockEntity marker) {
        super(Component.translatable("screen.ametrin_structures.fixture"));
        this.pos = marker.getBlockPos();
        marker.fixtureData().forEach(data -> alternatives.add(new Alternative(data)));
        this.customName = marker.customName() == null ? "" : marker.customName().getString();
        this.useGravity = marker.useGravity();
        this.markPostProcessing = marker.markPostProcessing();
        this.offset = new double[]{marker.offset().x, marker.offset().y, marker.offset().z};
        this.becomes = marker.declaredBecomes().map(BlockStateParser::serialize).orElse("");
        RegistryKeyCache.refresh();
        this.availableTypes = ASRegistries.FIXTURE_TYPES.keySet().stream()
                .sorted(Comparator.comparing(Identifier::toString))
                .toList();
        if (alternatives.isEmpty() && !availableTypes.isEmpty()) {
            var empty = ASFixtures.EMPTY.getId();
            alternatives.add(new Alternative(availableTypes.contains(empty) ? empty : availableTypes.getFirst()));
        }
    }

    @Override
    protected void init() {
        tabs.clear();
        tabs.add(new ListTab(Component.translatable("screen.ametrin_structures.page.fixtures"), this::populateFixtures));
        tabs.add(new ListTab(Component.translatable("screen.ametrin_structures.page.settings"), this::populateSettings));
        tabNavigationBar = MenuTabBar.builder(tabManager, width).addTabs(tabs.toArray(Tab[]::new)).build();
        addRenderableWidget(tabNavigationBar);

        var footer = layout.addToFooter(LinearLayout.horizontal().spacing(8));
        footer.addChild(Button.builder(Component.translatable("screen.ametrin_structures.generate"), _ -> generate())
                .width(FOOTER_BUTTON_WIDTH)
                .tooltip(Tooltip.create(Component.translatable("screen.ametrin_structures.generate.tooltip")))
                .build());
        footer.addChild(Button.builder(CommonComponents.GUI_DONE, _ -> onClose()).width(FOOTER_BUTTON_WIDTH).build());
        footer.addChild(Button.builder(CommonComponents.GUI_CANCEL, _ -> close()).width(FOOTER_BUTTON_WIDTH).build());
        layout.visitWidgets(widget -> {
            widget.setTabOrderGroup(1);
            addRenderableWidget(widget);
        });
        tabNavigationBar.selectTab(tabIndex, false);
        repositionElements();
    }

    @Override
    public void repositionElements() {
        if (tabNavigationBar == null) {
            return;
        }
        tabNavigationBar.arrangeElements(width);
        int tabAreaTop = tabNavigationBar.getRectangle().bottom();
        tabManager.setTabArea(new ScreenRectangle(0, tabAreaTop, width, height - layout.getFooterHeight() - tabAreaTop));
        layout.setHeaderHeight(tabAreaTop);
        layout.arrangeElements();
    }

    @Override
    protected void setInitialFocus() {}

    @Override
    public void tick() {
        super.tick();
        // Deferred to the tick, so the list never rebuilds while one of its own widgets handles input.
        if (rebuildFixtures) {
            rebuildFixtures = false;
            tabs.getFirst().populate();
            if (refocusType && typeBox != null && typeRow != null) {
                // Typing a type rebuilds the rows; keep typing in the new type box.
                var list = tabs.getFirst().list;
                changeFocus(ComponentPath.path(typeBox, typeRow, list, this));
                typeBox.moveCursorToEnd(false);
            }
            refocusType = false;
        }
    }

    private void onTabSelected(Tab tab) {
        if (tab instanceof ListTab listTab && tabs.contains(listTab)) {
            tabIndex = tabs.indexOf(listTab);
        }
    }

    private void requestFixturesRebuild() {
        rebuildFixtures = true;
    }

    private void populateFixtures(RowList list, CompletionPopup completions) {
        var alternative = current();
        list.addRow(alternativesRow());
        typeRow = new Row(Component.translatable("screen.ametrin_structures.type"), List.of(), typeBox(alternative, completions));
        list.addRow(typeRow);
        var weightLabel = Component.translatable("screen.ametrin_structures.weight");
        var weightBox = editBox(controlWidth(), weightLabel, alternative.weight);
        weightBox.setHint(Component.literal("1").withStyle(EditBox.SEARCH_HINT_STYLE));
        onEdit(weightBox, value -> parseWeight(value).isPresent(), value -> alternative.weight = value);
        list.addRow(new Row(weightLabel, List.of(), weightBox));
        var chanceLabel = Component.translatable("screen.ametrin_structures.generation_chance");
        var chanceBox = editBox(controlWidth(), chanceLabel, alternative.chance);
        chanceBox.setHint(Component.literal("1.0").withStyle(EditBox.SEARCH_HINT_STYLE));
        onEdit(chanceBox, value -> parseChance(value).isPresent(), value -> alternative.chance = value);
        list.addRow(new Row(chanceLabel, tooltip(Component.translatable("screen.ametrin_structures.generation_chance.tooltip")), chanceBox));
        var conditionsLabel = Component.translatable("screen.ametrin_structures.conditions");
        var conditionsBox = editBox(controlWidth(), conditionsLabel, alternative.conditions);
        conditionsBox.setMaxLength(Integer.MAX_VALUE);
        conditionsBox.setHint(Component.literal("[{type: \"ametrin_structures:biome\", biomes: \"#minecraft:is_forest\"}]").withStyle(EditBox.SEARCH_HINT_STYLE));
        onEdit(conditionsBox, value -> value.isBlank() || parseConditions(value).isPresent(), value -> alternative.conditions = value);
        list.addRow(new Row(conditionsLabel, tooltip(Component.translatable("screen.ametrin_structures.conditions.tooltip")), conditionsBox));

        var fields = fieldsOf(alternative.type);
        if (!fields.isEmpty()) {
            list.addRow(new HeaderRow(Component.translatable("screen.ametrin_structures.parameters")));
        }
        for (var field : fields) {
            list.addRow(fieldRow(field, alternative, completions));
        }
    }

    private Row alternativesRow() {
        boolean several = alternatives.size() > 1;
        var previous = smallButton("◀", "screen.ametrin_structures.previous", () -> step(-1));
        var next = smallButton("▶", "screen.ametrin_structures.next", () -> step(1));
        var add = smallButton("+", "screen.ametrin_structures.add", this::addAlternative);
        var remove = smallButton("-", "screen.ametrin_structures.remove", this::removeAlternative);
        previous.active = several;
        next.active = several;
        remove.active = several;
        return new Row(
                Component.translatable("screen.ametrin_structures.alternatives", selected + 1, alternatives.size()),
                List.of(),
                previous, next, add, remove);
    }

    private EditBox typeBox(Alternative alternative, CompletionPopup completions) {
        var box = editBox(controlWidth(), Component.translatable("screen.ametrin_structures.type"), alternative.type.toString());
        completions.attach(box, () -> availableTypes.stream().map(Identifier::toString).toList());
        onEdit(box, value -> knownType(value).isPresent(), value -> knownType(value)
                .filter(type -> !type.equals(alternative.type))
                .ifPresent(type -> {
                    alternative.setType(type);
                    refocusType = true;
                    requestFixturesRebuild();
                }));
        typeBox = box;
        return box;
    }

    private Optional<Identifier> knownType(String value) {
        return Optional.ofNullable(Identifier.tryParse(value)).filter(availableTypes::contains);
    }

    private Row fieldRow(FixtureField<?> field, Alternative alternative, CompletionPopup completions) {
        var key = field.key();
        var type = field.type();
        var name = fieldName(key);
        var text = alternative.texts.getOrDefault(key, "");
        var defaultText = field.defaultText(registries());
        var lines = new ArrayList<Component>();
        lines.add(Component.literal(key).withStyle(ChatFormatting.YELLOW));
        lines.add(Component.literal(type.name()).withStyle(ChatFormatting.GRAY));
        if (field.presence() == FixtureField.Presence.REQUIRED) {
            lines.add(Component.translatable("screen.ametrin_structures.required").withStyle(ChatFormatting.GRAY));
        }
        defaultText.ifPresent(value -> lines.add(Component.translatable("editGamerule.default", value).withStyle(ChatFormatting.GRAY)));
        var tooltip = tooltip(lines.toArray(Component[]::new));

        if (type.editor() == FieldType.Editor.TOGGLE) {
            boolean value = Boolean.parseBoolean(text.isBlank() ? defaultText.orElse("false") : text);
            var toggle = CycleButton.onOffBuilder(value)
                    .displayOnlyValue()
                    .create(0, 0, controlWidth(), WIDGET_HEIGHT, name, (_, enabled) -> alternative.texts.put(key, enabled.toString()));
            return new Row(name, tooltip, toggle);
        }
        if (type.editor() == FieldType.Editor.CHOICE) {
            return new Row(name, tooltip, choiceButton(field, alternative, name, text.isBlank() ? defaultText.orElse("") : text));
        }

        var box = editBox(controlWidth(), name, text);
        box.setHint(hint(field, defaultText).withStyle(EditBox.SEARCH_HINT_STYLE));
        Predicate<String> valid = value -> type.textToTag(value, registries()).isSuccess();
        completions.attach(box, type.editor() == FieldType.Editor.BLOCK_STATE
                ? Completer.blockState(blocks(), valid)
                : Completer.filtering(() -> candidates(type)));
        onEdit(box, valid, value -> alternative.texts.put(key, value));
        return new Row(name, tooltip, box);
    }

    /// What a blank field means: its default, nothing for an optional field, or an example of what a
    /// required one takes, marked so it isn't mistaken for a default.
    private static MutableComponent hint(FixtureField<?> field, Optional<String> defaultText) {
        if (defaultText.isPresent()) {
            return Component.literal(defaultText.get());
        }
        if (field.presence() == FixtureField.Presence.OPTIONAL) {
            return Component.translatable("screen.ametrin_structures.none");
        }
        return field.type().example()
                .map(example -> Component.translatable("screen.ametrin_structures.example", example))
                .orElseGet(() -> Component.literal(field.type().name()));
    }

    /// Cycles through the choices, with a blank one standing for none when the field has no default.
    private CycleButton<String> choiceButton(FixtureField<?> field, Alternative alternative, Component name, String initial) {
        var values = new ArrayList<String>();
        if (field.presence() != FixtureField.Presence.DEFAULT) {
            values.add("");
        }
        values.addAll(field.type().suggestions().apply(registries()));
        if (!values.contains(initial)) {
            values.add(initial);
        }
        return CycleButton.<String>builder(
                        value -> value.isEmpty() ? Component.translatable("screen.ametrin_structures.none") : Component.literal(value),
                        initial)
                .withValues(values)
                .displayOnlyValue()
                .create(0, 0, controlWidth(), WIDGET_HEIGHT, name, (_, value) -> alternative.texts.put(field.key(), value));
    }

    private void populateSettings(RowList list, CompletionPopup completions) {
        var nameLabel = Component.translatable("screen.ametrin_structures.custom_name");
        var nameBox = editBox(controlWidth(), nameLabel, customName);
        nameBox.setResponder(value -> customName = value);
        list.addRow(new Row(nameLabel, List.of(), nameBox));

        var offsetLabel = Component.translatable("screen.ametrin_structures.offset");
        var offsetBoxes = new EditBox[3];
        int offsetWidth = (controlWidth() - 2 * GAP) / 3;
        String[] axes = {"X", "Y", "Z"};
        for (int axis = 0; axis < 3; axis++) {
            int index = axis;
            var box = editBox(offsetWidth, Component.literal(axes[axis]), formatOffset(offset[axis]));
            box.setHint(Component.literal(axes[axis]).withStyle(EditBox.SEARCH_HINT_STYLE));
            onEdit(box, value -> parseOffset(value).isPresent(), value -> parseOffset(value).ifPresent(parsed -> offset[index] = parsed));
            offsetBoxes[axis] = box;
        }
        var offsetTooltip = Component.translatable("screen.ametrin_structures.offset.tooltip", FixtureBlockEntity.MAX_OFFSET);
        list.addRow(new Row(offsetLabel, tooltip(offsetTooltip), offsetBoxes));

        var becomesLabel = Component.translatable("screen.ametrin_structures.becomes");
        var becomesBox = editBox(controlWidth(), becomesLabel, becomes);
        becomesBox.setHint(Component.translatable("screen.ametrin_structures.becomes.hint").withStyle(EditBox.SEARCH_HINT_STYLE));
        completions.attach(becomesBox, Completer.blockState(blocks(), _ -> true));
        onEdit(becomesBox, value -> parseState(value).isPresent(), value -> becomes = value);
        list.addRow(new Row(becomesLabel, List.of(), becomesBox));

        var gravityLabel = Component.translatable("screen.ametrin_structures.use_gravity");
        list.addRow(new Row(
                gravityLabel,
                List.of(),
                CycleButton.onOffBuilder(useGravity)
                        .displayOnlyValue()
                        .create(0, 0, controlWidth(), WIDGET_HEIGHT, gravityLabel, (_, value) -> useGravity = value)));

        var postProcessingLabel = Component.translatable("screen.ametrin_structures.mark_post_processing");
        list.addRow(new Row(
                postProcessingLabel,
                List.of(),
                CycleButton.onOffBuilder(markPostProcessing)
                        .displayOnlyValue()
                        .create(0, 0, controlWidth(), WIDGET_HEIGHT, postProcessingLabel, (_, value) -> markPostProcessing = value)));
    }

    private Alternative current() {
        return alternatives.get(Math.min(selected, alternatives.size() - 1));
    }

    private void step(int delta) {
        selected = Math.floorMod(selected + delta, alternatives.size());
        tabs.getFirst().scrollToTop();
        requestFixturesRebuild();
    }

    // Starts from a copy of the shown alternative, since variants of one fixture are the common case.
    private void addAlternative() {
        alternatives.add(new Alternative(current()));
        selected = alternatives.size() - 1;
        tabs.getFirst().scrollToTop();
        requestFixturesRebuild();
    }

    private void removeAlternative() {
        if (alternatives.size() > 1) {
            alternatives.remove(selected);
            selected = Math.max(0, selected - 1);
            requestFixturesRebuild();
        }
    }

    private static List<FixtureField<?>> fieldsOf(Identifier type) {
        var fixture = ASRegistries.FIXTURE_TYPES.getValue(type);
        return fixture == null ? List.of() : fixture.fields();
    }

    private static HolderLookup.Provider registries() {
        return Objects.requireNonNull(Minecraft.getInstance().level, "the screen only opens in a level").registryAccess();
    }

    private static DynamicOps<Tag> ops() {
        return registries().createSerializationContext(NbtOps.INSTANCE);
    }

    private static Component fieldName(String key) {
        return Component.translatableWithFallback("fixture_parameter." + key, humanize(key));
    }

    /// `generation_chance` → `Generation chance`
    private static String humanize(String key) {
        var words = key.replace('_', ' ');
        return words.isEmpty() ? words : words.substring(0, 1).toUpperCase(Locale.ROOT) + words.substring(1);
    }

    private List<FormattedCharSequence> tooltip(Component... lines) {
        var result = new ArrayList<FormattedCharSequence>();
        for (var line : lines) {
            result.addAll(font.split(line, 200));
        }
        return result;
    }

    private Button smallButton(String symbol, String tooltipKey, Runnable action) {
        return Button.builder(Component.literal(symbol), _ -> action.run())
                .size(SMALL_BUTTON, WIDGET_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable(tooltipKey)))
                .build();
    }

    // Rows grow with the screen up to a limit; the controls take what the label leaves.
    private int rowWidth() {
        return Math.min(MAX_ROW_WIDTH, width - 24);
    }

    private int controlWidth() {
        int row = rowWidth();
        return row - Math.min(MAX_LABEL_WIDTH, row / 2) - GAP;
    }

    private EditBox editBox(int width, Component label, String value) {
        var box = new EditBox(font, width, WIDGET_HEIGHT, label) {
            // The hint and the completion's ghost text aren't clipped to the box on their own.
            @Override
            public void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
                graphics.enableScissor(getX(), getY(), getRight(), getBottom());
                super.extractWidgetRenderState(graphics, mouseX, mouseY, partialTick);
                graphics.disableScissor();
            }
        };
        box.setMaxLength(512);
        box.setValue(value);
        return box;
    }

    /// Stores every edit and turns the text red while it is filled in but not valid.
    private static void onEdit(EditBox box, Predicate<String> valid, Consumer<String> store) {
        box.setResponder(value -> {
            store.accept(value);
            box.setTextColor(value.isBlank() || valid.test(value) ? EditBox.DEFAULT_TEXT_COLOR : INVALID_COLOR);
        });
        box.setTextColor(box.getValue().isBlank() || valid.test(box.getValue()) ? EditBox.DEFAULT_TEXT_COLOR : INVALID_COLOR);
    }

    private static HolderLookup<Block> blocks() {
        return registries().lookupOrThrow(Registries.BLOCK);
    }

    /// A field type's own suggestions, plus the keys the server reports for the registry it points into.
    private static List<String> candidates(FieldType<?> type) {
        Set<String> candidates = new LinkedHashSet<>(type.suggestions().apply(registries()));
        type.registry()
                .ifPresent(registry -> RegistryKeyCache.get(registry.identifier()).forEach(id -> candidates.add(id.toString())));
        return List.copyOf(candidates);
    }

    private @Nullable ListTab currentTab() {
        return tabManager.getCurrentTab() instanceof ListTab tab ? tab : null;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        var tab = currentTab();
        if (tab != null && tab.completions.keyPressed(event)) {
            return true;
        }
        return tabNavigationBar != null && tabNavigationBar.keyPressed(event) || super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        var tab = currentTab();
        return tab != null && tab.completions.mouseClicked(event) || super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        var tab = currentTab();
        return tab != null && tab.completions.mouseScrolled(x, y, scrollY) || super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        var tab = currentTab();
        if (tab != null) {
            tab.completions.extractRenderState(graphics, mouseX, mouseY);
        }
    }

    @Override
    protected void extractMenuBackground(GuiGraphicsExtractor graphics) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, CreateWorldScreen.TAB_HEADER_BACKGROUND, 0, 0, 0.0F, 0.0F, width, layout.getHeaderHeight(), 16, 16);
        extractMenuBackground(graphics, 0, layout.getHeaderHeight(), width, height);
    }

    /// Saves, like pressing Done: losing edits to Escape is worse than an unwanted save.
    @Override
    public void onClose() {
        sendUpdate();
        close();
    }

    private void close() {
        super.onClose();
    }

    /// Saves the edits, then runs the marker in place. The server hands the marker back as an item
    /// first, since running it consumes the block.
    private void generate() {
        sendUpdate();
        ClientPacketDistributor.sendToServer(new ASPayloads.GenerateFixture(pos));
        close();
    }

    private void sendUpdate() {
        ClientPacketDistributor.sendToServer(new ASPayloads.UpdateFixture(
                pos,
                alternatives.stream().map(Alternative::toTag).toList(),
                customName.isBlank() ? Optional.empty() : Optional.of(Component.literal(customName)),
                useGravity,
                markPostProcessing,
                new Vec3(offset[0], offset[1], offset[2]),
                parseState(becomes)));
    }

    private static Optional<BlockState> parseState(String raw) {
        if (raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(BlockStateParser.parseForBlock(blocks(), raw, false).blockState());
        } catch (CommandSyntaxException exception) {
            return Optional.empty();
        }
    }

    private static Optional<Double> parseOffset(String raw) {
        try {
            var value = Double.parseDouble(raw.trim());
            return FixtureBlockEntity.isValidOffset(value) ? Optional.of(value) : Optional.empty();
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    // Whole numbers without the trailing `.0`.
    private static String formatOffset(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    private static Optional<Integer> parseWeight(String raw) {
        try {
            var weight = Integer.parseInt(raw.trim());
            return weight >= 1 ? Optional.of(weight) : Optional.empty();
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    private static Optional<Float> parseChance(String raw) {
        try {
            var chance = Float.parseFloat(raw.trim());
            return chance >= 0.0F && chance <= 1.0F ? Optional.of(chance) : Optional.empty();
        } catch (NumberFormatException exception) {
            return Optional.empty();
        }
    }

    /// SNBT for a list of conditions, as NBT when it parses and reads as conditions.
    private static Optional<Tag> parseConditions(String raw) {
        try {
            var tag = TagParser.create(NbtOps.INSTANCE).parseFully(raw.trim());
            return FixtureCondition.LIST_CODEC.parse(ops(), tag).isSuccess() ? Optional.of(tag) : Optional.empty();
        } catch (CommandSyntaxException exception) {
            return Optional.empty();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /// One fixture alternative while it is edited: its type, weight, chance and the text of each field.
    /// Text that doesn't parse is kept as it is, so the alternative stays unreadable, but not lost,
    /// until it's fixed.
    private static final class Alternative {
        private static final String TYPE_KEY = Fixture.TYPE_KEY;
        private static final String WEIGHT_KEY = WeightedFixture.WEIGHT_KEY;
        private static final String CHANCE_KEY = WeightedFixture.GENERATION_CHANCE_KEY;

        private Identifier type;
        private String weight = "";
        private String chance = "";
        private String conditions = "";
        private final Map<String, String> texts = new LinkedHashMap<>();
        // The alternative as it was stored. What the screen doesn't edit is sent back as it came, such as
        // every field of a type this game doesn't know, or a type that isn't an id, until the type changes.
        private CompoundTag stored;

        private Alternative(Identifier type) {
            this.type = type;
            this.stored = typeOnly(type);
        }

        // Copies the text as typed, so a copy of an unreadable alternative stays unreadable too.
        private Alternative(Alternative source) {
            this.type = source.type;
            this.weight = source.weight;
            this.chance = source.chance;
            this.conditions = source.conditions;
            this.texts.putAll(source.texts);
            this.stored = source.stored.copy();
        }

        private Alternative(Tag stored) {
            var data = stored instanceof CompoundTag compound ? compound : new CompoundTag();
            this.stored = data;
            var id = data.getString(TYPE_KEY).map(Identifier::tryParse);
            this.type = id.orElseGet(() -> Identifier.withDefaultNamespace("unknown"));
            this.weight = text(data.get(WEIGHT_KEY)).orElse("");
            this.chance = text(data.get(CHANCE_KEY)).orElse("");
            this.conditions = Optional.ofNullable(data.get(WeightedFixture.CONDITIONS_KEY)).map(Tag::toString).orElse("");
            for (var field : fieldsOf(type)) {
                var value = data.get(field.key());
                if (value != null) {
                    texts.put(field.key(), field.type().tagToText(value, registries()).result().or(() -> text(value)).orElse(""));
                }
            }
        }

        private void setType(Identifier type) {
            this.type = type;
            this.texts.clear();
            this.stored = typeOnly(type);
        }

        private static CompoundTag typeOnly(Identifier type) {
            var data = new CompoundTag();
            data.putString(TYPE_KEY, type.toString());
            return data;
        }

        // Weight, chance and the fields go into one alternative as they're stored.
        private Tag toTag() {
            var data = stored.copy();
            put(data, WEIGHT_KEY, weight, value -> parseWeight(value).map(IntTag::valueOf));
            put(data, CHANCE_KEY, chance, value -> parseChance(value).map(FloatTag::valueOf));
            put(data, WeightedFixture.CONDITIONS_KEY, conditions, FixtureScreen::parseConditions);
            for (var field : fieldsOf(type)) {
                put(data, field.key(), texts.getOrDefault(field.key(), ""), value -> field.type().textToTag(value, registries()).result());
            }
            return data;
        }

        // A blank value stays out, so it takes its default. Text that doesn't parse stays text, which
        // keeps the alternative unreadable rather than losing what was typed.
        private static void put(CompoundTag data, String key, String text, Function<String, Optional<? extends Tag>> parse) {
            if (text.isBlank()) {
                data.remove(key);
            } else {
                var parsed = parse.apply(text);
                data.put(key, parsed.isPresent() ? parsed.get() : StringTag.valueOf(text));
            }
        }

        private static Optional<String> text(@Nullable Tag tag) {
            return switch (tag) {
                case null -> Optional.empty();
                case StringTag(String value) -> Optional.of(value);
                case NumericTag number -> Optional.of(number.toString().replaceAll("[bsLfd]$", ""));
                default -> Optional.of(tag.toString());
            };
        }
    }

    @FunctionalInterface
    private interface Populator {
        void populate(RowList list, CompletionPopup completions);
    }

    /// A tab showing one scrolling list of rows.
    private final class ListTab implements Tab {
        private final Component title;
        private final Populator populator;
        private final RowList list = new RowList();
        private final FrameLayout layout = new FrameLayout();
        private final CompletionPopup completions = new CompletionPopup(font);

        private ListTab(Component title, Populator populator) {
            this.title = title;
            this.populator = populator;
            layout.addChild(list);
            populate();
        }

        private void populate() {
            completions.clear();
            list.setFocused(null);
            list.clearEntries();
            populator.populate(list, completions);
            list.refreshScrollAmount();
        }

        private void scrollToTop() {
            list.setScrollAmount(0);
        }

        @Override
        public Component getTabTitle() {
            return title;
        }

        @Override
        public Component getTabExtraNarration() {
            return Component.empty();
        }

        @Override
        public void visitChildren(Consumer<AbstractWidget> consumer) {
            consumer.accept(list);
        }

        @Override
        public void doLayout(ScreenRectangle area) {
            list.updateSizeAndPosition(area.width(), area.height(), area.left(), area.top());
        }

        @Override
        public Layout getLayout() {
            return layout;
        }
    }

    private final class RowList extends ContainerObjectSelectionList<RowList.Entry> {
        private RowList() {
            super(Minecraft.getInstance(), 0, 0, 0, ROW_HEIGHT);
        }

        private void addRow(Entry row) {
            addEntry(row);
        }

        @Override
        public int getRowWidth() {
            return rowWidth();
        }

        private abstract static class Entry extends ContainerObjectSelectionList.Entry<Entry> {}
    }

    /// A label on the left, its controls right-aligned. The label's tooltip explains the row.
    private final class Row extends RowList.Entry {
        private final Component label;
        private final List<FormattedCharSequence> tooltip;
        private final List<AbstractWidget> controls;

        private Row(Component label, List<FormattedCharSequence> tooltip, AbstractWidget... controls) {
            this.label = label;
            this.tooltip = tooltip;
            this.controls = List.of(controls);
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
            int x = getContentRight();
            for (var control : controls.reversed()) {
                x -= control.getWidth();
                control.setPosition(x, getContentY());
                control.extractRenderState(graphics, mouseX, mouseY, partialTick);
                x -= GAP;
            }
            int labelWidth = Math.max(20, x - getContentX());
            var lines = font.split(label, labelWidth);
            int y = getContentY() + (WIDGET_HEIGHT - Math.min(lines.size(), 2) * 10) / 2 + 1;
            for (int line = 0; line < Math.min(lines.size(), 2); line++) {
                graphics.text(font, lines.get(line), getContentX(), y + line * 10, -1);
            }
            boolean overLabel = mouseX >= getContentX() && mouseX < getContentX() + labelWidth
                    && mouseY >= getContentY() && mouseY < getContentY() + WIDGET_HEIGHT;
            if (hovered && overLabel) {
                var shown = new ArrayList<FormattedCharSequence>();
                if (lines.size() > 2) {
                    shown.add(label.getVisualOrderText());
                }
                shown.addAll(tooltip);
                if (!shown.isEmpty()) {
                    graphics.setTooltipForNextFrame(shown, mouseX, mouseY);
                }
            }
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return controls;
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return controls;
        }
    }

    private final class HeaderRow extends RowList.Entry {
        private final Component label;

        private HeaderRow(Component label) {
            this.label = label.copy().withStyle(ChatFormatting.BOLD, ChatFormatting.YELLOW);
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
            graphics.centeredText(font, label, getContentXMiddle(), getContentY() + 6, -1);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }
}
