package com.ragdoll.item;

import com.ragdoll.RagdollMod;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class RagdollItems {

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(RagdollMod.MOD_ID);

    public static final DeferredItem<PhysicsGunItem> PHYSICS_GUN =
            ITEMS.registerItem(
                    "physics_gun",
                    PhysicsGunItem::new,
                    new Item.Properties()
                            .stacksTo(1)
                            .rarity(Rarity.EPIC));

    private RagdollItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        modEventBus.addListener(
                RagdollItems::addCreativeTabContents);
    }

    private static void addCreativeTabContents(
            BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(PHYSICS_GUN.get());
        }
    }
}
