package com.ragdoll.client;

import com.electronwill.nightconfig.core.Config;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.ragdoll.RagdollConfig;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.gui.ConfigurationScreen.ConfigurationSectionScreen;
import net.neoforged.neoforge.common.ModConfigSpec.ValueSpec;

import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** GUI-only grouping: keep the existing TOML paths and saved values intact. */
public final class RagdollConfigurationScreen extends ConfigurationSectionScreen {
    private static final List<String> BASIC_KEYS = List.of("maxRagdolls", "despawnSeconds", "selfCollision",
            "renderCape", "render3dSkin", "renderHd3dSkin");

    public RagdollConfigurationScreen(Screen parent, ModConfig.Type type, ModConfig config, Component title) {
        super(groupBasicSettings(Context.top(config.getModId(), parent, config, (c, k, e) -> e)), title);
    }

    private RagdollConfigurationScreen(Context context, Component title) {
        super(context, title);
    }

    private static Context groupBasicSettings(Context original) {
        Config basicValues = Config.inMemory();
        Config basicSpecs = Config.inMemory();
        for (String key : BASIC_KEYS) {
            basicValues.set(key, original.modSpec().getValues().getRaw(key));
            basicSpecs.set(key, original.valueSpecs().get(key));
        }
        Config category = Config.inMemory();
        category.set("basicSettings", basicValues);
        var entries = new LinkedHashSet<UnmodifiableConfig.Entry>();
        original.entries().stream().filter(e -> e.getKey().equals("enabled")).forEach(entries::add);
        entries.addAll(category.entrySet());
        for (String key : List.of("sleepRagdoll", "livePlayerRagdoll", "corpseRagdoll")) {
            original.entries().stream().filter(e -> e.getKey().equals(key)).forEach(entries::add);
        }
        var specs = new LinkedHashMap<>(original.valueSpecs());
        specs.put("basicSettings", basicSpecs);
        return new Context(original.modId(), original.parent(), original.modConfig(), original.modSpec(),
                entries, specs, original.keylist(), original.filter());
    }

    @Override
    protected Element createSection(String key, UnmodifiableConfig specs, UnmodifiableConfig values) {
        // NightConfig's default maps do not preserve insertion order. Order the actual UI entries explicitly.
        Set<? extends UnmodifiableConfig.Entry> entries = values.entrySet();
        if (key.equals("basicSettings")) {
            var ordered = new LinkedHashSet<UnmodifiableConfig.Entry>();
            for (String basicKey : BASIC_KEYS) {
                entries.stream().filter(e -> e.getKey().equals(basicKey)).forEach(ordered::add);
            }
            entries = ordered;
        }
        final var sectionEntries = entries;
        return new Element(getTranslationComponent(key), getTooltipComponent(key, null),
                Button.builder(Component.translatable(getTranslationKey(key) + ".button"), button ->
                        minecraft.setScreen(sectionCache.computeIfAbsent(key, k ->
                                new RagdollConfigurationScreen(Context.section(context, this, sectionEntries,
                                        specs.valueMap(), key), Component.translatable(getTranslationKey(key))))))
                        .tooltip(Tooltip.create(getTooltipComponent(key, null))).build(), false);
    }

    @Override
    protected Element createBooleanValue(String key, ValueSpec spec, Supplier<Boolean> source,
                                         Consumer<Boolean> target) {
        boolean missingSkinMod = key.equals("render3dSkin") && !RagdollConfig.skinLayersAvailable();
        boolean missing3dSkin = key.equals("renderHd3dSkin") && !RagdollConfig.render3dSkin();
        if (!missingSkinMod && !missing3dSkin) {
            return super.createBooleanValue(key, spec, source, target);
        }
        var tooltip = Component.translatable(missing3dSkin
                ? "ragdoll.configuration.renderHd3dSkin.requires3dSkin"
                : "ragdoll.configuration.render3dSkin.requiresMod");
        var button = Button.builder(Component.translatable("options.off"), ignored -> {}).build();
        button.active = false;
        button.setTooltip(Tooltip.create(tooltip));
        return new Element(getTranslationComponent(key), tooltip, button, false);
    }

    @Override
    protected void onChanged(String key) {
        super.onChanged(key);
        // Also refresh after undo/reset, not just direct clicks, so the dependency is immediate.
        if (key.equals("render3dSkin")) rebuild();
    }

}
