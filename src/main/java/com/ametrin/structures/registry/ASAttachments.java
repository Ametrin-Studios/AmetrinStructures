package com.ametrin.structures.registry;

import com.ametrin.structures.AmetrinStructures;
import com.ametrin.structures.spawner.SpawnerProfileAttachment;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public final class ASAttachments {
    public static final DeferredRegister<AttachmentType<?>> REGISTER = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, AmetrinStructures.MOD_ID);

    // Not synced since clients can't resolve profiles, the server sends them the applied result.
    public static final Supplier<AttachmentType<SpawnerProfileAttachment>> SPAWNER_PROFILE = REGISTER.register("spawner_profile",
            () -> AttachmentType.builder(() -> SpawnerProfileAttachment.DEFAULT)
                    .serialize(SpawnerProfileAttachment.CODEC)
                    .build());
}
