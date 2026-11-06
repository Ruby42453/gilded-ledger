package com.gildedledger.tracking;

import com.gildedledger.data.SourceCategory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Tracks everything that isn't combat, farming or a purchase. Every action is
 * filed under the skill that gave it the most XP; actions that give no XP at all
 * (combining crystal key halves, making unfinished potions) go to Processing.
 *
 * <ul>
 *   <li><b>Gathering</b>: clicking a tree, rock, fishing spot, stall or pickpocket
 *       target. Items gained are loot; stackable consumables used up (bait,
 *       feathers) are costs. Named after the target.</li>
 *   <li><b>Processing</b>: using an item on an item or on a station, or Clean,
 *       Light, Craft-rune and similar. Items used up are costs, items made are
 *       loot. Named after the main product.</li>
 *   <li><b>Anything else that gives XP</b>: clicking some other object or NPC
 *       (tending a Forester's campfire, for example). Only booked once XP arrives,
 *       so doors, ladders and shop counters never show up. Named after the object
 *       or NPC.</li>
 * </ul>
 *
 * An inventory change and the XP for it can land a tick apart, so a change with
 * no XP is held for one tick before deciding where it belongs. Any unrelated click
 * (walking, an NPC, a bank, a farming patch) ends the session.
 */
public class SkillingTracker
{
	static final int IDLE_TICKS = 100;
	static final int MAX_DISTANCE = 15;
	/** Lighting a fire removes the log a few ticks before the XP lands; wait this long for it. */
	static final int XP_WAIT_TICKS = 10;
	private static final int COINS = 995;

	private enum Mode
	{
		NONE, GATHERING, PROCESSING, OTHER
	}

	private final Ledger ledger;
	private final ItemInfo items;

	private Mode mode = Mode.NONE;
	private SourceCategory targetCategory;
	private String targetName;
	private TileKey targetTile;
	private int lastActivityTick;
	private String lastKey;
	/** The spell behind the current processing action, if it is one. */
	private String actionSpell;
	private String lastName;
	private SourceCategory lastCategory;
	private int lastBookedTick = Integer.MIN_VALUE / 2;
	/** Items gained within this many ticks after an action, with no XP of their own, are part of it. */
	static final int TRAILING_GAIN_TICKS = 3;

	private final Map<Integer, Integer> tickChanges = new HashMap<>();
	private final Map<String, Long> tickXp = new HashMap<>();
	private final List<SupplyTracker.Use> tickSupplies = new ArrayList<>();

	/** Changes from the previous tick that came with no XP, waiting to see if XP follows. */
	/** XP with no items for this long after the last item change isn't the action's: it's "Other". */
	static final int XP_FOLLOW_TICKS = 5;
	private int lastItemTick = Integer.MIN_VALUE / 2;
	/** The group (fish, rock...) this session's catches go to, once known. */
	private String sessionGroup;
	private String pendingCreature;
	private int pendingCreatureTick = Integer.MIN_VALUE / 2;
	private int currentTick;
	private final Map<String, Long> pendingOtherXp = new HashMap<>();
	/** Fishing bait used before the session's first catch. */
	private final Map<Integer, Integer> earlyChanges = new HashMap<>();
	private final List<SupplyTracker.Use> earlySupplies = new ArrayList<>();
	private final Map<String, Long> earlyXp = new HashMap<>();
	/** Catches named in chat this tick (item → count). */
	private final Map<Integer, Integer> caughtThisTick = new HashMap<>();
	private int destroyedCatchTick = Integer.MIN_VALUE / 2;

	private final Map<Integer, Integer> heldChanges = new HashMap<>();
	private final List<SupplyTracker.Use> heldSupplies = new ArrayList<>();
	private int heldSinceTick;

	public SkillingTracker(Ledger ledger, ItemInfo items)
	{
		this.ledger = ledger;
		this.items = items;
	}

	/** The player clicked a gathering target, e.g. "Chop down" on a Yew tree. */
	public void onGatherClicked(SourceCategory category, String name, TileKey target, int tick)
	{
		if (category == null || name == null)
		{
			return;
		}
		flushHeld();
		lastKey = null;
		sessionGroup = null;
		mode = Mode.GATHERING;
		targetCategory = category;
		targetName = name;
		targetTile = target;
		lastActivityTick = tick;
	}

	/** The player started a processing action. */
	public void onProcessClicked(int tick)
	{
		onProcessClicked(tick, null);
	}

	/**
	 * The player started a processing action.
	 *
	 * @param spell the spell, when it's a spell (alching, Superglass Make): Magic is filed by spell
	 */
	public void onProcessClicked(int tick, String spell)
	{
		if (mode != Mode.PROCESSING)
		{
			flushHeld();
			lastKey = null;
			sessionGroup = null;
		}
		mode = Mode.PROCESSING;
		actionSpell = spell;
		lastActivityTick = tick;
	}

	/** Any other object or NPC; tracked only if it turns out to give XP. */
	public void onOtherTargetClicked(String name, TileKey target, int tick)
	{
		if (name == null)
		{
			onOtherAction();
			return;
		}
		flushHeld();
		lastKey = null;
		sessionGroup = null;
		mode = Mode.OTHER;
		targetName = name;
		targetTile = target;
		lastActivityTick = tick;
	}

	/** Anything that isn't part of the current skilling action. */
	public void onOtherAction()
	{
		flushHeld();
		mode = Mode.NONE;
	}

	public boolean isActive()
	{
		return mode != Mode.NONE;
	}

	public void onInventoryChanged(InventoryDiff diff)
	{
		if (mode != Mode.NONE)
		{
			diff.changes().forEach((id, delta) -> tickChanges.merge(id, delta, Integer::sum));
		}
	}

	public void onXp(String skill, long amount)
	{
		if (mode != Mode.NONE && amount > 0)
		{
			tickXp.merge(skill, amount, Long::sum);
		}
	}

	/** Food, potions, runes and so on used while skilling (e.g. runes for alching). */
	public boolean onSupplyUsed(SupplyTracker.Use use)
	{
		if (mode == Mode.NONE)
		{
			return false;
		}
		tickSupplies.add(use);
		return true;
	}

	/** XP from no action at all, such as a minigame's reward: the skill's "Other". */
	public void bookOtherXp(String skill, long amount)
	{
		SourceCategory category = SourceCategory.forSkill(skill);
		if (category == null || amount <= 0)
		{
			return;
		}
		ledger.addXp(otherKey(category), SkillGroups.OTHER, category, skill, amount);
	}

	public static String otherKey(SourceCategory category)
	{
		return "other:" + category.name();
	}

	/**
	 * Game messages: "You've caught a crimson swift." names the creature a trap caught,
	 * and "You catch a trout." / "You get some yew logs." / "You just found a Ruby!"
	 * name a catch, which counts even when it went straight into a fish barrel, log
	 * basket or gem bag instead of the inventory.
	 */
	public void onChatMessage(String message, int tick)
	{
		if (SkillGroups.isDestroyedCatch(message))
		{
			destroyedCatchTick = tick;
		}
		if (mode == Mode.GATHERING)
		{
			for (String name : SkillGroups.caughtItemNames(message))
			{
				int id = items.idByName(name);
				if (id > 0)
				{
					caughtThisTick.merge(id, 1, Integer::sum);
					break;
				}
			}
		}
		String creature = SkillGroups.creatureFromMessage(message);
		if (creature != null)
		{
			pendingCreature = creature;
			pendingCreatureTick = tick;
		}
	}

	/**
	 * A catch the game announced that never reached the inventory went into a fish
	 * barrel, log basket or gem bag: count it as if it had.
	 */
	private void addCatchesInBags(int tick)
	{
		if (mode == Mode.GATHERING && tick - destroyedCatchTick > 0)
		{
			caughtThisTick.forEach((id, count) ->
			{
				int gained = Math.max(0, tickChanges.getOrDefault(id, 0));
				if (gained < count)
				{
					tickChanges.merge(id, count - gained, Integer::sum);
				}
			});
		}
		caughtThisTick.clear();
	}

	/**
	 * XP in a tick with nothing gained or used, a while after the action last changed
	 * any items, isn't that action's (a minigame reward, for one): it goes to Other,
	 * unless items follow on the next tick.
	 */
	private boolean isLoneXp(int tick)
	{
		return !tickXp.isEmpty() && tickChanges.isEmpty() && tickSupplies.isEmpty()
			&& heldChanges.isEmpty() && heldSupplies.isEmpty() && tick - lastItemTick > XP_FOLLOW_TICKS;
	}

	public void onGameTick(int tick, TileKey playerTile)
	{
		currentTick = tick;
		addCatchesInBags(tick);
		boolean itemsThisTick = !tickChanges.isEmpty() || !tickSupplies.isEmpty();
		if (!pendingOtherXp.isEmpty())
		{
			// Last tick's lone XP: if items turned up now, it was this action's after all.
			if (itemsThisTick)
			{
				pendingOtherXp.forEach((skill, xp) -> tickXp.merge(skill, xp, Long::sum));
			}
			else
			{
				pendingOtherXp.forEach(this::bookOtherXp);
			}
			pendingOtherXp.clear();
		}
		if (itemsThisTick)
		{
			lastItemTick = tick;
		}
		if (mode != Mode.NONE && isLoneXp(tick))
		{
			pendingOtherXp.putAll(tickXp);
			tickXp.clear();
		}
		if (mode == Mode.GATHERING)
		{
			resolveGathering(tick);
		}
		else if (mode == Mode.PROCESSING)
		{
			resolveProcessing(tick);
		}
		else if (mode == Mode.OTHER)
		{
			resolveOther(tick);
		}
		tickChanges.clear();
		tickXp.clear();
		tickSupplies.clear();

		boolean idle = tick - lastActivityTick > IDLE_TICKS;
		boolean walkedAway = (mode == Mode.GATHERING || mode == Mode.OTHER)
			&& targetTile != null && playerTile != null && targetTile.distanceTo(playerTile) > MAX_DISTANCE;
		if (mode != Mode.NONE && (idle || walkedAway))
		{
			onOtherAction();
		}
	}

	public void reset()
	{
		mode = Mode.NONE;
		tickChanges.clear();
		tickXp.clear();
		tickSupplies.clear();
		heldChanges.clear();
		heldSupplies.clear();
		sessionGroup = null;
		pendingCreature = null;
		pendingOtherXp.clear();
		clearEarly();
	}

	// ---------------------------------------------------------------- gathering

	private void resolveGathering(int tick)
	{
		// Drift net fishing gives Fishing and Hunter XP: it goes wherever more XP went.
		SourceCategory bySkill = SourceCategory.forSkill(topSkill(tickXp));
		if (bySkill != null)
		{
			targetCategory = bySkill;
		}
		if (!tickChanges.isEmpty() || !tickXp.isEmpty() || !tickSupplies.isEmpty())
		{
			if (targetCategory == SourceCategory.FISHING && sessionGroup == null && !caughtFish(tickChanges))
			{
				// Bait used before the first catch: we don't know the fish yet, so keep it
				// until we do (a karambwanji used on a karambwan spot belongs to Karambwan).
				tickChanges.forEach((id, delta) -> earlyChanges.merge(id, delta, Integer::sum));
				earlySupplies.addAll(tickSupplies);
				tickXp.forEach((skill, xp) -> earlyXp.merge(skill, xp, Long::sum));
				lastActivityTick = tick;
				return;
			}
			Map<Integer, Integer> changes = new HashMap<>(tickChanges);
			List<SupplyTracker.Use> supplies = new ArrayList<>(tickSupplies);
			Map<String, Long> xp = new HashMap<>(tickXp);
			earlyChanges.forEach((id, delta) -> changes.merge(id, delta, Integer::sum));
			supplies.addAll(earlySupplies);
			earlyXp.forEach((skill, amount) -> xp.merge(skill, amount, Long::sum));
			clearEarly();
			bookAction(changes, supplies, xp, targetCategory, true);
			lastActivityTick = tick;
		}
	}

	private boolean caughtFish(Map<Integer, Integer> changes)
	{
		return changes.entrySet().stream().anyMatch(e -> e.getValue() > 0 && SkillGroups.isFish(items.name(e.getKey())));
	}

	/** The session ended before any fish was caught: book the bait under the spot as before. */
	private void flushEarly()
	{
		if (earlyChanges.isEmpty() && earlySupplies.isEmpty() && earlyXp.isEmpty())
		{
			return;
		}
		Map<Integer, Integer> changes = new HashMap<>(earlyChanges);
		List<SupplyTracker.Use> supplies = new ArrayList<>(earlySupplies);
		Map<String, Long> xp = new HashMap<>(earlyXp);
		clearEarly();
		bookAction(changes, supplies, xp, SourceCategory.FISHING, true);
	}

	private void clearEarly()
	{
		earlyChanges.clear();
		earlySupplies.clear();
		earlyXp.clear();
	}

	// ---------------------------------------------------------------- other XP actions

	private void resolveOther(int tick)
	{
		SourceCategory bySkill = SourceCategory.forSkill(topSkill(tickXp));
		if (bySkill == null)
		{
			// No XP yet: hold the changes in case XP follows. If none comes, an item
			// conversion (logs and coins into planks at the sawmill) is Processing.
			holdWithoutXp(tick);
			return;
		}
		bookAction(merged(), mergedSupplies(), tickXp, bySkill, false);
		lastActivityTick = tick;
		heldChanges.clear();
		heldSupplies.clear();
	}

	/**
	 * Books one action. Skills are split by item, not by how you did it: Oak logs
	 * burned with a tinderbox, on a campfire or at a Forester's campfire all land on
	 * "Oak logs"; runecrafting splits into Death rune, Blood rune and so on. Thieving
	 * is the exception and is split by NPC or stall.
	 *
	 * @param gathering items gained are the point; only stackable consumables
	 *                  (bait, feathers) count as costs
	 */
	private void bookAction(Map<Integer, Integer> changes, List<SupplyTracker.Use> supplies,
		Map<String, Long> xp, SourceCategory category, boolean gathering)
	{
		String name;
		String key;
		if (category == SourceCategory.THIEVING && targetName != null)
		{
			name = targetName;
			key = "target:" + category.name() + ":" + name;
		}
		else if (category == SourceCategory.MAGIC && mode == Mode.PROCESSING && actionSpell != null)
		{
			// Magic is split by spell: High Level Alchemy, Superglass Make, Plank Make...
			name = actionSpell;
			key = "spell:" + category.name() + ":" + name;
		}
		else
		{
			int item = mostValuable(changes, true);
			if (item <= 0 && !gathering)
			{
				item = mostValuable(changes, false);
			}
			String group = groupName(category, changes, gathering);
			if (group != null)
			{
				name = group;
				key = SkillGroups.OTHER.equals(group) ? otherKey(category) : "item:" + category.name() + ":" + name;
			}
			else if (item > 0)
			{
				name = items.name(item);
				key = "item:" + category.name() + ":" + name;
			}
			else if (lastKey != null && lastCategory == category)
			{
				// XP or bait only, e.g. the XP drop a tick after the catch.
				name = lastName;
				key = lastKey;
			}
			else if (targetName != null && gathering)
			{
				name = targetName;
				key = "target:" + category.name() + ":" + name;
			}
			else
			{
				return;
			}
		}
		lastKey = key;
		lastName = name;
		lastCategory = category;
		lastBookedTick = currentTick;

		boolean gained = false;
		for (Map.Entry<Integer, Integer> change : changes.entrySet())
		{
			int id = change.getKey();
			int delta = change.getValue();
			if (delta > 0)
			{
				ledger.addLoot(key, name, category, id, delta);
				gained = true;
			}
			else if (delta < 0 && (!gathering || items.stackable(id)))
			{
				bookCost(key, name, category, id, -delta);
			}
		}
		// One action, whether it made something (a bow, a log) or only used something up (a log burned).
		if (gathering ? gained : !changes.isEmpty())
		{
			ledger.incrementCount(key, name, category);
		}
		bookXpAndSupplies(key, name, category, xp, supplies);
	}

	/**
	 * The group an action belongs to for skills that are grouped by more than the item
	 * made (see {@link SkillGroups}), or null to name it after the item.
	 */
	private String groupName(SourceCategory category, Map<Integer, Integer> changes, boolean gathering)
	{
		List<String> made = new ArrayList<>();
		List<String> used = new ArrayList<>();
		changes.forEach((id, delta) -> (delta > 0 ? made : used).add(items.name(id)));
		switch (category)
		{
			case RUNECRAFTING:
				if (changes.isEmpty())
				{
					return null;
				}
				return made.isEmpty() ? SkillGroups.OTHER : SkillGroups.runecraftingAltar(made, used);
			case FISHING:
			{
				if (sessionGroup == null)
				{
					made.stream().filter(SkillGroups::isFish).findFirst()
						.ifPresent(fish -> sessionGroup = SkillGroups.fishGroup(fish));
				}
				return sessionGroup;
			}
			case MINING:
			{
				if (sessionGroup == null && (gathering || !made.isEmpty()))
				{
					String ore = made.isEmpty() ? null : made.get(0);
					String rock = SkillGroups.miningRock(gathering ? targetName : null, ore);
					sessionGroup = SkillGroups.OTHER.equals(rock) ? null : rock;
				}
				return sessionGroup;
			}
			case HUNTER:
			{
				if (currentTick - pendingCreatureTick <= 3 && pendingCreature != null)
				{
					String creature = pendingCreature;
					pendingCreature = null;
					return creature;
				}
				if (targetName != null && !SkillGroups.isTrap(targetName) && mode != Mode.PROCESSING)
				{
					// Caught by hand: the target is the creature (butterflies, implings).
					return targetName;
				}
				int best = mostValuable(changes, true);
				return best > 0 ? SkillGroups.creatureFromItem(items.name(best)) : null;
			}
			case HERBLORE:
			{
				int best = mostValuable(changes, true);
				return best > 0 ? SkillGroups.herblore(items.name(best)) : null;
			}
			default:
				return null;
		}
	}

	/**
	 * Renames older entries to the current grouping: runes into their altar, fish into
	 * their spot's group, ores into their rock, potions without their dose.
	 */
	public void tidyGroups()
	{
		List<com.gildedledger.data.SourceRecord> records = new ArrayList<>(ledger.data().sources.values());
		for (com.gildedledger.data.SourceRecord record : records)
		{
			if (record.key == null || !record.key.startsWith("item:") || record.name == null)
			{
				continue;
			}
			String group;
			switch (record.category)
			{
				case RUNECRAFTING:
					// Only old per-rune entries; altars (and Other) are already grouped.
					group = SkillGroups.isAltarGroup(record.name) ? null : SkillGroups.altarFor(record.name, null);
					break;
				case FISHING:
					group = SkillGroups.isFish(record.name) ? SkillGroups.fishGroup(record.name) : null;
					break;
				case MINING:
					// Only old per-ore entries; rocks and veins are already grouped.
					group = SkillGroups.isOre(record.name) ? SkillGroups.rockFromOre(record.name) : null;
					break;
				case HUNTER:
					group = SkillGroups.creatureFromItem(record.name);
					break;
				case HERBLORE:
					group = SkillGroups.herblore(record.name);
					break;
				default:
					group = null;
			}
			if (group == null || group.equals(record.name))
			{
				continue;
			}
			String key = SkillGroups.OTHER.equals(group)
				? otherKey(record.category) : "item:" + record.category.name() + ":" + group;
			ledger.mergeInto(record.key, key, group);
		}
	}

	// ---------------------------------------------------------------- processing

	private void resolveProcessing(int tick)
	{
		SourceCategory bySkill = SourceCategory.forSkill(topSkill(tickXp));
		boolean tickHasItems = !tickChanges.isEmpty() || !tickSupplies.isEmpty();
		if (tickHasItems || !tickXp.isEmpty())
		{
			lastActivityTick = tick;
		}

		if (bySkill != null && actionSpell != null && tickXp.containsKey("Magic"))
		{
			// A spell is Magic even when it also gives other XP (Superglass Make gives Crafting XP).
			bySkill = SourceCategory.MAGIC;
		}
		if (bySkill != null)
		{
			// XP arrived: this tick's changes plus any held from last tick belong to that skill.
			bookAction(merged(), mergedSupplies(), tickXp, bySkill, false);
			heldChanges.clear();
			heldSupplies.clear();
			return;
		}

		// No XP yet. Keep the changes until XP arrives; if none comes in time it's plain Processing.
		holdWithoutXp(tick);
	}

	/** Books held no-XP processing changes as Processing; held "other" changes are dropped. */
	private void holdWithoutXp(int tick)
	{
		boolean holding = !heldChanges.isEmpty() || !heldSupplies.isEmpty();
		if (holding && tick - heldSinceTick > XP_WAIT_TICKS)
		{
			flushHeld();
			holding = false;
		}
		if (!tickChanges.isEmpty() || !tickSupplies.isEmpty())
		{
			if (!holding)
			{
				heldSinceTick = tick;
			}
			tickChanges.forEach((id, delta) -> heldChanges.merge(id, delta, Integer::sum));
			heldChanges.values().removeIf(v -> v == 0);
			heldSupplies.addAll(tickSupplies);
		}
	}

	/**
	 * Settles changes that never got XP. A processing action is always Processing.
	 * For anything else clicked (an NPC or object), only a real conversion counts:
	 * items went in and different items came out (logs into planks at the sawmill,
	 * venator shards into dragon arrowtips), a coin fee included. Buying with coins
	 * or selling for coins is never tracked.
	 */
	private void flushHeld()
	{
		flushEarly();
		boolean held = !heldChanges.isEmpty() || !heldSupplies.isEmpty();
		if (held && lastKey != null && heldSupplies.isEmpty() && onlyGains(heldChanges)
			&& heldSinceTick - lastBookedTick <= TRAILING_GAIN_TICKS)
		{
			// Extra items right after an action with no XP of their own, like Runecrafting
			// bonus runes (Raiments of the Eye, abyssal lantern): part of that action.
			heldChanges.forEach((id, delta) ->
				ledger.addLoot(lastKey, lastName, lastCategory, id, delta));
			heldChanges.clear();
			return;
		}
		if (held && (mode == Mode.PROCESSING || (mode == Mode.OTHER && isConversion(heldChanges))))
		{
			bookAction(new HashMap<>(heldChanges), new ArrayList<>(heldSupplies),
				new HashMap<>(), SourceCategory.PROCESSING, false);
		}
		heldChanges.clear();
		heldSupplies.clear();
	}

	private static boolean onlyGains(Map<Integer, Integer> changes)
	{
		return !changes.isEmpty() && changes.values().stream().allMatch(v -> v > 0);
	}

	private boolean isConversion(Map<Integer, Integer> changes)
	{
		boolean itemsMade = false;
		boolean itemsUsed = false;
		for (Map.Entry<Integer, Integer> change : changes.entrySet())
		{
			if (ItemRules.isCurrency(items.name(change.getKey())))
			{
				continue;
			}
			itemsMade |= change.getValue() > 0;
			itemsUsed |= change.getValue() < 0;
		}
		return itemsMade && itemsUsed;
	}

	// ---------------------------------------------------------------- helpers

	private Map<Integer, Integer> merged()
	{
		Map<Integer, Integer> all = new HashMap<>(heldChanges);
		tickChanges.forEach((id, delta) -> all.merge(id, delta, Integer::sum));
		all.values().removeIf(v -> v == 0);
		return all;
	}

	private List<SupplyTracker.Use> mergedSupplies()
	{
		List<SupplyTracker.Use> all = new ArrayList<>(heldSupplies);
		all.addAll(tickSupplies);
		return all;
	}

	private static String topSkill(Map<String, Long> xp)
	{
		String best = null;
		long bestXp = 0;
		for (Map.Entry<String, Long> entry : xp.entrySet())
		{
			if (entry.getValue() > bestXp)
			{
				best = entry.getKey();
				bestXp = entry.getValue();
			}
		}
		return best;
	}

	/** Item with the largest total value among gains (or losses); coins only if nothing else changed. */
	private int mostValuable(Map<Integer, Integer> changes, boolean gains)
	{
		int best = -1;
		long bestValue = -1;
		for (Map.Entry<Integer, Integer> change : changes.entrySet())
		{
			int delta = change.getValue();
			if (gains ? delta <= 0 : delta >= 0)
			{
				continue;
			}
			int id = change.getKey();
			if (items.canonical(id) == COINS && changes.size() > 1)
			{
				continue;
			}
			long value = items.marketPrice(id) * Math.abs(delta);
			if (value > bestValue)
			{
				best = id;
				bestValue = value;
			}
		}
		return best;
	}

	private void bookXpAndSupplies(String key, String name, SourceCategory category,
		Map<String, Long> xp, List<SupplyTracker.Use> supplies)
	{
		for (Map.Entry<String, Long> entry : xp.entrySet())
		{
			ledger.addXp(key, name, category, entry.getKey(), entry.getValue());
		}
		for (SupplyTracker.Use use : supplies)
		{
			ledger.addSupply(key, name, category, use.lineKey, use.itemId, use.lineName, use.unit, use.quantity, use.gp);
		}
	}

	private void bookCost(String key, String name, SourceCategory category, int itemId, long quantity)
	{
		ledger.addSupply(key, name, category, String.valueOf(itemId), itemId, items.name(itemId), null,
			quantity, items.marketPrice(itemId) * quantity);
	}
}
