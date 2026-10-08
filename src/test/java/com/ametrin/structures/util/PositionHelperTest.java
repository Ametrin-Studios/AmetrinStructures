package com.ametrin.structures.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PositionHelperTest {
    @Test
    void transformMatchesVanillaTemplatePlacement() {
        var offset = new BlockPos(1, 2, 3);
        for (Mirror mirror : Mirror.values()) {
            for (Rotation rotation : Rotation.values()) {
                var expected = Vec3.atLowerCornerOf(StructureTemplate.transform(offset, mirror, rotation, BlockPos.ZERO));
                assertEquals(expected, PositionHelper.transform(Vec3.atLowerCornerOf(offset), mirror, rotation), mirror + " " + rotation);
            }
        }
    }
}
