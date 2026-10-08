package com.ametrin.structures.registry;

import com.ametrin.structures.AmetrinStructures;
import com.ametrin.structures.foam.FoamSpreadBehaviorType;
import com.ametrin.structures.foam.FoamSpreadBehaviors;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ASFoamSpreadBehaviors {
    public static final DeferredRegister<FoamSpreadBehaviorType> REGISTER = DeferredRegister.create(ASRegistries.FOAM_SPREAD_BEHAVIOR_TYPE, AmetrinStructures.MOD_ID);

    public static final DeferredHolder<FoamSpreadBehaviorType, FoamSpreadBehaviorType> FACES = REGISTER.register("faces", () -> new FoamSpreadBehaviorType(FoamSpreadBehaviors.Faces.CODEC));
    public static final DeferredHolder<FoamSpreadBehaviorType, FoamSpreadBehaviorType> EDGES = REGISTER.register("edges", () -> new FoamSpreadBehaviorType(FoamSpreadBehaviors.Edges.CODEC));
    public static final DeferredHolder<FoamSpreadBehaviorType, FoamSpreadBehaviorType> FACES_AND_EDGES = REGISTER.register("faces_and_edges", () -> new FoamSpreadBehaviorType(FoamSpreadBehaviors.FacesAndEdges.CODEC));
    public static final DeferredHolder<FoamSpreadBehaviorType, FoamSpreadBehaviorType> PLANAR = REGISTER.register("planar", () -> new FoamSpreadBehaviorType(FoamSpreadBehaviors.Planar.CODEC));
}
