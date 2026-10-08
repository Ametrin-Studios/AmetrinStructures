package com.ametrin.structures.registry;

import com.ametrin.structures.AmetrinStructures;
import com.ametrin.structures.foam.FoamSpreadRestrictionType;
import com.ametrin.structures.foam.FoamSpreadRestrictions;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ASFoamSpreadRestrictions {
    public static final DeferredRegister<FoamSpreadRestrictionType> REGISTER = DeferredRegister.create(ASRegistries.FOAM_SPREAD_RESTRICTION_TYPE, AmetrinStructures.MOD_ID);

    public static final DeferredHolder<FoamSpreadRestrictionType, FoamSpreadRestrictionType> MAX_DISTANCE =
            REGISTER.register("max_distance", () -> new FoamSpreadRestrictionType(FoamSpreadRestrictions.MaxDistance.CODEC));
    public static final DeferredHolder<FoamSpreadRestrictionType, FoamSpreadRestrictionType> REQUIRES_SHELTER =
            REGISTER.register("requires_shelter", () -> new FoamSpreadRestrictionType(FoamSpreadRestrictions.RequiresShelter.CODEC));
    public static final DeferredHolder<FoamSpreadRestrictionType, FoamSpreadRestrictionType> STACK_COUNT_DISTANCE =
            REGISTER.register("stack_count_distance", () -> new FoamSpreadRestrictionType(FoamSpreadRestrictions.StackCountDistance.CODEC));
}
