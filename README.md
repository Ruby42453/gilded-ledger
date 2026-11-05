# Gilded Ledger

A RuneLite plugin that answers "did that actually make money?" for every monster you kill, every skill you train and every farming patch you run.

## What it tracks

**PvM, per monster**
- **Loot only counts once you pick it up.** A drop is tied to the kill when it lands. It's added to the log when you click *Take* and the item leaves the tile while you're standing on it. Drops you leave behind, let despawn, or that someone else grabs never count.
- **Supplies used:** potion doses (valued per dose), food, runes from the inventory and rune pouch (only when a spell was actually cast), arrows/bolts/darts/thrown weapons and Dizana's quiver ammo.
- **Charged gear**, priced at what a charge costs to refill: Trident of the seas/swamp, Sanguinesti staff, Tumeken's shadow, Warped sceptre, Scythe of vitur and revenant weapons per attack; the blowpipe's darts (at your Ava's device's rate) and Zulrah's scales per shot; and Zulrah's scales from serpentine, tanzanite and magma helms.
- **Cannonballs** are charged to the monster the cannon was shooting.
  - Supplies used within 2 minutes after a fight are charged to that monster, so the teleport out counts.
  - Supplies used up to 2 minutes before a fight (a super combat potion before the goblin) move from General supplies to that monster when it dies, and so do charges spent before the fight registered.
- **Blowpipe:** each shot is counted from its dart projectile and charged the expected darts (by Ava's device) and 2/3 of a scale. **Check** the blowpipe now and then: the exact darts and scales used since the last Check replace the estimate.
  - Anything else goes to *Unassigned supplies*.
- **XP** gained while fighting the monster, split by skill (Attack, Strength, Defence, Ranged, Magic, Hitpoints, Slayer) with a total.
- **Loot grid** for each monster with item icons and stack sizes, like RuneLite's Loot Tracker. Hover an item for its GE and High Alchemy values.
- **Current Slayer task** card at the top of the panel, using RuneLite's Slayer plugin. It shows remaining count, kills, loot, supplies, profit, combat XP and Slayer XP for task monsters only. Finished tasks are kept in history.

**Farming, per crop.** Every patch type is covered: herbs, trees, fruit trees, hardwoods, calquat, celastrus, redwood, spirit and crystal trees, allotments, bushes and more. Entries are grouped by crop across all patches: all Toadflax together, all Yew trees together.
- **Costs:** the seed or sapling planted, compost (bucket or bottomless bucket) and gardener payments.
- **Loot:** everything harvested from the patch, including fruit and logs from chopping a patch tree.
- **XP:** Farming XP (including tree check-health) and Woodcutting XP from chopping patch trees.
- Seeds, saplings and compost are charged the moment they're used. Compost goes on the crop in the patch, and moves to the new crop if you then plant something different. Composting an empty patch before planting counts towards whatever you plant, Farming XP included. Each crop has one entry, so "Papaya" and "Papaya tree" are the same.
- If the crop isn't known (planted before the plugin was installed), it's worked out from the harvest: grimy huasca → Huasca, yew logs → Yew tree.
- The game's farming messages ("You plant…", "You treat…") are tracked too, so planting counts even if a patch isn't recognised.
- Noting herbs at the leprechaun, and anything moved while a bank, deposit box, tool leprechaun or seed vault is open, is never counted.

**Skilling, one category per skill**
- Gathering: **Woodcutting** (per tree), **Fishing** (per method), **Mining** (per rock), **Hunter** (per creature), **Thieving** (per NPC or stall).
- Processing, per product: **Runecrafting** (per rune), **Herblore**, **Cooking**, **Fletching**, **Crafting**, **Smithing**, **Firemaking**, **Magic** (alching) and more.
- Every action goes in the category of the skill that gave it the most XP, so drift net fishing lands in Fishing or Hunter by whichever gave more.
- **Processing** is only for actions that give no XP (combining crystal key halves, unfinished potions, turning logs into planks at the sawmill or venator shards into dragon arrowtips). NPC actions are tracked through their dialogue too. Buying from or selling to NPCs is never tracked.
- Burying bones and scattering ashes is **Prayer**: the bones are the cost, while the monster that dropped them keeps them as loot.
- Spells cast during a skilling action (alching mid-Slayer task, say) charge their runes and Magic XP to that action, not to the monster.
- Anything else you click that gives XP is tracked too, named after what you clicked (e.g. tending a Forester's campfire under Firemaking).
- Items made or gathered are loot; items used up (essence, logs, bait, feathers…) are costs. Filling or emptying essence pouches is ignored.
- Dropped items leave the loot of the source that gave them, and go back if you pick them up again.

**Teleports** — tablets (including house tabs), teleport scrolls and teleport spells (their runes) have their own category, shown as one table. Diary alternatives are tracked separately, e.g. *Varrock Teleport (Grand Exchange)*, *Camelot Teleport (Seers')*, *Watchtower Teleport (Yanille)*. They are never charged to a monster, task or skill.

**General supplies** — food, potions and runes used outside any fight or skilling action, in one table.

## Reminders

Settings → **Reminders** has four notifications. Each one has a checkbox to turn it off and a gear to customize it like any RuneLite notification: tray, sound, volume, flash, game message, and whether it fires while the client is focused.
- **Boost potion reminder:** a set number of seconds (30 by default) before a combat boost runs out. This works for any stat boost: super combat, ranging, magic and divine potions, skilling boosts such as stews and the imbued heart. Divine potions use their own timer, and other boosts follow the one-level-a-minute drain (90 seconds with Preserve).
- **Prayer reminder:** when prayer drains to a set number of points (10 by default).
- **Cannon reminder:** when the cannon is down to a set number of cannonballs (5 by default).
- **Superior spawn:** when a superior Slayer monster appears.
- **Popup box:** each reminder also slides a small box in at the top of the game screen, in the style of a collection log entry, saying what it's for (e.g. *Prayer: your prayer is low: 10 points left*). It shows even while the client is focused, and can be turned off.

Cannonballs are only a cost as they're fired. Loading them into the cannon isn't.

## The panel

- **Total profit** at the top, with loot, supplies and total XP underneath.
- **Slayer task** card with a progress bar, task kills and task-only profit.
- **Top sources**: your three most profitable sources across all categories (click to hide).
- **Categories** show their icon, profit and XP. Click to minimize one to just its title; right-click to reset it.
- PvM shows a Loot Tracker style box per monster: loot grid, red supplies grid, and XP bubbles per skill. Click its title bar to collapse it.
- Skills have a box per item, however you did it: Runecrafting per rune, Firemaking per log type (tinderbox, campfire or Forester's campfire all count as one), Woodcutting per log, Fishing per fish. Thieving is per NPC or stall. Teleports and General supplies are one table each.
- **Magic** is split by spell (High Level Alchemy, Superglass Make, Plank Make…); combat spells stay with the monster.
- **Settings → Display:** *Skill layout* (one entry per item, or one table per skill) and *Collapse new entries*.
- **Right-click any loot or supply icon** to exclude it from profit; it fades out and stops counting. Right-click again to include it.
- **Collapse all / Expand all** buttons at the bottom.
- Teleports also show the Magic XP from teleport spells.

## Loot values

Loot is valued at the Grand Exchange price, or the High Alchemy price for untradeable items. Each monster also shows its loot's total High Alchemy value. Supplies use GE prices at the moment they were used, or the High Alchemy price when an item has no GE price (so cleaning a tarnished ring costs the tarnished ring and earns the cleaned one).

## Known limits

- Herbs placed straight into a herb sack, and loot sent to a looting bag by auto-pickup features, aren't seen as inventory gains.
- Jewellery charges aren't counted as supplies yet. Blowpipe darts are an estimate between Checks, since the game decides each dart's fate at random.
- Kills are counted when RuneLite sees the monster's drop (the same way RuneLite's own Loot Tracker counts them).

## Developing in VS Code

1. Install a JDK 11+ (Eclipse Temurin) and the recommended extensions this folder suggests: *Extension Pack for Java* and *Gradle for Java*.
2. Open the folder. VS Code imports the Gradle project automatically.
3. Press **F5** (*Run RuneLite with Gilded Ledger*), or run `./gradlew run`.
4. If you use a Jagex account, follow https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts to log in to the development client.
5. Run the tests with `./gradlew test`.

## Credits

The drop-to-pickup and click-then-confirm supply techniques are adapted from [PvM Toolkit](https://github.com/arbeerby-pixel/pvm-tools), as is the reminder popup box. Charged-weapon and blowpipe detection follows [Supplies Tracker](https://github.com/pwatts6060/SuppliesTrackerExternal). The farming harvest approach is adapted from [Farming Profit](https://github.com/JaredEzz/farming-profit-plugin). All are BSD-2 licensed.
