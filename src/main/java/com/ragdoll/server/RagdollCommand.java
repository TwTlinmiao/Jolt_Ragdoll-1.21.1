package com.ragdoll.server;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.ragdoll.client.physics.ServerRagdollManager;
import com.ragdoll.network.RagdollSpawnPacket;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class RagdollCommand {
    private RagdollCommand() {}

    public static void register(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("joltragdoll")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("set")
                        .then(Commands.literal("self")
                                .executes(context -> copy(context.getSource(),
                                        context.getSource().getPlayerOrException(),
                                        context.getSource().getPosition()))
                                .then(Commands.argument("pos", Vec3Argument.vec3())
                                        .executes(context -> copy(context.getSource(),
                                                context.getSource().getPlayerOrException(),
                                                Vec3Argument.getVec3(context, "pos")))
                                        .then(Commands.literal("copy")
                                                .executes(context -> copy(context.getSource(),
                                                        context.getSource().getPlayerOrException(),
                                                        Vec3Argument.getVec3(context, "pos"))))))
                        .then(Commands.argument("player", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                        context.getSource().getOnlinePlayerNames(), builder))
                                .then(Commands.argument("pos", Vec3Argument.vec3())
                                        .then(Commands.literal("copy")
                                                .executes(context -> copyNamed(context.getSource(),
                                                        StringArgumentType.getString(context, "player"),
                                                        Vec3Argument.getVec3(context, "pos"))))
                                        .then(Commands.literal("none")
                                                .executes(context -> spawn(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "player"),
                                                        Vec3Argument.getVec3(context, "pos"),
                                                        RagdollSpawnPacket.SpawnStyle.NONE)))
                                        .then(Commands.literal("cape")
                                                .executes(context -> spawn(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "player"),
                                                        Vec3Argument.getVec3(context, "pos"),
                                                        RagdollSpawnPacket.SpawnStyle.CAPE)))
                                        .then(Commands.literal("elytra")
                                                .executes(context -> spawn(
                                                        context.getSource(),
                                                        StringArgumentType.getString(context, "player"),
                                                        Vec3Argument.getVec3(context, "pos"),
                                                        RagdollSpawnPacket.SpawnStyle.ELYTRA))))))
                .then(Commands.literal("remove")
                        .then(Commands.literal("single")
                                .executes(context -> removeSingle(
                                        context.getSource())))
                        .then(Commands.literal("all")
                                .executes(context -> removeAll(
                                        context.getSource()))));
        event.getDispatcher().register(root);
    }

    private static int copyNamed(CommandSourceStack source, String name, Vec3 position) {
        ServerPlayer player = source.getServer().getPlayerList().getPlayerByName(name);
        if (player == null) {
            source.sendFailure(Component.literal("The player must be online to copy their appearance"));
            return 0;
        }
        return copy(source, player, position);
    }

    private static int copy(CommandSourceStack source, ServerPlayer player, Vec3 position) {
        RagdollSpawnPacket packet = new RagdollSpawnPacket(ServerPlayerDeaths.createSnapshot(
                player, UUID.randomUUID(), Vec3.ZERO, position));
        if (!ServerRagdollManager.spawn(source.getLevel(), packet)) {
            source.sendFailure(Component.literal("Could not spawn the copied Jolt ragdoll"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Copied the appearance of "
                + player.getGameProfile().getName() + " to a Jolt ragdoll"), true);
        return 1;
    }

    private static int spawn(
                             CommandSourceStack source,
                             String requestedName,
                             Vec3 position,
                             RagdollSpawnPacket.SpawnStyle style) {
        GameProfile profile = profile(source, requestedName);
        GameProfile namedProfile = withRequestedName(profile, requestedName);
        SkinTexture skin = skinTexture(namedProfile);
        RagdollSpawnPacket packet = RagdollSpawnPacket.command(
                UUID.randomUUID(),
                namedProfile.getId(),
                requestedName,
                skin.value(),
                skin.signature(),
                position.x,
                position.y,
                position.z,
                source.getRotation().y,
                style);
        if (!ServerRagdollManager.spawn(source.getLevel(), packet)) {
            source.sendFailure(Component.literal(
                    "Could not spawn the Jolt ragdoll because its terrain is not loaded"));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Spawned Jolt ragdoll for "
                + requestedName + " as " + style.name().toLowerCase(Locale.ROOT)), true);
        return 1;
    }

    private static GameProfile withRequestedName(GameProfile profile, String requestedName) {
        GameProfile named = new GameProfile(profile.getId(), requestedName);
        named.getProperties().putAll(profile.getProperties());
        return named;
    }

    private static int removeSingle(
            CommandSourceStack source) {
        int removed = ServerRagdollManager.removeNearest(
                source.getLevel(), source.getPosition());
        if (removed == 0) {
            source.sendFailure(Component.literal(
                    "No Jolt ragdoll exists in this dimension"));
            return 0;
        }
        source.sendSuccess(
                () -> Component.literal(
                        "Removed the nearest Jolt ragdoll"),
                true);
        return removed;
    }

    private static int removeAll(
            CommandSourceStack source) {
        int removed = ServerRagdollManager.removeAll(
                source.getLevel());
        if (removed == 0) {
            source.sendFailure(Component.literal(
                    "No Jolt ragdolls exist in this dimension"));
            return 0;
        }
        source.sendSuccess(
                () -> Component.literal(
                        "Removed " + removed
                                + " Jolt ragdoll(s)"),
                true);
        return removed;
    }

    private static GameProfile profile(CommandSourceStack source, String name) {
        ServerPlayer online = source.getServer().getPlayerList().getPlayerByName(name);
        if (online != null) {
            return online.getGameProfile();
        }
        Optional<GameProfile> cached = source.getServer().getProfileCache().get(name);
        return cached.orElseGet(() -> UUIDUtil.createOfflineProfile(name));
    }

    private static SkinTexture skinTexture(GameProfile profile) {
        Collection<Property> textures = profile.getProperties().get("textures");
        if (textures.isEmpty()) {
            return SkinTexture.EMPTY;
        }
        Property texture = textures.iterator().next();
        return new SkinTexture(nullToEmpty(texture.value()), nullToEmpty(texture.signature()));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record SkinTexture(String value, String signature) {
        private static final SkinTexture EMPTY = new SkinTexture("", "");
    }
}
