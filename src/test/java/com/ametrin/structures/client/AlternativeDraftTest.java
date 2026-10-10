package com.ametrin.structures.client;

import com.ametrin.structures.registry.ASFixtures;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.MinecraftServer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(EphemeralTestServerProvider.class)
class AlternativeDraftTest {
    @Test
    void aStoredAlternativeComesBackAsItWas(MinecraftServer server) {
        var stored = new CompoundTag();
        stored.putString("type", ASFixtures.LOOT_CONTAINER.getId().toString());
        stored.put("weight", IntTag.valueOf(3));
        stored.put("generation_chance", FloatTag.valueOf(0.5F));
        stored.putString("loot_table", "minecraft:chests/simple_dungeon");

        var draft = new AlternativeDraft(stored, server.registryAccess());

        assertEquals("3", draft.weight);
        assertEquals("0.5", draft.chance);
        assertEquals("minecraft:chests/simple_dungeon", draft.texts.get("loot_table"));
        assertEquals(stored, draft.toTag(server.registryAccess()));
    }

    @Test
    void textThatDoesNotParseIsKeptAndBlankTextLeftOut(MinecraftServer server) {
        var draft = new AlternativeDraft(ASFixtures.LOOT_CONTAINER.getId());
        draft.weight = "lots";
        draft.chance = " ";
        draft.texts.put("loot_table", "not an id!");

        var tag = (CompoundTag) draft.toTag(server.registryAccess());

        assertEquals(StringTag.valueOf("lots"), tag.get("weight"));
        assertFalse(tag.contains("generation_chance"));
        assertEquals(StringTag.valueOf("not an id!"), tag.get("loot_table"));
    }

    @Test
    void changingTheTypeDropsTheOldFields(MinecraftServer server) {
        var draft = new AlternativeDraft(ASFixtures.LOOT_CONTAINER.getId());
        draft.texts.put("loot_table", "minecraft:chests/simple_dungeon");

        draft.setType(ASFixtures.EMPTY.getId());

        assertTrue(draft.texts.isEmpty());
        var expected = new CompoundTag();
        expected.putString("type", ASFixtures.EMPTY.getId().toString());
        assertEquals(expected, draft.toTag(server.registryAccess()));
    }

    @Test
    void anUnknownTypeKeepsItsFields(MinecraftServer server) {
        var stored = new CompoundTag();
        stored.putString("type", "othermod:crate");
        stored.putInt("size", 9);

        var draft = new AlternativeDraft(stored, server.registryAccess());

        assertTrue(draft.fields().isEmpty());
        assertEquals(stored, draft.toTag(server.registryAccess()));
    }

    @Test
    void aCopyIsEditedOnItsOwn() {
        var original = new AlternativeDraft(ASFixtures.LOOT_CONTAINER.getId());
        original.texts.put("loot_table", "minecraft:chests/simple_dungeon");

        var copy = new AlternativeDraft(original);
        copy.texts.put("loot_table", "minecraft:chests/buried_treasure");

        assertEquals("minecraft:chests/simple_dungeon", original.texts.get("loot_table"));
    }
}
