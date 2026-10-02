package net.kb150.survivorcolonies.entity;

import com.minecolonies.api.entity.citizen.Skill;
import net.kb150.survivorcolonies.SurvivorColonies;
import net.kb150.survivorcolonies.data.SurvivorDataLoader;
import net.kb150.survivorcolonies.data.SurvivorPersonality;
import net.kb150.survivorcolonies.entity.ai.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.tags.BlockTags;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class SurvivorEntity extends PathfinderMob {

    private static final EntityDataAccessor<String> SURVIVOR_NAME = 
        SynchedEntityData.defineId(SurvivorEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> IS_FEMALE = 
        SynchedEntityData.defineId(SurvivorEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> TEXTURE_ID = 
        SynchedEntityData.defineId(SurvivorEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> TEXTURE_SUFFIX = 
        SynchedEntityData.defineId(SurvivorEntity.class, EntityDataSerializers.STRING);

    private static final EntityDataAccessor<ItemStack> RECRUIT_COST = 
        SynchedEntityData.defineId(SurvivorEntity.class, EntityDataSerializers.ITEM_STACK);
    private static final EntityDataAccessor<CompoundTag> SKILLS_TAG = 
        SynchedEntityData.defineId(SurvivorEntity.class, EntityDataSerializers.COMPOUND_TAG);

    private static final EntityDataAccessor<Integer> TRUST = 
        SynchedEntityData.defineId(SurvivorEntity.class, EntityDataSerializers.INT);

    private static final EntityDataAccessor<CompoundTag> SYNCED_INVENTORY = 
        SynchedEntityData.defineId(SurvivorEntity.class, EntityDataSerializers.COMPOUND_TAG);

    private static final EntityDataAccessor<CompoundTag> SYNCED_DIALOG_STATES = 
        SynchedEntityData.defineId(SurvivorEntity.class, EntityDataSerializers.COMPOUND_TAG);

    private ItemStack extraItem = ItemStack.EMPTY;
    private final SimpleContainer inventory = new SimpleContainer(36);

    private final Map<UUID, Integer> playerDialogStates = new HashMap<>();

    private int trustUpdateTimer = 0;
    private static final int TRUST_TICK_INTERVAL = 1200;
    private final Set<String> readDialogues = new HashSet<>();

    private SurvivorCampfireGoal campfireGoal;
    private SurvivorTentGoal tentGoal;
    private Player tradingPlayer;
    
    private BlockPos knownCampfirePos = null;
    private BlockPos knownTentPos = null;

    private SurvivorActivity currentActivity = SurvivorActivity.DAY_ROAM;
    private static final int ACTIVITY_UPDATE_INTERVAL = 20;

    private boolean wasNightLastActivityCheck = false;
    private boolean nightDecisionSleep = true;

    private static final long EVENING_SETUP_START = 12000L;
    private static final long EVENING_SETUP_END = 13000L;

    // Zentraler AI-Selector Debugger
    private String lastRunningGoalsSummary = "";
    private int selectorHeartbeatTimer = 0;

    public SurvivorEntity(EntityType<? extends PathfinderMob> type, Level level) {
        super(type, level);

        // FEUER & LAGERFEUER ALS ABSOLUT UNPASS㨁BAR DEFINIEREN (-1.0F = NIEMALS DURCHLAUFEN)
        this.setPathfindingMalus(BlockPathTypes.DAMAGE_FIRE, -1.0F);
        this.setPathfindingMalus(BlockPathTypes.DANGER_FIRE, -1.0F);

        this.inventory.addListener(container -> {
            if (!this.level().isClientSide) {
                CompoundTag tag = new CompoundTag();
                tag.put("Items", this.inventory.createTag());
                this.entityData.set(SYNCED_INVENTORY, tag);
            }
        });
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.28D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.0D)
                .add(Attributes.ARMOR, 0.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(SURVIVOR_NAME, "Alex");
        this.entityData.define(IS_FEMALE, false);
        this.entityData.define(TEXTURE_ID, 1);
        this.entityData.define(TEXTURE_SUFFIX, "_b");
        this.entityData.define(RECRUIT_COST, new ItemStack(Items.EMERALD, 5));
        this.entityData.define(SKILLS_TAG, new CompoundTag());
        this.entityData.define(TRUST, 10);
        this.entityData.define(SYNCED_INVENTORY, new CompoundTag());
        this.entityData.define(SYNCED_DIALOG_STATES, new CompoundTag());
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new SurvivorFleeToTentGoal(this));

        this.goalSelector.addGoal(2, new AvoidEntityGoal<>(
            this, 
            Monster.class, 
            16.0F, 
            1.3D,  
            1.5D,  
            (entity) -> {
                boolean hasTentToFleeTo = this.knownTentPos != null;
                return this.wantsToFleeFromTarget() && !hasTentToFleeTo;
            }
        ));

        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.2D, true));
        this.goalSelector.addGoal(4, new SurvivorInteractGoal(this));

        this.tentGoal = new SurvivorTentGoal(this);
        this.goalSelector.addGoal(5, this.tentGoal);

        this.goalSelector.addGoal(6, new SurvivorScavengeGoal(this));

        if (ModList.get().isLoaded("zombieremains")) {
            this.goalSelector.addGoal(7, new SurvivorHarvestRemainsGoal(this));
        }

        this.goalSelector.addGoal(8, new SurvivorEatGoal(this));

        this.campfireGoal = new SurvivorCampfireGoal(this);
        this.goalSelector.addGoal(9, this.campfireGoal);

        this.goalSelector.addGoal(10, new SurvivorSeekShelterGoal(this));
        this.goalSelector.addGoal(11, new SurvivorSentryGoal(this));

        // 1. HurtByTargetGoal: Wer mich angreift, IST mein Ziel (egal wie stark er ist!)
        // Ob gekämpft oder geflohen wird, entscheidet Prio 1 (Flee) vs Prio 3 (Attack)
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));

        // 2. NearestAttackableTargetGoal: Nur schwächere Monster aktiv jagen
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(
            this, 
            Monster.class, 
            10,    
            true,  
            false, 
            target -> {
                if (target instanceof Creeper) return false;
                
                boolean isHealthyEnough = this.getHealth() >= (this.getMaxHealth() * 0.5F);
                boolean isEnemyWeakerOrEqual = target.getHealth() <= this.getHealth();
                
                return isHealthyEnough && isEnemyWeakerOrEqual;
            }
        ));
    }

    @Override
    public void tick() {
        super.tick();

        if (!this.level().isClientSide) {
            // --- ZENTRALER GOAL-SELECTOR DEBUGGER ---
            debugInspectGoalSelector();

            // NOTFALL-REFLEX: Wenn er IM Lagerfeuer steht -> Sofort mit Satz zur Seite wegspringen!
            if (this.level().getBlockState(this.blockPosition()).is(BlockTags.CAMPFIRES)) {
                net.minecraft.core.Direction escapeDir = net.minecraft.core.Direction.Plane.HORIZONTAL.getRandomDirection(this.random);
                this.setDeltaMovement(escapeDir.getStepX() * 0.35D, 0.25D, escapeDir.getStepZ() * 0.35D);
                this.hurtMarked = true;
            }

            // Bei Gefahr oder wenn kein Goal läuft: Sofort aufspringen!
            boolean inDanger = this.getTarget() != null || this.getLastHurtByMob() != null;
            if (this.isPassenger() && (inDanger || this.campfireGoal == null || !this.campfireGoal.isRunning())) {
                this.stopRiding();
            }

            if (this.tickCount % ACTIVITY_UPDATE_INTERVAL == 0) {
                updateActivity();
            }

            this.trustUpdateTimer++;
            if (this.trustUpdateTimer >= TRUST_TICK_INTERVAL) {
                this.trustUpdateTimer = 0;
                if (this.level().getNearestPlayer(this, 16.0D) != null) {
                    this.addTrust(1);
                }
            }

            if (this.tickCount % 10 == 0 && this.isAlive()) {
                List<net.minecraft.world.entity.item.ItemEntity> items = 
                    this.level().getEntitiesOfClass(
                        net.minecraft.world.entity.item.ItemEntity.class,
                        this.getBoundingBox().inflate(3.0D),
                        item -> item.isAlive() && !item.getItem().isEmpty()
                    );

                for (net.minecraft.world.entity.item.ItemEntity itemEntity : items) {
                    ItemStack stack = itemEntity.getItem();

                    if (this.tryEquipBetterItem(stack)) {
                        itemEntity.discard();
                    } else {
                        ItemStack remainder = this.inventory.addItem(stack);

                        if (remainder.isEmpty()) {
                            itemEntity.discard();
                            this.playSound(net.minecraft.sounds.SoundEvents.ITEM_PICKUP, 0.2F, 1.0F);
                        } else if (remainder.getCount() < stack.getCount()) {
                            itemEntity.setItem(remainder);
                            this.playSound(net.minecraft.sounds.SoundEvents.ITEM_PICKUP, 0.2F, 1.0F);
                        }
                    }
                }
            }
        }
    }

    private void debugInspectGoalSelector() {
        // Sammelt alle Goals mit vollem Package-Namen, um fremde Mod-Injektionen sofort zu identifizieren
        String currentRunningGoals = this.goalSelector.getRunningGoals()
                .map(wg -> "[P" + wg.getPriority() + ":" + wg.getGoal().getClass().getName() + "]")
                .collect(Collectors.joining(", "));

        if (currentRunningGoals.isEmpty()) {
            currentRunningGoals = "[NONE]";
        }

        // 1. Sofortiges Logging bei JEDER Änderung der aktiven Goals
        if (!currentRunningGoals.equals(this.lastRunningGoalsSummary)) {
            SurvivorColonies.LOGGER.info("[AI-CHANGE] {}: {} -> {} | Act: {} | Pass: {} | Target: {}",
                    this.getSurvivorName(),
                    this.lastRunningGoalsSummary.isEmpty() ? "[INIT]" : this.lastRunningGoalsSummary,
                    currentRunningGoals,
                    this.currentActivity,
                    this.isPassenger(),
                    this.getTarget() != null ? this.getTarget().getType().getDescriptionId() : "none");
            this.lastRunningGoalsSummary = currentRunningGoals;
            this.selectorHeartbeatTimer = 0;
        }

        // 2. Regelmäßiger Heartbeat alle 60 Ticks (3 Sekunden), falls sich nichts ändert
        this.selectorHeartbeatTimer++;
        if (this.selectorHeartbeatTimer >= 60) {
            this.selectorHeartbeatTimer = 0;
            SurvivorColonies.LOGGER.info("[AI-STATE] {}: Active: {} | Act: {} | HP: {}/{} | Pos: [{}, {}, {}] | Pass: {}",
                    this.getSurvivorName(),
                    currentRunningGoals,
                    this.currentActivity,
                    (int) this.getHealth(),
                    (int) this.getMaxHealth(),
                    this.getBlockX(), this.getBlockY(), this.getBlockZ(),
                    this.isPassenger());
        }
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }

        this.setTradingPlayer(player);

        if (this.level().isClientSide) {
            net.kb150.survivorcolonies.client.ClientHooks.openRecruitScreen(this);
        }

        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);

        if (SYNCED_INVENTORY.equals(key) && this.level().isClientSide) {
            CompoundTag tag = this.entityData.get(SYNCED_INVENTORY);
            if (tag.contains("Items")) {
                this.inventory.clearContent();
                this.inventory.fromTag(tag.getList("Items", 10));
            }
        }

        if (SYNCED_DIALOG_STATES.equals(key) && this.level().isClientSide) {
            unpackDialogStatesFromTag(this.entityData.get(SYNCED_DIALOG_STATES));
        }
    }

    private void unpackDialogStatesFromTag(CompoundTag tag) {
        this.playerDialogStates.clear();
        for (String key : tag.getAllKeys()) {
            try {
                UUID uuid = UUID.fromString(key);
                this.playerDialogStates.put(uuid, tag.getInt(key));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private CompoundTag packDialogStatesToTag() {
        CompoundTag tag = new CompoundTag();
        for (Map.Entry<UUID, Integer> entry : this.playerDialogStates.entrySet()) {
            tag.putInt(entry.getKey().toString(), entry.getValue());
        }
        return tag;
    }

    public static boolean checkSurvivorSpawnRules(EntityType<SurvivorEntity> type, ServerLevelAccessor level, MobSpawnType spawnType, BlockPos pos, RandomSource random) {
        return level.getFluidState(pos).isEmpty() 
            && level.getFluidState(pos.below()).isEmpty() 
            && level.getBlockState(pos.below()).isSolid();
    }

    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, @Nullable SpawnGroupData spawnData, @Nullable CompoundTag dataTag) {
        SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, spawnData, dataTag);

        this.setPersistenceRequired();

        SurvivorDataLoader.equipRandomly(this, level.getRandom());

        Map<String, Integer> newSkills = new HashMap<>();
        Skill[] allSkills = Skill.values();
        int skillAmount = 3 + level.getRandom().nextInt(3);

        for (int i = 0; i < skillAmount; i++) {
            Skill randomSkill = allSkills[level.getRandom().nextInt(allSkills.length)];
            int levelValue = level.getRandom().nextInt(10) + 1;
            newSkills.put(randomSkill.name(), levelValue);
        }

        setSkills(newSkills);
        return data;
    }

    public boolean tryEquipBetterItem(ItemStack newStack) {
        if (newStack.isEmpty()) return false;

        EquipmentSlot slot = Mob.getEquipmentSlotForItem(newStack);
        ItemStack currentStack = this.getItemBySlot(slot);

        boolean isBetter = false;

        if (newStack.getItem() instanceof ArmorItem newArmor) {
            int currentDefense = (currentStack.getItem() instanceof ArmorItem currentArmor) ? currentArmor.getDefense() : 0;
            if (newArmor.getDefense() > currentDefense) {
                isBetter = true;
            }
        } else if (newStack.getItem() instanceof SwordItem newSword) {
            float currentDmg = (currentStack.getItem() instanceof SwordItem currentSword) ? currentSword.getDamage() : 0;
            if (newSword.getDamage() > currentDmg) {
                isBetter = true;
            }
        }

        if (isBetter) {
            this.setItemSlot(slot, newStack.copy());
            this.playSound(net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_GENERIC, 1.0F, 1.0F);

            if (!currentStack.isEmpty()) {
                this.inventory.addItem(currentStack);
            }
            return true;
        }
        return false;
    }

    public void applySkillAttributes() {
        Map<String, Integer> currentSkills = getSkills();
        if (currentSkills.isEmpty()) return;

        int strength = currentSkills.getOrDefault("Strength", 1);
        var attackAttr = this.getAttribute(Attributes.ATTACK_DAMAGE);
        if (attackAttr != null) attackAttr.setBaseValue(4.0D + (strength * 0.5D));

        int stamina = currentSkills.getOrDefault("Stamina", 1);
        var healthAttr = this.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr != null) {
            double oldMax = healthAttr.getBaseValue();
            double newMax = 20.0D + (stamina * 2.0D);
            healthAttr.setBaseValue(newMax);
            if (this.getHealth() == (float) oldMax || this.getHealth() < newMax) {
                this.setHealth((float) newMax);
            }
        }

        int athletics = currentSkills.getOrDefault("Athletics", 1);
        var speedAttr = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speedAttr != null) speedAttr.setBaseValue(0.28D + (athletics * 0.008D));

        int agility = currentSkills.getOrDefault("Agility", 1);
        var kbAttr = this.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (kbAttr != null) kbAttr.setBaseValue(Math.min(1.0D, agility * 0.05D));

        int focus = currentSkills.getOrDefault("Focus", 1);
        var armorAttr = this.getAttribute(Attributes.ARMOR);
        if (armorAttr != null) armorAttr.setBaseValue(focus * 0.5D);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("SurvivorName", getSurvivorName());
        tag.putBoolean("IsFemale", isFemale());
        tag.putInt("TextureId", getTextureId());
        tag.putString("TextureSuffix", getTextureSuffix());
        tag.putInt("Trust", getTrust());

        tag.put("RecruitCost", getRecruitCost().save(new CompoundTag()));
        if (!this.extraItem.isEmpty()) {
            tag.put("ExtraItem", this.extraItem.save(new CompoundTag()));
        }
        
        if (this.knownCampfirePos != null) {
            tag.putInt("CampfireX", this.knownCampfirePos.getX());
            tag.putInt("CampfireY", this.knownCampfirePos.getY());
            tag.putInt("CampfireZ", this.knownCampfirePos.getZ());
        }

        if (this.knownTentPos != null) {
            tag.putInt("TentX", this.knownTentPos.getX());
            tag.putInt("TentY", this.knownTentPos.getY());
            tag.putInt("TentZ", this.knownTentPos.getZ());
        }

        tag.put("Skills", this.entityData.get(SKILLS_TAG));
        tag.put("Inventory", this.inventory.createTag());

        CompoundTag readTag = new CompoundTag();
        this.readDialogues.forEach(key -> readTag.putBoolean(key, true));
        tag.put("ReadDialogues", readTag);

        tag.put("DialogStates", packDialogStatesToTag());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("SurvivorName")) setSurvivorName(tag.getString("SurvivorName"));
        if (tag.contains("IsFemale")) setFemale(tag.getBoolean("IsFemale"));
        if (tag.contains("TextureId")) setTextureId(tag.getInt("TextureId"));
        if (tag.contains("TextureSuffix")) setTextureSuffix(tag.getString("TextureSuffix"));
        if (tag.contains("Trust")) setTrust(tag.getInt("Trust"));

        if (tag.contains("RecruitCost")) setRecruitCost(ItemStack.of(tag.getCompound("RecruitCost")));
        if (tag.contains("ExtraItem")) this.extraItem = ItemStack.of(tag.getCompound("ExtraItem"));

        if (tag.contains("Skills")) this.entityData.set(SKILLS_TAG, tag.getCompound("Skills"));
        if (tag.contains("Inventory")) this.inventory.fromTag(tag.getList("Inventory", 10));

        if (tag.contains("ReadDialogues")) {
            CompoundTag readTag = tag.getCompound("ReadDialogues");
            this.readDialogues.clear();
            this.readDialogues.addAll(readTag.getAllKeys());
        }

        if (tag.contains("CampfireX") && tag.contains("CampfireY") && tag.contains("CampfireZ")) {
            this.knownCampfirePos = new BlockPos(
                tag.getInt("CampfireX"), 
                tag.getInt("CampfireY"), 
                tag.getInt("CampfireZ")
            );
        }

        if (tag.contains("TentX") && tag.contains("TentY") && tag.contains("TentZ")) {
            this.knownTentPos = new BlockPos(
                tag.getInt("TentX"), 
                tag.getInt("TentY"), 
                tag.getInt("TentZ")
            );
        }

        if (tag.contains("DialogStates", Tag.TAG_COMPOUND)) {
            unpackDialogStatesFromTag(tag.getCompound("DialogStates"));
            this.entityData.set(SYNCED_DIALOG_STATES, packDialogStatesToTag());
        }

        this.applySkillAttributes();
    }

    public int getDialogState(UUID playerUuid) {
        if (this.level().isClientSide && this.playerDialogStates.isEmpty()) {
            unpackDialogStatesFromTag(this.entityData.get(SYNCED_DIALOG_STATES));
        }
        return this.playerDialogStates.getOrDefault(playerUuid, -1);
    }

    public void setDialogState(UUID playerUuid, int reactionId) {
        this.playerDialogStates.put(playerUuid, reactionId);
        if (!this.level().isClientSide) {
            this.entityData.set(SYNCED_DIALOG_STATES, packDialogStatesToTag());
        }
    }

    public SurvivorActivity getActivity() {
        return this.currentActivity;
    }

    private void updateActivity() {
        if (this.getTarget() != null || this.hurtTime > 0 || this.isOnFire()) {
            if (this.tentGoal != null && this.tentGoal.isSleeping() && this.getTarget() == null) {
                // Schlafen bleibt ungestört
            } else {
                this.currentActivity = SurvivorActivity.COMBAT;
                return;
            }
        }

        if (this.campfireGoal != null && this.campfireGoal.isRunning() && this.isPassenger()) {
            this.currentActivity = SurvivorActivity.CAMPFIRE_IDLE;
            return;
        }

        long timeOfDay = this.level().getDayTime() % 24000L;
        boolean isEarlyEvening = !this.level().isNight()
                && timeOfDay >= EVENING_SETUP_START
                && timeOfDay < EVENING_SETUP_END;

        if (isEarlyEvening) {
            this.currentActivity = SurvivorActivity.EVENING_SETUP;
            return;
        }

        boolean isNightNow = this.level().isNight();
        if (isNightNow && !this.wasNightLastActivityCheck) {
            boolean hasFood = this.hasEdibleFoodInInventory();
            boolean isHungry = this.getHealth() < this.getMaxHealth();
            boolean isHungryWithoutFood = isHungry && !hasFood;

            String motivation = SurvivorPersonality.getMotivation(this.getUUID());
            boolean isHunterType = motivation.equalsIgnoreCase("revenge")
                    || motivation.equalsIgnoreCase("food");

            this.nightDecisionSleep = !(isHungryWithoutFood || isHunterType);
        }
        this.wasNightLastActivityCheck = isNightNow;

        if (isNightNow) {
            this.currentActivity = this.nightDecisionSleep
                    ? SurvivorActivity.SLEEPING
                    : SurvivorActivity.NIGHT_PATROL;
            return;
        }

        this.currentActivity = SurvivorActivity.DAY_ROAM;
    }

    public boolean hasEdibleFoodInInventory() {
        for (int i = 0; i < this.inventory.getContainerSize(); i++) {
            ItemStack stack = this.inventory.getItem(i);
            if (!stack.isEmpty() && stack.isEdible()) {
                return true;
            }
        }
        return false;
    }

    public float getFleeHealthThreshold() {
        String motivation = SurvivorPersonality.getMotivation(this.getUUID());
        return switch (motivation.toLowerCase()) {
            case "revenge" -> 0.25F;
            case "safety" -> 0.6F;
            case "money" -> 0.45F;
            case "food" -> 0.4F;
            default -> 0.5F;
        };
    }

    public boolean wantsToFleeFromTarget() {
        LivingEntity target = this.getTarget();
        if (target == null) {
            target = this.getLastHurtByMob();
        }
        if (target == null) return false;

        // Wenn der Feind übermächtig ist (mehr HP oder mehr Max-HP als der Survivor, z.B. Enderman mit 40 HP):
        // SOFORT DIE FLUCHT ERGREIFEN!
        if (target.getMaxHealth() > this.getMaxHealth() || target.getHealth() > this.getHealth()) {
            return true;
        }

        float healthFraction = this.getHealth() / this.getMaxHealth();
        return healthFraction < getFleeHealthThreshold();
    }

    public void spawnSleepParticles(BlockPos at) {
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.CLOUD,
                    at.getX() + 0.5D, at.getY() + 0.6D, at.getZ() + 0.5D,
                    3, 0.15D, 0.1D, 0.15D, 0.005D);
        }
    }

    public void setHiddenInTent(boolean hidden) {
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.SQUID_INK,
                    this.getX(), this.getY() + 0.6D, this.getZ(),
                    14, 0.25D, 0.3D, 0.25D, 0.01D);
        }
        this.setInvisible(hidden);
        this.setInvulnerable(hidden);
    }

    public int getTrust() { return this.entityData.get(TRUST); }
    public void setTrust(int value) { this.entityData.set(TRUST, Math.min(100, Math.max(0, value))); }
    public void addTrust(int amount) { setTrust(getTrust() + amount); }

    public boolean hasReadDialogue(String key) { return this.readDialogues.contains(key); }
    public void markDialogueRead(String key) {
        if (this.readDialogues.add(key)) {
            this.addTrust(5);
        }
    }

    public String getSurvivorName() { return this.entityData.get(SURVIVOR_NAME); }
    public void setSurvivorName(String name) {
        this.entityData.set(SURVIVOR_NAME, name);
        this.setCustomName(net.minecraft.network.chat.Component.literal(name));
        this.setCustomNameVisible(true);
    }

    public boolean isFemale() { return this.entityData.get(IS_FEMALE); }
    public void setFemale(boolean female) { this.entityData.set(IS_FEMALE, female); }

    public int getTextureId() { return this.entityData.get(TEXTURE_ID); }
    public void setTextureId(int id) { this.entityData.set(TEXTURE_ID, id); }

    public String getTextureSuffix() { return this.entityData.get(TEXTURE_SUFFIX); }
    public void setTextureSuffix(String suffix) { this.entityData.set(TEXTURE_SUFFIX, suffix); }

    public ItemStack getRecruitCost() { return this.entityData.get(RECRUIT_COST); }
    public void setRecruitCost(ItemStack cost) { this.entityData.set(RECRUIT_COST, cost); }

    public ItemStack getExtraItem() { return this.extraItem; }
    public void setExtraItem(ItemStack item) { this.extraItem = item; }

    public SimpleContainer getInventory() { return this.inventory; }

    public Player getTradingPlayer() { return this.tradingPlayer; }
    public void setTradingPlayer(Player player) { this.tradingPlayer = player; }
    public SurvivorTentGoal getTentGoal() { return this.tentGoal; }

    public BlockPos getKnownCampfirePos() { 
        return this.knownCampfirePos; 
    }
    public void setKnownCampfirePos(BlockPos pos) { 
        this.knownCampfirePos = pos; 
    }

    public BlockPos getKnownTentPos() { 
        return this.knownTentPos; 
    }
    public void setKnownTentPos(BlockPos pos) { 
        this.knownTentPos = pos; 
    }
    
    public Map<String, Integer> getSkills() {
        Map<String, Integer> map = new HashMap<>();
        CompoundTag tag = this.entityData.get(SKILLS_TAG);
        for (String key : tag.getAllKeys()) {
            map.put(key, tag.getInt(key));
        }
        return map;
    }

    public void setSkills(Map<String, Integer> skills) {
        CompoundTag tag = new CompoundTag();
        skills.forEach(tag::putInt);
        this.entityData.set(SKILLS_TAG, tag);
        this.applySkillAttributes();
    }

    public boolean hasUsedOption(int optionId) {
        return this.hasReadDialogue(String.valueOf(optionId));
    }

    public void markOptionUsed(int optionId) {
        this.markDialogueRead(String.valueOf(optionId));
    }
}