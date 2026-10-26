package com.ragdoll.client.physics;

import com.github.stephengold.joltjni.AaBox;
import com.github.stephengold.joltjni.BatchBodyInterface;
import com.github.stephengold.joltjni.BodyCreationSettings;
import com.github.stephengold.joltjni.BoxShape;
import com.github.stephengold.joltjni.BoxShapeSettings;
import com.github.stephengold.joltjni.BroadPhaseLayerInterfaceTable;
import com.github.stephengold.joltjni.CollisionGroup;
import com.github.stephengold.joltjni.GroupFilterTable;
import com.github.stephengold.joltjni.JobSystemThreadPool;
import com.github.stephengold.joltjni.FixedConstraintSettings;
import com.github.stephengold.joltjni.MassProperties;
import com.github.stephengold.joltjni.Mat44;
import com.github.stephengold.joltjni.MotorSettings;
import com.github.stephengold.joltjni.ObjectLayerPairFilterTable;
import com.github.stephengold.joltjni.ObjectVsBroadPhaseLayerFilterTable;
import com.github.stephengold.joltjni.PhysicsSystem;
import com.github.stephengold.joltjni.PointConstraintSettings;
import com.github.stephengold.joltjni.Quat;
import com.github.stephengold.joltjni.RVec3;
import com.github.stephengold.joltjni.RotatedTranslatedShape;
import com.github.stephengold.joltjni.SixDofConstraint;
import com.github.stephengold.joltjni.SixDofConstraintSettings;
import com.github.stephengold.joltjni.StaticCompoundShapeSettings;
import com.github.stephengold.joltjni.TempAllocatorMalloc;
import com.github.stephengold.joltjni.TransformedShape;
import com.github.stephengold.joltjni.TwoBodyConstraint;
import com.github.stephengold.joltjni.Vec3;
import com.github.stephengold.joltjni.enumerate.EActivation;
import com.github.stephengold.joltjni.enumerate.EConstraintSpace;
import com.github.stephengold.joltjni.enumerate.EAxis;
import com.github.stephengold.joltjni.enumerate.EMotorState;
import com.github.stephengold.joltjni.enumerate.EMotionQuality;
import com.github.stephengold.joltjni.enumerate.EMotionType;
import com.github.stephengold.joltjni.enumerate.EOverrideMassProperties;
import com.github.stephengold.joltjni.enumerate.ESpringMode;
import com.ragdoll.RagdollConfig;
import com.ragdoll.RagdollMod;
import com.ragdoll.model.RagdollInitialPose;
import com.ragdoll.client.physics.RagdollPhysicsState;
import com.ragdoll.model.RagdollDeathSnapshot;
import net.minecraft.world.level.Level;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BubbleColumnBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.joml.Quaternionf;

final class JoltPhysicsWorld implements AutoCloseable {

    private static final int OBJECT_LAYER_MOVING = 0;

    private static final int OBJECT_LAYER_NON_MOVING = 1;

    private static final int OBJECT_LAYER_GHOST = 2;

    private static final int NUM_OBJECT_LAYERS = 3;

    private static final int BROAD_PHASE_LAYER_MOVING = 0;

    private static final int BROAD_PHASE_LAYER_NON_MOVING = 1;

    private static final int BROAD_PHASE_LAYER_GHOST = 2;

    private static final int NUM_BROAD_PHASE_LAYERS = 3;

    private static final int TERRAIN_HORIZONTAL_RADIUS = 8;

    private static final int TERRAIN_BLOCKS_BELOW = 6;

    private static final int TERRAIN_BLOCKS_ABOVE = 6;

    private static final int TERRAIN_FALLBACK_BLOCKS_BELOW = 64;

    private static final int TERRAIN_SAFETY_REFRESH_INTERVAL_TICKS = 100;

    private static final int NO_TERRAIN_BODY = -1;

    private static final float MIN_BOX_SIZE = 1.0e-4f;

    private static final float FIXED_TIME_STEP = 1.0f / 60.0f;

    private static final int SUB_STEPS_PER_CLIENT_TICK = 3;

    private static final int LOG_INTERVAL_TICKS = 20;

    private static final int MAX_LOG_TICKS = 300;

    private static final int SETTLED_TICKS_BEFORE_SLEEP = 20;

    private static final float MAX_SETTLED_LINEAR_SPEED = 0.25f;

    // Stay below the addon's 30 blocks/second follower limit after repeated hits.
    private static final float MAX_CONTROLLED_KNOCKBACK_SPEED = 28.0f;

    private static final float MAX_SETTLED_ANGULAR_SPEED = 0.75f;

    private static final float PHYSICS_GUN_LINEAR_STIFFNESS = 250.0f;

    private static final float PHYSICS_GUN_LINEAR_DAMPING = 25.0f;

    private static final float PHYSICS_GUN_MAX_ACCELERATION = 120.0f;

    private static final float PHYSICS_GUN_ANGULAR_STIFFNESS = 150.0f;

    private static final float PHYSICS_GUN_ANGULAR_DAMPING = 15.0f;

    private static final float PHYSICS_GUN_MAX_ANGULAR_ACCELERATION = 120.0f;

    private static final double PHYSICS_GUN_TARGET_SMOOTHING = 0.45;

    private static final float PHYSICS_GUN_ROTATION_SMOOTHING = 0.65f;

    private static final float IMPACT_PARTICLE_THRESHOLD = 4.0f;

    private static final int IMPACT_PARTICLE_COOLDOWN_TICKS = 10;

    private static final double IMPACT_BLOCK_SEARCH_PADDING = 0.08;

    private static final float TORSO_MASS = 32.0f;

    private static final float HEAD_MASS = 8.0f;

    private static final float ARM_MASS = 4.0f;

    private static final float LEG_MASS = 8.0f;

    private static final float WATER_BUOYANCY_SCALE = 1.55f;

    private static final float WATER_LINEAR_DRAG = 2.8f;

    private static final float WATER_ANGULAR_DRAG = 3.6f;

    private static final float WATER_FLOW_SPEED = 1.5f;

    private static final float WATER_RIGHTING_MIN_SUBMERGENCE = 0.35f;

    private static final float WATER_RIGHTING_TORQUE = 10.0f;

    private static final float WATER_RIGHTING_DAMPING = 4.0f;

    private static final float WATER_SURFACE_BOB_SPEED = 0.22f;

    private static final float WATER_SURFACE_BOB_PERIOD_TICKS = 72.0f;

    private static final float LAVA_BUOYANCY_SCALE = 1.55f;

    private static final float LAVA_LINEAR_DRAG = 8.5f;

    private static final float LAVA_ANGULAR_DRAG = 10.0f;

    private static final float LAVA_FLOW_SPEED = 0.35f;

    private static final float LAVA_RIGHTING_TORQUE = 3.5f;

    private static final float LAVA_RIGHTING_DAMPING = 6.5f;

    private static final float LAVA_SURFACE_BOB_SPEED = 0.07f;

    private static final float LAVA_SURFACE_BOB_PERIOD_TICKS = 120.0f;

    // Vanilla values are blocks/tick. Jolt velocities are blocks/second.
    private static final float BUBBLE_INSIDE_UP_DELTA = 0.06f * 20.0f;

    private static final float BUBBLE_INSIDE_UP_LIMIT = 0.7f * 20.0f;

    private static final float BUBBLE_INSIDE_DOWN_DELTA = -0.03f * 20.0f;

    private static final float BUBBLE_INSIDE_DOWN_LIMIT = -0.3f * 20.0f;

    private static final float BUBBLE_SURFACE_UP_DELTA = 0.1f * 20.0f;

    private static final float BUBBLE_SURFACE_UP_LIMIT = 1.8f * 20.0f;

    private static final float BUBBLE_SURFACE_DOWN_LIMIT = -0.9f * 20.0f;

    private static final float DEFAULT_PART_FRICTION = 0.65f;

    private static final float ICE_PART_FRICTION = 0.02f;

    private static final float STICKY_PART_FRICTION = 0.8f;

    private static final float WEB_HORIZONTAL_FACTOR = (float) Math.pow(0.25, 1.0 / SUB_STEPS_PER_CLIENT_TICK);

    private static final float WEB_VERTICAL_FACTOR = (float) Math.pow(0.05, 1.0 / SUB_STEPS_PER_CLIENT_TICK);

    private static final float GROUNDED_WEB_HORIZONTAL_FACTOR = (float) Math.pow(0.75, 1.0 / SUB_STEPS_PER_CLIENT_TICK);

    private static final float GROUNDED_WEB_VERTICAL_FACTOR = (float) Math.pow(0.5, 1.0 / SUB_STEPS_PER_CLIENT_TICK);

    private static final float POWDER_SNOW_HORIZONTAL_FACTOR = (float) Math.pow(0.9, 1.0 / SUB_STEPS_PER_CLIENT_TICK);

    private static final float POWDER_SNOW_SINK_SPEED = -0.35f;

    private static final float HONEY_SLIDE_SPEED = -0.05f * 20.0f;

    private static final float HONEY_SLIDE_MINIMUM_SPEED = -0.08f * 20.0f;

    private static final float SLIME_BOUNCE_SCALE = 0.8f;

    private static final double FLUID_SAMPLE_SPREAD = 0.65;

    private static final float INITIAL_VELOCITY_SCALE = 0.4f;

    private static final double PLAYER_PUSH_STRENGTH = 4.0;

    private static final double PLAYER_PUSH_BIAS_PER_DEPTH = 12.0;

    private static final double PLAYER_PUSH_SPEED_TRANSFER = 2.5;

    private static final double PLAYER_PUSH_MAX_BIAS_SPEED = 1.4;

    private static final double PLAYER_PUSH_MAX_IMPULSE = 36.0;

    private static final double PLAYER_PUSH_TANGENT_FRICTION = 0.12;

    private static final double PLAYER_PUSH_IMPULSE_BUDGET = 54.0;

    private static final double PLAYER_PUSH_VERTICAL_SCALE = 0.15;

    private static final double PLAYER_PUSH_AXIS_EPSILON = 1.0e-8;

    private static final float ATTACK_IMPULSE = 28.0f;

    private static final float ATTACK_UPWARD_IMPULSE = 4.0f;

    private static final float GRAB_TARGET_MAX_SPEED = 3.0f;

    private static final float GRAB_TARGET_HALF_EXTENT = 0.02f;

    private static final int JOINT_POSITION_STEPS_OVERRIDE = 8;

    private static final int JOINT_VELOCITY_STEPS_OVERRIDE = 4;

    // Push continuously with smaller per-tick impulses instead of a large
    // pulse every few ticks, which reads as visible stop-and-go motion.
    private static final double TERRAIN_SWEEP_MINIMUM_FALL = 0.20;

    private static final double TERRAIN_SWEEP_CLEARANCE = 0.015;

    private static final double TERRAIN_SWEEP_MINIMUM_LIFT = 0.03;

    private static final int SELF_COLLISION_SUBGROUPS = 6;

    private static final int SUBGROUP_HEAD = 0;

    private static final int SUBGROUP_BODY = 1;

    private static final int SUBGROUP_LEFT_ARM = 2;

    private static final int SUBGROUP_RIGHT_ARM = 3;

    private static final int SUBGROUP_LEFT_LEG = 4;

    private static final int SUBGROUP_RIGHT_LEG = 5;

    private static final Map<Level, SharedPhysicsContext> SHARED_PHYSICS_CONTEXTS = new java.util.WeakHashMap<>();

    private final SharedPhysicsContext sharedContext;

    private final BroadPhaseLayerInterfaceTable layerMap;

    private final ObjectLayerPairFilterTable objectLayerFilter;

    private final ObjectVsBroadPhaseLayerFilterTable broadPhaseFilter;

    private final GroupFilterTable selfCollisionFilter;

    private final TempAllocatorMalloc tempAllocator;

    private final JobSystemThreadPool jobSystem;

    private final PhysicsSystem physicsSystem;

    private final BatchBodyInterface bodyInterface;

    private final Level level;

    private final List<RagdollPart> parts = new ArrayList<>();

    private final List<RagdollPartRenderState> renderParts;

    private final List<TwoBodyConstraint> constraints = new ArrayList<>();


    private final BlockPos localOrigin;

    private final int collisionGroupId;

    private final double spawnX;

    private final double spawnGroundY;

    private final double spawnZ;

    private final float spawnYawRadians;

    private final Quat spawnRotation;

    private final double modelScale;

    private final float massScale;

    private final double torsoCenterX;

    private final double torsoCenterY;

    private final double torsoCenterZ;


    private final RagdollPart torso;

    private int terrainBodyId = NO_TERRAIN_BODY;

    private int terrainBoxCount;

    private List<TerrainBox> terrainBoxes;

    private BlockPos terrainScanCenter;

    private boolean terrainUsesFallback;

    private boolean terrainDirty;

    private int clientTicks;

    private int settledTicks;

    private int impactParticleCooldownTicks;

    private float fluidFaceTargetSign;

    private Vec3[] velocitiesBeforeSharedUpdate;

    private boolean touchedFluidThisTick;

    private boolean touchedSpecialThisTick;

    private final Map<UUID, PhysicsGunControl> physicsGunControls =
            new HashMap<>();

    private TwoBodyConstraint grabConstraint;

    private RagdollPart grabbedPart;

    private double grabLocalX;

    private double grabLocalY;

    private double grabLocalZ;

    private double grabTargetX;

    private double grabTargetY;

    private double grabTargetZ;

    private BoxShape grabTargetShape;

    private int grabTargetBodyId = NO_TERRAIN_BODY;

    private boolean closed;

    private boolean externallyControlled;

    private static synchronized SharedPhysicsContext acquireSharedContext(Level level) {
        SharedPhysicsContext context = SHARED_PHYSICS_CONTEXTS.get(level);
        if (context == null || context.closed) {
            context = new SharedPhysicsContext(level);
            SHARED_PHYSICS_CONTEXTS.put(level, context);
        }
        context.acquire();
        return context;
    }

    private static void configureSelfCollisionFilter(GroupFilterTable filter, boolean enabled) {
        for (int first = 0; first < SELF_COLLISION_SUBGROUPS; first++) {
            for (int second = first + 1; second < SELF_COLLISION_SUBGROUPS; second++) {
                filter.enableCollision(first, second);
            }
        }
        if (!enabled) {
            // Sable suppresses contacts only for the five parts joined directly to the torso.
            filter.disableCollision(SUBGROUP_HEAD, SUBGROUP_BODY);
            filter.disableCollision(SUBGROUP_BODY, SUBGROUP_LEFT_ARM);
            filter.disableCollision(SUBGROUP_BODY, SUBGROUP_RIGHT_ARM);
            filter.disableCollision(SUBGROUP_BODY, SUBGROUP_LEFT_LEG);
            filter.disableCollision(SUBGROUP_BODY, SUBGROUP_RIGHT_LEG);
        }
    }

    private static final class SharedPhysicsContext {

        private final Level level;

        private final BlockPos origin = BlockPos.ZERO;

        private final BroadPhaseLayerInterfaceTable layerMap;

        private final ObjectLayerPairFilterTable objectLayerFilter;

        private final ObjectVsBroadPhaseLayerFilterTable broadPhaseFilter;

        private final GroupFilterTable selfCollisionEnabledFilter;

        private final GroupFilterTable selfCollisionDisabledFilter;

        private final TempAllocatorMalloc tempAllocator;

        private final JobSystemThreadPool jobSystem;

        private final PhysicsSystem physicsSystem;

        private final BatchBodyInterface bodyInterface;

        private int referenceCount;

        private int nextCollisionGroupId = 1;

        private boolean closed;

        private SharedPhysicsContext(Level level) {
            this.level = level;
            objectLayerFilter = new ObjectLayerPairFilterTable(NUM_OBJECT_LAYERS);
            // Moving-moving must stay enabled in the shared world so separate
            // ragdolls can collide. Same-ragdoll filtering is handled by
            // CollisionGroup groupId/subgroupId instead.
            objectLayerFilter.enableCollision(OBJECT_LAYER_MOVING, OBJECT_LAYER_MOVING);
            objectLayerFilter.enableCollision(OBJECT_LAYER_MOVING, OBJECT_LAYER_NON_MOVING);
            objectLayerFilter.disableCollision(OBJECT_LAYER_MOVING, OBJECT_LAYER_GHOST);
            objectLayerFilter.disableCollision(OBJECT_LAYER_GHOST, OBJECT_LAYER_MOVING);
            objectLayerFilter.disableCollision(OBJECT_LAYER_NON_MOVING, OBJECT_LAYER_NON_MOVING);
            objectLayerFilter.disableCollision(OBJECT_LAYER_NON_MOVING, OBJECT_LAYER_GHOST);
            objectLayerFilter.disableCollision(OBJECT_LAYER_GHOST, OBJECT_LAYER_NON_MOVING);
            objectLayerFilter.disableCollision(OBJECT_LAYER_GHOST, OBJECT_LAYER_GHOST);
            selfCollisionEnabledFilter = new GroupFilterTable(SELF_COLLISION_SUBGROUPS);
            configureSelfCollisionFilter(selfCollisionEnabledFilter, true);
            selfCollisionDisabledFilter = new GroupFilterTable(SELF_COLLISION_SUBGROUPS);
            configureSelfCollisionFilter(selfCollisionDisabledFilter, false);
            layerMap = new BroadPhaseLayerInterfaceTable(NUM_OBJECT_LAYERS, NUM_BROAD_PHASE_LAYERS);
            layerMap.mapObjectToBroadPhaseLayer(OBJECT_LAYER_MOVING, BROAD_PHASE_LAYER_MOVING);
            layerMap.mapObjectToBroadPhaseLayer(OBJECT_LAYER_NON_MOVING, BROAD_PHASE_LAYER_NON_MOVING);
            layerMap.mapObjectToBroadPhaseLayer(OBJECT_LAYER_GHOST, BROAD_PHASE_LAYER_GHOST);
            broadPhaseFilter = new ObjectVsBroadPhaseLayerFilterTable(layerMap, NUM_BROAD_PHASE_LAYERS, objectLayerFilter, NUM_OBJECT_LAYERS);
            tempAllocator = new TempAllocatorMalloc();
            jobSystem = JoltBootstrap.acquireSharedJobSystem();
            physicsSystem = new PhysicsSystem();
            physicsSystem.init(8_192, 0, 16_384, 16_384, layerMap, broadPhaseFilter, objectLayerFilter);
            physicsSystem.setGravity(0.0f, -9.81f, 0.0f);
            bodyInterface = physicsSystem.getBodyInterface();
            RagdollMod.LOGGER.info("Created shared Jolt ragdoll physics world for {}", level.dimension().location());
        }

        private void acquire() {
            ++referenceCount;
        }

        private void release() {
            --referenceCount;
            if (referenceCount <= 0) {
                close();
                synchronized (JoltPhysicsWorld.class) {
                    SHARED_PHYSICS_CONTEXTS.remove(level, this);
                }
            }
        }

        private int allocateCollisionGroupId() {
            return nextCollisionGroupId++;
        }

        private void advancePhysics() {
            int updateError = physicsSystem.update(FIXED_TIME_STEP, 1, tempAllocator, jobSystem);
            if (updateError != 0) {
                throw new IllegalStateException("Jolt update failed with error mask " + updateError);
            }
        }

        private void close() {
            if (closed) {
                return;
            }
            closed = true;
            physicsSystem.close();
            tempAllocator.close();
            broadPhaseFilter.close();
            layerMap.close();
            selfCollisionEnabledFilter.close();
            selfCollisionDisabledFilter.close();
            objectLayerFilter.close();
            JoltBootstrap.releaseSharedJobSystem(jobSystem);
            RagdollMod.LOGGER.info("Closed shared Jolt ragdoll physics world");
        }
    }

    private JoltPhysicsWorld(SharedPhysicsContext sharedContext, Level level, TerrainSnapshot terrain, double worldX, double worldY, double worldZ, float yaw, RagdollInitialPose initialPose, float xRot, float velocityX, float velocityY, float velocityZ, float requestedModelScale, RagdollPhysicsSettings physicsSettings, RagdollInitialLimbPose initialLimbPose) {
        this.sharedContext = sharedContext;
        layerMap = sharedContext.layerMap;
        objectLayerFilter = sharedContext.objectLayerFilter;
        broadPhaseFilter = sharedContext.broadPhaseFilter;
        selfCollisionFilter = physicsSettings.selfCollisionEnabled() ? sharedContext.selfCollisionEnabledFilter : sharedContext.selfCollisionDisabledFilter;
        tempAllocator = sharedContext.tempAllocator;
        jobSystem = sharedContext.jobSystem;
        physicsSystem = sharedContext.physicsSystem;
        bodyInterface = sharedContext.bodyInterface;
        this.level = level;
        localOrigin = terrain.origin();
        collisionGroupId = sharedContext.allocateCollisionGroupId();
        terrainBoxes = terrain.boxes();
        terrainBoxCount = terrain.boxes().size();
        terrainScanCenter = terrain.scanCenter();
        terrainUsesFallback = terrain.usedFallback();
        if (!terrain.boxes().isEmpty()) {
            terrainBodyId = createTerrainBody(terrain.boxes());
        }
        spawnX = worldX - localOrigin.getX();
        spawnGroundY = worldY - localOrigin.getY();
        spawnZ = worldZ - localOrigin.getZ();
        double safeRequestedScale = safeModelScale(requestedModelScale);
        modelScale = safeRequestedScale;
        double modelMassScale = modelScale * modelScale * modelScale;
        massScale = (float) Math.max(0.35, modelMassScale);
        spawnYawRadians = initialLimbPose == null
                ? (float) Math.toRadians(yaw)
                : initialLimbPose.rendererBodyYaw();
        // Match LivingEntityRenderer: vanilla player models are rendered at
        // 180 degrees minus the entity body yaw. Using yaw directly (or
        // yaw + 180) only happens to work for some cardinal directions.
        Quat modelYawRotation = Quat.sRotation(new Vec3(0.0f, 1.0f, 0.0f), (float) Math.PI - spawnYawRadians);
        float torsoPitch = switch(initialPose) {
            case PRONE ->
                (float) Math.toRadians(90.0f - clampPitch(xRot) * 0.45f);
            case ELYTRA ->
                (float) Math.toRadians(-90.0f - clampPitch(xRot));
            case CRAWLING -> (float) (-Math.PI / 2.0);
            case SWIMMING -> (float) Math.toRadians(-90.0f - clampPitch(xRot));
            case CROUCHING ->
                0.45f;
            case STANDING ->
                0.0f;
        };
        Quat defaultTorsoRotation = multiply(modelYawRotation, rotationX(torsoPitch));
        Quat rendererRootRotation = initialLimbPose == null
                ? modelYawRotation
                : multiply(multiply(modelYawRotation,
                        rotationX(initialLimbPose.rendererRootPitch())),
                        rotationY(initialLimbPose.rendererRootYaw()));
        spawnRotation = initialLimbPose == null
                ? defaultTorsoRotation
                : limbRotation(rendererRootRotation, defaultTorsoRotation,
                        initialLimbPose, "body");
        double crouch = initialPose == RagdollInitialPose.CROUCHING ? 1.0 : 0.0;
        RagdollInitialLimbPose.PartPose bodyPose = initialLimbPose == null
                ? new RagdollInitialLimbPose.PartPose(0, (float) (crouch * 3.2), 0, 0, 0, 0)
                : initialLimbPose.body();
        double[] bodyPivotDelta = modelPivotDelta(bodyPose,
                0.0, crouch * 3.2, 0.0);
        double[] bodyPivotWorldOffset = rotate(rendererRootRotation,
                bodyPivotDelta[0] * modelScale,
                bodyPivotDelta[1] * modelScale,
                bodyPivotDelta[2] * modelScale);
        torsoCenterX = bodyPivotWorldOffset[0];
        torsoCenterY = torsoCenterY(initialPose) * modelScale + bodyPivotWorldOffset[1];
        torsoCenterZ = bodyPivotWorldOffset[2];
        Quat headRotation = initialLimbPose != null
                ? limbRotation(rendererRootRotation, spawnRotation,
                        initialLimbPose, "head")
                : initialPose == RagdollInitialPose.PRONE ? spawnRotation
                : multiply(modelYawRotation, rotationX(modelPitchRadians(xRot)));
        Quat fallbackArmRotation = initialPose == RagdollInitialPose.STANDING
                ? modelYawRotation : spawnRotation;
        Quat leftArmRotation = limbRotation(rendererRootRotation,
                fallbackArmRotation, initialLimbPose, "left_arm");
        Quat rightArmRotation = limbRotation(rendererRootRotation,
                fallbackArmRotation, initialLimbPose, "right_arm");
        Quat fallbackLegRotation = initialPose == RagdollInitialPose.CROUCHING
                ? multiply(modelYawRotation, rotationX(-0.2f)) : spawnRotation;
        Quat leftLegRotation = limbRotation(rendererRootRotation,
                fallbackLegRotation, initialLimbPose, "left_leg");
        Quat rightLegRotation = limbRotation(rendererRootRotation,
                fallbackLegRotation, initialLimbPose, "right_leg");
        RagdollInitialLimbPose.PartPose headPose = initialLimbPose == null
                ? new RagdollInitialLimbPose.PartPose(0, (float) (crouch * 4.2), 0, 0, 0, 0)
                : initialLimbPose.head();
        RagdollInitialLimbPose.PartPose leftArmPose = initialLimbPose == null
                ? new RagdollInitialLimbPose.PartPose(5, (float) (2 + crouch * 3.2), 0, 0, 0, 0)
                : initialLimbPose.leftArm();
        RagdollInitialLimbPose.PartPose rightArmPose = initialLimbPose == null
                ? new RagdollInitialLimbPose.PartPose(-5, (float) (2 + crouch * 3.2), 0, 0, 0, 0)
                : initialLimbPose.rightArm();
        RagdollInitialLimbPose.PartPose leftLegPose = initialLimbPose == null
                ? new RagdollInitialLimbPose.PartPose(1.9f,
                        (float) (12 + crouch * 0.2), (float) (crouch * 4), 0, 0, 0)
                : initialLimbPose.leftLeg();
        RagdollInitialLimbPose.PartPose rightLegPose = initialLimbPose == null
                ? new RagdollInitialLimbPose.PartPose(-1.9f,
                        (float) (12 + crouch * 0.2), (float) (crouch * 4), 0, 0, 0)
                : initialLimbPose.rightLeg();
        double[] headPivotDelta = modelPivotDelta(headPose, 0.0, crouch * 4.2, 0.0);
        double[] leftArmPivotDelta = modelPivotDelta(leftArmPose,
                5.0, 2.0 + crouch * 3.2, 0.0);
        double[] rightArmPivotDelta = modelPivotDelta(rightArmPose,
                -5.0, 2.0 + crouch * 3.2, 0.0);
        double[] leftLegPivotDelta = modelPivotDelta(leftLegPose,
                1.9, 12.0 + crouch * 0.2, crouch * 4.0);
        double[] rightLegPivotDelta = modelPivotDelta(rightLegPose,
                -1.9, 12.0 + crouch * 0.2, crouch * 4.0);
        double headTorsoAttachX = headPivotDelta[0] - bodyPivotDelta[0];
        // Sable anchors converted from block centers to visible model centers.
        // Its torso model is 0.03125 above the block center; Jolt's X axis is reversed.
        double headTorsoAttachY = 0.29875 + headPivotDelta[1] - bodyPivotDelta[1];
        double headPartAttachY = -0.27;
        double headPartAttachX = 0.0;
        double headTorsoAttachZ = headPivotDelta[2] - bodyPivotDelta[2];
        double headPartAttachZ = 0.0;
        double leftArmTorsoAttachY = 0.26875 + leftArmPivotDelta[1] - bodyPivotDelta[1];
        double rightArmTorsoAttachY = 0.26875 + rightArmPivotDelta[1] - bodyPivotDelta[1];
        double armPartAttachY = 0.3;
        double leftArmPartAttachX = -0.0025;
        double rightArmPartAttachX = -0.0025;
        double leftLegTorsoAttachY = -0.43125 + leftLegPivotDelta[1] - bodyPivotDelta[1];
        double rightLegTorsoAttachY = -0.43125 + rightLegPivotDelta[1] - bodyPivotDelta[1];
        double legPartAttachY = 0.3;
        double leftArmTorsoAttachX = -0.36 + leftArmPivotDelta[0] - bodyPivotDelta[0];
        double rightArmTorsoAttachX = 0.36 + rightArmPivotDelta[0] - bodyPivotDelta[0];
        double leftLegTorsoAttachX = -0.06 + leftLegPivotDelta[0] - bodyPivotDelta[0];
        double rightLegTorsoAttachX = 0.06 + rightLegPivotDelta[0] - bodyPivotDelta[0];
        double leftLegPartAttachX = 0.0575;
        double rightLegPartAttachX = -0.0625;
        double leftArmTorsoAttachZ = leftArmPivotDelta[2] - bodyPivotDelta[2];
        double rightArmTorsoAttachZ = rightArmPivotDelta[2] - bodyPivotDelta[2];
        double leftLegTorsoAttachZ = leftLegPivotDelta[2] - bodyPivotDelta[2];
        double rightLegTorsoAttachZ = rightLegPivotDelta[2] - bodyPivotDelta[2];
        double leftArmPartAttachZ = 0.0;
        double rightArmPartAttachZ = 0.0;
        double leftLegPartAttachZ = 0.0;
        double rightLegPartAttachZ = 0.0;
        JointProfile neckProfile = JointProfile.NECK;
        JointProfile shoulderProfile = JointProfile.SHOULDER;
        JointProfile hipProfile = JointProfile.HIP;
        torso = createPart("body", torsoCenterX, torsoCenterY, torsoCenterZ, spawnRotation);
        RagdollPart head = createAttachedPart("head", headTorsoAttachX, headTorsoAttachY, headTorsoAttachZ, headPartAttachX, headPartAttachY, headPartAttachZ, rendererRootRotation, headRotation);
        RagdollPart leftArm = createAttachedPart("left_arm", leftArmTorsoAttachX, leftArmTorsoAttachY, leftArmTorsoAttachZ, leftArmPartAttachX, armPartAttachY, leftArmPartAttachZ, rendererRootRotation, leftArmRotation);
        RagdollPart rightArm = createAttachedPart("right_arm", rightArmTorsoAttachX, rightArmTorsoAttachY, rightArmTorsoAttachZ, rightArmPartAttachX, armPartAttachY, rightArmPartAttachZ, rendererRootRotation, rightArmRotation);
        RagdollPart leftLeg = createAttachedPart("left_leg", leftLegTorsoAttachX, leftLegTorsoAttachY, leftLegTorsoAttachZ, leftLegPartAttachX, legPartAttachY, leftLegPartAttachZ, rendererRootRotation, leftLegRotation);
        RagdollPart rightLeg = createAttachedPart("right_leg", rightLegTorsoAttachX, rightLegTorsoAttachY, rightLegTorsoAttachZ, rightLegPartAttachX, legPartAttachY, rightLegPartAttachZ, rendererRootRotation, rightLegRotation);
        applyInitialLooseMotion(torso, head, leftArm, rightArm, leftLeg, rightLeg, velocityX, velocityY, velocityZ);
        createSixDofJoint(torso, head, headTorsoAttachX, headTorsoAttachY, headTorsoAttachZ, rendererRootRotation, headRotation, neckProfile);
        createSixDofJoint(torso, leftArm, leftArmTorsoAttachX, leftArmTorsoAttachY, leftArmTorsoAttachZ, rendererRootRotation, leftArmRotation, shoulderProfile);
        createSixDofJoint(torso, rightArm, rightArmTorsoAttachX, rightArmTorsoAttachY, rightArmTorsoAttachZ, rendererRootRotation, rightArmRotation, shoulderProfile);
        createSixDofJoint(torso, leftLeg, leftLegTorsoAttachX, leftLegTorsoAttachY, leftLegTorsoAttachZ, rendererRootRotation, leftLegRotation, hipProfile);
        createSixDofJoint(torso, rightLeg, rightLegTorsoAttachX, rightLegTorsoAttachY, rightLegTorsoAttachZ, rendererRootRotation, rightLegRotation, hipProfile);
        physicsSystem.optimizeBroadPhase();
        updatePartTransforms(true);
        renderParts = parts.stream().map(part -> part.renderState).toList();
    }

    private double torsoCenterY(RagdollInitialPose initialPose) {
        return switch(initialPose) {
            case PRONE ->
                0.35;
            case ELYTRA ->
                0.35;
            case CRAWLING -> 0.35;
            case SWIMMING -> 0.35;
            case CROUCHING ->
                0.82;
            case STANDING ->
                1.08125;
        };
    }

    private RagdollConfig.CollisionBox collisionBox(String partName) {
        return RagdollConfig.collisionBox(partName);
    }

    void refreshCollisionShape(String name) {
        if (closed) {
            return;
        }
        for (RagdollPart part : parts) {
            if (part.name.equals(name)) {
                part.refreshCollisionShape();
                return;
            }
        }
    }

    private void applyInitialLooseMotion(RagdollPart torsoPart, RagdollPart head, RagdollPart leftArm, RagdollPart rightArm, RagdollPart leftLeg, RagdollPart rightLeg, float baseVelocityX, float baseVelocityY, float baseVelocityZ) {
        float velocityX = baseVelocityX * INITIAL_VELOCITY_SCALE;
        float velocityY = baseVelocityY * INITIAL_VELOCITY_SCALE;
        float velocityZ = baseVelocityZ * INITIAL_VELOCITY_SCALE;
        // Give every part the same initial translation so the joints do not
        // have to accelerate stationary limbs to catch up with the torso.
        for (RagdollPart part : List.of(torsoPart, head, leftArm, rightArm, leftLeg, rightLeg)) {
            bodyInterface.setLinearVelocity(part.bodyId, velocityX, velocityY, velocityZ);
        }
    }

    static JoltPhysicsWorld createForDeath(Level level, BlockPos origin, double worldX, double worldY, double worldZ, float yaw, RagdollInitialPose initialPose, float xRot, float velocityX, float velocityY, float velocityZ, float modelScale, RagdollPhysicsSettings physicsSettings) {
        return createForDeath(level, origin, worldX, worldY, worldZ, yaw, initialPose, xRot,
                velocityX, velocityY, velocityZ, modelScale, physicsSettings, null);
    }

    static JoltPhysicsWorld createForDeath(Level level, BlockPos origin, double worldX, double worldY, double worldZ, float yaw, RagdollInitialPose initialPose, float xRot, float velocityX, float velocityY, float velocityZ, float modelScale, RagdollPhysicsSettings physicsSettings, RagdollInitialLimbPose initialLimbPose) {
        SharedPhysicsContext sharedContext = acquireSharedContext(level);
        TerrainSnapshot terrain = captureTerrain(level, origin, sharedContext.origin);
        JoltPhysicsWorld world = new JoltPhysicsWorld(sharedContext, level, terrain, worldX, worldY, worldZ, yaw, initialPose, xRot, velocityX, velocityY, velocityZ, modelScale, physicsSettings, initialLimbPose);
        String terrainBounds = world.describeTerrainBounds();
        RagdollMod.LOGGER.info("Created death ragdoll: dimension={}, origin={}, parts={}, " + "joints={}, collisionBoxes={}, terrainLocalBounds={}", level.dimension().location(), origin, world.parts.size(), world.constraints.size(), world.terrainBoxCount, terrainBounds);
        return world;
    }

    static boolean isTerrainRegionLoaded(Level level, BlockPos origin) {
        int minChunkX = (origin.getX() - TERRAIN_HORIZONTAL_RADIUS) >> 4;
        int maxChunkX = (origin.getX() + TERRAIN_HORIZONTAL_RADIUS) >> 4;
        int minChunkZ = (origin.getZ() - TERRAIN_HORIZONTAL_RADIUS) >> 4;
        int maxChunkZ = (origin.getZ() + TERRAIN_HORIZONTAL_RADIUS) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; ++chunkX) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; ++chunkZ) {
                if (!level.getChunkSource().hasChunk(chunkX, chunkZ)) {
                    return false;
                }
            }
        }
        return true;
    }

    boolean isSimulationRegionLoaded() {
        return isTerrainRegionLoaded(level, BlockPos.containing(torso.worldX, torso.worldY, torso.worldZ));
    }

    void refreshTerrainBeforeResume() {
        if (!isSimulationRegionLoaded()) {
            return;
        }
        terrainDirty = true;
        refreshTerrain();
    }

    void markTerrainDirty(BlockPos blockPos) {
        if (closed || terrainScanCenter == null) {
            return;
        }
        int blocksBelow = terrainUsesFallback ? TERRAIN_FALLBACK_BLOCKS_BELOW : TERRAIN_BLOCKS_BELOW;
        if (Math.abs(blockPos.getX() - terrainScanCenter.getX()) <= TERRAIN_HORIZONTAL_RADIUS && Math.abs(blockPos.getZ() - terrainScanCenter.getZ()) <= TERRAIN_HORIZONTAL_RADIUS && blockPos.getY() >= terrainScanCenter.getY() - blocksBelow && blockPos.getY() <= terrainScanCenter.getY() + TERRAIN_BLOCKS_ABOVE) {
            terrainDirty = true;
        }
    }

    boolean hasActiveBodies() {
        for (RagdollPart part : parts) {
            if (bodyInterface.isActive(part.bodyId)) {
                return true;
            }
        }
        return false;
    }

    boolean pushFromPlayer(AABB playerBounds, double velocityX, double velocityY, double velocityZ, double movementX, double movementZ) {
        double horizontalSpeed = Math.max(Math.sqrt(velocityX * velocityX + velocityZ * velocityZ), Math.sqrt(movementX * movementX + movementZ * movementZ));
        if (horizontalSpeed <= 0.01) {
            return false;
        }
        double directionX = Math.abs(velocityX) + Math.abs(velocityZ) >= Math.abs(movementX) + Math.abs(movementZ) ? velocityX : movementX;
        double directionZ = Math.abs(velocityX) + Math.abs(velocityZ) >= Math.abs(movementX) + Math.abs(movementZ) ? velocityZ : movementZ;
        double directionLength = Math.sqrt(directionX * directionX + directionZ * directionZ);
        directionX /= directionLength;
        directionZ /= directionLength;
        PushBody playerBody = PushBody.player(playerBounds);
        List<PushContact> contacts = new ArrayList<>();
        for (RagdollPart part : parts) {
            double[] center = collisionCenter(part);
            PushBody partBody = new PushBody(center[0], center[1], center[2], part.rotationX, part.rotationY, part.rotationZ, part.rotationW, part.halfX, part.halfY, part.halfZ);
            PushContact contact = pushContact(playerBody, partBody);
            if (contact != null) {
                contacts.add(new PushContact(part, contact.normal, contact.point, contact.penetration));
            }
        }
        if (contacts.isEmpty()) {
            return false;
        }
        contacts.sort((first, second) -> Double.compare(second.penetration, first.penetration));
        double remainingBudget = PLAYER_PUSH_IMPULSE_BUDGET * PLAYER_PUSH_STRENGTH;
        boolean pushed = false;
        for (PushContact contact : contacts) {
            if (remainingBudget <= 1.0e-6) {
                break;
            }
            RagdollPart part = contact.part;
            Vec3 bodyVelocity = velocityAtPoint(part, contact.point);
            Vec3 relativeVelocity = new Vec3(bodyVelocity.getX() - (float) velocityX, bodyVelocity.getY() - (float) velocityY, bodyVelocity.getZ() - (float) velocityZ);
            Vec3 normal = contact.normal;
            double normalSpeed = relativeVelocity.getX() * normal.getX() + relativeVelocity.getY() * normal.getY() + relativeVelocity.getZ() * normal.getZ();
            double desiredSpeed = Math.min(PLAYER_PUSH_MAX_BIAS_SPEED * PLAYER_PUSH_STRENGTH, (contact.penetration * PLAYER_PUSH_BIAS_PER_DEPTH + horizontalSpeed * PLAYER_PUSH_SPEED_TRANSFER) * PLAYER_PUSH_STRENGTH);
            if (normalSpeed >= desiredSpeed) {
                continue;
            }
            double impulseMagnitude = Math.min(PLAYER_PUSH_MAX_IMPULSE * PLAYER_PUSH_STRENGTH, Math.max(0.0, desiredSpeed - normalSpeed) * partMass(part.name));
            double impulseX = normal.getX() * impulseMagnitude;
            double impulseY = normal.getY() * impulseMagnitude;
            double impulseZ = normal.getZ() * impulseMagnitude;
            double tangentX = relativeVelocity.getX() - normal.getX() * normalSpeed;
            double tangentY = relativeVelocity.getY() - normal.getY() * normalSpeed;
            double tangentZ = relativeVelocity.getZ() - normal.getZ() * normalSpeed;
            double tangentSpeed = Math.sqrt(tangentX * tangentX + tangentY * tangentY + tangentZ * tangentZ);
            if (tangentSpeed > 1.0e-6 && impulseMagnitude > 0.0) {
                double friction = Math.min(tangentSpeed * partMass(part.name), impulseMagnitude * PLAYER_PUSH_TANGENT_FRICTION) / tangentSpeed;
                impulseX -= tangentX * friction;
                impulseY -= tangentY * friction;
                impulseZ -= tangentZ * friction;
            }
            double along = impulseX * directionX + impulseZ * directionZ;
            if (along <= 1.0e-6) {
                continue;
            }
            impulseX = directionX * along;
            impulseY *= PLAYER_PUSH_VERTICAL_SCALE;
            impulseZ = directionZ * along;
            double length = Math.sqrt(impulseX * impulseX + impulseY * impulseY + impulseZ * impulseZ);
            if (length > remainingBudget) {
                double scale = remainingBudget / length;
                impulseX *= scale;
                impulseY *= scale;
                impulseZ *= scale;
                length = remainingBudget;
            }
            activateAllParts();
            bodyInterface.addImpulse(part.bodyId, new Vec3((float) impulseX, (float) impulseY, (float) impulseZ));
            remainingBudget -= length;
            pushed = true;
        }
        if (pushed) {
            settledTicks = 0;
        }
        return pushed;
    }

    private Vec3 velocityAtPoint(RagdollPart part, Vec3 worldPoint) {
        Vec3 linear = bodyInterface.getLinearVelocity(part.bodyId);
        Vec3 angular = bodyInterface.getAngularVelocity(part.bodyId);
        RVec3 center = bodyInterface.getCenterOfMassPosition(part.bodyId);
        double radiusX = worldPoint.getX() - (center.xx() + localOrigin.getX());
        double radiusY = worldPoint.getY() - (center.yy() + localOrigin.getY());
        double radiusZ = worldPoint.getZ() - (center.zz() + localOrigin.getZ());
        return new Vec3(linear.getX() + (float) (angular.getY() * radiusZ - angular.getZ() * radiusY), linear.getY() + (float) (angular.getZ() * radiusX - angular.getX() * radiusZ), linear.getZ() + (float) (angular.getX() * radiusY - angular.getY() * radiusX));
    }

    private static PushContact pushContact(PushBody first, PushBody second) {
        Vec3 centerDelta = new Vec3((float) (second.centerX - first.centerX), (float) (second.centerY - first.centerY), (float) (second.centerZ - first.centerZ));
        Vec3[] firstAxes = pushAxes(first);
        Vec3[] secondAxes = pushAxes(second);
        double[] firstExtents = { first.halfX, first.halfY, first.halfZ };
        double[] secondExtents = { second.halfX, second.halfY, second.halfZ };
        PushAxis best = null;
        for (Vec3 axis : firstAxes) {
            best = testPushAxis(centerDelta, axis, firstAxes, firstExtents, secondAxes, secondExtents, best);
            if (best == PushAxis.SEPARATED) {
                return null;
            }
        }
        for (Vec3 axis : secondAxes) {
            best = testPushAxis(centerDelta, axis, firstAxes, firstExtents, secondAxes, secondExtents, best);
            if (best == PushAxis.SEPARATED) {
                return null;
            }
        }
        for (Vec3 firstAxis : firstAxes) {
            for (Vec3 secondAxis : secondAxes) {
                Vec3 axis = firstAxis.cross(secondAxis);
                if (axis.lengthSq() <= PLAYER_PUSH_AXIS_EPSILON) {
                    continue;
                }
                axis = axis.normalized();
                best = testPushAxis(centerDelta, axis, firstAxes, firstExtents, secondAxes, secondExtents, best);
                if (best == PushAxis.SEPARATED) {
                    return null;
                }
            }
        }
        if (best == null || best == PushAxis.SEPARATED) {
            return null;
        }
        Vec3 normal = best.axis;
        if (centerDelta.dot(normal) < 0.0f) {
            normal = new Vec3(-normal.getX(), -normal.getY(), -normal.getZ());
        }
        double firstProjection = projectedPushRadius(normal, firstAxes, firstExtents);
        double secondProjection = projectedPushRadius(normal, secondAxes, secondExtents);
        Vec3 firstSupport = new Vec3((float) (first.centerX + normal.getX() * firstProjection), (float) (first.centerY + normal.getY() * firstProjection), (float) (first.centerZ + normal.getZ() * firstProjection));
        Vec3 secondSupport = new Vec3((float) (second.centerX - normal.getX() * secondProjection), (float) (second.centerY - normal.getY() * secondProjection), (float) (second.centerZ - normal.getZ() * secondProjection));
        Vec3 point = new Vec3((firstSupport.getX() + secondSupport.getX()) * 0.5f, (firstSupport.getY() + secondSupport.getY()) * 0.5f, (firstSupport.getZ() + secondSupport.getZ()) * 0.5f);
        return new PushContact(null, normal, point, best.penetration);
    }

    private static PushAxis testPushAxis(Vec3 centerDelta, Vec3 axis, Vec3[] firstAxes, double[] firstExtents, Vec3[] secondAxes, double[] secondExtents, PushAxis currentBest) {
        double overlap = projectedPushRadius(axis, firstAxes, firstExtents) + projectedPushRadius(axis, secondAxes, secondExtents) - Math.abs(centerDelta.dot(axis));
        if (overlap < 0.0) {
            return PushAxis.SEPARATED;
        }
        return currentBest == null || overlap < currentBest.penetration ? new PushAxis(axis, overlap) : currentBest;
    }

    private static double projectedPushRadius(Vec3 axis, Vec3[] boxAxes, double[] extents) {
        return extents[0] * Math.abs(axis.dot(boxAxes[0])) + extents[1] * Math.abs(axis.dot(boxAxes[1])) + extents[2] * Math.abs(axis.dot(boxAxes[2]));
    }

    private static Vec3[] pushAxes(PushBody body) {
        return new Vec3[] { rotatePushAxis(1.0f, 0.0f, 0.0f, body), rotatePushAxis(0.0f, 1.0f, 0.0f, body), rotatePushAxis(0.0f, 0.0f, 1.0f, body) };
    }

    private static Vec3 rotatePushAxis(float x, float y, float z, PushBody body) {
        double[] result = rotate(x, y, z, body.rotationX, body.rotationY, body.rotationZ, body.rotationW);
        return new Vec3((float) result[0], (float) result[1], (float) result[2]);
    }

    GrabHit findGrabTarget(double eyeX, double eyeY, double eyeZ, double directionX, double directionY, double directionZ, double reach) {
        net.minecraft.world.phys.Vec3 start = new net.minecraft.world.phys.Vec3(eyeX, eyeY, eyeZ);
        net.minecraft.world.phys.Vec3 end = start.add(directionX * reach, directionY * reach, directionZ * reach);
        GrabHit closest = null;
        for (RagdollPart part : parts) {
            double[] radii = axisAlignedRadii(part);
            AABB bounds = new AABB(part.worldX - radii[0], part.worldY - radii[1], part.worldZ - radii[2], part.worldX + radii[0], part.worldY + radii[1], part.worldZ + radii[2]);
            var hit = bounds.clip(start, end);
            if (hit.isEmpty()) {
                continue;
            }
            double distance = start.distanceTo(hit.get());
            if (closest == null || distance < closest.distance) {
                net.minecraft.world.phys.Vec3 location = hit.get();
                closest = new GrabHit(part, distance, location.x, location.y, location.z);
            }
        }
        return closest;
    }

    PhysicsGunHit createPhysicsGunHit(GrabHit hit) {
        if (hit == null || !parts.contains(hit.part)) {
            return null;
        }
        RagdollPart part = hit.part;
        // Velthoric makes a frozen physics-gun body dynamic again when the
        // next grab ray hits it. This provides the unlock path after a
        // right-click freeze.
        if (part.frozen) {
            setPartFrozen(part, false);
        }
        if (bodyInterface.getMotionType(part.bodyId)
                != EMotionType.Dynamic) {
            return null;
        }
        double[] local = rotate(
                hit.hitX - part.worldX,
                hit.hitY - part.worldY,
                hit.hitZ - part.worldZ,
                -part.rotationX,
                -part.rotationY,
                -part.rotationZ,
                part.rotationW);
        return new PhysicsGunHit(
                part.name,
                hit.distance,
                local[0],
                local[1],
                local[2],
                part.rotationX,
                part.rotationY,
                part.rotationZ,
                part.rotationW);
    }

    Quaternionf physicsGunPartRotation(String partName) {
        RagdollPart part = findPart(partName);
        if (part == null || part.frozen
                || bodyInterface.getMotionType(part.bodyId)
                != EMotionType.Dynamic) {
            return null;
        }
        Quat rotation = bodyInterface.getRotation(part.bodyId);
        return new Quaternionf(
                rotation.getX(),
                rotation.getY(),
                rotation.getZ(),
                rotation.getW()).normalize();
    }

    void beginPhysicsGunTargetUpdate() {
        for (PhysicsGunControl control : physicsGunControls.values()) {
            control.updated = false;
        }
    }

    void finishPhysicsGunTargetUpdate() {
        physicsGunControls.values().removeIf(control -> !control.updated);
    }

    boolean setPhysicsGunTarget(
            UUID controllerId,
            String partName,
            double localX,
            double localY,
            double localZ,
            double targetX,
            double targetY,
            double targetZ,
            float targetRotationX,
            float targetRotationY,
            float targetRotationZ,
            float targetRotationW,
            boolean rotationMode) {
        RagdollPart part = findPart(partName);
        if (part == null || part.frozen
                || bodyInterface.getMotionType(part.bodyId)
                != EMotionType.Dynamic) {
            return false;
        }

        PhysicsGunControl control = physicsGunControls.get(controllerId);
        if (control == null || !control.partName.equals(partName)) {
            control = new PhysicsGunControl(
                    partName,
                    localX,
                    localY,
                    localZ,
                    targetX,
                    targetY,
                    targetZ);
            physicsGunControls.put(controllerId, control);
        }
        control.targetX = targetX;
        control.targetY = targetY;
        control.targetZ = targetZ;
        control.targetRotationX = targetRotationX;
        control.targetRotationY = targetRotationY;
        control.targetRotationZ = targetRotationZ;
        control.targetRotationW = targetRotationW;
        if (rotationMode && !control.rotationMode) {
            Quat currentRotation =
                    bodyInterface.getRotation(part.bodyId);
            control.smoothedRotationX = currentRotation.getX();
            control.smoothedRotationY = currentRotation.getY();
            control.smoothedRotationZ = currentRotation.getZ();
            control.smoothedRotationW = currentRotation.getW();
        }
        control.rotationMode = rotationMode;
        control.updated = true;
        return true;
    }

    private void applyPhysicsGunSubstep() {
        for (PhysicsGunControl control : physicsGunControls.values()) {
            applyPhysicsGunControl(control);
        }
    }

    private void applyPhysicsGunControl(PhysicsGunControl control) {
        RagdollPart part = findPart(control.partName);
        if (part == null || part.frozen
                || bodyInterface.getMotionType(part.bodyId)
                != EMotionType.Dynamic) {
            return;
        }

        control.smoothedTargetX +=
                (control.targetX - control.smoothedTargetX)
                        * PHYSICS_GUN_TARGET_SMOOTHING;
        control.smoothedTargetY +=
                (control.targetY - control.smoothedTargetY)
                        * PHYSICS_GUN_TARGET_SMOOTHING;
        control.smoothedTargetZ +=
                (control.targetZ - control.smoothedTargetZ)
                        * PHYSICS_GUN_TARGET_SMOOTHING;

        RVec3 bodyPosition = new RVec3();
        Quat bodyRotation = new Quat();
        bodyInterface.getPositionAndRotation(
                part.bodyId, bodyPosition, bodyRotation);
        double[] anchorOffset = rotate(
                control.localX, control.localY, control.localZ,
                bodyRotation.getX(),
                bodyRotation.getY(),
                bodyRotation.getZ(),
                bodyRotation.getW());
        double currentX = bodyPosition.xx() + localOrigin.getX()
                + anchorOffset[0];
        double currentY = bodyPosition.yy() + localOrigin.getY()
                + anchorOffset[1];
        double currentZ = bodyPosition.zz() + localOrigin.getZ()
                + anchorOffset[2];
        Vec3 linearVelocity =
                bodyInterface.getLinearVelocity(part.bodyId);
        Vec3 angularVelocity =
                bodyInterface.getAngularVelocity(part.bodyId);
        double accelerationX =
                (control.smoothedTargetX - currentX)
                        * PHYSICS_GUN_LINEAR_STIFFNESS
                        - linearVelocity.getX()
                        * PHYSICS_GUN_LINEAR_DAMPING;
        double accelerationY =
                (control.smoothedTargetY - currentY)
                        * PHYSICS_GUN_LINEAR_STIFFNESS
                        - linearVelocity.getY()
                        * PHYSICS_GUN_LINEAR_DAMPING;
        double accelerationZ =
                (control.smoothedTargetZ - currentZ)
                        * PHYSICS_GUN_LINEAR_STIFFNESS
                        - linearVelocity.getZ()
                        * PHYSICS_GUN_LINEAR_DAMPING;
        double accelerationLength = Math.sqrt(
                accelerationX * accelerationX
                        + accelerationY * accelerationY
                        + accelerationZ * accelerationZ);
        if (accelerationLength > PHYSICS_GUN_MAX_ACCELERATION) {
            double scale =
                    PHYSICS_GUN_MAX_ACCELERATION / accelerationLength;
            accelerationX *= scale;
            accelerationY *= scale;
            accelerationZ *= scale;
        }
        float mass = partMass(part.name);
        Vec3 force = new Vec3(
                (float) (accelerationX * mass),
                (float) (accelerationY * mass),
                (float) (accelerationZ * mass));
        // Velthoric calculates the error at the selected local point but
        // applies the resulting force at the center of mass. This avoids
        // creating an additional alternating torque at an arm or leg.
        bodyInterface.addForce(part.bodyId, force);

        if (control.rotationMode) {
            Quaternionf targetRotation = new Quaternionf(
                    control.targetRotationX,
                    control.targetRotationY,
                    control.targetRotationZ,
                    control.targetRotationW).normalize();
            Quaternionf smoothedRotation = new Quaternionf(
                    control.smoothedRotationX,
                    control.smoothedRotationY,
                    control.smoothedRotationZ,
                    control.smoothedRotationW)
                    .normalize()
                    .slerp(
                            targetRotation,
                            PHYSICS_GUN_ROTATION_SMOOTHING)
                    .normalize();
            control.smoothedRotationX = smoothedRotation.x;
            control.smoothedRotationY = smoothedRotation.y;
            control.smoothedRotationZ = smoothedRotation.z;
            control.smoothedRotationW = smoothedRotation.w;
            Quaternionf currentRotation = new Quaternionf(
                    bodyRotation.getX(),
                    bodyRotation.getY(),
                    bodyRotation.getZ(),
                    bodyRotation.getW()).normalize();
            Quaternionf error = new Quaternionf(smoothedRotation)
                    .mul(new Quaternionf(currentRotation).conjugate())
                    .normalize();
            if (error.w < 0.0f) {
                error.set(-error.x, -error.y, -error.z, -error.w);
            }
            double angularAccelerationX =
                    error.x * PHYSICS_GUN_ANGULAR_STIFFNESS
                            - angularVelocity.getX()
                            * PHYSICS_GUN_ANGULAR_DAMPING;
            double angularAccelerationY =
                    error.y * PHYSICS_GUN_ANGULAR_STIFFNESS
                            - angularVelocity.getY()
                            * PHYSICS_GUN_ANGULAR_DAMPING;
            double angularAccelerationZ =
                    error.z * PHYSICS_GUN_ANGULAR_STIFFNESS
                            - angularVelocity.getZ()
                            * PHYSICS_GUN_ANGULAR_DAMPING;
            double angularAccelerationLength = Math.sqrt(
                    angularAccelerationX * angularAccelerationX
                            + angularAccelerationY * angularAccelerationY
                            + angularAccelerationZ * angularAccelerationZ);
            if (angularAccelerationLength
                    > PHYSICS_GUN_MAX_ANGULAR_ACCELERATION) {
                double scale = PHYSICS_GUN_MAX_ANGULAR_ACCELERATION
                        / angularAccelerationLength;
                angularAccelerationX *= scale;
                angularAccelerationY *= scale;
                angularAccelerationZ *= scale;
            }
            Vec3 desiredAngularAcceleration = new Vec3(
                    (float) angularAccelerationX,
                    (float) angularAccelerationY,
                    (float) angularAccelerationZ);
            try (Mat44 inverseInertia =
                         bodyInterface.getInverseInertia(part.bodyId);
                 Mat44 inertia = inverseInertia.inversed3x3()) {
                bodyInterface.addTorque(
                        part.bodyId,
                        inertia.multiply3x3(
                                desiredAngularAcceleration));
            }
        }
        bodyInterface.activateBody(part.bodyId);
        settledTicks = 0;
    }

    boolean freezePhysicsGunPart(String partName) {
        RagdollPart part = findPart(partName);
        if (part == null) {
            return false;
        }
        setPartFrozen(part, true);
        return true;
    }

    boolean applyAttack(RagdollPart part, double hitX, double hitY, double hitZ, double directionX, double directionY, double directionZ) {
        if (!parts.contains(part)) {
            return false;
        }
        bodyInterface.addImpulse(part.bodyId, new Vec3((float) directionX * ATTACK_IMPULSE, (float) directionY * ATTACK_IMPULSE + ATTACK_UPWARD_IMPULSE, (float) directionZ * ATTACK_IMPULSE), new RVec3(hitX - localOrigin.getX(), hitY - localOrigin.getY(), hitZ - localOrigin.getZ()));
        bodyInterface.activateBody(part.bodyId);
        settledTicks = 0;
        return true;
    }

    boolean applyAttack(String partName, double hitX, double hitY, double hitZ,
                        double directionX, double directionY, double directionZ) {
        RagdollPart part = findPart(partName);
        return part != null && applyAttack(
                part, hitX, hitY, hitZ, directionX, directionY, directionZ);
    }

    boolean updateGrab(RagdollPart part, double grabX, double grabY, double grabZ, double targetX, double targetY, double targetZ) {
        if (!parts.contains(part)) {
            return false;
        }
        if (part.frozen) {
            return false;
        }
        boolean newGrab = grabConstraint == null || grabbedPart != part;
        if (newGrab) {
            releaseGrab();
            double[] localOffset = rotate(grabX - part.worldX, grabY - part.worldY, grabZ - part.worldZ, -part.rotationX, -part.rotationY, -part.rotationZ, part.rotationW);
            grabLocalX = localOffset[0];
            grabLocalY = localOffset[1];
            grabLocalZ = localOffset[2];
        }
        double[] worldOffset = rotate(grabLocalX, grabLocalY, grabLocalZ, part.rotationX, part.rotationY, part.rotationZ, part.rotationW);
        net.minecraft.world.phys.Vec3 grabPoint = new net.minecraft.world.phys.Vec3(part.worldX + worldOffset[0], part.worldY + worldOffset[1], part.worldZ + worldOffset[2]);
        net.minecraft.world.phys.Vec3 target = clampGrabTarget(grabPoint, new net.minecraft.world.phys.Vec3(targetX, targetY, targetZ));
        grabTargetX = target.x;
        grabTargetY = target.y;
        grabTargetZ = target.z;
        if (newGrab) {
            grabbedPart = part;
            ensureGrabTargetBody(grabPoint);
            try (PointConstraintSettings settings = new PointConstraintSettings()) {
                settings.setSpace(EConstraintSpace.WorldSpace);
                settings.setNumPositionStepsOverride(2);
                settings.setNumVelocityStepsOverride(1);
                settings.setPoint1(new RVec3(grabPoint.x - localOrigin.getX(), grabPoint.y - localOrigin.getY(), grabPoint.z - localOrigin.getZ()));
                settings.setPoint2(new RVec3(target.x - localOrigin.getX(), target.y - localOrigin.getY(), target.z - localOrigin.getZ()));
                grabConstraint = bodyInterface.createConstraint(settings, part.bodyId, grabTargetBodyId);
                physicsSystem.addConstraint(grabConstraint);
            }
        }
        applyGrabPhysics();
        activateAllParts();
        bodyInterface.activateBody(grabTargetBodyId);
        settledTicks = 0;
        return true;
    }

    private void setPartFrozen(RagdollPart part, boolean frozen) {
        if (part.frozen == frozen) {
            return;
        }
        if (grabbedPart == part) {
            releaseGrab();
        }
        part.frozen = frozen;
        bodyInterface.setMotionType(
                part.bodyId,
                frozen ? EMotionType.Kinematic : EMotionType.Dynamic,
                frozen ? EActivation.DontActivate : EActivation.Activate);
        bodyInterface.setLinearAndAngularVelocity(
                part.bodyId, new Vec3(), new Vec3());
        if (!frozen) {
            bodyInterface.activateBody(part.bodyId);
        }
        settledTicks = 0;
    }

    boolean updateGrab(String partName, double grabX, double grabY, double grabZ,
                       double targetX, double targetY, double targetZ) {
        RagdollPart part = findPart(partName);
        return part != null && updateGrab(
                part, grabX, grabY, grabZ, targetX, targetY, targetZ);
    }

    private boolean applyGrabPhysics() {
        if (grabConstraint == null || grabbedPart == null || grabTargetBodyId == NO_TERRAIN_BODY) {
            return false;
        }
        moveGrabTargetBody(new net.minecraft.world.phys.Vec3(grabTargetX, grabTargetY, grabTargetZ));
        activateAllParts();
        bodyInterface.activateBody(grabTargetBodyId);
        settledTicks = 0;
        return true;
    }

    private void ensureGrabTargetBody(net.minecraft.world.phys.Vec3 target) {
        if (grabTargetBodyId != NO_TERRAIN_BODY) {
            return;
        }
        if (grabTargetShape == null) {
            grabTargetShape = new BoxShape(new Vec3(GRAB_TARGET_HALF_EXTENT, GRAB_TARGET_HALF_EXTENT, GRAB_TARGET_HALF_EXTENT), 0.0f);
        }
        try (BodyCreationSettings settings = new BodyCreationSettings().setShape(grabTargetShape).setPosition(target.x - localOrigin.getX(), target.y - localOrigin.getY(), target.z - localOrigin.getZ()).setRotation(Quat.sIdentity()).setMotionType(EMotionType.Kinematic).setObjectLayer(OBJECT_LAYER_GHOST)) {
            grabTargetBodyId = bodyInterface.createAndAddBody(settings, EActivation.Activate);
        }
    }

    private void moveGrabTargetBody(net.minecraft.world.phys.Vec3 target) {
        RVec3 current = bodyInterface.getPosition(grabTargetBodyId);
        net.minecraft.world.phys.Vec3 currentWorld = new net.minecraft.world.phys.Vec3(current.xx() + localOrigin.getX(), current.yy() + localOrigin.getY(), current.zz() + localOrigin.getZ());
        net.minecraft.world.phys.Vec3 delta = target.subtract(currentWorld);
        double maximumStep = GRAB_TARGET_MAX_SPEED * FIXED_TIME_STEP;
        if (delta.lengthSqr() > maximumStep * maximumStep) {
            target = currentWorld.add(delta.scale(maximumStep / delta.length()));
        }
        bodyInterface.moveKinematic(grabTargetBodyId, new RVec3(target.x - localOrigin.getX(), target.y - localOrigin.getY(), target.z - localOrigin.getZ()), Quat.sIdentity(), FIXED_TIME_STEP);
    }

    void releaseGrab() {
        if (grabConstraint != null) {
            physicsSystem.removeConstraint(grabConstraint);
            grabConstraint.close();
            grabConstraint = null;
        }
        grabbedPart = null;
        grabLocalX = 0.0;
        grabLocalY = 0.0;
        grabLocalZ = 0.0;
        grabTargetX = 0.0;
        grabTargetY = 0.0;
        grabTargetZ = 0.0;
        if (grabTargetBodyId != NO_TERRAIN_BODY) {
            bodyInterface.removeBody(grabTargetBodyId);
            bodyInterface.destroyBody(grabTargetBodyId);
            grabTargetBodyId = NO_TERRAIN_BODY;
        }
    }

    private net.minecraft.world.phys.Vec3 clampGrabTarget(net.minecraft.world.phys.Vec3 grabPoint, net.minecraft.world.phys.Vec3 target) {
        BlockHitResult hit = level.clip(new ClipContext(grabPoint, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
        if (hit.getType() != HitResult.Type.BLOCK) {
            return target;
        }
        net.minecraft.world.phys.Vec3 delta = target.subtract(grabPoint);
        double length = delta.length();
        if (hit.isInside() || length <= 1.0e-6) {
            return hit.getLocation();
        }
        return hit.getLocation().subtract(delta.scale(Math.min(0.04, length) / length));
    }

    private RagdollPart createPart(String name, double relativeX, double relativeY, double relativeZ, Quat rotation) {
        RagdollConfig.CollisionBox box = collisionBox(name);
        double partScale = RagdollConfig.collisionScale(modelScale);
        float halfX = (float) (box.halfX() * partScale);
        float halfY = (float) (box.halfY() * partScale);
        float halfZ = (float) (box.halfZ() * partScale);
        float offsetX = (float) (box.offsetX() * partScale);
        float offsetY = (float) (box.offsetY() * partScale);
        float offsetZ = (float) (box.offsetZ() * partScale);
        float minimumHalfExtent = Math.min(halfX, Math.min(halfY, halfZ));
        float convexRadius = Math.min(0.05f, minimumHalfExtent * 0.5f);
        BoxShape innerShape = new BoxShape(new Vec3(halfX, halfY, halfZ), convexRadius);
        RotatedTranslatedShape shape = new RotatedTranslatedShape(new Vec3(offsetX, offsetY, offsetZ), Quat.sIdentity(), innerShape);
        double positionX = spawnX + relativeX;
        double positionY = spawnGroundY + relativeY;
        double positionZ = spawnZ + relativeZ;
        int bodyId;
        int subgroupId = collisionSubgroupId(name);
        int bodyCollisionGroupId = collisionGroupId;
        try (CollisionGroup collisionGroup = new CollisionGroup(selfCollisionFilter, bodyCollisionGroupId, subgroupId);
            MassProperties massProperties = new MassProperties().setMass(partMass(name));
            BodyCreationSettings settings = new BodyCreationSettings().setShape(shape).setPosition(positionX, positionY, positionZ).setRotation(rotation).setMotionType(EMotionType.Dynamic).setMotionQuality(EMotionQuality.LinearCast).setObjectLayer(OBJECT_LAYER_MOVING).setCollisionGroup(collisionGroup).setOverrideMassProperties(EOverrideMassProperties.CalculateInertia).setMassPropertiesOverride(massProperties).setFriction(DEFAULT_PART_FRICTION).setRestitution(0.0f).setLinearDamping(linearDamping(name)).setAngularDamping(angularDamping(name)).setEnhancedInternalEdgeRemoval(true)) {
            bodyId = bodyInterface.createAndAddBody(settings, EActivation.Activate);
        }
        RagdollPart part = new RagdollPart(name, innerShape, shape, bodyId, halfX, halfY, halfZ, offsetX, offsetY, offsetZ);
        parts.add(part);
        return part;
    }

    private int collisionSubgroupId(String name) {
        return switch (name) {
            case "head" -> SUBGROUP_HEAD;
            case "body" -> SUBGROUP_BODY;
            case "left_arm" -> SUBGROUP_LEFT_ARM;
            case "right_arm" -> SUBGROUP_RIGHT_ARM;
            case "left_leg" -> SUBGROUP_LEFT_LEG;
            case "right_leg" -> SUBGROUP_RIGHT_LEG;
            default -> throw new IllegalArgumentException("Unknown ragdoll part: " + name);
        };
    }

    /**
     * Original 64 kg profile: 32 + 8 + 4 + 4 + 8 + 8.
     */
    private float partMass(String name) {
        return basePartMass(name) * massScale;
    }

    private float linearDamping(String name) {
        return 0.25f;
    }

    private float angularDamping(String name) {
        return 1.2f;
    }

    private static float basePartMass(String name) {
        return switch(name) {
            case "body" ->
                TORSO_MASS;
            case "head" ->
                HEAD_MASS;
            case "left_arm", "right_arm" ->
                ARM_MASS;
            case "left_leg", "right_leg" ->
                LEG_MASS;
            default ->
                throw new IllegalArgumentException("Unknown ragdoll part: " + name);
        };
    }

    private RagdollPart createAttachedPart(String name, double torsoAttachX, double torsoAttachY, double torsoAttachZ, double partAttachX, double partAttachY, double partAttachZ, Quat partRotation) {
        return createAttachedPart(name, torsoAttachX, torsoAttachY, torsoAttachZ, partAttachX, partAttachY, partAttachZ, spawnRotation, partRotation);
    }

    private RagdollPart createAttachedPart(String name, double torsoAttachX, double torsoAttachY, double torsoAttachZ, double partAttachX, double partAttachY, double partAttachZ, Quat parentRotation, Quat partRotation) {
        double[] torsoAttachment = rotate(parentRotation, torsoAttachX * modelScale, torsoAttachY * modelScale, torsoAttachZ * modelScale);
        double[] partAttachment = rotate(partRotation, partAttachX * modelScale, partAttachY * modelScale, partAttachZ * modelScale);
        return createPart(name,
                torsoCenterX + torsoAttachment[0] - partAttachment[0],
                torsoCenterY + torsoAttachment[1] - partAttachment[1],
                torsoCenterZ + torsoAttachment[2] - partAttachment[2], partRotation);
    }

    private void createSixDofJoint(RagdollPart first, RagdollPart second, double torsoAttachX, double torsoAttachY, double torsoAttachZ, Quat firstRotation, Quat secondRotation, JointProfile profile) {
        double[] attachment = rotate(firstRotation, torsoAttachX * modelScale, torsoAttachY * modelScale, torsoAttachZ * modelScale);
        RVec3 jointPosition = new RVec3(spawnX + torsoCenterX + attachment[0],
                spawnGroundY + torsoCenterY + attachment[1],
                spawnZ + torsoCenterZ + attachment[2]);
        Vec3 axisX1 = firstRotation.rotateAxisX();
        Vec3 axisX2 = secondRotation.rotateAxisX();
        Vec3 axisY1 = firstRotation.rotateAxisY();
        Vec3 axisY2 = secondRotation.rotateAxisY();
        try (SixDofConstraintSettings settings = new SixDofConstraintSettings()) {
            settings.setSpace(EConstraintSpace.WorldSpace);
            settings.setPosition1(jointPosition);
            settings.setPosition2(jointPosition);
            settings.setAxisX1(axisX1);
            settings.setAxisX2(axisX2);
            settings.setAxisY1(axisY1);
            settings.setAxisY2(axisY2);
            settings.makeFixedAxis(EAxis.TranslationX);
            settings.makeFixedAxis(EAxis.TranslationY);
            settings.makeFixedAxis(EAxis.TranslationZ);
            // Sable leaves joint rotation free; position motors hold the
            // head and limbs near their rest orientations.
            settings.makeFreeAxis(EAxis.RotationX);
            settings.makeFreeAxis(EAxis.RotationY);
            settings.makeFreeAxis(EAxis.RotationZ);
            settings.setMaxFriction(EAxis.RotationX, profile.frictionTorque);
            settings.setMaxFriction(EAxis.RotationY, profile.frictionTorque);
            settings.setMaxFriction(EAxis.RotationZ, profile.frictionTorque);
            settings.setNumPositionStepsOverride(jointPositionSteps(profile));
            settings.setNumVelocityStepsOverride(jointVelocitySteps(profile));
            SixDofConstraint constraint = (SixDofConstraint) bodyInterface.createConstraint(settings, first.bodyId, second.bodyId);
            configureRestMotor(constraint.getMotorSettings(EAxis.RotationX), profile.motorFrequency, profile.motorDamping, profile.motorTorque);
            configureRestMotor(constraint.getMotorSettings(EAxis.RotationY), profile.motorFrequency, profile.motorDamping, profile.motorTorque);
            configureRestMotor(constraint.getMotorSettings(EAxis.RotationZ), profile.motorFrequency, profile.motorDamping, profile.motorTorque);
            constraint.setMotorState(EAxis.RotationX, EMotorState.Position);
            constraint.setMotorState(EAxis.RotationY, EMotorState.Position);
            constraint.setMotorState(EAxis.RotationZ, EMotorState.Position);
            constraint.setTargetOrientationCs(Quat.sIdentity());
            physicsSystem.addConstraint(constraint);
            constraints.add(constraint);
        }
    }

    private static void configureRestMotor(MotorSettings motor, float frequency, float damping, float maxTorque) {
        motor.getSpringSettings().setMode(ESpringMode.FrequencyAndDamping)
                .setFrequency(frequency).setDamping(damping);
        motor.setTorqueLimit(maxTorque);
    }

    private static Quat rotationX(float radians) {
        return Quat.sRotation(new Vec3(1.0f, 0.0f, 0.0f), radians);
    }

    private static Quat rotationY(float radians) {
        return Quat.sRotation(new Vec3(0.0f, 1.0f, 0.0f), radians);
    }

    private static Quat rotationZ(float radians) {
        return Quat.sRotation(new Vec3(0.0f, 0.0f, 1.0f), radians);
    }

    private static Quat limbRotation(Quat modelYaw, Quat fallback,
                                     RagdollInitialLimbPose pose, String part) {
        if (pose == null) return fallback;
        return multiply(modelYaw, modelPartRotation(pose.part(part).rotations()));
    }

    private static double[] modelPivotDelta(
            RagdollInitialLimbPose.PartPose pose,
            double defaultX, double defaultY, double defaultZ) {
        return new double[]{
                -(pose.x() - defaultX) / 16.0,
                -(pose.y() - defaultY) / 16.0,
                (pose.z() - defaultZ) / 16.0};
    }

    private static Quat modelPartRotation(float[] rotation) {
        // RagdollRenderer applies S = rotateZ(pi) after the physics rotation.
        // Conjugating the model rotation by S flips X and Y, but not Z:
        // S * Rmodel * S^-1. Apply the same basis conversion to every pose.
        return multiply(rotationZ(rotation[2]),
                multiply(rotationY(-rotation[1]), rotationX(-rotation[0])));
    }

    private static float modelPitchRadians(float minecraftPitch) {
        return (float) Math.toRadians(-clampPitch(minecraftPitch));
    }

    private static Quat multiply(Quat first, Quat second) {
        float ax = first.getX();
        float ay = first.getY();
        float az = first.getZ();
        float aw = first.getW();
        float bx = second.getX();
        float by = second.getY();
        float bz = second.getZ();
        float bw = second.getW();
        return new Quat(aw * bx + ax * bw + ay * bz - az * by, aw * by - ax * bz + ay * bw + az * bx, aw * bz + ax * by - ay * bx + az * bw, aw * bw - ax * bx - ay * by - az * bz);
    }

    private static double[] rotate(Quat rotation, double x, double y, double z) {
        double qx = rotation.getX();
        double qy = rotation.getY();
        double qz = rotation.getZ();
        double qw = rotation.getW();
        double tx = 2.0 * (qy * z - qz * y);
        double ty = 2.0 * (qz * x - qx * z);
        double tz = 2.0 * (qx * y - qy * x);
        return new double[] { x + qw * tx + qy * tz - qz * ty, y + qw * ty + qz * tx - qx * tz, z + qw * tz + qx * ty - qy * tx };
    }

    private static float clampPitch(float pitch) {
        return Math.max(-90.0f, Math.min(90.0f, pitch));
    }

    private static int jointPositionSteps(JointProfile profile) {
        return JOINT_POSITION_STEPS_OVERRIDE;
    }

    private static int jointVelocitySteps(JointProfile profile) {
        return JOINT_VELOCITY_STEPS_OVERRIDE;
    }

    private static double safeModelScale(float requestedScale) {
        return Float.isFinite(requestedScale) ? Math.clamp(requestedScale, 0.25f, RagdollDeathSnapshot.MAX_MODEL_SCALE) : 1.0;
    }

    private static TerrainSnapshot captureTerrain(Level level, BlockPos scanCenter, BlockPos coordinateOrigin) {
        List<TerrainBox> boxes = new ArrayList<>();
        CollisionContext collisionContext = CollisionContext.empty();
        BlockPos min = scanCenter.offset(-TERRAIN_HORIZONTAL_RADIUS, -TERRAIN_BLOCKS_BELOW, -TERRAIN_HORIZONTAL_RADIUS);
        BlockPos max = scanCenter.offset(TERRAIN_HORIZONTAL_RADIUS, TERRAIN_BLOCKS_ABOVE, TERRAIN_HORIZONTAL_RADIUS);
        for (BlockPos blockPos : BlockPos.betweenClosed(min, max)) {
            if (!level.hasChunkAt(blockPos)) {
                continue;
            }
            addCollisionBoxes(level, blockPos, coordinateOrigin, collisionContext, boxes);
        }
        // A player can die a few blocks above the ground while every nearby
        // chunk is already loaded. In that case the compact terrain volume is
        // all air. Search downward per X/Z column and add only the first solid
        // surface, keeping the fallback useful without filling the compound
        // shape with thousands of buried blocks.
        boolean usedFallback = boxes.isEmpty();
        if (usedFallback) {
            int minimumY = Math.max(level.getMinBuildHeight(), scanCenter.getY() - TERRAIN_FALLBACK_BLOCKS_BELOW);
            BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
            for (int x = min.getX(); x <= max.getX(); ++x) {
                for (int z = min.getZ(); z <= max.getZ(); ++z) {
                    for (int y = min.getY() - 1; y >= minimumY; --y) {
                        cursor.set(x, y, z);
                        if (addCollisionBoxes(level, cursor, coordinateOrigin, collisionContext, boxes)) {
                            break;
                        }
                    }
                }
            }
        }
        return new TerrainSnapshot(coordinateOrigin.immutable(), scanCenter.immutable(), List.copyOf(boxes), usedFallback);
    }

    private static boolean addCollisionBoxes(Level level, BlockPos blockPos, BlockPos origin, CollisionContext collisionContext, List<TerrainBox> boxes) {
        BlockState blockState = level.getBlockState(blockPos);
        if (blockState.isAir()) {
            return false;
        }
        VoxelShape voxelShape = blockState.getCollisionShape(level, blockPos, collisionContext);
        boolean added = false;
        for (AABB bounds : voxelShape.toAabbs()) {
            float halfX = (float) ((bounds.maxX - bounds.minX) * 0.5);
            float halfY = (float) ((bounds.maxY - bounds.minY) * 0.5);
            float halfZ = (float) ((bounds.maxZ - bounds.minZ) * 0.5);
            if (halfX <= MIN_BOX_SIZE || halfY <= MIN_BOX_SIZE || halfZ <= MIN_BOX_SIZE) {
                continue;
            }
            float centerX = (float) (blockPos.getX() - origin.getX() + (bounds.minX + bounds.maxX) * 0.5);
            float centerY = (float) (blockPos.getY() - origin.getY() + (bounds.minY + bounds.maxY) * 0.5);
            float centerZ = (float) (blockPos.getZ() - origin.getZ() + (bounds.minZ + bounds.maxZ) * 0.5);
            boxes.add(new TerrainBox(centerX, centerY, centerZ, halfX, halfY, halfZ));
            added = true;
        }
        return added;
    }

    private int createTerrainBody(List<TerrainBox> boxes) {
        List<BoxShapeSettings> boxSettings = new ArrayList<>(boxes.size());
        try (StaticCompoundShapeSettings compoundSettings = new StaticCompoundShapeSettings()) {
            for (TerrainBox box : boxes) {
                BoxShapeSettings shapeSettings = new BoxShapeSettings(box.halfX(), box.halfY(), box.halfZ());
                shapeSettings.setConvexRadius(0.0f);
                boxSettings.add(shapeSettings);
                compoundSettings.addShape(box.centerX(), box.centerY(), box.centerZ(), shapeSettings);
            }
            try (BodyCreationSettings terrainSettings = new BodyCreationSettings().setShapeSettings(compoundSettings).setPosition(0.0, 0.0, 0.0).setMotionType(EMotionType.Static).setObjectLayer(OBJECT_LAYER_NON_MOVING).setFriction(0.8f)) {
                return bodyInterface.createAndAddBody(terrainSettings, EActivation.DontActivate);
            }
        } finally {
            for (BoxShapeSettings settings : boxSettings) {
                settings.close();
            }
        }
    }

    static void tickAll(List<JoltPhysicsWorld> worlds) {
        if (worlds.isEmpty()) {
            return;
        }
        for (JoltPhysicsWorld world : worlds) {
            world.beginSharedTick();
        }
        for (int i = 0; i < SUB_STEPS_PER_CLIENT_TICK; ++i) {
            for (JoltPhysicsWorld world : worlds) {
                world.applySharedSubstepForces();
            }
            worlds.getFirst().sharedContext.advancePhysics();
        }
        for (JoltPhysicsWorld world : worlds) {
            world.finishSharedTick();
        }
    }

    void tick() {
        tickAll(List.of(this));
    }

    private void beginSharedTick() {
        if (impactParticleCooldownTicks > 0) {
            --impactParticleCooldownTicks;
        }
        boolean movedToNewBlock = terrainScanCenter == null || (int) Math.floor(torso.worldX) != terrainScanCenter.getX() || (int) Math.floor(torso.worldY) != terrainScanCenter.getY() || (int) Math.floor(torso.worldZ) != terrainScanCenter.getZ();
        boolean safetyRefresh = clientTicks > 0 && clientTicks % TERRAIN_SAFETY_REFRESH_INTERVAL_TICKS == 0;
        if (terrainDirty || movedToNewBlock || safetyRefresh) {
            refreshTerrain();
        }
        touchedFluidThisTick = isRagdollTouchingFluid();
        touchedSpecialThisTick = isRagdollInsideSpecialMedium();
        velocitiesBeforeSharedUpdate = captureLinearVelocities();
        for (RagdollPart part : parts) {
            part.copyCurrentToPrevious();
        }
    }

    private void applySharedSubstepForces() {
        applyPhysicsGunSubstep();
        touchedSpecialThisTick |= applyGrabPhysics();
        touchedSpecialThisTick |= applySpecialBlockPhysics();
        touchedFluidThisTick |= applyFluidPhysics();
        // Update before each solver step so low-speed settling stays quiet,
        // while falling or thrown parts have a stronger, speed-scaled response.
        for (RagdollPart part : parts) {
            float speed = bodyInterface.getLinearVelocity(part.bodyId).length();
            bodyInterface.setRestitution(part.bodyId, RagdollImpactResponse.restitution(speed));
        }
    }

    private void finishSharedTick() {
        ++clientTicks;
        updatePartTransforms(false);
        preventTerrainTunneling();
        applySpecialImpactResponses(velocitiesBeforeSharedUpdate);
        emitImpactParticles(velocitiesBeforeSharedUpdate);
        updateRagdollSleeping(touchedFluidThisTick || touchedSpecialThisTick);
        velocitiesBeforeSharedUpdate = null;
        if (clientTicks <= MAX_LOG_TICKS && clientTicks % LOG_INTERVAL_TICKS == 0) {
            int activeParts = 0;
            float maximumLinearSpeed = 0.0f;
            float maximumAngularSpeed = 0.0f;
            for (RagdollPart part : parts) {
                if (bodyInterface.isActive(part.bodyId)) {
                    ++activeParts;
                    maximumLinearSpeed = Math.max(maximumLinearSpeed, bodyInterface.getLinearVelocity(part.bodyId).length());
                    maximumAngularSpeed = Math.max(maximumAngularSpeed, bodyInterface.getAngularVelocity(part.bodyId).length());
                }
            }
            RagdollMod.LOGGER.debug("Jolt ragdoll t={}s: bodyWorldY={}, activeParts={}/{}, " + "maxLinearSpeed={}, maxAngularSpeed={}", clientTicks / 20, format(torso.worldY), activeParts, parts.size(), format(maximumLinearSpeed), format(maximumAngularSpeed));
        }
    }

    private boolean isRagdollTouchingFluid() {
        for (RagdollPart part : parts) {
            FluidSample water = sampleFluid(part, part.worldX, part.worldY, part.worldZ, part.rotationX, part.rotationY, part.rotationZ, part.rotationW, FluidTags.WATER);
            if (water.submergedFraction() > 0.0f) {
                return true;
            }
            if (sampleFluid(part, part.worldX, part.worldY, part.worldZ, part.rotationX, part.rotationY, part.rotationZ, part.rotationW, FluidTags.LAVA).submergedFraction() > 0.0f) {
                return true;
            }
        }
        return false;
    }

    private boolean isRagdollInsideSpecialMedium() {
        for (RagdollPart part : parts) {
            if (specialMedium(part) == SpecialMedium.POWDER_SNOW) {
                return true;
            }
        }
        return false;
    }

    private boolean applySpecialBlockPhysics() {
        boolean touchedPowderSnow = false;
        boolean grounded = isRagdollGrounded();
        for (RagdollPart part : parts) {
            BlockState support = supportingBlockState(part);
            if (isIce(support)) {
                bodyInterface.setFriction(part.bodyId, ICE_PART_FRICTION);
            } else if (support.is(Blocks.HONEY_BLOCK) || support.is(Blocks.SLIME_BLOCK)) {
                bodyInterface.setFriction(part.bodyId, STICKY_PART_FRICTION);
            } else {
                bodyInterface.setFriction(part.bodyId, DEFAULT_PART_FRICTION);
            }
            SpecialMedium medium = specialMedium(part);
            if (medium == SpecialMedium.NONE) {
                continue;
            }
            bodyInterface.activateBody(part.bodyId);
            Vec3 linearVelocity = bodyInterface.getLinearVelocity(part.bodyId);
            Vec3 angularVelocity = bodyInterface.getAngularVelocity(part.bodyId);
            if (medium == SpecialMedium.WEB) {
                float horizontalFactor = grounded ? GROUNDED_WEB_HORIZONTAL_FACTOR : WEB_HORIZONTAL_FACTOR;
                float verticalFactor = grounded ? GROUNDED_WEB_VERTICAL_FACTOR : WEB_VERTICAL_FACTOR;
                bodyInterface.setLinearVelocity(part.bodyId, linearVelocity.getX() * horizontalFactor, linearVelocity.getY() * verticalFactor, linearVelocity.getZ() * horizontalFactor);
                continue;
            }
            touchedPowderSnow = true;
            bodyInterface.setLinearVelocity(part.bodyId, linearVelocity.getX() * POWDER_SNOW_HORIZONTAL_FACTOR, Math.max(POWDER_SNOW_SINK_SPEED, linearVelocity.getY()), linearVelocity.getZ() * POWDER_SNOW_HORIZONTAL_FACTOR);
            bodyInterface.setAngularVelocity(part.bodyId, new Vec3(angularVelocity.getX() * 0.9f, angularVelocity.getY() * 0.9f, angularVelocity.getZ() * 0.9f));
        }
        return touchedPowderSnow;
    }

    private boolean isRagdollGrounded() {
        for (RagdollPart part : parts) {
            BlockState support = supportingBlockState(part);
            if (!support.is(Blocks.COBWEB) && !support.is(Blocks.POWDER_SNOW) && !support.getCollisionShape(level, BlockPos.ZERO, CollisionContext.empty()).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private void applySpecialImpactResponses(Vec3[] velocitiesBeforeUpdate) {
        if (velocitiesBeforeUpdate.length != parts.size()) {
            return;
        }
        for (int index = 0; index < parts.size(); ++index) {
            RagdollPart part = parts.get(index);
            Vec3 before = velocitiesBeforeUpdate[index];
            Vec3 current = bodyInterface.getLinearVelocity(part.bodyId);
            BlockState support = supportingBlockState(part);
            if (support.is(Blocks.SLIME_BLOCK)) {
                float velocityY = current.getY();
                if (before.getY() < -0.5f && current.getY() <= 0.1f) {
                    velocityY = Math.max(velocityY, -before.getY() * SLIME_BOUNCE_SCALE);
                }
                float horizontalFactor = 1.0f;
                if (Math.abs(current.getY()) < 0.1f * 20.0f) {
                    horizontalFactor = 0.4f + Math.abs(current.getY() / 20.0f) * 0.2f;
                }
                bodyInterface.setLinearVelocity(part.bodyId, current.getX() * horizontalFactor, velocityY, current.getZ() * horizontalFactor);
                bodyInterface.activateBody(part.bodyId);
                settledTicks = 0;
                continue;
            }
            BlockContact contact = findImpactBlock(part);
            if (contact == null || !contact.state().is(Blocks.HONEY_BLOCK) || before.getY() >= HONEY_SLIDE_MINIMUM_SPEED || current.getY() >= -0.1f) {
                continue;
            }
            float horizontalScale = Math.min(1.0f, HONEY_SLIDE_SPEED / before.getY());
            bodyInterface.setLinearVelocity(part.bodyId, before.getX() * horizontalScale, HONEY_SLIDE_SPEED, before.getZ() * horizontalScale);
            bodyInterface.activateBody(part.bodyId);
            settledTicks = 0;
        }
    }

    private SpecialMedium specialMedium(RagdollPart part) {
        double[] center = collisionCenter(part);
        double[] radii = axisAlignedRadii(part);
        BlockPos minimum = BlockPos.containing(center[0] - radii[0], center[1] - radii[1], center[2] - radii[2]);
        BlockPos maximum = BlockPos.containing(center[0] + radii[0], center[1] + radii[1], center[2] + radii[2]);
        for (BlockPos position : BlockPos.betweenClosed(minimum, maximum)) {
            BlockState state = level.getBlockState(position);
            if (!state.is(Blocks.COBWEB) && !state.is(Blocks.POWDER_SNOW)) {
                continue;
            }
            if (!obbIntersectsBlock(part, center, position)) {
                continue;
            }
            if (state.is(Blocks.COBWEB)) {
                return SpecialMedium.WEB;
            }
            if (state.is(Blocks.POWDER_SNOW)) {
                return SpecialMedium.POWDER_SNOW;
            }
        }
        return SpecialMedium.NONE;
    }

    private static boolean obbIntersectsBlock(RagdollPart part, double[] center, BlockPos blockPos) {
        double[][] partAxes = { rotate(1.0, 0.0, 0.0, part.rotationX, part.rotationY, part.rotationZ, part.rotationW), rotate(0.0, 1.0, 0.0, part.rotationX, part.rotationY, part.rotationZ, part.rotationW), rotate(0.0, 0.0, 1.0, part.rotationX, part.rotationY, part.rotationZ, part.rotationW) };
        double[] delta = { blockPos.getX() + 0.5 - center[0], blockPos.getY() + 0.5 - center[1], blockPos.getZ() + 0.5 - center[2] };
        double[] halfExtents = { part.halfX, part.halfY, part.halfZ };
        double[][] blockAxes = { { 1.0, 0.0, 0.0 }, { 0.0, 1.0, 0.0 }, { 0.0, 0.0, 1.0 } };
        for (double[] axis : partAxes) {
            if (separatesObbAndBlock(axis, delta, partAxes, halfExtents)) {
                return false;
            }
        }
        for (double[] axis : blockAxes) {
            if (separatesObbAndBlock(axis, delta, partAxes, halfExtents)) {
                return false;
            }
        }
        for (double[] partAxis : partAxes) {
            for (double[] blockAxis : blockAxes) {
                double[] axis = { partAxis[1] * blockAxis[2] - partAxis[2] * blockAxis[1], partAxis[2] * blockAxis[0] - partAxis[0] * blockAxis[2], partAxis[0] * blockAxis[1] - partAxis[1] * blockAxis[0] };
                if (separatesObbAndBlock(axis, delta, partAxes, halfExtents)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean separatesObbAndBlock(double[] axis, double[] delta, double[][] partAxes, double[] halfExtents) {
        double lengthSquared = dot(axis, axis);
        if (lengthSquared < 1.0e-12) {
            return false;
        }
        double distance = Math.abs(dot(delta, axis));
        double partRadius = 0.0;
        for (int index = 0; index < partAxes.length; ++index) {
            partRadius += halfExtents[index] * Math.abs(dot(partAxes[index], axis));
        }
        double blockRadius = 0.5 * (Math.abs(axis[0]) + Math.abs(axis[1]) + Math.abs(axis[2]));
        return distance > partRadius + blockRadius + 1.0e-7;
    }

    private static double dot(double[] first, double[] second) {
        return first[0] * second[0] + first[1] * second[1] + first[2] * second[2];
    }

    private BlockState supportingBlockState(RagdollPart part) {
        double[] center = collisionCenter(part);
        double[] radii = axisAlignedRadii(part);
        BlockPos position = BlockPos.containing(center[0], center[1] - radii[1] - 0.03, center[2]);
        return level.getBlockState(position);
    }

    private static boolean isIce(BlockState state) {
        return state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.FROSTED_ICE) || state.is(Blocks.BLUE_ICE);
    }

    private boolean applyFluidPhysics() {
        boolean touchedFluid = false;
        boolean torsoTouchedFluid = false;
        for (RagdollPart part : parts) {
            RVec3 position = new RVec3();
            Quat rotation = new Quat();
            bodyInterface.getPositionAndRotation(part.bodyId, position, rotation);
            FluidSample fluid = sampleFluid(part, position.xx() + localOrigin.getX(), position.yy() + localOrigin.getY(), position.zz() + localOrigin.getZ(), rotation.getX(), rotation.getY(), rotation.getZ(), rotation.getW(), FluidTags.WATER);
            boolean lava = false;
            if (fluid.submergedFraction() <= 0.0f) {
                fluid = sampleFluid(part, position.xx() + localOrigin.getX(), position.yy() + localOrigin.getY(), position.zz() + localOrigin.getZ(), rotation.getX(), rotation.getY(), rotation.getZ(), rotation.getW(), FluidTags.LAVA);
                lava = fluid.submergedFraction() > 0.0f;
            }
            float submerged = fluid.submergedFraction();
            if (submerged <= 0.0f) {
                continue;
            }
            touchedFluid = true;
            if (part == torso) {
                torsoTouchedFluid = true;
            }
            bodyInterface.activateBody(part.bodyId);
            float linearDrag = lava ? LAVA_LINEAR_DRAG : WATER_LINEAR_DRAG;
            float angularDrag = lava ? LAVA_ANGULAR_DRAG : WATER_ANGULAR_DRAG;
            float flowSpeed = lava ? LAVA_FLOW_SPEED : WATER_FLOW_SPEED;
            float buoyancyScale = lava ? LAVA_BUOYANCY_SCALE : WATER_BUOYANCY_SCALE;
            float bubbleColumn = (float) fluid.bubbleColumn();
            float surfaceFactor = surfaceBobFactor(submerged) * (1.0f - Math.min(1.0f, Math.abs(bubbleColumn)));
            float linearBlend = 1.0f - (float) Math.exp(-linearDrag * submerged * FIXED_TIME_STEP);
            float verticalBlend = 1.0f - (float) Math.exp(-linearDrag * (1.0f + surfaceFactor * 0.75f) * submerged * FIXED_TIME_STEP);
            Vec3 linearVelocity = bodyInterface.getLinearVelocity(part.bodyId);
            float flowX = (float) fluid.flowX() * flowSpeed;
            float flowY = (float) fluid.flowY() * flowSpeed + surfaceBobVelocity(lava) * surfaceFactor;
            float flowZ = (float) fluid.flowZ() * flowSpeed;
            float velocityX = linearVelocity.getX() + (flowX - linearVelocity.getX()) * linearBlend;
            float velocityY = linearVelocity.getY() + (flowY - linearVelocity.getY()) * verticalBlend;
            float velocityZ = linearVelocity.getZ() + (flowZ - linearVelocity.getZ()) * linearBlend;
            if (bubbleColumn != 0.0f) {
                boolean atSurface = fluid.surfaceBubbleColumn() != 0.0;
                float coverage = Math.min(1.0f, Math.abs(bubbleColumn) * submerged);
                float delta;
                float limit;
                if (bubbleColumn > 0.0f) {
                    delta = atSurface ? BUBBLE_SURFACE_UP_DELTA : BUBBLE_INSIDE_UP_DELTA;
                    limit = atSurface ? BUBBLE_SURFACE_UP_LIMIT : BUBBLE_INSIDE_UP_LIMIT;
                    velocityY = Math.min(limit, velocityY + delta * coverage / SUB_STEPS_PER_CLIENT_TICK);
                } else {
                    delta = BUBBLE_INSIDE_DOWN_DELTA;
                    limit = atSurface ? BUBBLE_SURFACE_DOWN_LIMIT : BUBBLE_INSIDE_DOWN_LIMIT;
                    velocityY = Math.max(limit, velocityY + delta * coverage / SUB_STEPS_PER_CLIENT_TICK);
                }
            }
            bodyInterface.setLinearVelocity(part.bodyId, velocityX, velocityY, velocityZ);
            float angularScale = (float) Math.exp(-angularDrag * submerged * FIXED_TIME_STEP);
            Vec3 angularVelocity = bodyInterface.getAngularVelocity(part.bodyId);
            bodyInterface.setAngularVelocity(part.bodyId, new Vec3(angularVelocity.getX() * angularScale, angularVelocity.getY() * angularScale, angularVelocity.getZ() * angularScale));
            float buoyancy = partMass(part.name) * 9.81f * buoyancyScale * submerged;
            bodyInterface.addForce(part.bodyId, new Vec3(0.0f, buoyancy, 0.0f));
            if (part == torso) {
                applyFluidRightingTorque(submerged, lava, rotation.getX(), rotation.getY(), rotation.getZ(), rotation.getW());
            }
        }
        if (!torsoTouchedFluid) {
            fluidFaceTargetSign = 0.0f;
        }
        return touchedFluid;
    }

    private static float surfaceBobFactor(float submerged) {
        return Math.max(0.0f, 1.0f - Math.abs(submerged - 0.65f) / 0.35f);
    }

    private float surfaceBobVelocity(boolean lava) {
        float speed = lava ? LAVA_SURFACE_BOB_SPEED : WATER_SURFACE_BOB_SPEED;
        float period = lava ? LAVA_SURFACE_BOB_PERIOD_TICKS : WATER_SURFACE_BOB_PERIOD_TICKS;
        double phase = clientTicks * Math.PI * 2.0 / period + tumbleAxisAngle();
        return (float) Math.sin(phase) * speed;
    }

    private void applyFluidRightingTorque(float submerged, boolean lava, float rotationX, float rotationY, float rotationZ, float rotationW) {
        if (submerged < WATER_RIGHTING_MIN_SUBMERGENCE) {
            return;
        }
        double[] faceNormal = rotate(0.0, 0.0, 1.0, rotationX, rotationY, rotationZ, rotationW);
        if (fluidFaceTargetSign == 0.0f) {
            fluidFaceTargetSign = faceNormal[1] >= 0.0 ? 1.0f : -1.0f;
        }
        double axisX = -fluidFaceTargetSign * faceNormal[2];
        double axisZ = fluidFaceTargetSign * faceNormal[0];
        double axisLength = Math.sqrt(axisX * axisX + axisZ * axisZ);
        if (axisLength < 1.0e-5) {
            return;
        }
        double aligned = Math.max(-1.0, Math.min(1.0, faceNormal[1] * fluidFaceTargetSign));
        double angle = Math.atan2(axisLength, aligned);
        float strength = Math.min(1.0f, (submerged - WATER_RIGHTING_MIN_SUBMERGENCE) / (1.0f - WATER_RIGHTING_MIN_SUBMERGENCE));
        Vec3 angularVelocity = bodyInterface.getAngularVelocity(torso.bodyId);
        float rightingTorque = lava ? LAVA_RIGHTING_TORQUE : WATER_RIGHTING_TORQUE;
        float rightingDamping = lava ? LAVA_RIGHTING_DAMPING : WATER_RIGHTING_DAMPING;
        float torqueX = (float) (axisX / axisLength * angle * rightingTorque * strength) - angularVelocity.getX() * rightingDamping;
        float torqueZ = (float) (axisZ / axisLength * angle * rightingTorque * strength) - angularVelocity.getZ() * rightingDamping;
        bodyInterface.addTorque(torso.bodyId, new Vec3(torqueX, 0.0f, torqueZ));
    }

    private FluidSample sampleFluid(RagdollPart part, double bodyX, double bodyY, double bodyZ, float rotationX, float rotationY, float rotationZ, float rotationW, TagKey<Fluid> fluidTag) {
        double[] center = collisionCenter(part, bodyX, bodyY, bodyZ, rotationX, rotationY, rotationZ, rotationW);
        double verticalRadius = verticalRadius(part, rotationX, rotationY, rotationZ, rotationW);
        verticalRadius = Math.max(verticalRadius, MIN_BOX_SIZE);
        double bottomY = center[1] - verticalRadius;
        double topY = center[1] + verticalRadius;
        double[] localX = rotate(part.halfX * FLUID_SAMPLE_SPREAD, 0.0, 0.0, rotationX, rotationY, rotationZ, rotationW);
        double[] localZ = rotate(0.0, 0.0, part.halfZ * FLUID_SAMPLE_SPREAD, rotationX, rotationY, rotationZ, rotationW);
        double[][] columns = { { center[0], center[2] }, { center[0] + localX[0], center[2] + localX[2] }, { center[0] - localX[0], center[2] - localX[2] }, { center[0] + localZ[0], center[2] + localZ[2] }, { center[0] - localZ[0], center[2] - localZ[2] } };
        double submergedTotal = 0.0;
        double flowX = 0.0;
        double flowY = 0.0;
        double flowZ = 0.0;
        double flowWeight = 0.0;
        double bubbleColumn = 0.0;
        double surfaceBubbleColumn = 0.0;
        BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos();
        int minimumY = Math.max(level.getMinBuildHeight(), (int) Math.floor(bottomY));
        int maximumY = Math.min(level.getMaxBuildHeight() - 1, (int) Math.floor(topY));
        for (double[] column : columns) {
            double highestSurface = Double.NEGATIVE_INFINITY;
            double columnFlowX = 0.0;
            double columnFlowY = 0.0;
            double columnFlowZ = 0.0;
            double columnBubbleColumn = 0.0;
            double columnSurfaceBubbleColumn = 0.0;
            int flowSamples = 0;
            for (int y = minimumY; y <= maximumY; ++y) {
                blockPos.set((int) Math.floor(column[0]), y, (int) Math.floor(column[1]));
                FluidState fluid = level.getFluidState(blockPos);
                if (!fluid.is(fluidTag)) {
                    continue;
                }
                highestSurface = Math.max(highestSurface, y + fluid.getHeight(level, blockPos));
                net.minecraft.world.phys.Vec3 flow = fluid.getFlow(level, blockPos);
                columnFlowX += flow.x;
                columnFlowY += flow.y;
                columnFlowZ += flow.z;
                BlockState state = level.getBlockState(blockPos);
                if (state.is(Blocks.BUBBLE_COLUMN)) {
                    double direction = state.getValue(BubbleColumnBlock.DRAG_DOWN) ? -1.0 : 1.0;
                    columnBubbleColumn += direction;
                    if (level.getBlockState(blockPos.above()).isAir()) {
                        columnSurfaceBubbleColumn += direction;
                    }
                }
                ++flowSamples;
            }
            if (highestSurface == Double.NEGATIVE_INFINITY) {
                continue;
            }
            double submerged = Math.max(0.0, Math.min(1.0, (highestSurface - bottomY) / (topY - bottomY)));
            submergedTotal += submerged;
            if (flowSamples > 0 && submerged > 0.0) {
                flowX += columnFlowX / flowSamples * submerged;
                flowY += columnFlowY / flowSamples * submerged;
                flowZ += columnFlowZ / flowSamples * submerged;
                bubbleColumn += columnBubbleColumn / flowSamples * submerged;
                surfaceBubbleColumn += columnSurfaceBubbleColumn / flowSamples * submerged;
                flowWeight += submerged;
            }
        }
        float submergedFraction = (float) (submergedTotal / columns.length);
        if (flowWeight <= 0.0) {
            return new FluidSample(submergedFraction, 0.0, 0.0, 0.0, 0.0, 0.0);
        }
        return new FluidSample(submergedFraction, flowX / flowWeight, flowY / flowWeight, flowZ / flowWeight, bubbleColumn / flowWeight, surfaceBubbleColumn / flowWeight);
    }

    private void refreshTerrain() {
        BlockPos scanCenter = BlockPos.containing(torso.worldX, torso.worldY, torso.worldZ);
        if (!isTerrainRegionLoaded(level, scanCenter)) {
            return;
        }
        TerrainSnapshot refreshed = captureTerrain(level, scanCenter, localOrigin);
        terrainScanCenter = refreshed.scanCenter();
        terrainUsesFallback = refreshed.usedFallback();
        terrainDirty = false;
        if (terrainBoxes.equals(refreshed.boxes())) {
            return;
        }
        int previousBoxCount = terrainBoxCount;
        int replacementBodyId = refreshed.boxes().isEmpty() ? NO_TERRAIN_BODY : createTerrainBody(refreshed.boxes());
        if (terrainBodyId != NO_TERRAIN_BODY) {
            bodyInterface.removeBody(terrainBodyId);
            bodyInterface.destroyBody(terrainBodyId);
        }
        terrainBodyId = replacementBodyId;
        terrainBoxes = refreshed.boxes();
        terrainBoxCount = terrainBoxes.size();
        activateAllParts();
        settledTicks = 0;
        RagdollMod.LOGGER.debug("Refreshed ragdoll terrain around {}: collisionBoxes={}->{}", scanCenter, previousBoxCount, terrainBoxCount);
    }

    private String describeTerrainBounds() {
        if (terrainBodyId == NO_TERRAIN_BODY) {
            return "none";
        }
        try (TransformedShape transformedTerrain = bodyInterface.getTransformedShape(terrainBodyId);
            AaBox bounds = transformedTerrain.getWorldSpaceBounds()) {
            Vec3 min = bounds.getMin();
            Vec3 max = bounds.getMax();
            return "[(" + format(min.getX()) + ", " + format(min.getY()) + ", " + format(min.getZ()) + "), (" + format(max.getX()) + ", " + format(max.getY()) + ", " + format(max.getZ()) + ")]";
        }
    }

    private void activateAllParts() {
        for (RagdollPart part : parts) {
            bodyInterface.activateBody(part.bodyId);
        }
    }

    private Vec3[] captureLinearVelocities() {
        Vec3[] velocities = new Vec3[parts.size()];
        for (int index = 0; index < parts.size(); ++index) {
            velocities[index] = bodyInterface.getLinearVelocity(parts.get(index).bodyId);
        }
        return velocities;
    }

    private void emitImpactParticles(Vec3[] velocitiesBeforeUpdate) {
        if (impactParticleCooldownTicks > 0 || velocitiesBeforeUpdate.length != parts.size()) {
            return;
        }
        float largestDelta = IMPACT_PARTICLE_THRESHOLD;
        RagdollPart impactPart = null;
        BlockContact impactContact = null;
        for (int index = 0; index < parts.size(); ++index) {
            RagdollPart part = parts.get(index);
            Vec3 before = velocitiesBeforeUpdate[index];
            Vec3 after = bodyInterface.getLinearVelocity(part.bodyId);
            float deltaX = before.getX() - after.getX();
            float deltaY = before.getY() - after.getY();
            float deltaZ = before.getZ() - after.getZ();
            float velocityDelta = (float) Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);
            if (velocityDelta <= largestDelta) {
                continue;
            }
            BlockContact contact = findImpactBlock(part);
            if (contact == null) {
                continue;
            }
            largestDelta = velocityDelta;
            impactPart = part;
            impactContact = contact;
        }
        if (impactPart == null || impactContact == null) {
            return;
        }
        impactParticleCooldownTicks = IMPACT_PARTICLE_COOLDOWN_TICKS;
        float strength = Math.clamp((largestDelta - IMPACT_PARTICLE_THRESHOLD) / 24.0f, 0.0f, 1.0f);
        BlockParticleOption particle = new BlockParticleOption(ParticleTypes.BLOCK, impactContact.state());
        int particleCount = 4 + Math.round(strength * 10.0f);
        double spread = 0.22 + strength * 0.20;
        for (int index = 0; index < particleCount; ++index) {
            double offsetX = (level.getRandom().nextDouble() - 0.5) * spread;
            double offsetZ = (level.getRandom().nextDouble() - 0.5) * spread;
            double velocityX = (level.getRandom().nextDouble() - 0.5) * (0.04 + strength * 0.05);
            double velocityY = 0.03 + level.getRandom().nextDouble() * (0.04 + strength * 0.08);
            double velocityZ = (level.getRandom().nextDouble() - 0.5) * (0.04 + strength * 0.05);
            level.addParticle(particle, impactContact.x() + offsetX, impactContact.y() + 0.02, impactContact.z() + offsetZ, velocityX, velocityY, velocityZ);
        }
        RagdollMod.LOGGER.debug("Spawned ragdoll impact particles: part={}, delta={}, block={}", impactPart.name, format(largestDelta), impactContact.state());
    }

    private BlockContact findImpactBlock(RagdollPart part) {
        double[] center = collisionCenter(part);
        double[] radii = axisAlignedRadii(part);
        AABB partBounds = new AABB(center[0] - radii[0], center[1] - radii[1], center[2] - radii[2], center[0] + radii[0], center[1] + radii[1], center[2] + radii[2]).inflate(IMPACT_BLOCK_SEARCH_PADDING);
        BlockContact closest = null;
        double closestDistanceSquared = Double.POSITIVE_INFINITY;
        BlockPos minimum = BlockPos.containing(partBounds.minX, partBounds.minY, partBounds.minZ);
        BlockPos maximum = BlockPos.containing(partBounds.maxX, partBounds.maxY, partBounds.maxZ);
        for (BlockPos position : BlockPos.betweenClosed(minimum, maximum)) {
            BlockState state = level.getBlockState(position);
            VoxelShape shape = state.getCollisionShape(level, position, CollisionContext.empty());
            if (shape.isEmpty()) {
                continue;
            }
            for (AABB localBounds : shape.toAabbs()) {
                AABB worldBounds = localBounds.move(position.getX(), position.getY(), position.getZ());
                if (!worldBounds.intersects(partBounds)) {
                    continue;
                }
                double contactX = Math.clamp(center[0], worldBounds.minX, worldBounds.maxX);
                double contactY = Math.clamp(center[1], worldBounds.minY, worldBounds.maxY);
                double contactZ = Math.clamp(center[2], worldBounds.minZ, worldBounds.maxZ);
                double deltaX = center[0] - contactX;
                double deltaY = center[1] - contactY;
                double deltaZ = center[2] - contactZ;
                double distanceSquared = deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
                if (distanceSquared < closestDistanceSquared) {
                    closestDistanceSquared = distanceSquared;
                    closest = new BlockContact(state, contactX, contactY, contactZ);
                }
            }
        }
        return closest;
    }

    private static double[] axisAlignedRadii(RagdollPart part) {
        double x = part.rotationX;
        double y = part.rotationY;
        double z = part.rotationZ;
        double w = part.rotationW;
        double matrixXX = 1.0 - 2.0 * (y * y + z * z);
        double matrixXY = 2.0 * (x * y - w * z);
        double matrixXZ = 2.0 * (x * z + w * y);
        double matrixYX = 2.0 * (x * y + w * z);
        double matrixYY = 1.0 - 2.0 * (x * x + z * z);
        double matrixYZ = 2.0 * (y * z - w * x);
        double matrixZX = 2.0 * (x * z - w * y);
        double matrixZY = 2.0 * (y * z + w * x);
        double matrixZZ = 1.0 - 2.0 * (x * x + y * y);
        return new double[] { Math.abs(matrixXX) * part.halfX + Math.abs(matrixXY) * part.halfY + Math.abs(matrixXZ) * part.halfZ, Math.abs(matrixYX) * part.halfX + Math.abs(matrixYY) * part.halfY + Math.abs(matrixYZ) * part.halfZ, Math.abs(matrixZX) * part.halfX + Math.abs(matrixZY) * part.halfY + Math.abs(matrixZZ) * part.halfZ };
    }

    /**
     * Jolt's linear cast normally catches fast impacts. This Minecraft-side
     * sweep is a final guard for the frame in which the streamed terrain body
     * is replaced while a ragdoll is falling at high speed.
     */
    private boolean preventTerrainTunneling() {
        double requiredLift = 0.0;
        String hitPartName = null;
        List<RagdollPart> sweptContactParts = new ArrayList<>();
        Map<RagdollPart, Double> terrainLifts = new HashMap<>();
        for (RagdollPart part : parts) {
            double[] previousCenter = collisionCenter(part, part.previousWorldX, part.previousWorldY, part.previousWorldZ, part.previousRotationX, part.previousRotationY, part.previousRotationZ, part.previousRotationW);
            double[] currentCenter = collisionCenter(part, part.worldX, part.worldY, part.worldZ, part.rotationX, part.rotationY, part.rotationZ, part.rotationW);
            double previousRadius = verticalRadius(part, part.previousRotationX, part.previousRotationY, part.previousRotationZ, part.previousRotationW);
            double currentRadius = verticalRadius(part, part.rotationX, part.rotationY, part.rotationZ, part.rotationW);
            double previousBottomY = previousCenter[1] - previousRadius;
            double currentBottomY = currentCenter[1] - currentRadius;
            if (previousBottomY - currentBottomY < TERRAIN_SWEEP_MINIMUM_FALL) {
                continue;
            }
            net.minecraft.world.phys.Vec3 start = new net.minecraft.world.phys.Vec3(previousCenter[0], previousBottomY, previousCenter[2]);
            net.minecraft.world.phys.Vec3 end = new net.minecraft.world.phys.Vec3(currentCenter[0], currentBottomY, currentCenter[2]);
            BlockHitResult hit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, CollisionContext.empty()));
            boolean insideBlock = hit.isInside();
            if (hit.getType() != HitResult.Type.BLOCK || (!insideBlock && hit.getDirection() != Direction.UP)) {
                continue;
            }
            double collisionTopY = hit.getLocation().y;
            if (insideBlock) {
                BlockPos hitPos = hit.getBlockPos();
                VoxelShape shape = level.getBlockState(hitPos).getCollisionShape(level, hitPos, CollisionContext.empty());
                if (!shape.isEmpty()) {
                    collisionTopY = hitPos.getY() + shape.max(Direction.Axis.Y);
                }
            }
            double lift = collisionTopY + TERRAIN_SWEEP_CLEARANCE - currentBottomY;
            if (lift >= TERRAIN_SWEEP_MINIMUM_LIFT) {
                sweptContactParts.add(part);
                terrainLifts.put(part, lift);
                if (lift > requiredLift) {
                    requiredLift = lift;
                    hitPartName = part.name;
                }
            }
        }
        if (requiredLift <= 0.0) {
            return false;
        }
        // Correct only bodies that actually crossed the terrain. Moving every
        // body by the largest correction repeatedly injects energy into the ragdoll.
        for (RagdollPart part : sweptContactParts) {
            RVec3 position = bodyInterface.getPosition(part.bodyId);
            bodyInterface.setPosition(part.bodyId, new RVec3(position.xx(), position.yy() + terrainLifts.get(part), position.zz()), EActivation.Activate);
        }
        // Rebound only the parts that crossed terrain, not the whole body.
        // Small contacts stop without bouncing; strong impacts get a capped,
        // dissipative rebound. Existing solver-generated upward motion is kept.
        for (RagdollPart contactPart : sweptContactParts) {
            Vec3 velocity = bodyInterface.getLinearVelocity(contactPart.bodyId);
            if (velocity.getY() >= 0.0f) {
                continue;
            }
            bodyInterface.setLinearVelocity(contactPart.bodyId, velocity.getX(),
                    RagdollImpactResponse.sweepVerticalVelocity(velocity.getY()), velocity.getZ());
        }
        settledTicks = 0;
        updatePartTransforms(false);
        RagdollMod.LOGGER.debug("Prevented terrain tunneling by ragdoll part {} " + "(lifted {} blocks)", hitPartName, format(requiredLift));
        return true;
    }

    private float tumbleAxisX() {
        return (float) Math.cos(tumbleAxisAngle());
    }

    private float tumbleAxisZ() {
        return (float) Math.sin(tumbleAxisAngle());
    }

    private double tumbleAxisAngle() {
        int positionHash = Double.hashCode(spawnX);
        positionHash = 31 * positionHash + Double.hashCode(spawnGroundY);
        positionHash = 31 * positionHash + Double.hashCode(spawnZ);
        double offset = ((positionHash >>> 1) & 255) / 255.0 * Math.PI;
        return spawnYawRadians + offset;
    }

    private static double verticalRadius(RagdollPart part, float quaternionX, float quaternionY, float quaternionZ, float quaternionW) {
        double matrixYX = 2.0 * (quaternionX * quaternionY + quaternionW * quaternionZ);
        double matrixYY = 1.0 - 2.0 * (quaternionX * quaternionX + quaternionZ * quaternionZ);
        double matrixYZ = 2.0 * (quaternionY * quaternionZ - quaternionW * quaternionX);
        return Math.abs(matrixYX) * part.halfX + Math.abs(matrixYY) * part.halfY + Math.abs(matrixYZ) * part.halfZ;
    }

    List<RagdollPartRenderState> getPartRenderStates(float partialTick) {
        float interpolation = Math.max(0.0f, Math.min(1.0f, partialTick));
        for (RagdollPart part : parts) {
            part.updateRenderState(interpolation, part.renderState);
        }
        return renderParts;
    }

    AABB getPartRenderBounds(float partialTick, double padding) {
        if (parts.isEmpty()) {
            return null;
        }
        float interpolation = Math.max(0.0f, Math.min(1.0f, partialTick));
        double minimumX = Double.POSITIVE_INFINITY;
        double minimumY = Double.POSITIVE_INFINITY;
        double minimumZ = Double.POSITIVE_INFINITY;
        double maximumX = Double.NEGATIVE_INFINITY;
        double maximumY = Double.NEGATIVE_INFINITY;
        double maximumZ = Double.NEGATIVE_INFINITY;
        for (RagdollPart part : parts) {
            double x = lerp(part.previousWorldX, part.worldX, interpolation);
            double y = lerp(part.previousWorldY, part.worldY, interpolation);
            double z = lerp(part.previousWorldZ, part.worldZ, interpolation);
            double[] radii = axisAlignedRadii(part);
            minimumX = Math.min(minimumX, x - radii[0]);
            minimumY = Math.min(minimumY, y - radii[1]);
            minimumZ = Math.min(minimumZ, z - radii[2]);
            maximumX = Math.max(maximumX, x + radii[0]);
            maximumY = Math.max(maximumY, y + radii[1]);
            maximumZ = Math.max(maximumZ, z + radii[2]);
        }
        return new AABB(minimumX - padding, minimumY - padding, minimumZ - padding, maximumX + padding, maximumY + padding, maximumZ + padding);
    }

    RagdollPartRenderState getPartRenderState(String partName, float partialTick) {
        RagdollPart part = findPart(partName);
        if (part == null) {
            return null;
        }
        float interpolation = Math.max(0.0f, Math.min(1.0f, partialTick));
        return part.toRenderState(interpolation);
    }

    private RagdollPart findPart(String name) {
        for (RagdollPart part : parts) {
            if (part.name.equals(name)) {
                return part;
            }
        }
        return null;
    }

    RagdollPhysicsState captureState() {
        List<RagdollPhysicsState.PartState> states = new ArrayList<>(parts.size());
        for (RagdollPart part : parts) {
            RVec3 position = new RVec3();
            Quat rotation = new Quat();
            bodyInterface.getPositionAndRotation(part.bodyId, position, rotation);
            Vec3 linearVelocity = bodyInterface.getLinearVelocity(part.bodyId);
            Vec3 angularVelocity = bodyInterface.getAngularVelocity(part.bodyId);
            states.add(new RagdollPhysicsState.PartState(part.name, position.xx() + localOrigin.getX(), position.yy() + localOrigin.getY(), position.zz() + localOrigin.getZ(), rotation.getX(), rotation.getY(), rotation.getZ(), rotation.getW(), linearVelocity.getX(), linearVelocity.getY(), linearVelocity.getZ(), angularVelocity.getX(), angularVelocity.getY(), angularVelocity.getZ(), part.frozen));
        }
        return new RagdollPhysicsState(states);
    }

    boolean applyState(RagdollPhysicsState state) {
        return applyState(state, true);
    }

    /**
     * Preserve existing momentum and rotations while pushing the whole body.
     */
    void addVelocity(float x, float y, float z) {
        for (RagdollPart part : parts) {
            Vec3 velocity = bodyInterface.getLinearVelocity(part.bodyId);
            float vx = velocity.getX() + x;
            float vy = velocity.getY() + y;
            float vz = velocity.getZ() + z;
            float speed = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
            float scale = speed > MAX_CONTROLLED_KNOCKBACK_SPEED ? MAX_CONTROLLED_KNOCKBACK_SPEED / speed : 1;
            bodyInterface.setLinearVelocity(part.bodyId, vx * scale, vy * scale, vz * scale);
            bodyInterface.activateBody(part.bodyId);
        }
        settledTicks = 0;
    }

    /**
     * Shared Jolt simulation must not advance remote, owner-streamed bodies.
     */
    void setExternallyControlled() {
        externallyControlled = true;
        for (RagdollPart part : parts) {
            bodyInterface.setMotionType(part.bodyId, EMotionType.Kinematic, EActivation.DontActivate);
            bodyInterface.setLinearAndAngularVelocity(part.bodyId, new Vec3(), new Vec3());
        }
    }

    void setLocallyControlled() {
        externallyControlled = false;
        for (RagdollPart part : parts) {
            bodyInterface.setMotionType(
                    part.bodyId,
                    part.frozen
                            ? EMotionType.Kinematic
                            : EMotionType.Dynamic,
                    part.frozen
                            ? EActivation.DontActivate
                            : EActivation.Activate);
            bodyInterface.activateBody(part.bodyId);
        }
        settledTicks = 0;
    }

    /**
     * Preserve the last tick's transform so streamed addon bodies interpolate.
     */
    boolean applyStreamedState(RagdollPhysicsState state) {
        RagdollPhysicsState stationary = new RagdollPhysicsState(state.parts().stream().map(p -> new RagdollPhysicsState.PartState(p.partName(), p.x(), p.y(), p.z(), p.rotationX(), p.rotationY(), p.rotationZ(), p.rotationW(), 0, 0, 0, 0, 0, 0, p.frozen())).toList());
        return applyState(stationary, false);
    }

    private boolean applyState(RagdollPhysicsState state, boolean resetPrevious) {
        if (state.parts().size() != parts.size()) {
            return false;
        }
        for (RagdollPhysicsState.PartState snapshot : state.parts()) {
            RagdollPart part = findPart(snapshot.partName());
            if (part == null || !isFinite(snapshot)) {
                return false;
            }
        }
        for (RagdollPhysicsState.PartState snapshot : state.parts()) {
            RagdollPart part = findPart(snapshot.partName());
            part.frozen = snapshot.frozen();
            bodyInterface.setMotionType(
                    part.bodyId,
                    externallyControlled || snapshot.frozen()
                            ? EMotionType.Kinematic
                            : EMotionType.Dynamic,
                    EActivation.DontActivate);
            Vec3 linearVelocity = new Vec3(snapshot.linearX(), snapshot.linearY(), snapshot.linearZ());
            Vec3 angularVelocity = new Vec3(snapshot.angularX(), snapshot.angularY(), snapshot.angularZ());
            EActivation activation = linearVelocity.lengthSq() + angularVelocity.lengthSq() > 0.0001f ? EActivation.Activate : EActivation.DontActivate;
            bodyInterface.setPositionAndRotation(part.bodyId, new RVec3(snapshot.x() - localOrigin.getX(), snapshot.y() - localOrigin.getY(), snapshot.z() - localOrigin.getZ()), new Quat(snapshot.rotationX(), snapshot.rotationY(), snapshot.rotationZ(), snapshot.rotationW()), activation);
            bodyInterface.setLinearAndAngularVelocity(part.bodyId, linearVelocity, angularVelocity);
        }
        if (!resetPrevious) {
            for (RagdollPart part : parts) part.copyCurrentToPrevious();
        }
        updatePartTransforms(resetPrevious);
        return true;
    }

    private static boolean isFinite(RagdollPhysicsState.PartState state) {
        return Double.isFinite(state.x()) && Double.isFinite(state.y()) && Double.isFinite(state.z()) && Float.isFinite(state.rotationX()) && Float.isFinite(state.rotationY()) && Float.isFinite(state.rotationZ()) && Float.isFinite(state.rotationW()) && Float.isFinite(state.linearX()) && Float.isFinite(state.linearY()) && Float.isFinite(state.linearZ()) && Float.isFinite(state.angularX()) && Float.isFinite(state.angularY()) && Float.isFinite(state.angularZ());
    }

    private static double[] rotate(double x, double y, double z, double quaternionX, double quaternionY, double quaternionZ, double quaternionW) {
        double twiceCrossX = 2.0 * (quaternionY * z - quaternionZ * y);
        double twiceCrossY = 2.0 * (quaternionZ * x - quaternionX * z);
        double twiceCrossZ = 2.0 * (quaternionX * y - quaternionY * x);
        return new double[] { x + quaternionW * twiceCrossX + quaternionY * twiceCrossZ - quaternionZ * twiceCrossY, y + quaternionW * twiceCrossY + quaternionZ * twiceCrossX - quaternionX * twiceCrossZ, z + quaternionW * twiceCrossZ + quaternionX * twiceCrossY - quaternionY * twiceCrossX };
    }

    private static double[] collisionCenter(RagdollPart part) {
        return collisionCenter(part, part.worldX, part.worldY, part.worldZ, part.rotationX, part.rotationY, part.rotationZ, part.rotationW);
    }

    private static double[] collisionCenter(RagdollPart part, double bodyX, double bodyY, double bodyZ, float rotationX, float rotationY, float rotationZ, float rotationW) {
        double[] worldOffset = rotate(part.offsetX, part.offsetY, part.offsetZ, rotationX, rotationY, rotationZ, rotationW);
        return new double[] { bodyX + worldOffset[0], bodyY + worldOffset[1], bodyZ + worldOffset[2] };
    }

    private void updateRagdollSleeping(boolean touchingFluid) {
        if (touchingFluid) {
            settledTicks = 0;
            return;
        }
        boolean anyActive = false;
        float maxLinearSpeed = MAX_SETTLED_LINEAR_SPEED;
        float maxAngularSpeed = MAX_SETTLED_ANGULAR_SPEED;
        int settledTicksRequired = SETTLED_TICKS_BEFORE_SLEEP;
        float maxLinearSpeedSquared = maxLinearSpeed * maxLinearSpeed;
        float maxAngularSpeedSquared = maxAngularSpeed * maxAngularSpeed;
        for (RagdollPart part : parts) {
            if (!bodyInterface.isActive(part.bodyId)) {
                continue;
            }
            anyActive = true;
            Vec3 linearVelocity = bodyInterface.getLinearVelocity(part.bodyId);
            Vec3 angularVelocity = bodyInterface.getAngularVelocity(part.bodyId);
            if (linearVelocity.lengthSq() > maxLinearSpeedSquared || angularVelocity.lengthSq() > maxAngularSpeedSquared) {
                settledTicks = 0;
                return;
            }
        }
        if (!anyActive) {
            settledTicks = 0;
            return;
        }
        ++settledTicks;
        if (settledTicks >= settledTicksRequired) {
            for (RagdollPart part : parts) {
                bodyInterface.deactivateBody(part.bodyId);
            }
            settledTicks = 0;
            RagdollMod.LOGGER.debug("Put settled Jolt ragdoll to sleep");
        }
    }

    private void updatePartTransforms(boolean resetPrevious) {
        for (RagdollPart part : parts) {
            RVec3 position = new RVec3();
            Quat rotation = new Quat();
            bodyInterface.getPositionAndRotation(part.bodyId, position, rotation);
            part.worldX = position.xx() + localOrigin.getX();
            part.worldY = position.yy() + localOrigin.getY();
            part.worldZ = position.zz() + localOrigin.getZ();
            part.rotationX = rotation.getX();
            part.rotationY = rotation.getY();
            part.rotationZ = rotation.getZ();
            part.rotationW = rotation.getW();
            if (resetPrevious) {
                part.copyCurrentToPrevious();
            }
        }
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        releaseGrab();
        for (TwoBodyConstraint constraint : constraints) {
            physicsSystem.removeConstraint(constraint);
            constraint.close();
        }
        constraints.clear();
        if (terrainBodyId != NO_TERRAIN_BODY) {
            bodyInterface.removeBody(terrainBodyId);
            bodyInterface.destroyBody(terrainBodyId);
            terrainBodyId = NO_TERRAIN_BODY;
        }
        for (RagdollPart part : parts) {
            bodyInterface.removeBody(part.bodyId);
            bodyInterface.destroyBody(part.bodyId);
            part.shape.close();
            part.innerShape.close();
        }
        if (grabTargetShape != null) {
            grabTargetShape.close();
            grabTargetShape = null;
        }
        RagdollMod.LOGGER.info("Closed Jolt ragdoll world");
        sharedContext.release();
    }

    private static String format(double value) {
        return String.format("%.4f", value);
    }

    private static double lerp(double start, double end, float amount) {
        return start + (end - start) * amount;
    }

    private record FluidSample(float submergedFraction, double flowX, double flowY, double flowZ, double bubbleColumn, double surfaceBubbleColumn) {
    }

    private enum SpecialMedium {

        NONE, WEB, POWDER_SNOW
    }

    record GrabHit(RagdollPart part, double distance, double hitX, double hitY, double hitZ) {
    }

    record PhysicsGunHit(
            String partName,
            double distance,
            double localX,
            double localY,
            double localZ,
            float rotationX,
            float rotationY,
            float rotationZ,
            float rotationW) {
    }

    private record PushBody(double centerX, double centerY, double centerZ, float rotationX, float rotationY, float rotationZ, float rotationW, double halfX, double halfY, double halfZ) {

        private static PushBody player(AABB bounds) {
            return new PushBody((bounds.minX + bounds.maxX) * 0.5, (bounds.minY + bounds.maxY) * 0.5, (bounds.minZ + bounds.maxZ) * 0.5, 0.0f, 0.0f, 0.0f, 1.0f, (bounds.maxX - bounds.minX) * 0.5, (bounds.maxY - bounds.minY) * 0.5, (bounds.maxZ - bounds.minZ) * 0.5);
        }
    }

    private record PushContact(RagdollPart part, Vec3 normal, Vec3 point, double penetration) {
    }

    private record PushAxis(Vec3 axis, double penetration) {

        private static final PushAxis SEPARATED = new PushAxis(null, Double.NEGATIVE_INFINITY);
    }

    private static final class PhysicsGunControl {

        private final String partName;

        private final double localX;

        private final double localY;

        private final double localZ;

        private double targetX;

        private double targetY;

        private double targetZ;

        private double smoothedTargetX;

        private double smoothedTargetY;

        private double smoothedTargetZ;

        private float targetRotationX;

        private float targetRotationY;

        private float targetRotationZ;

        private float targetRotationW = 1.0f;

        private float smoothedRotationX;

        private float smoothedRotationY;

        private float smoothedRotationZ;

        private float smoothedRotationW = 1.0f;

        private boolean rotationMode;

        private boolean updated = true;

        private PhysicsGunControl(
                String partName,
                double localX,
                double localY,
                double localZ,
                double targetX,
                double targetY,
                double targetZ) {
            this.partName = partName;
            this.localX = localX;
            this.localY = localY;
            this.localZ = localZ;
            this.targetX = targetX;
            this.targetY = targetY;
            this.targetZ = targetZ;
            this.smoothedTargetX = targetX;
            this.smoothedTargetY = targetY;
            this.smoothedTargetZ = targetZ;
        }
    }

    final class RagdollPart {

        private final String name;

        private BoxShape innerShape;

        private RotatedTranslatedShape shape;

        private final int bodyId;

        private float halfX;

        private float halfY;

        private float halfZ;

        private float offsetX;

        private float offsetY;

        private float offsetZ;

        private final RagdollPartRenderState renderState;

        private double previousWorldX;

        private double previousWorldY;

        private double previousWorldZ;

        private float previousRotationX;

        private float previousRotationY;

        private float previousRotationZ;

        private float previousRotationW;

        private double worldX;

        private double worldY;

        private double worldZ;

        private float rotationX;

        private float rotationY;

        private float rotationZ;

        private float rotationW;

        private boolean frozen;

        String name() {
            return name;
        }

        private RagdollPart(String name, BoxShape innerShape, RotatedTranslatedShape shape, int bodyId, float halfX, float halfY, float halfZ, float offsetX, float offsetY, float offsetZ) {
            this.name = name;
            this.innerShape = innerShape;
            this.shape = shape;
            this.bodyId = bodyId;
            this.halfX = halfX;
            this.halfY = halfY;
            this.halfZ = halfZ;
            this.offsetX = offsetX;
            this.offsetY = offsetY;
            this.offsetZ = offsetZ;
            renderState = new RagdollPartRenderState(name, halfX, halfY, halfZ, offsetX, offsetY, offsetZ);
        }

        private void copyCurrentToPrevious() {
            previousWorldX = worldX;
            previousWorldY = worldY;
            previousWorldZ = worldZ;
            previousRotationX = rotationX;
            previousRotationY = rotationY;
            previousRotationZ = rotationZ;
            previousRotationW = rotationW;
        }

        private void refreshCollisionShape() {
            RagdollConfig.CollisionBox box = collisionBox(name);
            double partScale = RagdollConfig.collisionScale(modelScale);
            float newHalfX = (float) (box.halfX() * partScale);
            float newHalfY = (float) (box.halfY() * partScale);
            float newHalfZ = (float) (box.halfZ() * partScale);
            float newOffsetX = (float) (box.offsetX() * partScale);
            float newOffsetY = (float) (box.offsetY() * partScale);
            float newOffsetZ = (float) (box.offsetZ() * partScale);
            float convexRadius = Math.min(0.05f, Math.min(newHalfX, Math.min(newHalfY, newHalfZ)) * 0.5f);
            BoxShape newInnerShape = new BoxShape(new Vec3(newHalfX, newHalfY, newHalfZ), convexRadius);
            RotatedTranslatedShape newShape = new RotatedTranslatedShape(new Vec3(newOffsetX, newOffsetY, newOffsetZ), Quat.sIdentity(), newInnerShape);
            bodyInterface.setShape(bodyId, newShape, true, EActivation.Activate);
            RotatedTranslatedShape oldShape = shape;
            BoxShape oldInnerShape = innerShape;
            shape = newShape;
            innerShape = newInnerShape;
            halfX = newHalfX;
            halfY = newHalfY;
            halfZ = newHalfZ;
            offsetX = newOffsetX;
            offsetY = newOffsetY;
            offsetZ = newOffsetZ;
            renderState.updateCollisionBounds(newHalfX, newHalfY, newHalfZ, newOffsetX, newOffsetY, newOffsetZ);
            oldShape.close();
            oldInnerShape.close();
        }

        private RagdollPartRenderState toRenderState(float interpolation) {
            RagdollPartRenderState state = new RagdollPartRenderState(name, halfX, halfY, halfZ, offsetX, offsetY, offsetZ);
            updateRenderState(interpolation, state);
            return state;
        }

        private void updateRenderState(float interpolation, RagdollPartRenderState state) {
            float endX = rotationX;
            float endY = rotationY;
            float endZ = rotationZ;
            float endW = rotationW;
            float dot = previousRotationX * endX + previousRotationY * endY + previousRotationZ * endZ + previousRotationW * endW;
            if (dot < 0.0f) {
                endX = -endX;
                endY = -endY;
                endZ = -endZ;
                endW = -endW;
            }
            float qx = previousRotationX + (endX - previousRotationX) * interpolation;
            float qy = previousRotationY + (endY - previousRotationY) * interpolation;
            float qz = previousRotationZ + (endZ - previousRotationZ) * interpolation;
            float qw = previousRotationW + (endW - previousRotationW) * interpolation;
            float inverseLength = 1.0f / (float) Math.sqrt(qx * qx + qy * qy + qz * qz + qw * qw);
            state.update(lerp(previousWorldX, worldX, interpolation), lerp(previousWorldY, worldY, interpolation), lerp(previousWorldZ, worldZ, interpolation), qx * inverseLength, qy * inverseLength, qz * inverseLength, qw * inverseLength);
        }
    }

    private enum JointProfile {

        NECK(0.65f, 1.8f, 1.4f, 40.0f),
        SHOULDER(0.60f, 0.75f, 1.35f, 4.0f),
        HIP(0.55f, 0.75f, 1.35f, 10.0f);

        private final float frictionTorque;
        private final float motorFrequency;
        private final float motorDamping;
        private final float motorTorque;

        JointProfile(float frictionTorque, float motorFrequency, float motorDamping, float motorTorque) {
            this.frictionTorque = frictionTorque;
            this.motorFrequency = motorFrequency;
            this.motorDamping = motorDamping;
            this.motorTorque = motorTorque;
        }
    }

    private record TerrainSnapshot(BlockPos origin, BlockPos scanCenter, List<TerrainBox> boxes, boolean usedFallback) {
    }

    private record TerrainBox(float centerX, float centerY, float centerZ, float halfX, float halfY, float halfZ) {
    }

    private record BlockContact(BlockState state, double x, double y, double z) {
    }
}


