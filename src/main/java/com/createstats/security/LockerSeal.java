package com.createstats.security;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

public record LockerSeal(UUID id, String owner) {
    public static final Codec<LockerSeal> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    UUIDUtil.CODEC.fieldOf("id").forGetter(LockerSeal::id),
                    Codec.STRING.optionalFieldOf("owner", "").forGetter(LockerSeal::owner)
            ).apply(instance, LockerSeal::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, LockerSeal> STREAM_CODEC =
            StreamCodec.composite(
                    UUIDUtil.STREAM_CODEC, LockerSeal::id,
                    ByteBufCodecs.stringUtf8(32), LockerSeal::owner,
                    LockerSeal::new);

    public boolean hasOwner() {
        return !this.owner.isBlank();
    }
}
