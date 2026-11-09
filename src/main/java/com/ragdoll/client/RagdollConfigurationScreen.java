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
            "renderCape", "render3dSkin", "renderHd3dSkin", "showPartRigidBodies",
            "showEntityInteractionBody", "gravity", "massScale",
            "initialVelocityScale", "playerPushStrength", "explosionStrength", "friction",
            "linearDamping", "angularDamping", "restitution");
    private static final List<String> LIVE_TRIGGER_KEYS = List.of(
            "damageEnabled",
            "entityDamageEnabled", "entityDamageThreshold",
            "fallDamageEnabled", "fallDamageThreshold",
            "explosionDamageEnabled", "explosionDamageThreshold",
            "wallImpactEnabled", "wallImpactThreshold",
            "heavyHitEnabled", "heavyHitThreshold",
            "lightningEnabled", "lightningThreshold",
            "entityDamageForce", "fallForce", "explosionForce",
            "wallImpactForce", "heavyHitForce", "lightningForce");
    private static final List<TriggerGroup> TRIGGER_GROUPS = List.of(
            new TriggerGroup("entityAttack", "entityDamageEnabled", "entityDamageForce"),
            new TriggerGroup("fall", "fallDamageEnabled", "fallForce"),
            new TriggerGroup("explosion", "explosionDamageEnabled", "explosionForce"),
            new TriggerGroup("wallImpact", "wallImpactEnabled", "wallImpactForce"),
            new TriggerGroup("heavyHit", "heavyHitEnabled", "heavyHitForce"),
            new TriggerGroup("lightning", "lightningEnabled", "lightningForce"));

    public RagdollConfigurationScreen(Screen parent, ModConfig.Type type, ModConfig config, Component title) {
        super(groupBasicSettings(Context.top(config.getModId(), parent, config, (c, k, e) -> e)), title);
    }

    private RagdollConfigurationScreen(Context context, Component title) {
        super(context, title);
    }

    private static Context groupBasicSettings(Context original) {
        Config basicValues = Config.inMemory();
        Config basicSpecs = Config.inMemory();
        Config triggerValues = Config.inMemory();
        Config triggerSpecs = Config.inMemory();
        Config liveValues = Config.inMemory();
        Config liveSpecs = Config.inMemory();
        for (String key : BASIC_KEYS) {
            boolean exists = original.entries().stream().anyMatch(entry -> entry.getKey().equals(key));
            if (!exists) continue;
            basicValues.set(key, original.modSpec().getValues().getRaw(key));
            basicSpecs.set(key, original.valueSpecs().get(key));
        }
        boolean serverConfig = original.modConfig().getType() == ModConfig.Type.SERVER;
        UnmodifiableConfig liveValueCategory = serverConfig
                ? original.modSpec().getValues().get("livePlayerRagdoll") : null;
        Object liveSpecObject = serverConfig
                ? original.valueSpecs().get("livePlayerRagdoll") : null;
        UnmodifiableConfig liveSpecCategory = liveSpecObject instanceof UnmodifiableConfig config
                ? config : null;
        if (liveValueCategory != null && liveSpecCategory != null) {
            for (UnmodifiableConfig.Entry entry : liveValueCategory.entrySet()) {
                String key = entry.getKey();
                if (LIVE_TRIGGER_KEYS.contains(key)) {
                    triggerValues.set(key, entry.getRawValue());
                    triggerSpecs.set(key, liveSpecCategory.get(key));
                } else {
                    liveValues.set(key, entry.getRawValue());
                    liveSpecs.set(key, liveSpecCategory.get(key));
                }
            }
        }
        var entries = new LinkedHashSet<UnmodifiableConfig.Entry>();
        original.entries().stream()
                .filter(e -> !e.getKey().equals("_legacyCommonValuesMigrated")
                        && !BASIC_KEYS.contains(e.getKey())
                        && e.getKey().equals("enabled"))
                .forEach(entries::add);
        if (!basicValues.isEmpty()) {
            Config category = Config.inMemory();
            category.set("basicSettings", basicValues);
            entries.addAll(category.entrySet());
        }
        for (String key : List.of("sleepRagdoll", "livePlayerRagdoll", "corpseRagdoll")) {
            if (key.equals("livePlayerRagdoll") && !triggerValues.isEmpty()) {
                Config category = Config.inMemory();
                category.set(key, liveValues);
                entries.addAll(category.entrySet());
            } else {
                original.entries().stream().filter(e -> e.getKey().equals(key)).forEach(entries::add);
            }
            if (key.equals("livePlayerRagdoll") && !triggerValues.isEmpty()) {
                Config category = Config.inMemory();
                category.set("triggers", triggerValues);
                entries.addAll(category.entrySet());
            }
        }
        var specs = new LinkedHashMap<>(original.valueSpecs());
        if (!basicValues.isEmpty()) specs.put("basicSettings", basicSpecs);
        if (!triggerValues.isEmpty()) specs.put("triggers", triggerSpecs);
        if (!liveValues.isEmpty()) specs.put("livePlayerRagdoll", liveSpecs);
        return new Context(original.modId(), original.parent(), original.modConfig(), original.modSpec(),
                entries, specs, original.keylist(), original.filter());
    }

    @Override
    protected Element createSection(String key, UnmodifiableConfig specs, UnmodifiableConfig values) {
        // NightConfig's default maps do not preserve insertion order. Order the actual UI entries explicitly.
        Set<? extends UnmodifiableConfig.Entry> entries = values.entrySet();
        var sectionSpecs = specs.valueMap();
        if (key.equals("basicSettings")) {
            var ordered = new LinkedHashSet<UnmodifiableConfig.Entry>();
            for (String basicKey : BASIC_KEYS) {
                entries.stream().filter(e -> e.getKey().equals(basicKey)).forEach(ordered::add);
            }
            entries = ordered;
        } else if (key.equals("triggers")
                && context.modConfig().getType() == ModConfig.Type.SERVER) {
            var ordered = new LinkedHashSet<UnmodifiableConfig.Entry>();
            entries.stream().filter(e -> e.getKey().equals("damageEnabled"))
                    .forEach(ordered::add);
            var groupedSpecs = new LinkedHashMap<>(sectionSpecs);
            for (TriggerGroup group : TRIGGER_GROUPS) {
                Config groupValues = Config.inMemory();
                Config groupSpecs = Config.inMemory();
                for (String field : List.of(group.enabledKey(), group.forceKey())) {
                    if (values.contains(field)) {
                        groupValues.set(field, values.get(field));
                        groupSpecs.set(field, specs.get(field));
                    }
                }
                if (!groupValues.isEmpty()) {
                    Config groupCategory = Config.inMemory();
                    groupCategory.set(group.key(), groupValues);
                    ordered.addAll(groupCategory.entrySet());
                    groupedSpecs.put(group.key(), groupSpecs);
                }
            }
            entries = ordered;
            sectionSpecs = groupedSpecs;
        }
        final var sectionEntries = entries;
        final var childSpecs = sectionSpecs;
        return new Element(getTranslationComponent(key), getTooltipComponent(key, null),
                Button.builder(Component.translatable(getTranslationKey(key) + ".button"), button ->
                        minecraft.setScreen(sectionCache.computeIfAbsent(key, k ->
                                new RagdollConfigurationScreen(Context.section(context, this, sectionEntries,
                                        childSpecs, key), Component.translatable(getTranslationKey(key))))))
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

    private record TriggerGroup(String key, String enabledKey, String forceKey) {
    }

}
