package com.ametrin.structures.client;

import com.ametrin.structures.client.Completer.Completion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import org.jspecify.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/// Completion for plain edit boxes. Shows matching candidates under the focused box, and the best prefix match as ghost text inside it.
///
/// Works like vanilla's command suggestions, but for any list of candidates. The screen has to forward key, click, scroll and render calls to it. Up and down select, Tab accepts and Escape closes.
final class CompletionPopup {
    private static final int LINE_HEIGHT = 12;
    private static final int VISIBLE_LINES = 8;
    private static final int BACKGROUND = 0xFF000000;
    private static final int BORDER = 0xFFA0A0A0;
    private static final int HIGHLIGHT = 0xFF303030;
    private static final int SELECTED_COLOR = 0xFFFFFF00;
    private static final int UNSELECTED_COLOR = 0xFFAAAAAA;

    private final Font font;
    private final Map<EditBox, Completer> sources = new IdentityHashMap<>();

    private @Nullable EditBox shownFor;
    private String shownValue = "";
    private int shownKeysVersion;
    private @Nullable String dismissedValue;
    private List<Completion> matches = List.of();
    private int selected;
    private int scroll;

    public CompletionPopup(Font font) {
        this.font = font;
    }

    /// Offers `candidates`, filtered by what was typed, while `box` has focus.
    public void attach(EditBox box, Supplier<List<String>> candidates) {
        attach(box, Completer.filtering(candidates));
    }

    /// Offers whatever `completer` proposes for the box's text while `box` has focus.
    public void attach(EditBox box, Completer completer) {
        sources.put(box, completer);
    }

    /// Forgets every box, for when the screen rebuilds its widgets.
    public void clear() {
        sources.clear();
        shownFor = null;
        matches = List.of();
    }

    public boolean keyPressed(KeyEvent event) {
        EditBox box = refresh();
        if (box == null || matches.isEmpty()) {
            return false;
        }
        if (event.isUp() || event.isDown()) {
            select(selected + (event.isUp() ? -1 : 1));
            return true;
        }
        if (event.isCycleFocus()) {
            accept(box, matches.get(selected));
            return true;
        }
        if (event.isEscape()) {
            dismissedValue = box.getValue();
            box.setSuggestion(null);
            matches = List.of();
            return true;
        }
        return false;
    }

    public boolean mouseClicked(MouseButtonEvent event) {
        EditBox box = refresh();
        int line = lineAt(box, event.x(), event.y());
        if (box == null || line < 0) {
            return false;
        }
        accept(box, matches.get(scroll + line));
        return true;
    }

    public boolean mouseScrolled(double x, double y, double scrollY) {
        EditBox box = refresh();
        if (box == null || lineAt(box, x, y) < 0 || scrollY == 0) {
            return false;
        }
        scroll = Math.clamp(scroll - (int) Math.signum(scrollY), 0, Math.max(0, matches.size() - VISIBLE_LINES));
        return true;
    }

    /// Draws the list over everything else on the screen. Call after the screen's own widgets.
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        EditBox box = refresh();
        if (box == null || matches.isEmpty()) {
            return;
        }
        int hovered = lineAt(box, mouseX, mouseY);
        int x = box.getX();
        int y = listTop(box);
        int width = listWidth(box);
        graphics.nextStratum();
        // Opaque, so the widgets underneath don't show through.
        graphics.fill(x - 1, y - 1, x + width + 1, y + visibleLines() * LINE_HEIGHT + 1, BORDER);
        graphics.fill(x, y, x + width, y + visibleLines() * LINE_HEIGHT, BACKGROUND);
        for (int line = 0; line < visibleLines(); line++) {
            int index = scroll + line;
            int lineY = y + line * LINE_HEIGHT;
            boolean highlighted = index == selected || line == hovered;
            if (highlighted) {
                graphics.fill(x, lineY, x + width, lineY + LINE_HEIGHT, HIGHLIGHT);
            }
            graphics.text(font, matches.get(index).label(), x + 2, lineY + 2, highlighted ? SELECTED_COLOR : UNSELECTED_COLOR);
        }
    }

    // Recomputes the matches when focus, the focused box's text or the known registry keys have changed.
    private @Nullable EditBox refresh() {
        EditBox focused = null;
        for (var box : sources.keySet()) {
            if (box.isFocused() && box.visible) {
                focused = box;
                break;
            }
        }
        if (focused == null) {
            shownFor = null;
            matches = List.of();
            return null;
        }
        var value = focused.getValue();
        var keysVersion = RegistryKeyCache.version(); // Keys requested for the first match arrive a moment later.
        if (focused != shownFor || !value.equals(shownValue) || keysVersion != shownKeysVersion) {
            if (focused != shownFor) {
                dismissedValue = null;
            }
            shownFor = focused;
            shownValue = value;
            shownKeysVersion = keysVersion;
            matches = Objects.equals(value, dismissedValue) ? List.of() : sources.get(focused).complete(value);
            selected = 0;
            scroll = 0;
            focused.setSuggestion(ghostText(value));
        }
        return focused;
    }

    private @Nullable String ghostText(String value) {
        if (matches.isEmpty() || value.isEmpty()) {
            return null;
        }
        var best = matches.get(selected).result();
        return best.regionMatches(true, 0, value, 0, value.length()) ? best.substring(value.length()) : null;
    }

    private void select(int index) {
        selected = Math.floorMod(index, matches.size());
        if (selected < scroll) {
            scroll = selected;
        } else if (selected >= scroll + VISIBLE_LINES) {
            scroll = selected - VISIBLE_LINES + 1;
        }
        if (shownFor != null) {
            shownFor.setSuggestion(ghostText(shownFor.getValue()));
        }
    }

    private void accept(EditBox box, Completion completion) {
        box.setValue(completion.result());
        box.moveCursorToEnd(false);
    }

    private int visibleLines() {
        return Math.min(VISIBLE_LINES, matches.size() - scroll);
    }

    private int listTop(EditBox box) {
        var height = visibleLines() * LINE_HEIGHT;
        var below = box.getBottom() + 1;
        var screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
        return below + height <= screenHeight ? below : box.getY() - 1 - height;
    }

    private int listWidth(EditBox box) {
        int widest = 0;
        for (int line = 0; line < visibleLines(); line++) {
            widest = Math.max(widest, font.width(matches.get(scroll + line).label()) + 4);
        }
        return Math.max(box.getWidth(), widest);
    }

    private int lineAt(@Nullable EditBox box, double x, double y) {
        if (box == null || matches.isEmpty()) {
            return -1;
        }
        int top = listTop(box);
        if (x < box.getX() || x >= box.getX() + listWidth(box) || y < top) {
            return -1;
        }
        int line = (int) ((y - top) / LINE_HEIGHT);
        return line < visibleLines() ? line : -1;
    }
}
