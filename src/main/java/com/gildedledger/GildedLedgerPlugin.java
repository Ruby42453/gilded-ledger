package com.gildedledger;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.inject.Provides;
import com.gildedledger.data.GpSession;
import com.gildedledger.data.SourceCategory;
import com.gildedledger.data.SourceRecord;
import com.gildedledger.data.TaskRecord;
import com.gildedledger.data.TrackerData;
import com.gildedledger.tracking.FarmingTracker;
import com.gildedledger.tracking.DropTracker;
import com.gildedledger.tracking.GroundDropLedger;
import com.gildedledger.tracking.InventoryDiff;
import com.gildedledger.tracking.ItemInfo;
import com.gildedledger.tracking.ItemRules;
import com.gildedledger.tracking.Ledger;
import com.gildedledger.tracking.LegacyTidy;
import com.gildedledger.tracking.MinigameRewards;
import com.gildedledger.tracking.RecentSupplies;
import com.gildedledger.tracking.SessionTracker;
import com.gildedledger.tracking.Reminders;
import com.gildedledger.tracking.SkillGroups;
import com.gildedledger.tracking.SkillRules;
import com.gildedledger.tracking.SkillingTracker;
import com.gildedledger.tracking.SupplyTracker;
import com.gildedledger.tracking.TileKey;
import com.gildedledger.ui.Fonts;
import com.gildedledger.ui.GildedLedgerPanel;
import com.gildedledger.ui.LootPrices;
import com.gildedledger.ui.PanelModel;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.Projectile;
import net.runelite.api.Prayer;
import net.runelite.api.Skill;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.AnimationChanged;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.ProjectileMoved;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemQuantityChanged;
import net.runelite.api.events.ItemSpawned;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.widgets.Widget;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.SpotanimID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.Notification;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.events.RuneScapeProfileChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.slayer.SlayerPlugin;
import net.runelite.client.plugins.slayer.SlayerPluginService;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@PluginDescriptor(
	name = "Gilded Ledger",
	description = "Profit and XP for everything you do: per-monster loot you pick up minus supplies, your Slayer task, every skill, farming, processing and teleports.",
	tags = {"loot", "profit", "supplies", "slayer", "farming", "herb", "tree", "battlestaff", "xp", "pvm"}
)
@PluginDependency(SlayerPlugin.class)
public class GildedLedgerPlugin extends Plugin
{
	private static final Logger log = LoggerFactory.getLogger(GildedLedgerPlugin.class);

	private static final String DATA_KEY = "ledger";
	private static final int SAVE_INTERVAL_TICKS = 50;
	private static final int COMBAT_XP_WINDOW_TICKS = 10;
	private static final int AMMO_WINDOW_TICKS = 10;
	private static final int PAYMENT_WINDOW_TICKS = 20;
	private static final int POUCH_WINDOW_TICKS = 3;
	private static final int TELEPORT_XP_WINDOW_TICKS = 3;
	/** A cannon shot counts for the monster it last fired at within this window. */
	private static final int CANNON_TARGET_TICKS = 10;
	/** Supplies used up to two minutes after a fight (the teleport out, say) are charged to it. */
	private static final int SUPPLY_ATTRIBUTION_TICKS = 200;
	private static final int SLAYER_SETTLE_TICKS = 10;
	private static final int SLAYER_BLANK_CONFIRMATIONS = 5;
	private static final int CANNON_PLACED = 4;
	/** A cannon fires one ball per shot; a bigger drop means it was picked up. */
	private static final int MAX_BALLS_PER_TICK = 2;

	private static final Set<Skill> COMBAT_SKILLS = Collections.unmodifiableSet(new HashSet<>(List.of(
		Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.RANGED, Skill.MAGIC, Skill.HITPOINTS, Skill.SLAYER)));

	/** XP from these always belongs to combat, never to a skilling action. */
	private static final Set<Skill> NON_SKILLING_XP = Collections.unmodifiableSet(new HashSet<>(List.of(
		Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.RANGED, Skill.HITPOINTS, Skill.SLAYER)));

	private static final int[] POUCH_TYPE_VARBITS = {
		VarbitID.RUNE_POUCH_TYPE_1, VarbitID.RUNE_POUCH_TYPE_2, VarbitID.RUNE_POUCH_TYPE_3,
		VarbitID.RUNE_POUCH_TYPE_4, VarbitID.RUNE_POUCH_TYPE_5, VarbitID.RUNE_POUCH_TYPE_6
	};
	private static final int[] POUCH_QUANTITY_VARBITS = {
		VarbitID.RUNE_POUCH_QUANTITY_1, VarbitID.RUNE_POUCH_QUANTITY_2, VarbitID.RUNE_POUCH_QUANTITY_3,
		VarbitID.RUNE_POUCH_QUANTITY_4, VarbitID.RUNE_POUCH_QUANTITY_5, VarbitID.RUNE_POUCH_QUANTITY_6
	};

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private SkillIconManager skillIconManager;

	@Inject
	private Gson gson;

	@Inject
	private GildedLedgerConfig config;

	@Inject
	private SlayerPluginService slayerService;

	@Inject
	private Notifier notifier;

	@Inject
	private ReminderPopup popup;

	private final Reminders reminders = new Reminders();
	private final MinigameRewards rewards = new MinigameRewards();

	/** Every skill a potion can boost. Hitpoints (brews, anglers) and Prayer have their own handling. */
	private static final Skill[] BOOSTED_SKILLS = boostableSkills();

	private static final Set<Skill> COMBAT_BOOST_SKILLS = Set.of(
		Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.RANGED, Skill.MAGIC);

	private static Skill[] boostableSkills()
	{
		List<Skill> skills = new ArrayList<>();
		for (Skill skill : Skill.values())
		{
			if (skill != Skill.HITPOINTS && skill != Skill.PRAYER)
			{
				skills.add(skill);
			}
		}
		return skills.toArray(new Skill[0]);
	}

	private Ledger ledger;
	private ItemInfo itemInfo;
	private GroundDropLedger groundDrops;
	private SupplyTracker supplies;
	private FarmingTracker farming;
	private SkillingTracker skilling;
	private DropTracker drops;
	private GildedLedgerPanel panel;
	private NavigationButton navButton;

	private boolean profileLoaded;
	private GameState lastGameState;
	private Map<Integer, Integer> lastCarried;
	private final Map<Integer, Integer> tickChanges = new HashMap<>();
	private final List<Integer> tickConsumed = new ArrayList<>();
	private boolean storageThisTick;
	private final Map<Skill, Integer> lastXp = new EnumMap<>(Skill.class);
	private final int[] pouchTypes = new int[POUCH_TYPE_VARBITS.length];
	private final int[] pouchQuantities = new int[POUCH_QUANTITY_VARBITS.length];
	private int ammoId = -1;
	private int ammoQty;
	private int weaponId = -1;
	private int weaponQty;
	private int cannonballs = -1;
	private int cannonballItem = ItemID.MCANNONBALL;
	private String cannonTargetName;
	private boolean cannonTargetIsTask;
	private int cannonTargetTick = Integer.MIN_VALUE / 2;
	private int headId = -1;
	private int headQty;
	private int capeId = -1;
	private int quiverAmmoId = -1;
	private int quiverAmmoQty;
	private final Map<Integer, Integer> chargeRemainders = new HashMap<>();
	private double blowpipeDartRemainder;
	private int blowpipeScaleRemainder;
	private int lastChargedAttackTick = Integer.MIN_VALUE / 2;
	/** Blowpipe shots already counted, by their projectile. */
	private final Set<Projectile> countedBlowpipeShots = Collections.newSetFromMap(new WeakHashMap<>());
	private boolean blowpipeProjectilesSeen;
	/** Set while booking a blowpipe's darts or scales, so the estimate can be corrected on Check. */
	private boolean bookingBlowpipe;
	private RecentSupplies recentSupplies;
	private SessionTracker sessions;

	// The monster the player is currently fighting.
	private String combatKey;
	private String combatName;
	private boolean combatIsTask;
	private int lastCombatTick = Integer.MIN_VALUE / 2;
	/** When the current fight started, so supplies used just before it can follow the kill. */
	private int fightStartTick = Integer.MIN_VALUE / 2;
	/** Last Bury or Scatter click: that Prayer XP is the player's, not a bonecrusher's. */
	private int buryClickTick = Integer.MIN_VALUE / 2;
	/** When the player last entered an instance (a boss room), for boss-fight supplies. */
	private int instanceEnteredTick = Integer.MIN_VALUE / 2;
	private boolean wasInInstance;

	private final Set<String> taskTargetNames = new HashSet<>();
	private int loginTick;
	private int slayerBlankCount;
	private int paymentClickTick = Integer.MIN_VALUE / 2;
	private int pouchClickTick = Integer.MIN_VALUE / 2;
	/** What the last clicked bag or pouch holds; only those items are moved by it. */
	private java.util.function.Predicate<String> pouchClickContents = name -> false;
	/** Seed box contents last seen, or null until it's first seen (so its contents aren't counted as new). */
	private Map<Integer, Integer> lastSeedBox;
	private int lastTeleportCastTick = Integer.MIN_VALUE / 2;
	private long shownRevision = -1;

	@Provides
	GildedLedgerConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(GildedLedgerConfig.class);
	}

	/** Settings saved while the plugin was called Pickup Profit, and ones that no longer exist. */
	private static final String[] LEGACY_SETTINGS = {
		"trackCombat", "trackFarming", "trackAmmo", "skillLayout", "collapseNew"
	};
	/** Skills are always tracked now; "Hide skilling" only hides them. */
	private static final String[] RETIRED_SETTINGS = {"trackPurchases", "attributionWindow", "lootValue", "trackSkilling"};

	private void migrateLegacySettings()
	{
		for (String key : LEGACY_SETTINGS)
		{
			String value = configManager.getConfiguration(GildedLedgerConfig.LEGACY_GROUP, key);
			if (value != null)
			{
				if (configManager.getConfiguration(GildedLedgerConfig.GROUP, key) == null)
				{
					configManager.setConfiguration(GildedLedgerConfig.GROUP, key, value);
				}
				configManager.unsetConfiguration(GildedLedgerConfig.LEGACY_GROUP, key);
			}
		}
		for (String key : RETIRED_SETTINGS)
		{
			configManager.unsetConfiguration(GildedLedgerConfig.LEGACY_GROUP, key);
		}
	}

	@Override
	protected void startUp()
	{
		migrateLegacySettings();
		ledger = new Ledger(System::currentTimeMillis);
		itemInfo = new ClientItemInfo();
		groundDrops = new GroundDropLedger();
		supplies = new SupplyTracker(itemInfo, this::bookSupply);
		recentSupplies = new RecentSupplies(ledger);
		sessions = new SessionTracker(ledger, lootPrices::value, System::currentTimeMillis);

		Map<String, Integer> compostIds = new HashMap<>();
		compostIds.put("Compost", ItemID.BUCKET_COMPOST);
		compostIds.put("Supercompost", ItemID.BUCKET_SUPERCOMPOST);
		compostIds.put("Ultracompost", ItemID.BUCKET_ULTRACOMPOST);
		farming = new FarmingTracker(ledger, itemInfo, compostIds);

		skilling = new SkillingTracker(ledger, itemInfo);
		// A boost drunk or eaten just before a skilling action (a dragonfruit pie before
		// fletching) is that action's supply, if the action's skill is still boosted.
		skilling.setActionListener((key, name, category) ->
		{
			Skill skill = skillFor(category);
			if (skill != null && client.getBoostedSkillLevel(skill) > client.getRealSkillLevel(skill))
			{
				recentSupplies.moveGeneralTo(key, name, category, false,
					client.getTickCount() - RecentSupplies.PRE_FIGHT_TICKS);
			}
		});
		drops = new DropTracker();

		buildPanel();

		if (client.getGameState() == GameState.LOGGED_IN)
		{
			clientThread.invoke(this::onLogin);
		}
	}

	/** Builds the side panel (again, when the font setting changes) and its toolbar button. */
	private void buildPanel()
	{
		Fonts.use(config.panelFont().getFamily());
		boolean wasOpen = panel != null && panel.isShowing();
		NavigationButton old = navButton;
		panel = new GildedLedgerPanel(itemManager, skillIconManager, this::onPanelAction);
		BufferedImage icon = ImageUtil.loadImageResource(getClass(), "panel_icon.png");
		navButton = NavigationButton.builder()
			.tooltip("Gilded Ledger")
			.icon(icon)
			.priority(6)
			.panel(panel)
			.build();
		if (old != null)
		{
			clientToolbar.removeNavigation(old);
		}
		clientToolbar.addNavigation(navButton);
		if (wasOpen)
		{
			clientToolbar.openPanel(navButton);
		}
	}

	@Override
	protected void shutDown()
	{
		// A session running when the plugin or client stops is paused, time counted up to now.
		if (profileLoaded)
		{
			sessions.pause();
		}
		save();
		clientThread.invoke(popup::close);
		clientToolbar.removeNavigation(navButton);
		resetTransientState();
		profileLoaded = false;
		panel = null;
		navButton = null;
	}

	// ---------------------------------------------------------------- lifecycle

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		GameState previous = lastGameState;
		lastGameState = state;
		if (state == GameState.LOGGED_IN)
		{
			// LOADING -> LOGGED_IN is just a region change; keep baselines so no XP or items are missed.
			if (previous != GameState.LOADING || !profileLoaded)
			{
				onLogin();
			}
		}
		else if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING)
		{
			save();
			resetTransientState();
		}
	}

	@Subscribe
	public void onRuneScapeProfileChanged(RuneScapeProfileChanged event)
	{
		save();
		profileLoaded = false;
		loadProfile();
	}

	@Subscribe
	public void onClientShutdown(ClientShutdown event)
	{
		if (profileLoaded)
		{
			sessions.pause();
		}
		save();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (GildedLedgerConfig.GROUP.equals(event.getGroup()) && "panelFont".equals(event.getKey()))
		{
			SwingUtilities.invokeLater(() ->
			{
				buildPanel();
				clientThread.invoke(() -> refreshPanel(true));
			});
			return;
		}
		if (GildedLedgerConfig.GROUP.equals(event.getGroup()) && !DATA_KEY.equals(event.getKey()))
		{
			clientThread.invoke(() -> refreshPanel(true));
		}
	}

	/** Categories hidden from the panel (still tracked): every skill, or the ones picked. */
	private Set<SourceCategory> hiddenCategories()
	{
		Set<SourceCategory> hidden = EnumSet.noneOf(SourceCategory.class);
		for (SourceCategory category : SourceCategory.values())
		{
			if (!category.isSkill() && category != SourceCategory.OTHER)
			{
				continue;
			}
			boolean hideThis = Boolean.parseBoolean(
				configManager.getConfiguration(GildedLedgerConfig.GROUP, "hide" + category.getLabel().replace(" ", "")));
			if (config.hideSkilling() && category.isSkill() || hideThis)
			{
				hidden.add(category);
			}
		}
		return hidden;
	}

	private void onLogin()
	{
		loginTick = client.getTickCount();
		slayerBlankCount = 0;
		if (!profileLoaded)
		{
			loadProfile();
		}
		lastCarried = null;
		lastSeedBox = null;
		clearTickChanges();
		lastXp.clear();
		cannonballs = -1;
		syncEquipmentBaseline();
		syncPouchBaseline();
		refreshPanel(true);
	}

	private void loadProfile()
	{
		String json = configManager.getRSProfileConfiguration(GildedLedgerConfig.GROUP, DATA_KEY);
		if (json == null)
		{
			// Saved while the plugin was called Pickup Profit: move it to the new group.
			String legacy = configManager.getRSProfileConfiguration(GildedLedgerConfig.LEGACY_GROUP, DATA_KEY);
			if (legacy != null && configManager.getRSProfileKey() != null)
			{
				json = legacy;
				configManager.setRSProfileConfiguration(GildedLedgerConfig.GROUP, DATA_KEY, legacy);
				configManager.unsetRSProfileConfiguration(GildedLedgerConfig.LEGACY_GROUP, DATA_KEY);
			}
		}
		TrackerData data = null;
		if (json != null && !json.isEmpty())
		{
			try
			{
				data = gson.fromJson(json, TrackerData.class);
			}
			catch (JsonParseException e)
			{
				log.warn("Gilded Ledger: saved data could not be read, starting fresh", e);
			}
		}
		ledger.load(data);
		farming.tidyUnknownCrops();
		skilling.tidyGroups();
		LegacyTidy.tidy(ledger, itemInfo);
		sessions.pauseAfterRestart();
		profileLoaded = configManager.getRSProfileKey() != null;
		taskTargetNames.clear();
		refreshPanel(true);
	}

	private void save()
	{
		if (ledger == null || !profileLoaded || !ledger.isDirty())
		{
			return;
		}
		configManager.setRSProfileConfiguration(GildedLedgerConfig.GROUP, DATA_KEY, gson.toJson(ledger.data()));
		ledger.markSaved();
	}

	private void resetTransientState()
	{
		groundDrops.clear();
		supplies.reset();
		recentSupplies.reset();
		reminders.reset();
		rewards.reset();
		farming.reset();
		skilling.reset();
		drops.reset();
		lastCarried = null;
		lastSeedBox = null;
		clearTickChanges();
		lastXp.clear();
		combatKey = null;
		combatName = null;
		combatIsTask = false;
	}

	private boolean tracking()
	{
		return profileLoaded && client.getGameState() == GameState.LOGGED_IN;
	}

	// ---------------------------------------------------------------- PvM loot

	@Subscribe
	public void onItemSpawned(ItemSpawned event)
	{
		if (tracking() && config.trackCombat())
		{
			groundDrops.recordSpawn(tileKey(event.getTile()), event.getItem().getId(),
				event.getItem().getQuantity(), client.getTickCount());
		}
	}

	@Subscribe
	public void onItemQuantityChanged(ItemQuantityChanged event)
	{
		if (!tracking() || !config.trackCombat())
		{
			return;
		}
		int change = event.getNewQuantity() - event.getOldQuantity();
		if (change > 0)
		{
			groundDrops.recordSpawn(tileKey(event.getTile()), event.getItem().getId(), change, client.getTickCount());
		}
		else if (change < 0)
		{
			onFloorItemRemoved(event.getTile(), event.getItem().getId(), -change);
		}
	}

	@Subscribe
	public void onItemDespawned(ItemDespawned event)
	{
		if (tracking() && config.trackCombat())
		{
			onFloorItemRemoved(event.getTile(), event.getItem().getId(), event.getItem().getQuantity());
		}
	}

	@Subscribe
	public void onNpcLootReceived(NpcLootReceived event)
	{
		if (!tracking() || !config.trackCombat())
		{
			return;
		}
		NPC npc = event.getNpc();
		String name = npcName(npc);
		if (name == null)
		{
			return;
		}
		boolean task = isTaskTarget(name);
		List<int[]> stacks = new ArrayList<>();
		for (ItemStack stack : event.getItems())
		{
			stacks.add(new int[]{stack.getId(), stack.getQuantity()});
		}
		WorldArea area = npc.getWorldArea();
		TileKey southWest = area != null
			? new TileKey(area.getX(), area.getY(), area.getPlane())
			: tileKey(npc.getWorldLocation());
		int size = area != null ? area.getWidth() : 1;
		groundDrops.tagNpcLoot(npcKey(name), task, southWest, size, stacks, client.getTickCount());

		ledger.incrementCount(npcKey(name), name, SourceCategory.COMBAT);
		// A potion before the fight, or charges spent before it registered as one: this kill's.
		// Boss fights (a boss, or any kill inside an instance) can be long, so for those
		// everything since entering the instance counts, up to 15 minutes before the kill.
		int tickNow = client.getTickCount();
		int since = fightStartTick - RecentSupplies.PRE_FIGHT_TICKS;
		if (ItemRules.isBoss(name) || client.isInInstancedRegion())
		{
			int arrived = client.isInInstancedRegion() ? instanceEnteredTick : fightStartTick;
			since = Math.max(Math.min(since, arrived - RecentSupplies.PRE_FIGHT_TICKS),
				tickNow - RecentSupplies.BOSS_TICKS);
		}
		recentSupplies.moveGeneralToKill(npcKey(name), name, task, since);
		if (task && ledger.data().currentTask != null && !ledger.data().currentTask.isFinished())
		{
			ledger.data().currentTask.kills++;
			ledger.changed();
		}
	}

	private void onFloorItemRemoved(Tile tile, int itemId, int quantity)
	{
		Player local = client.getLocalPlayer();
		TileKey playerTile = local == null ? null : tileKey(local.getWorldLocation());
		List<GroundDropLedger.Pickup> pickups = groundDrops.recordRemoval(
			tileKey(tile), itemId, quantity, client.getTickCount(), playerTile);
		for (GroundDropLedger.Pickup pickup : pickups)
		{
			// Either a monster's drop, or something the player dropped and is picking back up.
			SourceRecord record = ledger.data().sources.get(pickup.sourceKey);
			if (record == null && !pickup.sourceKey.startsWith("npc:"))
			{
				continue;
			}
			String name = record != null ? record.name : pickup.sourceKey.substring("npc:".length());
			SourceCategory category = record != null ? record.category : SourceCategory.COMBAT;
			ledger.addLoot(pickup.sourceKey, name, category, pickup.itemId, pickup.quantity);
			TaskRecord task = ledger.data().currentTask;
			if (pickup.taskTarget && task != null && !task.isFinished())
			{
				task.lootGp += lootPrice(pickup.itemId) * pickup.quantity;
				ledger.changed();
			}
		}
	}

	// ---------------------------------------------------------------- clicks

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (!tracking())
		{
			return;
		}
		int tick = client.getTickCount();
		String option = Text.removeTags(event.getMenuOption());
		String optionLower = option == null ? "" : option.toLowerCase(Locale.ENGLISH);
		MenuAction action = event.getMenuAction();

		String target = Text.removeTags(event.getMenuTarget());

		// Minigame reward pulls: the Rewards Guardian, the Tempoross reward pool, a supply crate...
		MinigameRewards.Minigame reward = event.isItemOp() && event.getItemId() > 0
			? MinigameRewards.forItem(itemInfo.name(event.getItemId()), option)
			: MinigameRewards.forTarget(target, option);
		if (reward != null)
		{
			rewards.onRewardClicked(reward, tick);
			skilling.onOtherAction();
			farming.onOtherAction();
			return;
		}
		if (!event.isItemOp() && action != MenuAction.WALK && action != MenuAction.CC_OP
			&& action != MenuAction.CC_OP_LOW_PRIORITY && action != MenuAction.WIDGET_CONTINUE)
		{
			// Clicking another object or NPC ends a reward pull; dropping junk or walking doesn't.
			rewards.onOtherClick();
		}

		boolean spellbookOp = action == MenuAction.CC_OP || action == MenuAction.CC_OP_LOW_PRIORITY;
		if (spellbookOp && !event.isItemOp() && SkillRules.isTeleportSpell(option, target))
		{
			// Teleport runes go to Teleports, and teleporting ends whatever you were doing.
			supplies.onTeleportCast(SkillRules.teleportSpellName(option, target), tick);
			lastTeleportCastTick = tick;
			stopActivities();
		}
		else if (optionLower.startsWith("cast") || isSpellbookWidget(spellbookOp, event))
		{
			supplies.onCastClicked(tick);
			// A spell cast straight from the spellbook (Superglass Make, Humidify...). Combat
			// spells need a target, so they never come through here.
			if (spellbookOp && !event.isItemOp())
			{
				startProcessing(tick, spellName(target));
			}
		}

		// An inventory item's own option (Eat, Drop, Clean...). Using an item ON something
		// (compost or a seed on a patch) is handled below with the thing it was used on.
		if (event.isItemOp() && event.getItemId() > 0 && !isUseOnSomething(action))
		{
			onInventoryItemClicked(event.getItemId(), option, tick);
			return;
		}

		switch (action)
		{
			case GROUND_ITEM_FIRST_OPTION:
			case GROUND_ITEM_SECOND_OPTION:
			case GROUND_ITEM_THIRD_OPTION:
			case GROUND_ITEM_FOURTH_OPTION:
			case GROUND_ITEM_FIFTH_OPTION:
				if ("take".equals(optionLower))
				{
					groundDrops.recordTake(sceneTile(event.getParam0(), event.getParam1()), event.getId(), tick);
				}
				stopActivities();
				break;
			case GAME_OBJECT_FIRST_OPTION:
			case GAME_OBJECT_SECOND_OPTION:
			case GAME_OBJECT_THIRD_OPTION:
			case GAME_OBJECT_FOURTH_OPTION:
			case GAME_OBJECT_FIFTH_OPTION:
			case WIDGET_TARGET_ON_GAME_OBJECT:
				paymentClickTick = Integer.MIN_VALUE / 2;
				onObjectClicked(event.getId(), event.getParam0(), event.getParam1(), option,
					action == MenuAction.WIDGET_TARGET_ON_GAME_OBJECT, tick);
				break;
			case NPC_FIRST_OPTION:
			case NPC_SECOND_OPTION:
			case NPC_THIRD_OPTION:
			case NPC_FOURTH_OPTION:
			case NPC_FIFTH_OPTION:
			case WIDGET_TARGET_ON_NPC:
				onNpcClicked(event.getMenuEntry().getNpc(), option, action == MenuAction.WIDGET_TARGET_ON_NPC, tick);
				break;
			case WIDGET_TARGET_ON_WIDGET:
				// Using one inventory item on another (herb on vial, knife on logs), or a spell on
				// an item ("High Level Alchemy -> Rune platebody"), which is filed by spell.
				startProcessing(tick, "cast".equals(optionLower) ? spellName(target) : null);
				break;
			case WALK:
			case PLAYER_FIRST_OPTION:
			case PLAYER_SECOND_OPTION:
			case PLAYER_THIRD_OPTION:
			case PLAYER_FOURTH_OPTION:
			case PLAYER_FIFTH_OPTION:
			case PLAYER_SIXTH_OPTION:
			case PLAYER_SEVENTH_OPTION:
			case PLAYER_EIGHTH_OPTION:
				// Walking off, or trading with another player, is never skilling.
				paymentClickTick = Integer.MIN_VALUE / 2;
				stopActivities();
				break;
			default:
				break;
		}
	}

	/**
	 * A spell's own right-click option in the spellbook, like NPC Contact → Dark Mage:
	 * a cast, even though the option doesn't say "Cast".
	 */
	private static boolean isSpellbookWidget(boolean spellbookOp, MenuOptionClicked event)
	{
		return spellbookOp && !event.isItemOp() && (event.getParam1() >>> 16) == InterfaceID.MAGIC_SPELLBOOK;
	}

	/** Something unrelated to farming or skilling was clicked. */
	private void stopActivities()
	{
		farming.onOtherAction();
		skilling.onOtherAction();
	}

	private void startProcessing(int tick)
	{
		startProcessing(tick, null);
	}

	/** @param spell the spell cast, when the action is a spell; Magic is filed by spell */
	private void startProcessing(int tick, String spell)
	{
		farming.onOtherAction();
		skilling.onProcessClicked(tick, spell);
	}

	private void onNpcClicked(NPC npc, String option, boolean usedSomethingOn, int tick)
	{
		farming.onOtherAction();
		String optionLower = option == null ? "" : option.toLowerCase(Locale.ENGLISH);
		if (npc == null)
		{
			skilling.onOtherAction();
			return;
		}
		paymentClickTick = optionLower.startsWith("pay") ? tick : Integer.MIN_VALUE / 2;

		SourceCategory gather = usedSomethingOn ? null : SkillRules.gatherFromNpc(option);
		String name = npcName(npc);
		if (gather != null && name != null)
		{
			String sourceName = gather == SourceCategory.FISHING ? SkillRules.fishingMethod(option) : name;
			skilling.onGatherClicked(gather, sourceName, tileKey(npc.getWorldLocation()), tick);
		}
		else if (!usedSomethingOn && name != null && SkillRules.couldGiveSkillingXp(option))
		{
			// Includes Talk-to, so conversions chosen in dialogue (the sawmill) are tracked too.
			skilling.onOtherTargetClicked(name, tileKey(npc.getWorldLocation()), tick);
		}
		else
		{
			skilling.onOtherAction();
		}
	}

	private void onInventoryItemClicked(int itemId, String option, int tick)
	{
		if (option == null)
		{
			return;
		}
		String o = option.toLowerCase(Locale.ENGLISH);
		if (o.equals("drop"))
		{
			drops.onDropClicked(itemId, tick);
			return;
		}
		if (o.equals("bury") || o.equals("scatter"))
		{
			buryClickTick = tick;
		}
		if (SkillRules.isProcessingItemOption(o))
		{
			startProcessing(tick);
			return;
		}
		SupplyTracker.Kind kind = null;
		if (o.equals("drink"))
		{
			kind = SupplyTracker.Kind.POTION;
		}
		else if (o.equals("eat"))
		{
			kind = SupplyTracker.Kind.FOOD;
		}
		else if (ItemRules.isTeleportConsumable(itemInfo.name(itemId), option))
		{
			kind = SupplyTracker.Kind.TELEPORT;
			stopActivities();
		}
		else if (SkillRules.isPouchShuffle(itemInfo.name(itemId), option))
		{
			pouchClickTick = tick;
			pouchClickContents = SkillRules.bagContents(itemInfo.name(itemId));
			return;
		}
		if (kind != null)
		{
			supplies.onConsumeClicked(itemId, kind, inventoryCount(itemId), tick);
		}
	}

	private void onObjectClicked(int objectId, int sceneX, int sceneY, String option, boolean usedItemOn, int tick)
	{
		ObjectComposition base = client.getObjectDefinition(objectId);
		if (base == null)
		{
			stopActivities();
			return;
		}
		ObjectComposition shown = base.getImpostorIds() != null ? base.getImpostor() : base;
		if (shown != null && shown.getName() != null && shown.getName().toLowerCase(Locale.ENGLISH).contains("totem"))
		{
			skilling.onValeTotemClicked(tick);
		}
		if (shown == null)
		{
			shown = base;
		}
		TileKey tile = sceneTile(sceneX, sceneY);

		if (isFarmingPatch(base, shown))
		{
			skilling.onOtherAction();
			if (config.trackFarming())
			{
				farming.onPatchClicked(tile, patchCategory(base, shown),
					ItemRules.cropFromPatchAppearance(shown.getName()), tick);
			}
			return;
		}
		farming.onOtherAction();

		if (usedItemOn)
		{
			// Raw food on a range, ore on a furnace, logs on a fire...
			startProcessing(tick);
			return;
		}
		SourceCategory gather = SkillRules.gatherFromObject(option);
		if (gather != null && shown.getName() != null)
		{
			String sourceName = gather == SourceCategory.FISHING ? SkillRules.fishingMethod(option) : shown.getName();
			skilling.onGatherClicked(gather, sourceName, tile, tick);
		}
		else if (SkillRules.isProcessingObjectOption(option))
		{
			startProcessing(tick);
		}
		else if (shown.getName() != null && !"null".equals(shown.getName()) && SkillRules.couldGiveSkillingXp(option))
		{
			// Anything else, e.g. "Tend-to" on a Forester's campfire: tracked if it gives XP.
			skilling.onOtherTargetClicked(shown.getName(), tile, tick);
		}
		else
		{
			skilling.onOtherAction();
		}
	}

	private static boolean isUseOnSomething(MenuAction action)
	{
		return action == MenuAction.WIDGET_TARGET_ON_GAME_OBJECT || action == MenuAction.WIDGET_TARGET_ON_NPC
			|| action == MenuAction.WIDGET_TARGET_ON_WIDGET || action == MenuAction.WIDGET_TARGET_ON_GROUND_ITEM
			|| action == MenuAction.WIDGET_TARGET_ON_PLAYER;
	}

	/** Patches are recognised by name ("Herb patch") or by the farming-only Guide / Rake options. */
	private static boolean isFarmingPatch(ObjectComposition base, ObjectComposition shown)
	{
		for (ObjectComposition comp : new ObjectComposition[]{base, shown})
		{
			if (comp == null)
			{
				continue;
			}
			String name = comp.getName();
			if (name != null && name.toLowerCase(Locale.ENGLISH).contains("patch"))
			{
				return true;
			}
			String[] actions = comp.getActions();
			if (actions == null)
			{
				continue;
			}
			for (String action : actions)
			{
				if ("Guide".equalsIgnoreCase(action) || "Rake".equalsIgnoreCase(action))
				{
					return true;
				}
			}
		}
		return false;
	}

	/** "Herb patch" from the base object, or from the patch's current look when the base has no name. */
	private static String patchCategory(ObjectComposition base, ObjectComposition shown)
	{
		String name = base.getName();
		if ((name == null || "null".equals(name) || !name.toLowerCase(Locale.ENGLISH).contains("patch"))
			&& shown.getName() != null && shown.getName().toLowerCase(Locale.ENGLISH).contains("patch"))
		{
			name = shown.getName();
		}
		return ItemRules.patchCategory(name);
	}

	// ---------------------------------------------------------------- inventory, equipment, varps

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (!tracking())
		{
			return;
		}
		if (event.getContainerId() == InventoryID.INV)
		{
			onInventoryChanged(event.getItemContainer());
			accumulateCarriedChanges();
		}
		else if (event.getContainerId() == InventoryID.WORN)
		{
			onEquipmentChanged(event.getItemContainer());
			accumulateCarriedChanges();
		}
		else if (event.getContainerId() == InventoryID.SEED_BOX)
		{
			// Seeds from a Master Farmer or a bird's nest go straight into an open seed box.
			// Counting its contents as carried makes those gains (and seeds planted from it) show,
			// while moving seeds between it and the inventory cancels out.
			Map<Integer, Integer> now = counts(event.getItemContainer());
			if (lastSeedBox != null && !isStorageOpen())
			{
				InventoryDiff.between(lastSeedBox, now).changes()
					.forEach((id, delta) -> tickChanges.merge(id, delta, Integer::sum));
			}
			lastSeedBox = now;
		}
	}

	private void onInventoryChanged(ItemContainer container)
	{
		Map<Integer, Integer> now = counts(container);
		int tick = client.getTickCount();

		// Banking, the tool leprechaun and the seed vault move items in and out;
		// none of that is ever loot or a supply cost.
		if (isStorageOpen())
		{
			supplies.reset();
			paymentClickTick = Integer.MIN_VALUE / 2;
			return;
		}
		tickConsumed.addAll(supplies.onInventoryChanged(id -> now.getOrDefault(id, 0), tick));
	}

	/**
	 * Inventory and worn equipment are compared as one pool of carried items, so
	 * equipping or unequipping something moves it within the pool and changes
	 * nothing. Changes are gathered over the tick and handled together on GameTick.
	 */
	private void accumulateCarriedChanges()
	{
		if (isStorageOpen())
		{
			storageThisTick = true;
		}
		if (lastCarried == null)
		{
			return;
		}
		Map<Integer, Integer> carried = carriedCounts();
		InventoryDiff.between(lastCarried, carried).changes()
			.forEach((id, delta) -> tickChanges.merge(id, delta, Integer::sum));
		lastCarried = carried;
	}

	private Map<Integer, Integer> carriedCounts()
	{
		Map<Integer, Integer> carried = new HashMap<>();
		ItemContainer inv = client.getItemContainer(InventoryID.INV);
		ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		if (inv != null)
		{
			counts(inv).forEach((id, qty) -> carried.merge(id, qty, Integer::sum));
		}
		if (worn != null)
		{
			counts(worn).forEach((id, qty) -> carried.merge(id, qty, Integer::sum));
		}
		return carried;
	}

	/** Books everything carried items did this tick. Runs first on each GameTick. */
	private void processCarriedChanges(int tick)
	{
		if (lastCarried == null)
		{
			// First tick after login: both containers are loaded now, so take the baseline.
			lastCarried = carriedCounts();
			clearTickChanges();
			return;
		}
		for (Map.Entry<Integer, Integer> change : tickChanges.entrySet())
		{
			int id = change.getKey();
			if (change.getValue() < 0
				&& (id == ItemID.SNAKEBOSS_SCALE || ChargedWeapons.BLOWPIPE_DART_PROJECTILES.containsValue(id)))
			{
				// Darts or scales loaded into the blowpipe: the last Check is out of date.
				recentSupplies.forgetBlowpipeCheck();
				break;
			}
		}
		if (storageThisTick)
		{
			// Items went into or out of storage, so recent loot can no longer be matched to drops.
			ledger.forgetLootHistory();
			clearTickChanges();
			return;
		}
		if (tickChanges.isEmpty())
		{
			tickConsumed.clear();
			return;
		}

		InventoryDiff diff = InventoryDiff.of(tickChanges).withoutNoteConversions(itemInfo);
		List<Integer> consumed = new ArrayList<>(tickConsumed);
		clearTickChanges();

		// Dropped items come back out of whichever source gave them, and are remembered on
		// the floor so picking them back up puts them back. They are never a cost.
		Map<Integer, Integer> dropped = drops.dropsIn(diff, tick);
		TileKey here = localTile();
		dropped.forEach((id, amount) ->
			ledger.removeDroppedLoot(id, -amount).forEach((sourceKey, qty) ->
				groundDrops.recordPlayerDrop(sourceKey, id, (int) (long) qty, here, tick)));
		diff = diff.without(dropped);

		// Minigame-only items (Guardians of the Rift essence, Wintertodt roots...) never count,
		// and neither does an essence pouch degrading or being repaired.
		Map<Integer, Integer> ignored = new HashMap<>();
		diff.changes().forEach((id, delta) ->
		{
			String name = itemInfo.name(id);
			if (ItemRules.isMinigameItem(name) || ItemRules.isDegrading(name))
			{
				ignored.put(id, delta);
			}
		});
		diff = diff.without(ignored);
		if (diff.isEmpty())
		{
			return;
		}

		// Filling or emptying a pouch or bag (essence pouches, rune pouch, fish barrel, log
		// basket, gem bag, herb sack, seed box) only moves items around. Only what that bag
		// holds is ignored, so runes crafted right after emptying a pouch still count.
		if (tick - pouchClickTick <= POUCH_WINDOW_TICKS)
		{
			Map<Integer, Integer> shuffled = new HashMap<>();
			diff.changes().forEach((id, delta) ->
			{
				if (pouchClickContents.test(itemInfo.name(id)))
				{
					shuffled.put(id, delta);
				}
			});
			diff = diff.without(shuffled);
		}

		// A binding necklace crumbling is charged per craft instead (see bookBindingCharge).
		Map<Integer, Integer> necklace = new HashMap<>();
		diff.changes().forEach((id, delta) ->
		{
			if (delta < 0 && "binding necklace".equalsIgnoreCase(itemInfo.name(id)))
			{
				necklace.put(id, delta);
			}
		});
		diff = diff.without(necklace);
		bookBindingCharge(diff);

		// Food eaten and potions drunk are already supplies; the leftover dose isn't loot.
		diff = diff.without(consumedChanges(consumed, diff));
		if (diff.isEmpty())
		{
			return;
		}

		// A minigame reward pull: everything received is that minigame's loot.
		if (rewards.onCarriedChanges(diff, tick, ledger))
		{
			return;
		}

		Map<Integer, Integer> loadedIntoCannon = new HashMap<>();
		for (Map.Entry<Integer, Integer> change : diff.changes().entrySet())
		{
			int id = change.getKey();
			int delta = change.getValue();
			if (delta < 0 && ItemRules.isRune(itemInfo.name(id), itemInfo.stackable(id)))
			{
				supplies.onRunesLost(id, -delta);
			}
			if (delta < 0 && isCannonball(id))
			{
				// Loading the cannon isn't a cost: each ball is charged when it's fired.
				cannonballItem = id;
				loadedIntoCannon.put(id, delta);
			}
		}
		diff = diff.without(loadedIntoCannon);
		if (diff.isEmpty())
		{
			return;
		}

		if (config.trackFarming())
		{
			// A payment only ever removes items; anything gained means it's something else.
			if (tick - paymentClickTick <= PAYMENT_WINDOW_TICKS && diff.hasNoGains())
			{
				farming.onGardenerPayment(diff, tick, localTile());
				paymentClickTick = Integer.MIN_VALUE / 2;
			}
			else
			{
				farming.onInventoryChanged(diff);
			}
		}
		// While a patch is being worked, its changes are farming's, never a skill's.
		if (!(config.trackFarming() && farming.isArmed()))
		{
			skilling.onInventoryChanged(diff);
		}
	}

	/** Each combination-rune craft uses one of a worn binding necklace's 16 charges. */
	private void bookBindingCharge(InventoryDiff diff)
	{
		boolean crafted = diff.changes().entrySet().stream()
			.anyMatch(e -> e.getValue() > 0 && SkillGroups.isCombinationRune(itemInfo.name(e.getKey())));
		ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		if (!crafted || worn == null)
		{
			return;
		}
		Item amulet = worn.getItem(EquipmentInventorySlot.AMULET.getSlotIdx());
		if (amulet == null || !"binding necklace".equalsIgnoreCase(itemInfo.name(amulet.getId())))
		{
			return;
		}
		int id = amulet.getId();
		skilling.onSupplyUsed(SupplyTracker.charge(id, "binding-charge", "Binding necklace", "charges", 1,
			Math.round(itemInfo.marketPrice(id) / 16.0)));
	}

	private static boolean isCannonball(int itemId)
	{
		return itemId == ItemID.MCANNONBALL || itemId == ItemID.GRANITE_CANNONBALL;
	}

	private void clearTickChanges()
	{
		tickChanges.clear();
		tickConsumed.clear();
		storageThisTick = false;
	}

	/** The inventory changes explained by consumables: the item eaten or sipped, and what it left behind. */
	private Map<Integer, Integer> consumedChanges(List<Integer> consumed, InventoryDiff diff)
	{
		Map<Integer, Integer> explained = new HashMap<>();
		for (int id : consumed)
		{
			explained.merge(id, -1, Integer::sum);
			String base = ItemRules.withoutDoses(itemInfo.name(id));
			for (Map.Entry<Integer, Integer> change : diff.changes().entrySet())
			{
				if (change.getValue() <= 0)
				{
					continue;
				}
				String name = itemInfo.name(change.getKey());
				if (ItemRules.isLeftover(base, name))
				{
					explained.merge(change.getKey(), 1, Integer::sum);
				}
			}
		}
		return explained;
	}

	private void onEquipmentChanged(ItemContainer container)
	{
		Item ammo = container.getItem(EquipmentInventorySlot.AMMO.getSlotIdx());
		Item weapon = container.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx());
		int newAmmoId = ammo == null ? -1 : ammo.getId();
		int newAmmoQty = ammo == null ? 0 : ammo.getQuantity();
		int newWeaponId = weapon == null ? -1 : weapon.getId();
		int newWeaponQty = weapon == null ? 0 : weapon.getQuantity();

		updateCombatTarget(client.getTickCount());
		boolean inCombat = client.getTickCount() - lastCombatTick <= AMMO_WINDOW_TICKS;
		if (config.trackAmmo() && inCombat && !isStorageOpen())
		{
			if (newAmmoId == ammoId && newAmmoQty < ammoQty)
			{
				supplies.recordDirect(SupplyTracker.Kind.AMMO, ammoId, ammoQty - newAmmoQty);
			}
			// Thrown weapons (darts, knives, chinchompas) are stackable and held in the weapon slot.
			if (newWeaponId == weaponId && newWeaponQty < weaponQty && weaponQty > 1)
			{
				supplies.recordDirect(SupplyTracker.Kind.AMMO, weaponId, weaponQty - newWeaponQty);
			}
		}
		// Serpentine, tanzanite and magma helms show their scales as the item's quantity.
		Item head = container.getItem(EquipmentInventorySlot.HEAD.getSlotIdx());
		int newHeadId = head == null ? -1 : head.getId();
		int newHeadQty = head == null ? 0 : head.getQuantity();
		if (config.trackAmmo() && inCombat && headId > 0 && ChargedWeapons.isScaleHelm(itemInfo.name(headId)))
		{
			int used = 0;
			if (newHeadId == headId && newHeadQty < headQty)
			{
				used = headQty - newHeadQty;
			}
			else if (newHeadId > 0 && !ChargedWeapons.isScaleHelm(itemInfo.name(newHeadId))
				&& itemInfo.name(newHeadId).toLowerCase(Locale.ENGLISH).contains("helm") && headQty <= 10)
			{
				// The last scales used up: the helm turns into its uncharged version.
				used = headQty;
			}
			if (used > 0)
			{
				supplies.recordDirect(SupplyTracker.Kind.CHARGES, ItemID.SNAKEBOSS_SCALE, used);
			}
		}
		Item cape = container.getItem(EquipmentInventorySlot.CAPE.getSlotIdx());
		capeId = cape == null ? -1 : cape.getId();

		ammoId = newAmmoId;
		ammoQty = newAmmoQty;
		weaponId = newWeaponId;
		weaponQty = newWeaponQty;
		headId = newHeadId;
		headQty = newHeadQty;
	}

	// ---------------------------------------------------------------- charged weapons, quiver, cannon

	/**
	 * The blowpipe's shots are too quick to count by animation changes, so (like the
	 * Supplies Tracker plugin) a shot is counted when its attack animation is on its
	 * first frame at the start of a tick. Darts and scales aren't visible to plugins,
	 * so their use is the expected amount per shot.
	 */
	private void trackBlowpipe(int tick)
	{
		Player local = client.getLocalPlayer();
		// Once blowpipe shots have been seen as projectiles, those are counted instead:
		// one projectile is exactly one shot, where the animation can be missed or repeat.
		if (blowpipeProjectilesSeen || !config.trackAmmo() || local == null || weaponId <= 0
			|| tick == lastChargedAttackTick || local.getAnimationFrame() != 0
			|| !ChargedWeapons.isBlowpipeAttack(local.getAnimation()) || !ChargedWeapons.isBlowpipe(itemInfo.name(weaponId)))
		{
			return;
		}
		lastChargedAttackTick = tick;
		bookBlowpipeShot();
	}

	/** A blowpipe dart leaving the player: exactly one shot. */
	private void onBlowpipeProjectile(Projectile projectile, int tick)
	{
		if (!config.trackAmmo() || weaponId <= 0 || !ChargedWeapons.isBlowpipe(itemInfo.name(weaponId))
			|| !countedBlowpipeShots.add(projectile))
		{
			return;
		}
		if (!blowpipeProjectilesSeen)
		{
			blowpipeProjectilesSeen = true;
			if (tick - lastChargedAttackTick <= 1)
			{
				// This shot was already counted from its animation.
				return;
			}
		}
		lastChargedAttackTick = tick;
		bookBlowpipeShot();
	}

	private void bookBlowpipeShot()
	{
		bookingBlowpipe = true;
		try
		{
			bookBlowpipeCharges();
		}
		finally
		{
			bookingBlowpipe = false;
		}
	}

	private void bookBlowpipeCharges()
	{
		// 2 Zulrah's scales every 3 shots.
		blowpipeScaleRemainder += 2;
		int scales = blowpipeScaleRemainder / 3;
		blowpipeScaleRemainder %= 3;
		if (scales > 0)
		{
			supplies.recordDirect(SupplyTracker.Kind.CHARGES, ItemID.SNAKEBOSS_SCALE, scales);
		}
		int dart = ledger.data().blowpipeDart;
		if (dart > 0)
		{
			blowpipeDartRemainder += ChargedWeapons.dartLossRate(capeId > 0 ? itemInfo.name(capeId) : null);
			int darts = (int) blowpipeDartRemainder;
			blowpipeDartRemainder -= darts;
			if (darts > 0)
			{
				supplies.recordDirect(SupplyTracker.Kind.CHARGES, dart, darts);
			}
		}
	}

	/**
	 * Charged staves, the Scythe of vitur, Tumeken's shadow and revenant weapons:
	 * one charge per attack animation, priced at what refilling it costs.
	 */
	@Subscribe
	public void onAnimationChanged(AnimationChanged event)
	{
		if (!tracking() || !config.trackAmmo() || event.getActor() != client.getLocalPlayer() || weaponId <= 0)
		{
			return;
		}
		int animation = event.getActor().getAnimation();
		for (int[] part : ChargedWeapons.costPerAttack(itemInfo.name(weaponId), animation))
		{
			int itemId = part[0];
			int per = part[2];
			int quantity = part[1];
			if (per > 1)
			{
				// e.g. one vial of blood every 100 scythe swings
				int owed = chargeRemainders.merge(itemId, quantity, Integer::sum);
				quantity = owed / per;
				chargeRemainders.put(itemId, owed % per);
			}
			if (quantity > 0)
			{
				supplies.recordDirect(SupplyTracker.Kind.CHARGES, itemId, quantity);
			}
		}
	}

	private void setBlowpipeDart(int dartId)
	{
		if (dartId > 0 && ledger.data().blowpipeDart != dartId)
		{
			ledger.data().blowpipeDart = dartId;
			ledger.changed();
		}
	}

	/** Dizana's quiver keeps its ammo in varps, not the equipment's ammo slot. */
	private void trackQuiverAmmo()
	{
		int newId = client.getVarpValue(VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO);
		int newQty = client.getVarpValue(VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO_AMOUNT);
		updateCombatTarget(client.getTickCount());
		boolean inCombat = client.getTickCount() - lastCombatTick <= AMMO_WINDOW_TICKS;
		if (config.trackAmmo() && inCombat && !isStorageOpen() && quiverAmmoId > 0)
		{
			int used = 0;
			if (newId == quiverAmmoId && newQty < quiverAmmoQty)
			{
				used = quiverAmmoQty - newQty;
			}
			else if (newId <= 0 && quiverAmmoQty <= 2)
			{
				used = quiverAmmoQty;
			}
			if (used > 0 && used <= 2)
			{
				supplies.recordDirect(SupplyTracker.Kind.AMMO, quiverAmmoId, used);
			}
		}
		quiverAmmoId = newId;
		quiverAmmoQty = Math.max(0, newQty);
	}

	@Subscribe
	public void onProjectileMoved(ProjectileMoved event)
	{
		if (!tracking())
		{
			return;
		}
		Projectile projectile = event.getProjectile();
		int id = projectile.getId();

		// The dart flying out of a blowpipe tells us which dart is loaded.
		// A dart leaving our own tile while we fire a blowpipe tells us which dart is loaded.
		Integer dart = ChargedWeapons.BLOWPIPE_DART_PROJECTILES.get(id);
		Player local = client.getLocalPlayer();
		boolean fromMe = local != null
			&& (projectile.getSourceActor() == local || local.getWorldLocation().equals(projectile.getSourcePoint()));
		if (dart != null && fromMe && ChargedWeapons.isBlowpipeAttack(local.getAnimation()))
		{
			setBlowpipeDart(dart);
		}
		// Any projectile the player fires mid blowpipe attack is a shot (the special attack included).
		if (local != null && projectile.getSourceActor() == local && ChargedWeapons.isBlowpipeAttack(local.getAnimation()))
		{
			onBlowpipeProjectile(projectile, client.getTickCount());
		}

		// Remember which monster the cannon is shooting, so its cannonballs are charged to it.
		if (id == SpotanimID.CANNONBALL_TRAVEL || id == SpotanimID.CANNONBALL_TRAVEL_GRANITE
			|| id == SpotanimID.CANNONBALL_TRAVEL_LEAGUE || id == SpotanimID.CANNONBALL_TRAVEL_GRANITE_LEAGUE)
		{
			Actor target = projectile.getTargetActor();
			if (target instanceof NPC && npcName((NPC) target) != null)
			{
				cannonTargetName = npcName((NPC) target);
				cannonTargetIsTask = isTaskTarget(cannonTargetName);
				cannonTargetTick = client.getTickCount();
			}
			if (id == SpotanimID.CANNONBALL_TRAVEL_GRANITE || id == SpotanimID.CANNONBALL_TRAVEL_GRANITE_LEAGUE)
			{
				cannonballItem = ItemID.GRANITE_CANNONBALL;
			}
		}
	}

	/** Cannonballs go to the monster the cannon was shooting, else the one you're fighting. */
	private void bookCannonballs(int spent)
	{
		if (!config.trackCombat())
		{
			return;
		}
		int tick = client.getTickCount();
		String name;
		boolean task;
		if (cannonTargetName != null && tick - cannonTargetTick <= CANNON_TARGET_TICKS)
		{
			name = cannonTargetName;
			task = cannonTargetIsTask;
		}
		else if (combatKey != null && tick - lastCombatTick <= SUPPLY_ATTRIBUTION_TICKS)
		{
			name = combatName;
			task = combatIsTask;
		}
		else
		{
			supplies.recordDirect(SupplyTracker.Kind.CANNONBALL, cannonballItem, spent);
			return;
		}
		long gp = itemInfo.marketPrice(cannonballItem) * spent;
		ledger.addSupply(npcKey(name), name, SourceCategory.COMBAT, String.valueOf(cannonballItem), cannonballItem,
			itemInfo.name(cannonballItem), null, spent, gp);
		TaskRecord current = ledger.data().currentTask;
		if (task && current != null && !current.isFinished())
		{
			current.supplyGp += gp;
			ledger.changed();
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (!tracking())
		{
			return;
		}
		if (event.getVarpId() == VarPlayerID.ROCKTHROWER)
		{
			int now = event.getValue();
			int spent = cannonballs - now;
			boolean placed = client.getVarpValue(VarPlayerID.DROPCANNON) == CANNON_PLACED;
			if (cannonballs >= 0 && placed && spent > 0 && spent <= MAX_BALLS_PER_TICK)
			{
				bookCannonballs(spent);
			}
			cannonballs = now;
			if (reminders.onCannonballs(now, config.cannonReminderBalls(), placed))
			{
				remind(config.cannonReminder(), "Cannon", now == 0
					? "Your cannon is out of cannonballs."
					: "Your cannon has " + now + (now == 1 ? " cannonball" : " cannonballs") + " left.");
			}
			return;
		}

		if (event.getVarpId() == VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO
			|| event.getVarpId() == VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO_AMOUNT)
		{
			trackQuiverAmmo();
			return;
		}

		int varbit = event.getVarbitId();
		for (int i = 0; i < POUCH_QUANTITY_VARBITS.length; i++)
		{
			if (varbit == POUCH_TYPE_VARBITS[i])
			{
				pouchTypes[i] = event.getValue();
				pouchQuantities[i] = client.getVarbitValue(POUCH_QUANTITY_VARBITS[i]);
				return;
			}
			if (varbit == POUCH_QUANTITY_VARBITS[i])
			{
				int lost = pouchQuantities[i] - event.getValue();
				pouchQuantities[i] = event.getValue();
				int runeId = pouchRuneId(pouchTypes[i]);
				boolean pouchShuffle = client.getTickCount() - pouchClickTick <= POUCH_WINDOW_TICKS;
				if (lost > 0 && runeId > 0 && !isStorageOpen() && !pouchShuffle)
				{
					supplies.onPouchRunesLost(runeId, lost);
				}
				return;
			}
		}
	}

	private void syncPouchBaseline()
	{
		for (int i = 0; i < POUCH_TYPE_VARBITS.length; i++)
		{
			pouchTypes[i] = client.getVarbitValue(POUCH_TYPE_VARBITS[i]);
			pouchQuantities[i] = client.getVarbitValue(POUCH_QUANTITY_VARBITS[i]);
		}
	}

	private void syncEquipmentBaseline()
	{
		ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		if (worn != null)
		{
			Item ammo = worn.getItem(EquipmentInventorySlot.AMMO.getSlotIdx());
			Item weapon = worn.getItem(EquipmentInventorySlot.WEAPON.getSlotIdx());
			ammoId = ammo == null ? -1 : ammo.getId();
			ammoQty = ammo == null ? 0 : ammo.getQuantity();
			weaponId = weapon == null ? -1 : weapon.getId();
			weaponQty = weapon == null ? 0 : weapon.getQuantity();
			Item head = worn.getItem(EquipmentInventorySlot.HEAD.getSlotIdx());
			headId = head == null ? -1 : head.getId();
			headQty = head == null ? 0 : head.getQuantity();
			Item cape = worn.getItem(EquipmentInventorySlot.CAPE.getSlotIdx());
			capeId = cape == null ? -1 : cape.getId();
		}
		quiverAmmoId = client.getVarpValue(VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO);
		quiverAmmoQty = Math.max(0, client.getVarpValue(VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO_AMOUNT));
	}

	private int pouchRuneId(int type)
	{
		if (type <= 0)
		{
			return -1;
		}
		EnumComposition runes = client.getEnum(EnumID.RUNEPOUCH_RUNE);
		return runes == null ? -1 : runes.getIntValue(type);
	}

	// ---------------------------------------------------------------- XP

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		Skill skill = event.getSkill();
		int xp = event.getXp();
		Integer previous = lastXp.put(skill, xp);
		if (!tracking() || previous == null || xp <= previous)
		{
			return;
		}
		int gained = xp - previous;
		int tick = client.getTickCount();

		if (skill == Skill.MAGIC)
		{
			supplies.onMagicXp();
			// Teleport spells give Magic XP; it belongs to Teleports, not the last fight or skill.
			if (tick - lastTeleportCastTick <= TELEPORT_XP_WINDOW_TICKS)
			{
				ledger.addXp(TrackerData.TELEPORTS_KEY, TrackerData.TELEPORTS_NAME, SourceCategory.TELEPORTS,
					skill.getName(), gained);
				return;
			}
		}
		boolean fighting = combatKey != null && tick - lastCombatTick <= COMBAT_XP_WINDOW_TICKS;
		if (skill == Skill.FARMING && fighting && FarmingTracker.HESPORI.equalsIgnoreCase(combatName))
		{
			// Killing Hespori gives Farming XP: Farming's, not the fight's.
			ledger.addXp("farming:" + FarmingTracker.HESPORI, FarmingTracker.HESPORI, SourceCategory.FARMING,
				skill.getName(), gained);
			return;
		}
		if (skill == Skill.PRAYER && tick - buryClickTick > 5 && (fighting || !skilling.isActive())
			&& bookCrusherCharge(gained))
		{
			return;
		}
		if (config.trackFarming())
		{
			farming.onXp(skill.getName(), gained);
		}
		// Magic XP during a skilling action (alching mid-task, say) is that action's, not the fight's.
		boolean magicForSkilling = skill == Skill.MAGIC && skilling.isActive();
		boolean farmingXp = skill == Skill.FARMING
			|| (config.trackFarming() && farming.isArmed() && skill == Skill.WOODCUTTING);
		if (!NON_SKILLING_XP.contains(skill) && !farmingXp
			&& (!fighting || skill != Skill.MAGIC || magicForSkilling))
		{
			if (skilling.isActive())
			{
				skilling.onXp(skill.getName(), gained);
			}
			else if (skill != Skill.MAGIC || tick - lastCombatTick > COMBAT_XP_WINDOW_TICKS)
			{
				// XP from nothing being done: minigame rewards (Guardians of the Rift, Wintertodt...),
				// lamps and the like go to the skill's "Other".
				skilling.bookOtherXp(skill.getName(), gained);
			}
		}

		TaskRecord task = ledger.data().currentTask;
		boolean taskActive = task != null && !task.isFinished();
		if (skill == Skill.SLAYER && taskActive)
		{
			task.slayerXp += gained;
			ledger.changed();
		}

		if (config.trackCombat() && COMBAT_SKILLS.contains(skill) && combatKey != null
			&& tick - lastCombatTick <= COMBAT_XP_WINDOW_TICKS && !magicForSkilling)
		{
			ledger.addXp(combatKey, combatName, SourceCategory.COMBAT, skill.getName(), gained);
			if (combatIsTask && taskActive && skill != Skill.SLAYER)
			{
				task.xp += gained;
				ledger.changed();
			}
		}
	}

	// ---------------------------------------------------------------- chat

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() == ChatMessageType.GAMEMESSAGE
			&& Text.removeTags(event.getMessage()).startsWith("A superior foe has appeared"))
		{
			remind(config.superiorReminder(), "Superior spawn", "A superior foe has appeared!");
		}
		if (tracking() && (event.getType() == ChatMessageType.GAMEMESSAGE || event.getType() == ChatMessageType.SPAM))
		{
			// "You've caught a crimson swift." names what a trap caught (some are filtered spam).
			skilling.onChatMessage(Text.removeTags(event.getMessage()), client.getTickCount());
		}
		if (tracking() && event.getType() == ChatMessageType.GAMEMESSAGE)
		{
			// "Check" on a blowpipe: "Darts: Adamant dart x 1,234. Scales: ..." tells us the dart.
			String message = Text.removeTags(event.getMessage());
			String dart = ChargedWeapons.dartFromCheckMessage(message);
			int dartId = -1;
			if (dart != null)
			{
				for (int id : ChargedWeapons.BLOWPIPE_DART_PROJECTILES.values())
				{
					if (dart.equalsIgnoreCase(itemInfo.name(id)))
					{
						setBlowpipeDart(id);
						dartId = id;
					}
				}
			}
			long[] counts = ChargedWeapons.blowpipeCheckCounts(message);
			if (counts != null)
			{
				// The exact darts and scales left: correct the per-shot estimate since the last Check.
				recentSupplies.onBlowpipeCheck(dartId, counts[0], counts[1], ItemID.SNAKEBOSS_SCALE, itemInfo::marketPrice);
				blowpipeDartRemainder = 0;
				blowpipeScaleRemainder = 0;
			}
		}
		if (tracking() && config.trackFarming()
			&& (event.getType() == ChatMessageType.GAMEMESSAGE || event.getType() == ChatMessageType.SPAM))
		{
			farming.onChatMessage(Text.removeTags(event.getMessage()), localTile(), client.getTickCount());
		}
	}

	// ---------------------------------------------------------------- tick

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!tracking())
		{
			return;
		}
		int tick = client.getTickCount();
		updateCombatTarget(tick);
		trackBlowpipe(tick);
		processCarriedChanges(tick);
		supplies.onGameTick(tick);
		// Runes taken from the rune pouch by no spell (combination runecrafting) cost the action.
		Map<Integer, Integer> pouchRunes = supplies.takeUnusedPouchRunes();
		if (!pouchRunes.isEmpty() && skilling.isActive())
		{
			Map<Integer, Integer> used = new HashMap<>();
			pouchRunes.forEach((id, qty) -> used.put(id, -qty));
			skilling.onInventoryChanged(InventoryDiff.of(used));
		}
		farming.onGameTick(tick, localTile());
		skilling.onGameTick(tick, localTile());
		groundDrops.expire(tick);
		recentSupplies.expire(tick);
		if (profileLoaded)
		{
			sessions.heartbeat();
		}
		boolean inInstance = client.isInInstancedRegion();
		if (inInstance && !wasInInstance)
		{
			instanceEnteredTick = tick;
		}
		wasInInstance = inInstance;
		checkReminders(tick);

		if (tick - loginTick > SLAYER_SETTLE_TICKS)
		{
			syncSlayerTask();
		}
		if (tick % SAVE_INTERVAL_TICKS == 0)
		{
			save();
		}
		refreshPanel(false);
	}

	// ---------------------------------------------------------------- reminders

	private void checkReminders(int tick)
	{
		if (reminders.onPrayer(client.getBoostedSkillLevel(Skill.PRAYER), config.prayerReminderPoints()))
		{
			remind(config.prayerReminder(), "Prayer",
				"Your prayer is low: " + client.getBoostedSkillLevel(Skill.PRAYER) + " points left.");
		}

		Map<String, Integer> boosts = new LinkedHashMap<>();
		Map<String, Integer> divine = new HashMap<>();
		for (Skill skill : BOOSTED_SKILLS)
		{
			if (config.boostReminderCombatOnly() && !COMBAT_BOOST_SKILLS.contains(skill))
			{
				continue;
			}
			boosts.put(skill.getName(), client.getBoostedSkillLevel(skill) - client.getRealSkillLevel(skill));
			divine.put(skill.getName(), divinePotionTicks(skill));
		}
		int warnTicks = (int) Math.ceil(config.boostReminderSeconds() / 0.6);
		List<String> ending = reminders.onBoosts(tick, boosts, divine, client.isPrayerActive(Prayer.PRESERVE), warnTicks);
		if (!ending.isEmpty())
		{
			remind(config.boostReminder(), "Boost", "Your " + joinSkills(ending)
				+ (ending.size() == 1 ? " boost runs" : " boosts run") + " out in about "
				+ config.boostReminderSeconds() + " seconds.");
		}
	}

	/**
	 * Prayer XP with no burying or scattering: a bonecrusher or ash sanctifier did it as a
	 * monster died. Each one uses a charge (the ash sanctifier's are a tenth of a death rune;
	 * the bonecrusher's ecto-tokens are worth nothing). Returns true if it was booked.
	 */
	private boolean bookCrusherCharge(long xp)
	{
		boolean crusher = carries("bonecrusher");
		boolean sanctifier = carries("ash sanctifier");
		if (!crusher && !sanctifier)
		{
			return false;
		}
		boolean ashes = sanctifier && (!crusher || ItemRules.isAshSanctifierXp(xp));
		String name = ashes ? "Ash sanctifier" : "Bonecrusher";
		int itemId = itemInfo.idByName(name);
		long gp = ashes ? Math.round(itemInfo.marketPrice(ItemID.DEATHRUNE) / 10.0) : 0;
		String key = "item:PRAYER:" + name;
		ledger.addSupply(key, name, SourceCategory.PRAYER, name.toLowerCase(Locale.ENGLISH) + "-charge",
			Math.max(itemId, 0), name, "charges", 1, gp);
		ledger.addXp(key, name, SourceCategory.PRAYER, Skill.PRAYER.getName(), xp);
		return true;
	}

	/** Is an item whose name starts with this carried (inventory or worn)? */
	private boolean carries(String namePrefix)
	{
		for (int containerId : new int[]{InventoryID.INV, InventoryID.WORN})
		{
			ItemContainer container = client.getItemContainer(containerId);
			if (container == null)
			{
				continue;
			}
			for (Item item : container.getItems())
			{
				if (item != null && item.getId() > 0
					&& itemInfo.name(item.getId()).toLowerCase(Locale.ENGLISH).startsWith(namePrefix))
				{
					return true;
				}
			}
		}
		return false;
	}

	/** Sends a reminder's notification, plus the popup box saying what it's for. */
	private void remind(Notification notification, String title, String message)
	{
		if (!notification.isEnabled())
		{
			return;
		}
		notifier.notify(notification, message);
		if (config.reminderPopup())
		{
			popup.show(title, message);
		}
	}

	/** Game ticks left on a divine potion boosting this skill (the varbits count game ticks). */
	private int divinePotionTicks(Skill skill)
	{
		int combat = client.getVarbitValue(VarbitID.DIVINECOMBAT_POTION_TIME);
		int bastion = client.getVarbitValue(VarbitID.DIVINEBASTION_POTION_TIME);
		int battlemage = client.getVarbitValue(VarbitID.DIVINEBATTLEMAGE_POTION_TIME);
		int steps;
		switch (skill)
		{
			case ATTACK:
				steps = Math.max(combat, client.getVarbitValue(VarbitID.DIVINEATTACK_POTION_TIME));
				break;
			case STRENGTH:
				steps = Math.max(combat, client.getVarbitValue(VarbitID.DIVINESTRENGTH_POTION_TIME));
				break;
			case DEFENCE:
				steps = Math.max(Math.max(combat, client.getVarbitValue(VarbitID.DIVINEDEFENCE_POTION_TIME)),
					Math.max(bastion, battlemage));
				break;
			case RANGED:
				steps = Math.max(bastion, client.getVarbitValue(VarbitID.DIVINERANGE_POTION_TIME));
				break;
			case MAGIC:
				steps = Math.max(battlemage, client.getVarbitValue(VarbitID.DIVINEMAGIC_POTION_TIME));
				break;
			default:
				steps = 0;
		}
		return steps;
	}

	private static Skill skillFor(SourceCategory category)
	{
		String skillName = category == null ? null : category.getSkillName();
		if (skillName == null)
		{
			return null;
		}
		for (Skill skill : Skill.values())
		{
			if (skill.getName().equalsIgnoreCase(skillName))
			{
				return skill;
			}
		}
		return null;
	}

	/** "Attack", "Attack and Strength", "Attack, Strength and Defence". */
	private static String joinSkills(List<String> skills)
	{
		if (skills.size() == 1)
		{
			return skills.get(0);
		}
		return String.join(", ", skills.subList(0, skills.size() - 1)) + " and " + skills.get(skills.size() - 1);
	}

	private void updateCombatTarget(int tick)
	{
		Player local = client.getLocalPlayer();
		if (local == null || !(local.getInteracting() instanceof NPC))
		{
			return;
		}
		NPC npc = (NPC) local.getInteracting();
		if (!isAttackable(npc))
		{
			return;
		}
		String name = npcName(npc);
		if (name == null)
		{
			return;
		}
		if (combatKey == null || !combatKey.equals(npcKey(name)) || tick - lastCombatTick > SUPPLY_ATTRIBUTION_TICKS)
		{
			fightStartTick = tick;
		}
		combatName = name;
		combatKey = npcKey(name);
		combatIsTask = isTaskTarget(name);
		lastCombatTick = tick;
	}

	private void syncSlayerTask()
	{
		String task = slayerService.getTask();
		int remaining = slayerService.getRemainingAmount();
		TrackerData data = ledger.data();
		TaskRecord current = data.currentTask;
		long now = ledger.now();

		if (task == null || task.isEmpty() || remaining <= 0)
		{
			// The Slayer plugin reports a blank task briefly while loading, so wait for it to settle.
			if (current != null && !current.isFinished() && ++slayerBlankCount >= SLAYER_BLANK_CONFIRMATIONS)
			{
				current.remainingAmount = 0;
				current.finishedMillis = now;
				ledger.changed();
			}
			return;
		}
		slayerBlankCount = 0;

		if (current == null || current.isFinished() || !task.equalsIgnoreCase(current.taskName))
		{
			data.archiveCurrentTask(now);
			data.currentTask = new TaskRecord(task, slayerService.getTaskLocation(),
				slayerService.getInitialAmount(), remaining, now);
			taskTargetNames.clear();
			ledger.changed();
		}
		else if (current.remainingAmount != remaining)
		{
			current.remainingAmount = remaining;
			current.initialAmount = Math.max(current.initialAmount, slayerService.getInitialAmount());
			ledger.changed();
		}

		for (NPC npc : slayerService.getTargets())
		{
			String name = npcName(npc);
			if (name != null)
			{
				taskTargetNames.add(name.toLowerCase(Locale.ENGLISH));
			}
		}
	}

	// ---------------------------------------------------------------- supply booking

	private void bookSupply(SupplyTracker.Use use)
	{
		if (ItemRules.isMinigameItem(itemInfo.name(use.itemId)))
		{
			return;
		}
		// Teleports are their own category, never charged to a monster, task or skill.
		if (use.teleport != null)
		{
			ledger.addSupply(TrackerData.TELEPORTS_KEY, TrackerData.TELEPORTS_NAME, SourceCategory.TELEPORTS,
				use.lineKey, use.itemId, use.lineName, use.unit, use.quantity, use.gp);
			if (use.countsAsUse)
			{
				ledger.incrementCount(TrackerData.TELEPORTS_KEY, TrackerData.TELEPORTS_NAME, SourceCategory.TELEPORTS);
			}
			return;
		}

		int tick = client.getTickCount();
		// Supplies used on the very first attack arrive before this tick's target update.
		updateCombatTarget(tick);
		boolean recentFight = config.trackCombat() && combatKey != null
			&& tick - lastCombatTick <= SUPPLY_ATTRIBUTION_TICKS;

		// During a skilling action (alching, say), its runes and food are that action's, even
		// mid-Slayer task. Cannonballs and arrows only ever come from a fight.
		boolean combatOnly = use.kind == SupplyTracker.Kind.CANNONBALL || use.kind == SupplyTracker.Kind.AMMO
			|| use.kind == SupplyTracker.Kind.CHARGES;
		if (!combatOnly && skilling.onSupplyUsed(use))
		{
			return;
		}
		if (!recentFight)
		{
			// Used outside any fight or skilling action: one General supplies table.
			ledger.addSupply(TrackerData.GENERAL_SUPPLIES_KEY, TrackerData.GENERAL_SUPPLIES_NAME,
				SourceCategory.GENERAL_SUPPLIES, use.lineKey, use.itemId, use.lineName, use.unit, use.quantity, use.gp);
			ledger.incrementCount(TrackerData.GENERAL_SUPPLIES_KEY, TrackerData.GENERAL_SUPPLIES_NAME,
				SourceCategory.GENERAL_SUPPLIES);
			RecentSupplies.Booked booked = new RecentSupplies.Booked(TrackerData.GENERAL_SUPPLIES_KEY,
				TrackerData.GENERAL_SUPPLIES_NAME, SourceCategory.GENERAL_SUPPLIES, false, use.lineKey, use.itemId,
				use.lineName, use.unit, use.quantity, use.gp, tick, true);
			recentSupplies.addGeneral(booked);
			if (bookingBlowpipe)
			{
				recentSupplies.addBlowpipe(booked);
			}
			return;
		}
		ledger.addSupply(combatKey, combatName, SourceCategory.COMBAT, use.lineKey, use.itemId, use.lineName,
			use.unit, use.quantity, use.gp);
		if (bookingBlowpipe)
		{
			recentSupplies.addBlowpipe(new RecentSupplies.Booked(combatKey, combatName, SourceCategory.COMBAT,
				combatIsTask, use.lineKey, use.itemId, use.lineName, use.unit, use.quantity, use.gp, tick, false));
		}

		TaskRecord task = ledger.data().currentTask;
		if (recentFight && combatIsTask && task != null && !task.isFinished())
		{
			task.supplyGp += use.gp;
			ledger.changed();
		}
	}

	// ---------------------------------------------------------------- panel

	private void onPanelAction(GildedLedgerPanel.Action action, String key)
	{
		clientThread.invoke(() ->
		{
			switch (action)
			{
				case RESET_SOURCE:
					ledger.remove(key);
					break;
				case RESET_CATEGORY:
					SourceCategory category = SourceCategory.valueOf(key);
					if (ledger.data().sources.values().removeIf(r -> r.category == category))
					{
						ledger.changed();
					}
					break;
				case RESET_ALL:
					ledger.data().sources.clear();
					resetTaskTotals();
					ledger.changed();
					break;
				case RESET_TASK:
					resetTaskTotals();
					break;
				case TOGGLE_LOOT:
				case TOGGLE_SUPPLY:
					toggleExcluded(action, key);
					break;
				case SESSION_START:
					sessions.start();
					break;
				case SESSION_PAUSE:
					sessions.pause();
					break;
				case SESSION_RESUME:
					sessions.resume();
					break;
				case SESSION_END:
					sessions.end();
					break;
				case SESSION_RENAME:
				{
					int split = key.indexOf('\n');
					if (split > 0)
					{
						sessions.rename(key.substring(0, split), key.substring(split + 1));
					}
					break;
				}
				case SESSION_DELETE:
					sessions.delete(key);
					break;
				case SESSION_CLEAR:
					sessions.clearLog();
					break;
			}
			save();
			refreshPanel(true);
		});
	}

	/** Right-click "Exclude from profit" / "Include in profit" on a loot or supply icon. */
	private void toggleExcluded(GildedLedgerPanel.Action action, String key)
	{
		int split = key.indexOf('\n');
		if (split < 0)
		{
			return;
		}
		String sourceKey = key.substring(0, split);
		String item = key.substring(split + 1);
		boolean loot = action == GildedLedgerPanel.Action.TOGGLE_LOOT;
		int itemId;
		try
		{
			itemId = loot ? Integer.parseInt(item) : -1;
		}
		catch (NumberFormatException e)
		{
			return;
		}

		// A skill shown as one combined table: apply to every entry in that skill.
		List<SourceRecord> records = new ArrayList<>();
		if (sourceKey.startsWith(PanelModel.COMBINED_PREFIX))
		{
			SourceCategory category;
			try
			{
				category = SourceCategory.valueOf(sourceKey.substring(PanelModel.COMBINED_PREFIX.length()));
			}
			catch (IllegalArgumentException e)
			{
				return;
			}
			for (SourceRecord r : ledger.data().sources.values())
			{
				if (r.category == category && (loot ? r.loot.containsKey(itemId) : r.supplies.containsKey(item)))
				{
					records.add(r);
				}
			}
		}
		else if (ledger.data().sources.containsKey(sourceKey))
		{
			records.add(ledger.data().sources.get(sourceKey));
		}
		if (records.isEmpty())
		{
			return;
		}

		boolean exclude = false;
		for (SourceRecord r : records)
		{
			exclude |= loot ? !r.excludedLoot.contains(itemId) : !r.excludedSupplies.contains(item);
		}
		for (SourceRecord r : records)
		{
			if (loot)
			{
				if (exclude)
				{
					r.excludedLoot.add(itemId);
				}
				else
				{
					r.excludedLoot.remove(itemId);
				}
			}
			else if (exclude)
			{
				r.excludedSupplies.add(item);
			}
			else
			{
				r.excludedSupplies.remove(item);
			}
		}
		ledger.changed();
	}

	/** "High Level Alchemy -> Rune platebody" → "High Level Alchemy". */
	private static String spellName(String target)
	{
		if (target == null)
		{
			return null;
		}
		int arrow = target.indexOf("->");
		String spell = (arrow >= 0 ? target.substring(0, arrow) : target).trim();
		return spell.isEmpty() ? null : spell;
	}

	/** Zeroes the current Slayer task's totals but keeps the task itself. */
	private void resetTaskTotals()
	{
		TaskRecord t = ledger.data().currentTask;
		if (t == null)
		{
			return;
		}
		t.kills = 0;
		t.lootGp = 0;
		t.supplyGp = 0;
		t.xp = 0;
		t.slayerXp = 0;
		t.startedMillis = ledger.now();
		ledger.changed();
	}

	private void refreshPanel(boolean force)
	{
		if (panel == null || ledger == null)
		{
			return;
		}
		if (!force && ledger.revision() == shownRevision)
		{
			return;
		}
		shownRevision = ledger.revision();
		Set<SourceCategory> hiddenCategories = hiddenCategories();
		boolean hidePlants = config.hideUntradeablePlants();
		Set<String> untradeableCrops = new HashSet<>(ledger.data().untradeableCrops);
		PanelModel model = PanelModel.build(ledger.data(), lootPrices, itemInfo::name, profileLoaded,
			config.skillLayout() == GildedLedgerConfig.SkillLayout.COMBINED,
			record -> hiddenCategories.contains(record.category)
				|| (hidePlants && record.category == SourceCategory.FARMING && untradeableCrops.contains(record.name)));
		boolean collapseNew = config.collapseNew();
		GpSession liveSession = profileLoaded ? sessions.live() : null;
		List<GpSession> sessionLog = new ArrayList<>(ledger.data().sessionLog);
		Map<String, PanelModel.SessionDetail> sessionDetails = new HashMap<>();
		for (GpSession logged : sessionLog)
		{
			sessionDetails.put(logged.id, PanelModel.sessionDetail(logged, lootPrices, itemInfo::name));
		}
		GildedLedgerPanel target = panel;
		SwingUtilities.invokeLater(() ->
		{
			target.setCollapseNew(collapseNew);
			target.update(model);
			target.updateSessions(liveSession, sessionLog, sessionDetails);
		});
	}

	// ---------------------------------------------------------------- helpers

	/** GE price, falling back to High Alchemy for untradeable items. Client thread only. */
	private final LootPrices lootPrices = new LootPrices()
	{
		@Override
		public long ge(int itemId)
		{
			int id = itemManager.canonicalize(itemId);
			if (id == ItemID.COINS)
			{
				return 1;
			}
			if (id == ItemID.PLATINUM)
			{
				return 1000;
			}
			if (isZeroValue(id))
			{
				return 0;
			}
			return itemManager.getItemPrice(id);
		}

		@Override
		public long highAlch(int itemId)
		{
			int id = itemManager.canonicalize(itemId);
			if (id == ItemID.COINS)
			{
				return 1;
			}
			if (id == ItemID.PLATINUM)
			{
				return 1000;
			}
			ItemComposition comp = itemManager.getItemComposition(id);
			return comp == null || ItemRules.isZeroValue(comp.getName()) ? 0 : comp.getHaPrice();
		}

		@Override
		public boolean valuedAtAlch(int itemId)
		{
			ItemComposition comp = itemManager.getItemComposition(itemManager.canonicalize(itemId));
			return comp != null && ItemRules.isTarnished(comp.getName());
		}

		@Override
		public boolean tradeable(int itemId)
		{
			int id = itemManager.canonicalize(itemId);
			if (id == ItemID.COINS || id == ItemID.PLATINUM)
			{
				return true;
			}
			ItemComposition comp = itemManager.getItemComposition(id);
			return comp != null && comp.isTradeable();
		}
	};

	/** Clues and reward currencies (marks of grace, abyssal pearls...) are worth nothing here. */
	private boolean isZeroValue(int itemId)
	{
		ItemComposition comp = itemManager.getItemComposition(itemId);
		return comp != null && ItemRules.isZeroValue(comp.getName());
	}

	private long lootPrice(int itemId)
	{
		return lootPrices.value(itemId);
	}

	private static final int[] STORAGE_INTERFACES = {
		InterfaceID.Bankmain.UNIVERSE,
		InterfaceID.BankDepositbox.UNIVERSE,
		InterfaceID.FarmingTools.UNIVERSE,
		InterfaceID.SeedVault.UNIVERSE
	};

	/** Bank, deposit box, tool leprechaun or seed vault is on screen. */
	private boolean isStorageOpen()
	{
		for (int component : STORAGE_INTERFACES)
		{
			Widget widget = client.getWidget(component);
			if (widget != null && !widget.isHidden())
			{
				return true;
			}
		}
		return false;
	}

	private boolean isTaskTarget(String npcName)
	{
		TaskRecord task = ledger.data().currentTask;
		return task != null && !task.isFinished()
			&& taskTargetNames.contains(npcName.toLowerCase(Locale.ENGLISH));
	}

	private static boolean isAttackable(NPC npc)
	{
		NPCComposition comp = npc.getTransformedComposition();
		if (comp == null || comp.getActions() == null)
		{
			return false;
		}
		for (String action : comp.getActions())
		{
			if ("Attack".equalsIgnoreCase(action))
			{
				return true;
			}
		}
		return false;
	}

	private static String npcName(NPC npc)
	{
		if (npc == null || npc.getName() == null)
		{
			return null;
		}
		String name = Text.removeTags(npc.getName()).trim();
		// Parts of a boss fight (Hespori's flowers, Zulrah's snakelings) are filed under the boss.
		return name.isEmpty() || "null".equalsIgnoreCase(name) ? null : ItemRules.bossGroup(name);
	}

	private static String npcKey(String name)
	{
		return "npc:" + name;
	}

	private int inventoryCount(int itemId)
	{
		ItemContainer inv = client.getItemContainer(InventoryID.INV);
		return inv == null ? 0 : inv.count(itemId);
	}

	private static Map<Integer, Integer> counts(ItemContainer container)
	{
		Item[] items = container.getItems();
		int[] ids = new int[items.length];
		int[] qty = new int[items.length];
		for (int i = 0; i < items.length; i++)
		{
			ids[i] = items[i].getId();
			qty[i] = items[i].getQuantity();
		}
		return InventoryDiff.counts(ids, qty);
	}

	private TileKey localTile()
	{
		Player local = client.getLocalPlayer();
		return local == null ? null : tileKey(local.getWorldLocation());
	}

	private TileKey sceneTile(int sceneX, int sceneY)
	{
		WorldView wv = client.getTopLevelWorldView();
		return tileKey(WorldPoint.fromScene(wv, sceneX, sceneY, wv.getPlane()));
	}

	private static TileKey tileKey(Tile tile)
	{
		return tile == null ? null : tileKey(tile.getWorldLocation());
	}

	private static TileKey tileKey(WorldPoint wp)
	{
		return wp == null ? null : new TileKey(wp.getX(), wp.getY(), wp.getPlane());
	}

	/** Item lookups backed by RuneLite's item manager. Must be used on the client thread. */
	private final class ClientItemInfo implements ItemInfo
	{
		@Override
		public String name(int itemId)
		{
			ItemComposition comp = itemManager.getItemComposition(itemId);
			return comp == null ? "" : comp.getName();
		}

		@Override
		public long marketPrice(int itemId)
		{
			int id = itemManager.canonicalize(itemId);
			if (id == ItemID.COINS)
			{
				return 1;
			}
			if (isZeroValue(id))
			{
				return 0;
			}
			long ge = itemManager.getItemPrice(id);
			if (ge > 0)
			{
				return ge;
			}
			// No GE price: tradeable items and tarnished items (a tarnished ring polishes into
			// a real ring) use their High Alchemy value; anything else untradeable is worth 0.
			ItemComposition item = itemManager.getItemComposition(id);
			if (item == null || !(item.isTradeable() || ItemRules.isTarnished(item.getName())))
			{
				return 0;
			}
			return Math.max(0, item.getHaPrice());
		}

		private final Map<String, Integer> idsByName = new HashMap<>();

		@Override
		public int idByName(String name)
		{
			if (name == null || name.isEmpty())
			{
				return -1;
			}
			String key = name.toLowerCase(Locale.ENGLISH);
			return idsByName.computeIfAbsent(key, k ->
			{
				for (var price : itemManager.search(k))
				{
					if (price.getName().equalsIgnoreCase(k))
					{
						return price.getId();
					}
				}
				return -1;
			});
		}

		@Override
		public boolean stackable(int itemId)
		{
			ItemComposition comp = itemManager.getItemComposition(itemId);
			return comp != null && comp.isStackable();
		}

		@Override
		public boolean tradeable(int itemId)
		{
			ItemComposition comp = itemManager.getItemComposition(itemManager.canonicalize(itemId));
			return comp != null && comp.isTradeable();
		}

		@Override
		public int canonical(int itemId)
		{
			return itemManager.canonicalize(itemId);
		}
	}
}
