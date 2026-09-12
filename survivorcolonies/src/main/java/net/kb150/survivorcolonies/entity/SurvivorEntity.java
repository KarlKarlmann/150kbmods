package net.kb150.survivorcolonies.entity;

import com.minecolonies.api.entity.citizen.Skill;
import net.kb150.survivorcolonies.client.gui.SurvivorRecruitScreen;
import net.kb150.survivorcolonies.data.SurvivorDataLoader;
import net.kb150.survivorcolonies.entity.ai.*;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.SwordItem;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;

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

    // Vertrauens-Wert (0 bis 100)
    private static final EntityDataAccessor<Integer> TRUST = 
        SynchedEntityData.defineId(SurvivorEntity.class, EntityDataSerializers.INT);

	private static final EntityDataAccessor<CompoundTag> SYNCED_INVENTORY = 
        SynchedEntityData.defineId(SurvivorEntity.class, EntityDataSerializers.COMPOUND_TAG);
		
    private ItemStack extraItem = ItemStack.EMPTY;
    private final SimpleContainer inventory = new SimpleContainer(36);
    
    // Proximity Trust Tracker
    private int trustUpdateTimer = 0;
    private static final int TRUST_TICK_INTERVAL = 1200; // 1 Minute (1200 Ticks)
    private final Set<String> readDialogues = new HashSet<>();

    private SurvivorCampfireGoal campfireGoal;
    private Player tradingPlayer;
	private BlockPos knownCampfirePos = null;
	public SurvivorEntity(EntityType<? extends PathfinderMob> type, Level level) {
		super(type, level);
		
		// NEU: Sobald sich ein Item ändert, wird der Client informiert
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
        this.entityData.define(TRUST, 10); // Startet misstrauisch (10/100)
		this.entityData.define(SYNCED_INVENTORY, new CompoundTag());
    }

	@Override
	protected void registerGoals() {
		// --- AKTIONEN (goalSelector: Was tut er?) ---
		this.goalSelector.addGoal(0, new FloatGoal(this));
		
		// Flucht-Verhalten: Flieht vor Monstern, wenn HP unter 50% ODER das Monster aktuell mehr HP hat
		this.goalSelector.addGoal(1, new AvoidEntityGoal<>(
			this, 
			Monster.class, 
			16.0F, 
			1.3D,  
			1.5D,  
			(entity) -> {
				boolean isLowHealth = this.getHealth() < (this.getMaxHealth() * 0.5F);
				boolean isEnemyStronger = entity.getHealth() > this.getHealth();
				return isLowHealth || isEnemyStronger;
			}
		));

		this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2D, true));
		this.goalSelector.addGoal(3, new SurvivorInteractGoal(this));
		this.goalSelector.addGoal(4, new SurvivorScavengeGoal(this));

		if (ModList.get().isLoaded("zombiesleeping")) {
			this.goalSelector.addGoal(5, new SurvivorHarvestRemainsGoal(this));
		}

		this.goalSelector.addGoal(6, new SurvivorEatGoal(this));

		this.campfireGoal = new SurvivorCampfireGoal(this);
		this.goalSelector.addGoal(7, this.campfireGoal);

		this.goalSelector.addGoal(8, new SurvivorSeekShelterGoal(this));
		this.goalSelector.addGoal(9, new SurvivorSentryGoal(this));

		// --- ZIELERFASSUNG (targetSelector: Wen greift er an?) ---
		// 1. Gegenschlag nur, wenn eigene Gesundheit über 50% ist UND der Angreifer nicht mehr HP hat
		this.targetSelector.addGoal(1, new HurtByTargetGoal(this) {
			@Override
			public boolean canUse() {
				if (!super.canUse()) return false;
				
				net.minecraft.world.entity.LivingEntity attacker = SurvivorEntity.this.getLastHurtByMob();
				if (attacker != null) {
					boolean isHealthyEnough = SurvivorEntity.this.getHealth() >= (SurvivorEntity.this.getMaxHealth() * 0.5F);
					boolean isEnemyWeakerOrEqual = attacker.getHealth() <= SurvivorEntity.this.getHealth();
					return isHealthyEnough && isEnemyWeakerOrEqual;
				}
				return false;
			}
		});

		// 2. Proaktiver Angriff nur, wenn eigene Gesundheit über 50% ist UND das Ziel nicht mehr HP hat
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
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }

        this.setTradingPlayer(player);

        if (this.level().isClientSide) {
            Minecraft.getInstance().setScreen(new SurvivorRecruitScreen(this));
        }

        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }
	@Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        
        // Client übernimmt das gespiegelte Inventar vom Server
        if (SYNCED_INVENTORY.equals(key) && this.level().isClientSide) {
            CompoundTag tag = this.entityData.get(SYNCED_INVENTORY);
            if (tag.contains("Items")) {
                this.inventory.clearContent();
                this.inventory.fromTag(tag.getList("Items", 10));
            }
        }
    }
	
	public static boolean checkSurvivorSpawnRules(EntityType<SurvivorEntity> type, ServerLevelAccessor level, MobSpawnType spawnType, BlockPos pos, RandomSource random) {
		// Verhindert den Spawn in Wasser/Lava und erzwingt festen Boden
		return level.getFluidState(pos).isEmpty() 
			&& level.getFluidState(pos.below()).isEmpty() 
			&& level.getBlockState(pos.below()).isSolid();
	}
	
	@Override
	public void tick() {
		super.tick();

		if (!this.level().isClientSide) {
			// Campfire Failsafe
			if (this.isPassenger() && (this.campfireGoal == null || !this.campfireGoal.isRunning())) {
				this.stopRiding();
			}

			// Passive Vertrauens-Gewinnung bei Nähe (+1 Vertrauen pro Minute)
			this.trustUpdateTimer++;
			if (this.trustUpdateTimer >= TRUST_TICK_INTERVAL) {
				this.trustUpdateTimer = 0;
				if (this.level().getNearestPlayer(this, 16.0D) != null) {
					this.addTrust(1);
				}
			}

			// NEU: Automatisches Aufsammeln ("Staubsauger")
			// Nur alle 10 Ticks prüfen, um Server-Performance zu sparen
			if (this.tickCount % 10 == 0 && this.isAlive()) {
				java.util.List<net.minecraft.world.entity.item.ItemEntity> items = 
					this.level().getEntitiesOfClass(
						net.minecraft.world.entity.item.ItemEntity.class,
						this.getBoundingBox().inflate(3.0D), // 3 Blöcke Aufhebe-Radius
						item -> item.isAlive() && !item.getItem().isEmpty()
					);

				for (net.minecraft.world.entity.item.ItemEntity itemEntity : items) {
					net.minecraft.world.item.ItemStack stack = itemEntity.getItem();
					
					// Zuerst prüfen, ob es eine bessere Waffe/Rüstung ist und ausgerüstet werden kann
					if (this.tryEquipBetterItem(stack)) {
						itemEntity.discard();
					} else {
						// Wenn nicht ausgerüstet, versuchen ins Inventar zu legen
						net.minecraft.world.item.ItemStack remainder = this.inventory.addItem(stack);
						
						if (remainder.isEmpty()) {
							// Komplett im Inventar verstaut
							itemEntity.discard();
							this.playSound(net.minecraft.sounds.SoundEvents.ITEM_PICKUP, 0.2F, 1.0F);
						} else if (remainder.getCount() < stack.getCount()) {
							// Inventar wurde voll, Rest bleibt auf dem Boden liegen
							itemEntity.setItem(remainder);
							this.playSound(net.minecraft.sounds.SoundEvents.ITEM_PICKUP, 0.2F, 1.0F);
						}
					}
				}
			}
		}
	}

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason, @Nullable SpawnGroupData spawnData, @Nullable CompoundTag dataTag) {
		SpawnGroupData data = super.finalizeSpawn(level, difficulty, reason, spawnData, dataTag);
		
		this.setPersistenceRequired();
		
		// 1. Setzt Geschlecht, Name (aus JSON) und Ausrüstung in einem Rutsch!
		SurvivorDataLoader.equipRandomly(this, level.getRandom());

		// 2. Skills generieren
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

		// Rüstungsvergleich
		if (newStack.getItem() instanceof ArmorItem newArmor) {
			int currentDefense = (currentStack.getItem() instanceof ArmorItem currentArmor) ? currentArmor.getDefense() : 0;
			if (newArmor.getDefense() > currentDefense) {
				isBetter = true;
			}
		} 
		// Waffenvergleich
		else if (newStack.getItem() instanceof SwordItem newSword) {
			float currentDmg = (currentStack.getItem() instanceof SwordItem currentSword) ? currentSword.getDamage() : 0;
			if (newSword.getDamage() > currentDmg) {
				isBetter = true;
			}
		}

		if (isBetter) {
			// Zieht das bessere Item an
			this.setItemSlot(slot, newStack.copy());
			this.playSound(net.minecraft.sounds.SoundEvents.ARMOR_EQUIP_GENERIC, 1.0F, 1.0F);
			
			// Altes Item zurück ins Inventar legen
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
        tag.put("Skills", this.entityData.get(SKILLS_TAG));
        tag.put("Inventory", this.inventory.createTag());

        CompoundTag readTag = new CompoundTag();
        this.readDialogues.forEach(key -> readTag.putBoolean(key, true));
        tag.put("ReadDialogues", readTag);
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
        this.applySkillAttributes();
    }

    // --- Vertrauens- & Dialog-System ---
    public int getTrust() { return this.entityData.get(TRUST); }
    public void setTrust(int value) { this.entityData.set(TRUST, Math.min(100, Math.max(0, value))); }
    public void addTrust(int amount) { setTrust(getTrust() + amount); }

    public boolean hasReadDialogue(String key) { return this.readDialogues.contains(key); }
    public void markDialogueRead(String key) {
        if (this.readDialogues.add(key)) {
            this.addTrust(5); // Einmaliger Vertrauensbonus für unbekannte Gesprächsoptionen
        }
    }

    // --- Getters & Setters ---
    public String getSurvivorName() { return this.entityData.get(SURVIVOR_NAME); }
	public void setSurvivorName(String name) {
		this.entityData.set(SURVIVOR_NAME, name);
		// Aktualisiert das echte Namensschild über dem Kopf des Überlebenden
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
	public BlockPos getKnownCampfirePos() { 
		return this.knownCampfirePos; 
	}
	public void setKnownCampfirePos(BlockPos pos) { 
		this.knownCampfirePos = pos; 
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
        // Wandelt die Integer-ID in einen String um und nutzt das existierende System
        return this.hasReadDialogue(String.valueOf(optionId));
    }

    public void markOptionUsed(int optionId) {
        this.markDialogueRead(String.valueOf(optionId));
    }
}