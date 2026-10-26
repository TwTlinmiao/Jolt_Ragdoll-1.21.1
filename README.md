# Jolt Ragdoll for NeoForge 1.21.1

This project ports only the Jolt Ragdoll module from the Minecraft 26.2 workspace. It remains a standalone mod with the `ragdoll` mod ID. The Sable player ragdoll and ragdoll reaction projects are not required.

Requirements: Minecraft 1.21.1, NeoForge 21.1.228 or newer in the 21.1 line, and Java 21. See `build.gradle` for the bundled native libraries and optional compatibility dependencies.

Build with `gradlew.bat build` (Windows) or `sh gradlew build` (Linux/macOS) from a shell whose `JAVA_HOME` points to Java 21. The current version is defined in `gradle.properties`; the mod JAR is written to `build/libs/`. Place that JAR in the game's `mods` directory. The `.gradle-local-maven` folder is an optional local download cache for this workspace. On a machine without it, Gradle uses the normal repositories. The build also runs pose, inventory, impact-response, and wearable compatibility regression checks.

Collision geometry follows the local Sable player ragdoll's actual collision shapes, not its outline shapes. Dimensions are full extents in blocks. Centers below are for the default standing pose, relative to the feet, before entity yaw; X uses Jolt's model frame.

| Part | Size X/Y/Z | Collision center X/Y/Z |
| --- | --- | --- |
| Head | 0.5 / 0.25 / 0.5 | 0 / 1.65 / 0 |
| Torso | 0.375 / 0.5625 / 0.25 | 0 / 1.01875 / 0 |
| Left arm | 0.125 / 0.625 / 0.25 | -0.3575 / 1.05 / 0 |
| Right arm | 0.125 / 0.625 / 0.25 | 0.3625 / 1.05 / 0 |
| Left leg | 0.125 / 0.625 / 0.25 | -0.1175 / 0.35 / 0 |
| Right leg | 0.125 / 0.625 / 0.25 | 0.1225 / 0.35 / 0 |

These centers are derived from Sable's `jointAnchor` and `centerVisiblePart` calculations; the legacy `PartSpawn.upOffset` values for limbs are not used by its current spawn loop. Neck, shoulder and hip anchors are converted to Jolt's model centers so the initial joints coincide. Jolt's pose rotations, mass, joint limits and simulation behavior remain in use.

Collision dimensions are fixed in the current version; there is no live collision tuning screen.

The `selfCollision` option follows Sable's `Ragdoll Parts Collide` rule. When enabled (the default), all 15 pairs among the six parts can collide. When disabled, only the five direct joint pairs (head/torso and torso/each arm or leg) cannot collide; the other 10 pairs remain collidable. Ragdolls always retain their joints; detached-parts mode has been removed. Existing `ragdoll-common.toml` values override defaults.
