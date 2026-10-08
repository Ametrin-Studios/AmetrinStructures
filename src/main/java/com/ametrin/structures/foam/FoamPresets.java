package com.ametrin.structures.foam;

import com.ametrin.structures.registry.ASDataComponents;
import com.ametrin.structures.registry.ASItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

import java.util.List;

public final class FoamPresets {
    public static final int DEFAULT_MAX_DISTANCE = 50;

    /// Spreads to faces and edges, up to [#DEFAULT_MAX_DISTANCE] blocks.
    public static final FoamSpread BASIC = new FoamSpread(FoamSpreadBehaviors.FacesAndEdges.INSTANCE, List.of(new FoamSpreadRestrictions.MaxDistance(DEFAULT_MAX_DISTANCE)));

    /// [#BASIC], kept under a roof.
    public static final FoamSpread INDOOR = new FoamSpread(FoamSpreadBehaviors.FacesAndEdges.INSTANCE, List.of(new FoamSpreadRestrictions.MaxDistance(DEFAULT_MAX_DISTANCE), FoamSpreadRestrictions.RequiresShelter.INSTANCE));

    /// Spreads to faces only, so it does not leak through diagonal gaps.
    public static final FoamSpread ORTHOGONAL = new FoamSpread(FoamSpreadBehaviors.Faces.INSTANCE, List.of(new FoamSpreadRestrictions.MaxDistance(DEFAULT_MAX_DISTANCE)));

    /// [#ORTHOGONAL], kept under a roof.
    public static final FoamSpread INDOOR_ORTHOGONAL = new FoamSpread(FoamSpreadBehaviors.Faces.INSTANCE, List.of(new FoamSpreadRestrictions.MaxDistance(DEFAULT_MAX_DISTANCE), FoamSpreadRestrictions.RequiresShelter.INSTANCE));

    /// Spreads to faces, as far as the stack holds items.
    public static final FoamSpread SIZED = new FoamSpread(FoamSpreadBehaviors.Faces.INSTANCE, List.of(FoamSpreadRestrictions.StackCountDistance.INSTANCE));

    /// Fills the plane facing the builder.
    public static final FoamSpread PLANAR = new FoamSpread(FoamSpreadBehaviors.Planar.INSTANCE, List.of(new FoamSpreadRestrictions.MaxDistance(DEFAULT_MAX_DISTANCE)));

    /// A preset in the operator items tab, named `item.ametrin_structures.foam.<name>`.
    public record Preset(String name, FoamSpread spread) {
        /// A stack of the library's foam.
        public ItemStack createStack() {
            return stack(spread, Component.translatable("item.ametrin_structures.foam." + name));
        }
    }

    public static final List<Preset> ALL = List.of(
            new Preset("basic", BASIC),
            new Preset("indoor", INDOOR),
            new Preset("orthogonal", ORTHOGONAL),
            new Preset("indoor_orthogonal", INDOOR_ORTHOGONAL),
            new Preset("sized", SIZED),
            new Preset("planar", PLANAR));

    private FoamPresets() {}

    public static ItemStack stack(FoamSpread spread, Component name) {
        return stack(ASItems.FOAM.get(), spread, name);
    }

    public static ItemStack stack(ItemLike foam, FoamSpread spread, Component name) {
        var stack = new ItemStack(foam);
        stack.set(ASDataComponents.FOAM_SPREAD, spread);
        stack.set(DataComponents.ITEM_NAME, name);
        return stack;
    }
}
