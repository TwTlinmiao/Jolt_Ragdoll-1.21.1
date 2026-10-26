package com.ragdoll.server;

import com.ragdoll.RagdollMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.network.IContainerFactory;

import java.util.function.Supplier;

public final class RagdollMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, RagdollMod.MOD_ID);
    public static final Supplier<MenuType<CorpseLootMenu>> CORPSE_LOOT =
            MENUS.register("corpse_loot", () -> new MenuType<>(
                    (IContainerFactory<CorpseLootMenu>) CorpseLootMenu::new,
                    FeatureFlags.DEFAULT_FLAGS));

    private RagdollMenus() {
    }
}
