package com.ragdoll.server;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class CorpseLootMenu extends AbstractContainerMenu {
    private static final int PAGE_MAIN = 0;
    private static final int PAGE_OTHER = 1;
    private static final int MAIN_SLOTS = 41;
    private final SimpleContainer display = new DisplayContainer();
    private final Inventory playerInventory;
    private final RagdollSavedData savedData;
    private final UUID ragdollId;
    private final String ownerName;
    private final List<ItemStack> mainItems = emptyMainItems();
    private final List<ItemStack> otherItems = new ArrayList<>();
    private int page = PAGE_MAIN;
    private boolean loading;
    private final DataSlot pageData = DataSlot.standalone();

    public CorpseLootMenu(int containerId, Inventory inventory,
            net.minecraft.network.RegistryFriendlyByteBuf buffer) {
        this(containerId, inventory, null, buffer.readUUID(), buffer.readUtf(64));
    }

    public CorpseLootMenu(
            int containerId,
            Inventory inventory,
            RagdollSavedData savedData,
            UUID ragdollId,
            String ownerName,
            List<ItemStack> loot) {
        this(containerId, inventory, savedData, ragdollId, ownerName);
        for (int i = 0; i < Math.min(MAIN_SLOTS, loot.size()); ++i) {
            mainItems.set(i, loot.get(i).copy());
        }
        for (int i = MAIN_SLOTS; i < loot.size(); ++i) {
            if (!loot.get(i).isEmpty()) otherItems.add(loot.get(i).copy());
        }
        loadPage();
    }

    private CorpseLootMenu(
            int containerId,
            Inventory inventory,
            RagdollSavedData savedData,
            UUID ragdollId,
            String ownerName) {
        super(RagdollMenus.CORPSE_LOOT.get(), containerId);
        this.playerInventory = inventory;
        this.savedData = savedData;
        this.ragdollId = ragdollId;
        this.ownerName = ownerName;
        addDataSlot(pageData);
        pageData.set(page);
        ((DisplayContainer) display).setCallback(this::onDisplayChanged);
        bindSlotPositions();
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId == 2 && page == PAGE_MAIN) {
            cacheCurrentPage();
            List<ItemStack> loot = new ArrayList<>(mainItems.size() + otherItems.size());
            mainItems.forEach(stack -> loot.add(stack.copy()));
            otherItems.forEach(stack -> loot.add(stack.copy()));
            var accessories = savedData.entries().stream()
                    .filter(entry -> entry.packet().ragdollId().equals(ragdollId))
                    .map(entry -> entry.packet().snapshot().accessories())
                    .findFirst().orElse(com.ragdoll.model.RagdollAccessorySnapshot.EMPTY);
            List<ItemStack> remaining = RagdollLootService.restoreItems(
                    player, loot, accessories);
            for (int i = 0; i < mainItems.size(); ++i) {
                mainItems.set(i, remaining.get(i).copy());
            }
            otherItems.removeIf(ItemStack::isEmpty);
            otherItems.clear();
            for (int i = MAIN_SLOTS; i < remaining.size(); ++i) {
                if (!remaining.get(i).isEmpty()) otherItems.add(remaining.get(i).copy());
            }
            loadPage();
            onDisplayChanged();
            return true;
        }
        if (buttonId == 3 && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            cacheCurrentPage();
            List<ItemStack> loot = new ArrayList<>(mainItems.size() + otherItems.size());
            mainItems.forEach(stack -> loot.add(stack.copy()));
            otherItems.forEach(stack -> loot.add(stack.copy()));
            RagdollLootService.clearRagdoll(serverPlayer, ragdollId, loot);
            return true;
        }
        if (buttonId == 0 && page == PAGE_MAIN) {
            cacheCurrentPage();
            page = PAGE_OTHER;
            pageData.set(page);
            loadPage();
            return true;
        }
        if (buttonId == 1 && page == PAGE_OTHER) {
            cacheCurrentPage();
            page = PAGE_MAIN;
            pageData.set(page);
            loadPage();
            return true;
        }
        return false;
    }

    private void bindSlotPositions() {
        slots.clear();
        for (int i = 0; i < 54; ++i) {
            if (page == PAGE_OTHER) {
                addSlot(new Slot(display, i,
                        8 + (i % 9) * 18, 18 + (i / 9) * 18));
            } else if (i < 36) {
                addSlot(new Slot(display, i,
                        8 + (i % 9) * 18, 18 + (i / 9) * 18));
            } else if (i < 40) {
                int armorIndex = i - 36;
                EquipmentSlot expected = switch (armorIndex) {
                    case 0 -> EquipmentSlot.HEAD;
                    case 1 -> EquipmentSlot.CHEST;
                    case 2 -> EquipmentSlot.LEGS;
                    default -> EquipmentSlot.FEET;
                };
                addSlot(new Slot(display, i, 8 + armorIndex * 18, 98) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return stack.isEmpty()
                                || stack.getItem() instanceof ArmorItem armor
                                && armor.getEquipmentSlot() == expected;
                    }
                });
            } else if (i == 40) {
                addSlot(new Slot(display, i, 80, 98));
            } else {
                addSlot(new Slot(display, i, -1000, -1000));
            }
        }
        if (page == PAGE_MAIN) {
            for (int row = 0; row < 3; ++row) {
                for (int column = 0; column < 9; ++column) {
                    addSlot(new Slot(playerInventory, 9 + row * 9 + column,
                            8 + column * 18, 142 + row * 18));
                }
            }
            for (int column = 0; column < 9; ++column) {
                addSlot(new Slot(playerInventory, column,
                        8 + column * 18, 200));
            }
        } else {
            for (int row = 0; row < 3; ++row) {
                for (int column = 0; column < 9; ++column) {
                    addSlot(new Slot(playerInventory, 9 + row * 9 + column,
                            8 + column * 18, 142 + row * 18));
                }
            }
            for (int column = 0; column < 9; ++column) {
                addSlot(new Slot(playerInventory, column,
                        8 + column * 18, 200));
            }
        }
    }

    private void loadPage() {
        loading = true;
        for (int i = 0; i < 54; ++i) display.setItem(i, ItemStack.EMPTY);
        if (page == PAGE_MAIN) {
            for (int i = 0; i < MAIN_SLOTS; ++i) {
                display.setItem(i, mainItems.get(i).copy());
            }
        } else {
            for (int i = 0; i < Math.min(54, otherItems.size()); ++i) {
                display.setItem(i, otherItems.get(i).copy());
            }
        }
        loading = false;
    }

    private void onDisplayChanged() {
        if (loading || savedData == null) return;
        cacheCurrentPage();
        List<ItemStack> loot = new ArrayList<>(mainItems.size() + otherItems.size());
        mainItems.forEach(stack -> loot.add(stack.copy()));
        otherItems.forEach(stack -> loot.add(stack.copy()));
        savedData.setLoot(ragdollId, loot);
        net.minecraft.server.level.ServerLevel level =
                playerInventory.player instanceof net.minecraft.server.level.ServerPlayer player
                        ? player.serverLevel() : null;
        if (level != null) {
            com.ragdoll.client.physics.ServerRagdollManager
                    .syncAppearance(level, ragdollId);
        }
    }

    private void cacheCurrentPage() {
        loading = true;
        if (page == PAGE_MAIN) {
            for (int i = 0; i < MAIN_SLOTS; ++i) {
                mainItems.set(i, display.getItem(i).copy());
            }
        } else {
            int next = 54;
            for (int i = 0; i < 54 && next < otherItems.size(); ++i) {
                if (display.getItem(i).isEmpty()) {
                    display.setItem(i, otherItems.get(next++).copy());
                }
            }
            List<ItemStack> remainder = new ArrayList<>();
            for (int i = 0; i < 54; ++i) {
                ItemStack item = display.getItem(i);
                if (!item.isEmpty()) remainder.add(item.copy());
            }
            for (int i = next; i < otherItems.size(); ++i) {
                remainder.add(otherItems.get(i).copy());
            }
            otherItems.clear();
            otherItems.addAll(remainder);
        }
        loading = false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(slotIndex);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack original = slot.getItem().copy();
        ItemStack moving = slot.getItem().copy();
        if (slotIndex < 54) {
            if (!moveItemStackTo(moving, 54, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            int start = 0;
            int end = page == PAGE_MAIN ? 36 : 54;
            if (!moveItemStackTo(moving, start, end, false)) return ItemStack.EMPTY;
        }
        if (moving.getCount() == original.getCount()) return ItemStack.EMPTY;
        slot.set(moving.isEmpty() ? ItemStack.EMPTY : moving);
        slot.onTake(player, moving);
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        if (savedData == null || ragdollId == null) return true;
        if (!com.ragdoll.RagdollConfig.corpseRagdollEnabled()) return false;
        return savedData.entries().stream()
                .filter(entry -> entry.packet().ragdollId().equals(ragdollId))
                .anyMatch(entry -> {
                    if (com.ragdoll.RagdollConfig.corpseOwnerOnly()
                            && !entry.packet().snapshot().playerId()
                            .equals(player.getUUID())) return false;
                    Vec3 position = new Vec3(
                            entry.packet().snapshot().x(),
                            entry.packet().snapshot().y(),
                            entry.packet().snapshot().z());
                    for (var part : entry.state().parts()) {
                        if ("body".equals(part.partName())) {
                            position = new Vec3(part.x(), part.y(), part.z());
                            break;
                        }
                    }
                    return player.position().distanceToSqr(position) <= 64.0;
                });
    }

    public int page() {
        return pageData.get();
    }

    public String ownerName() {
        return ownerName;
    }

    private static List<ItemStack> emptyMainItems() {
        List<ItemStack> result = new ArrayList<>(MAIN_SLOTS);
        for (int i = 0; i < MAIN_SLOTS; ++i) result.add(ItemStack.EMPTY);
        return result;
    }

    private final class DisplayContainer extends SimpleContainer {
        private Runnable callback = () -> {};

        DisplayContainer() {
            super(54);
        }

        void setCallback(Runnable callback) {
            this.callback = callback;
        }

        @Override
        public void setChanged() {
            super.setChanged();
            callback.run();
        }
    }
}
