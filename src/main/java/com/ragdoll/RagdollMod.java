package com.ragdoll;

import com.mojang.logging.LogUtils;
import com.ragdoll.network.RagdollNetwork;
import com.ragdoll.item.RagdollItems;
import com.ragdoll.server.RagdollCommand;
import com.ragdoll.server.ServerPlayerDeaths;
import com.ragdoll.server.RagdollLootService;
import com.ragdoll.server.RagdollMenus;
import com.ragdoll.client.physics.ServerRagdollManager;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/** Common entry point for player ragdolls. */
@Mod(RagdollMod.MOD_ID)
public final class RagdollMod {
    public static final String MOD_ID = "ragdoll";
    public static final Logger LOGGER = LogUtils.getLogger();

    public RagdollMod(IEventBus modEventBus, ModContainer modContainer) {
        RagdollItems.register(modEventBus);
        RagdollMenus.MENUS.register(modEventBus);
        modContainer.registerConfig(ModConfig.Type.COMMON, RagdollConfig.SPEC);
        modEventBus.addListener(RagdollNetwork::register);
        NeoForge.EVENT_BUS.addListener(RagdollCommand::register);
        NeoForge.EVENT_BUS.addListener(ServerPlayerDeaths::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(RagdollLootService::suppressVanillaDrops);
        NeoForge.EVENT_BUS.addListener(ServerRagdollManager::onLevelTick);
        NeoForge.EVENT_BUS.addListener(ServerRagdollManager::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(ServerRagdollManager::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(ServerRagdollManager::onPlayerChangedDimension);
        NeoForge.EVENT_BUS.addListener(ServerRagdollManager::onServerStarted);
        NeoForge.EVENT_BUS.addListener(ServerRagdollManager::onServerStopping);
        LOGGER.info("Player ragdoll module loaded");
    }
}
