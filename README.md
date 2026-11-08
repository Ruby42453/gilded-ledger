# Gilded Ledger

A RuneLite plugin that answers "did that actually make money?" for every monster you kill, every skill you train, every farming patch you run and every minigame you play, with GP/hour sessions to time it all.

> **Gilded Ledger is in constant development.** New tracking is added and existing tracking is refined regularly, so behaviour may change between updates. If something is tracked wrongly or you'd like something added, please email [LucidicRuby.plugins@gmail.com](mailto:LucidicRuby.plugins@gmail.com) or [open an issue](https://github.com/Ruby42453/gilded-ledger/issues) (see [Contact](#contact)).

## Contents

- [PvM](#pvm)
- [Farming](#farming)
- [Skilling](#skilling)
- [Minigames](#minigames)
- [Teleports and general supplies](#teleports-and-general-supplies)
- [GP/hour sessions](#gphour-sessions)
- [Reminders](#reminders)
- [The panel](#the-panel)
- [Settings](#settings)
- [Item values](#item-values)
- [Known limits](#known-limits)

## PvM

Tracked per monster.

- **Loot only counts once you pick it up.** A drop is tied to the kill when it lands, and is added when you click *Take* and the item leaves the tile while you're standing on it. Drops you leave behind, let despawn, or that someone else takes never count. Dropping an item takes it back off the monster, and picking it up again puts it back.
- **Supplies:** potion doses (valued per dose), food, runes from the inventory and rune pouch (only when a spell is cast), arrows, bolts, darts, thrown weapons and Dizana's quiver ammo.
- **Charged gear**, priced at what a charge costs to refill:
  - Trident of the seas and swamp, Sanguinesti staff, Tumeken's shadow, Warped sceptre, Scythe of vitur and revenant weapons, per attack.
  - Blowpipe darts (at your Ava's device's rate) and Zulrah's scales, per shot. Each shot is counted from the dart it fires. **Check** the blowpipe now and then: the exact darts and scales used since the last Check replace the estimate.
  - Zulrah's scales from serpentine, tanzanite and magma helms.
- **Cannonballs** count as they're fired at the monster, not when you load the cannon.
- **Supplies around a fight:** anything used up to 2 minutes before a fight (a super combat potion before the goblin) moves to that monster when it dies. Anything used up to 2 minutes after a fight (the teleport out) stays with it.
  - **Bosses** take longer: when a boss dies, or anything dies inside an instance, everything used since you entered (and up to 2 minutes before) counts, as far back as 15 minutes.
- **Bonecrusher and ash sanctifier:** each bone crushed or ash scattered uses a charge. They have their own entries under Prayer, *Bonecrusher* and *Ash sanctifier*, with the charges used (ash sanctifier charges cost a tenth of a death rune; ecto-tokens are worth nothing) and the Prayer XP.
- **Hespori:** the seed is a cost of the Hespori kill in PvM. The Farming XP for killing it goes to Farming.
- **Boss parts** are filed under the boss: Hespori's flowers under Hespori, Zulrah's snakelings under Zulrah, the God Wars bodyguards under their general, Dusk and Dawn under Grotesque Guardians, the Barrows brothers under Barrows, and so on.
- **XP** gained while fighting, split by skill.
- **Slayer task** card at the top of the panel, using RuneLite's Slayer plugin: a progress bar, kills, and profit for task monsters only.

## Farming

Tracked per crop, across every patch type: herbs, allotments, bushes, trees, fruit trees, hardwoods, calquat, celastrus, redwood, spirit and crystal trees and more. All Toadflax goes together, all Yew trees together.

- **Costs:** the seed or sapling, compost (bucket or bottomless bucket) and gardener payments, charged the moment they're used.
- **Loot:** everything harvested, including fruit and logs from chopping patch trees. Herbs picked straight into a **herb sack** count too.
- **XP:** Farming XP (including check-health) and Woodcutting XP from patch trees.
- Compost goes on the crop in the patch, and moves to the new crop if you plant something different. Composting an empty patch first counts towards whatever you plant, XP included.
- When the crop isn't known (planted before the plugin was installed), it's worked out from the harvest.
- The Farming Guild's anima plants (Attas, Kronos, Iasor) go together under *Other*.
- **Untradeable plants** (quest plants like goutweed) are hidden by default; see Settings.
- Noting herbs at the leprechaun, and anything moved while a bank, deposit box, tool leprechaun or seed vault is open, never counts.

## Skilling

One category per skill. Every action goes to the skill that gave it the most XP (drift net fishing lands in Fishing or Hunter, whichever gave more).

| Skill | Grouped by |
|---|---|
| Runecrafting | Altar (*Blood altar*, *Ourania altar* for ZMI), anything else under *Other* |
| Fishing | What the spot gives (*Trout & salmon*, *Karambwanji*) |
| Mining | Rock or vein (*Iron rocks*, *Motherlode Mine*), gems included |
| Hunter | Creature caught |
| Herblore | Potion whatever the dose, or herb when cleaning |
| Woodcutting, Firemaking | Log type (tinderbox, campfire or Forester's campfire all count as one) |
| Thieving | NPC or stall |
| Magic | Spell (High Level Alchemy, Superglass Make, NPC Contact…); combat spells stay with the monster |
| Others (Cooking, Fletching, Crafting, Smithing, Prayer…) | Item made |

- **Loot and costs:** items made or gathered are loot; items used up (essence, logs, bait, feathers…) are costs. Bait used before the first catch joins the fish caught. Using up something the same entry made (a bow fletched and then offered) takes it back off the loot instead of counting it twice.
- **Boosts:** a potion or food eaten just before a skilling action (a dragonfruit pie before fletching) is that action's supply, as long as the skill is still boosted. What's left after eating (half a pie, a pie dish, a vial) isn't loot.
- **Vale Totems:** logs, the bows and shields made for the totems and the ents' offerings all go into one *Vale Totems* entry under Fletching.
- **Bags:** fish into a fish barrel, logs into a log basket and gems into a gem bag count from the game's catch message. Seeds going into a seed box count from its contents. Filling or emptying pouches and bags is ignored.
- **Runecrafting:**
  - Bonus runes (levels, Raiments of the Eye, abyssal lantern) count with the craft.
  - Combination runes cost their essence, talisman and elemental runes (including runes from the rune pouch), plus 1/16 of a binding necklace per craft.
  - Essence pouches degrading or being repaired doesn't count.
- **Prayer:** burying bones or scattering ashes costs the bones, while the monster that dropped them keeps them as loot.
- **Spells mid-activity:** spells cast during a skilling action (alching mid-task) charge their runes and Magic XP to that action.
- **Anything else** you click that gives XP is tracked too, named after what you clicked (tending a Forester's campfire under Firemaking).
- **Other XP:** XP that comes from no action of yours, such as a minigame's reward XP, goes to that skill's **Other** entry.
- **Processing** is only for actions that give no XP: combining crystal key halves, unfinished potions, planks at the sawmill, venator shards into dragon arrowtips. Buying from or selling to NPCs is never tracked.

## Minigames

Reward pulls are tracked as their own entry under the minigame's skill. Everything received is loot, and the crate or pouch you open isn't a cost.

| Minigame | Rewards from | Category |
|---|---|---|
| Guardians of the Rift | Rewards Guardian, intricate pouches | Runecrafting |
| Tempoross | Reward pool | Fishing |
| Fishing Trawler | Reward net | Fishing |
| Wintertodt | Reward cart, supply crates | Firemaking |
| Motherlode Mine | The sack (same entry as the pay-dirt and XP) | Mining |
| Vale Totems | Offerings (same entry as the logs and bows) | Fletching |
| Hallowed Sepulchre | Grand Hallowed Coffin | Agility |
| Pyramid Plunder | Grand Gold Chest | Thieving |
| Hunter rumours | Loot sacks | Hunter |
| Soul Wars | Spoils of war | Other |

Minigame-only items (Guardians of the Rift essence and fragments, Wintertodt roots, the Tears of Guthix stone bowl, Chambers of Xeric potions…) are never loot or costs. Neither is a slayer ring losing a charge or an essence pouch degrading. A Wintertodt Big-search keeps counting for as long as items keep coming, even if you drop junk or walk meanwhile.

## Teleports and general supplies

- **Teleports:** tablets (including house tabs), teleport scrolls and teleport spells, in one table with the Magic XP from teleport spells. Diary alternatives are kept apart, e.g. *Varrock Teleport (Grand Exchange)*. Teleports are never charged to a monster, task or skill.
- **General supplies:** food, potions and runes used outside any fight or skilling action, in one table.

## GP/hour sessions

The **GP/hour session** card near the top of the panel times a session and works out its profit per hour.

- **Start session** starts the timer. Only profit made from then on counts, across every category, supplies included.
- **Pause** stops the timer, and nothing gained or spent while paused counts. It turns into **Resume**.
- **End session** saves it to the log. Closing the client pauses a running session, keeping the time played up to then.
- **See session log** lists finished sessions with their profit, time and GP/hour. Each is named after what you did most (*Blood rune crafting*, *Karambwan fishing*, *Abyssal demon*, *Guardians of the Rift*).
  - **Click a session** to see everything it gained and used, as one loot table and one supplies table.
  - Sort by GP/hour or latest.
  - **Right-click** a session to rename or delete it.
  - **Clear log** empties it, and **Back** returns to the main page.

## Reminders

Settings → **Reminders**. Each has a checkbox to turn it off and a gear to customize it like any RuneLite notification (tray, sound, flash, game message, and whether it fires while the client is focused).

- **Boost potion:** a set number of seconds (30 by default) before any stat boost runs out: super combat, ranging, magic and divine potions, stews, the imbued heart and so on. *Combat boosts only* leaves out skilling boosts.
- **Prayer:** when prayer drains to a set number of points (10 by default).
- **Cannon:** when the cannon is down to a set number of cannonballs (5 by default).
- **Superior spawn:** when a superior Slayer monster appears.
- **Popup box:** each reminder also slides a small box in at the top of the game screen saying what it's for. It shows even while the client is focused.

## The panel

**Main page**
- **Total profit** at the top, with loot, supplies and total XP underneath.
- Everything starts collapsed each time RuneLite starts. The **i** in the top right opens a page about the plugin and how to report bugs.
- **GP/hour session** card, then the **Slayer task** card with the kills left.
- **Top sources:** your three most profitable sources (click to hide).
- **Categories:** one row each with its icon, profit and XP. Click one to open its page; right-click to reset it.

**Category pages**
- Every entry in the category, sorted by **most recent** (default) or **most profit**, with a **Back** button to the main page.
- **PvM** has a Loot Tracker style box per monster with its kill count, a loot grid, a red supplies grid and XP per skill. Skills, Farming, Teleports and General supplies work the same way, without counts.
- **Collapse all / Expand all** for that category's entries (not on one-table categories like Teleports).
- **Right-click any loot or supply icon** to exclude it from profit; right-click again to include it.
- **Right-click an entry's title** to reset just that entry.
- XP with no loot or supplies behind it (minigame XP, an agility course) only adds to the category's XP; it has no entry of its own. A skill only appears once it has an entry with loot or supplies.

## Settings

- **Tracking:** PvM, farming patches and ammunition can each be turned off. Skills are always tracked. *Hide untradeable plants* (on by default) hides plants grown from untradeable seeds, like goutweed.
- **Display:**
  - *Skill layout:* one entry per item, or one table per skill.
  - *Hide skilling:* hides every skill from the panel and its totals.
  - *Font:* RuneScape, RuneScape (larger), fonts already on your computer (Arial, Times New Roman, Courier New), or one that comes with the plugin: Pixelify Sans, Silkscreen, VT323, Roboto Condensed, Barlow Semi Condensed, IBM Plex Sans, Source Sans 3, JetBrains Mono or Cinzel.
  - *Collapse new entries.*
- **Hide skills** (advanced, closed by default): hide single skills, or minigame rewards under Other. Hidden skills are still tracked; turn them back on and the history is there.
- **Reminders:** see above.

## Item values

- Loot and supplies are valued at the Grand Exchange price.
- Untradeable items are worth 0, except tarnished items (a tarnished ring polishes into a real ring), which use their High Alchemy price.
- Clue scrolls, caskets and reward currencies (marks of grace, abyssal pearls, molch pearls, golden nuggets, tokkul…) are worth 0.
- Supplies are priced at the moment they're used. Loot is valued at today's prices.

## Known limits

- Loot sent to a looting bag by auto-pickup features isn't seen.
- Jewellery charges aren't counted as supplies yet.
- Blowpipe darts are an estimate between Checks, since the game decides each dart's fate at random.
- Kills are counted when RuneLite sees the monster's drop, the same way RuneLite's own Loot Tracker counts them.

## Developing in VS Code

1. Install JDK 17 (Eclipse Temurin) and the extensions this folder recommends: *Extension Pack for Java* and *Gradle for Java*.
2. Open the folder. VS Code imports the Gradle project automatically.
3. Press **F5** (*Run RuneLite with Gilded Ledger*), or run `./gradlew run`.
4. If you use a Jagex account, follow https://github.com/runelite/runelite/wiki/Using-Jagex-Accounts to log in to the development client.
5. Run the tests with `./gradlew test`.

## Contact

Bug reports and feature requests are welcome:

- **Email:** [LucidicRuby.plugins@gmail.com](mailto:LucidicRuby.plugins@gmail.com)
- **GitHub:** [open an issue](https://github.com/Ruby42453/gilded-ledger/issues)

Including what you were doing, what the panel showed and what you expected (a screenshot helps) makes bugs much quicker to fix.

## Credits

The drop-to-pickup and click-then-confirm supply techniques are adapted from [PvM Toolkit](https://github.com/arbeerby-pixel/pvm-tools), as is the reminder popup box. Charged-weapon and blowpipe detection follows [Supplies Tracker](https://github.com/pwatts6060/SuppliesTrackerExternal). The farming harvest approach is adapted from [Farming Profit](https://github.com/JaredEzz/farming-profit-plugin). All are BSD-2 licensed.

The bundled fonts are from Google Fonts and Adobe (Source Sans 3) and are used under the SIL Open Font License; their licences are in `src/main/resources/com/gildedledger/fonts/licenses`.
