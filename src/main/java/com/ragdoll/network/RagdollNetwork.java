package com.ragdoll.network;

import com.ragdoll.RagdollMod;
import com.ragdoll.client.physics.ServerRagdollManager;
import com.ragdoll.server.RagdollLootService;
import com.ragdoll.RagdollConfig;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.util.function.Consumer;
import java.util.UUID;

public final class RagdollNetwork {
    public static Consumer<RagdollSpawnPacket> clientSpawn = packet -> {};
    public static Consumer<RagdollStatePacket> clientState = packet -> {};
    public static Consumer<RagdollRemovePacket> clientRemove = packet -> {};
    public static Consumer<RagdollPhysicsGunSyncPacket>
            clientPhysicsGunSync = packet -> {};
    public static Consumer<LiveRagdollStatePacket> clientLiveRagdollState = packet -> {};
    public static Consumer<SleepRagdollStatePacket> clientSleepRagdollState = packet -> {};

    private RagdollNetwork() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("5").optional();
        registrar.playToClient(RagdollSpawnPacket.TYPE, RagdollSpawnPacket.CODEC,
                (packet, context) -> context.enqueueWork(() -> clientSpawn.accept(packet)));
        registrar.playToClient(RagdollStatePacket.TYPE, RagdollStatePacket.CODEC,
                (packet, context) -> context.enqueueWork(() -> clientState.accept(packet)));
        registrar.playToClient(RagdollRemovePacket.TYPE, RagdollRemovePacket.CODEC,
                (packet, context) -> context.enqueueWork(() -> clientRemove.accept(packet)));
        registrar.playToClient(
                RagdollPhysicsGunSyncPacket.TYPE,
                RagdollPhysicsGunSyncPacket.CODEC,
                (packet, context) -> context.enqueueWork(() ->
                        clientPhysicsGunSync.accept(packet)));
        registrar.playToClient(LiveRagdollStatePacket.TYPE, LiveRagdollStatePacket.CODEC,
                (packet, context) -> context.enqueueWork(() -> clientLiveRagdollState.accept(packet)));
        registrar.playToClient(SleepRagdollStatePacket.TYPE, SleepRagdollStatePacket.CODEC,
                (packet, context) -> context.enqueueWork(() -> clientSleepRagdollState.accept(packet)));
        registrar.playToServer(LiveRagdollRequestPacket.TYPE, LiveRagdollRequestPacket.CODEC,
                (packet, context) -> context.enqueueWork(() -> {
                    ServerPlayer player = (ServerPlayer) context.player();
                    if (RagdollConfig.enabled() && RagdollConfig.liveRagdollEnabled()) {
                        ServerRagdollManager.toggleLiveRagdoll(player, packet);
                    }
                }));
        registrar.playToServer(SleepRagdollRequestPacket.TYPE, SleepRagdollRequestPacket.CODEC,
                (packet, context) -> context.enqueueWork(() ->
                        ServerRagdollManager.beginSleepRagdoll(
                                (ServerPlayer) context.player(), packet)));
        registrar.playToServer(RagdollControlRequestPacket.TYPE, RagdollControlRequestPacket.CODEC,
                (packet, context) -> context.enqueueWork(() ->
                        ServerRagdollManager.handleControl(packet, (ServerPlayer) context.player())));
        registrar.playToServer(
                RagdollPhysicsGunActionPacket.TYPE,
                RagdollPhysicsGunActionPacket.CODEC,
                (packet, context) -> context.enqueueWork(() ->
                        ServerRagdollManager.handlePhysicsGun(
                                packet,
                                (ServerPlayer) context.player())));
        registrar.playToServer(
                RagdollLootOpenPacket.TYPE,
                RagdollLootOpenPacket.CODEC,
                (packet, context) -> context.enqueueWork(() ->
                        RagdollLootService.open(
                                (ServerPlayer) context.player(),
                                packet.ragdollId(), packet.pickup())));
    }

    public static void sendControl(RagdollControlRequestPacket packet) {
        PacketDistributor.sendToServer(packet, new CustomPacketPayload[0]);
    }

    public static void sendPhysicsGun(
            RagdollPhysicsGunActionPacket packet) {
        PacketDistributor.sendToServer(
                packet, new CustomPacketPayload[0]);
    }

    public static void toggleLiveRagdoll() {
        PacketDistributor.sendToServer(new LiveRagdollRequestPacket(), new CustomPacketPayload[0]);
    }

    public static void toggleLiveRagdoll(LiveRagdollRequestPacket request) {
        PacketDistributor.sendToServer(request, new CustomPacketPayload[0]);
    }

    public static void beginSleepRagdoll(SleepRagdollRequestPacket request) {
        PacketDistributor.sendToServer(request, new CustomPacketPayload[0]);
    }

    public static void openLoot(UUID ragdollId) {
        openLoot(ragdollId, false);
    }

    public static void openLoot(UUID ragdollId, boolean pickup) {
        PacketDistributor.sendToServer(
                new RagdollLootOpenPacket(ragdollId, pickup),
                new CustomPacketPayload[0]);
    }
}
