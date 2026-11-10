package com.gildedledger;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Notification;
import net.runelite.client.config.Range;
import net.runelite.client.config.Units;

@ConfigGroup(GildedLedgerConfig.GROUP)
public interface GildedLedgerConfig extends Config
{
	String GROUP = "gildedledger";
	/** The config group used while the plugin was called Pickup Profit; migrated on start-up. */
	String LEGACY_GROUP = "pickupprofit";

	@ConfigSection(
		name = "Tracking",
		description = "What to track",
		position = 0
	)
	String trackingSection = "tracking";

	@ConfigItem(
		keyName = "trackCombat",
		name = "Track PvM",
		description = "Track picked-up monster loot, supplies and combat XP per monster",
		section = trackingSection,
		position = 0
	)
	default boolean trackCombat()
	{
		return true;
	}

	@ConfigItem(
		keyName = "trackFarming",
		name = "Track farming patches",
		description = "Track seed, sapling, compost and gardener costs against harvests and XP for every patch type",
		section = trackingSection,
		position = 1
	)
	default boolean trackFarming()
	{
		return true;
	}

	@ConfigItem(
		keyName = "hideUntradeablePlants",
		name = "Hide untradeable plants",
		description = "Don't track plants grown from untradeable seeds, like quest plants (goutweed from My Arm's Big Adventure)",
		section = trackingSection,
		position = 2
	)
	default boolean hideUntradeablePlants()
	{
		return true;
	}

	@ConfigItem(
		keyName = "trackAmmo",
		name = "Count ammunition",
		description = "Count arrows, bolts, darts and other ammunition used in combat as supplies",
		section = trackingSection,
		position = 3
	)
	default boolean trackAmmo()
	{
		return true;
	}

	@ConfigSection(
		name = "Display",
		description = "How the side panel is laid out",
		position = 1
	)
	String displaySection = "display";

	enum SkillLayout
	{
		SEPARATE("One entry per item"),
		COMBINED("One table per skill");

		private final String label;

		SkillLayout(String label)
		{
			this.label = label;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	@ConfigItem(
		keyName = "skillLayout",
		name = "Skill layout",
		description = "Show each skill split by item (Death rune, Blood rune...) or as one combined table. PvM is always one entry per monster.",
		section = displaySection,
		position = 0
	)
	default SkillLayout skillLayout()
	{
		return SkillLayout.SEPARATE;
	}

	@ConfigItem(
		keyName = "hideSkilling",
		name = "Hide skilling",
		description = "Hide every skill from the panel and its totals. Skills are always tracked, so turning this off brings everything back. To hide only some skills, use Hide skills below.",
		section = displaySection,
		position = 2
	)
	default boolean hideSkilling()
	{
		return false;
	}

	enum PanelFont
	{
		RUNESCAPE("RuneScape", "RuneScape"),
		RUNESCAPE_LARGE("RuneScape (larger)", "RuneScape (larger)"),
		// The next three are fonts already on the computer; the rest come with the plugin.
		SANS_SERIF("Arial", "Arial"),
		SERIF("Times New Roman", "Times New Roman"),
		MONOSPACED("Courier New", "Courier New"),
		PIXELIFY_SANS("Pixelify Sans", "Pixelify Sans"),
		SILKSCREEN("Silkscreen", "Silkscreen"),
		VT323("VT323", "VT323"),
		ROBOTO_CONDENSED("Roboto Condensed", "Roboto Condensed"),
		BARLOW_SEMI_CONDENSED("Barlow Semi Condensed", "Barlow Semi Condensed"),
		IBM_PLEX_SANS("IBM Plex Sans", "IBM Plex Sans"),
		SOURCE_SANS("Source Sans 3", "Source Sans 3"),
		JETBRAINS_MONO("JetBrains Mono", "JetBrains Mono"),
		CINZEL("Cinzel", "Cinzel");

		private final String label;
		private final String family;

		PanelFont(String label, String family)
		{
			this.label = label;
			this.family = family;
		}

		public String getFamily()
		{
			return family;
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	@ConfigItem(
		keyName = "panelFont",
		name = "Font",
		description = "The font the side panel is written in",
		section = displaySection,
		position = 3
	)
	default PanelFont panelFont()
	{
		return PanelFont.RUNESCAPE;
	}

	@ConfigItem(
		keyName = "timeZoneChoice",
		name = "Time zone",
		description = "When the Day, Week and Month views start: 00:00 each day, Monday 00:00 and the 1st at 00:00. "
			+ "UTC matches the game's daily reset. The others follow daylight saving where it applies (CST is CDT in summer).",
		section = displaySection,
		position = 4
	)
	default TimeZoneChoice timeZone()
	{
		return TimeZoneChoice.UTC;
	}

	enum TimeZoneChoice
	{
		UTC("UTC", "UTC", "UTC"),
		GMT("GMT - UK", "GMT", "Europe/London"),
		CET("CET - Central Europe", "CET", "Europe/Paris"),
		EET("EET - Eastern Europe", "EET", "Europe/Helsinki"),
		IST("IST - India", "IST", "Asia/Kolkata"),
		SGT("SGT - Singapore", "SGT", "Asia/Singapore"),
		JST("JST - Japan / Korea", "JST", "Asia/Tokyo"),
		AWST("AWST - Western Australia", "AWST", "Australia/Perth"),
		AEST("AEST - Eastern Australia", "AEST", "Australia/Sydney"),
		NZST("NZST - New Zealand", "NZST", "Pacific/Auckland"),
		BRT("BRT - Brazil", "BRT", "America/Sao_Paulo"),
		AST("AST - Atlantic", "AST", "America/Halifax"),
		EST("EST - US Eastern", "EST", "America/New_York"),
		CST("CST - US Central", "CST", "America/Chicago"),
		MST("MST - US Mountain", "MST", "America/Denver"),
		PST("PST - US Pacific", "PST", "America/Los_Angeles"),
		AKST("AKST - Alaska", "AKST", "America/Anchorage"),
		HST("HST - Hawaii", "HST", "Pacific/Honolulu");

		private final String label;
		private final String shortName;
		private final String zone;

		TimeZoneChoice(String label, String shortName, String zone)
		{
			this.label = label;
			this.shortName = shortName;
			this.zone = zone;
		}

		/** The short name shown in the panel, e.g. "CST". */
		public String getShortName()
		{
			return shortName;
		}

		public java.time.ZoneId getZone()
		{
			return java.time.ZoneId.of(zone);
		}

		@Override
		public String toString()
		{
			return label;
		}
	}

	@ConfigItem(
		keyName = "collapseNew",
		name = "Collapse new entries",
		description = "New monsters, items and categories appear minimized in the panel",
		section = displaySection,
		position = 1
	)
	default boolean collapseNew()
	{
		return false;
	}

	@ConfigSection(
		name = "Reminders",
		description = "Notifications to re-pot, drink prayer and reload the cannon. Click the gear next to each to customize it like any RuneLite notification (sound, flash, tray, game message...).",
		position = 2
	)
	String remindersSection = "reminders";

	@ConfigItem(
		keyName = "boostReminder",
		name = "Boost potion reminder",
		description = "Notify before a combat boost runs out: super combat, divine and ranging potions, magic potions, imbued heart and the like",
		section = remindersSection,
		position = 0
	)
	default Notification boostReminder()
	{
		return Notification.ON;
	}

	@Range(min = 0, max = 600)
	@Units(Units.SECONDS)
	@ConfigItem(
		keyName = "boostReminderSeconds",
		name = "Remind before boost ends",
		description = "How many seconds before a boost (or a divine potion's timer) runs out to remind you",
		section = remindersSection,
		position = 1
	)
	default int boostReminderSeconds()
	{
		return 30;
	}

	@ConfigItem(
		keyName = "boostReminderCombatOnly",
		name = "Combat boosts only",
		description = "Only remind about boosts to combat skills (Attack, Strength, Defence, Ranged, Magic), not skilling boosts",
		section = remindersSection,
		position = 1
	)
	default boolean boostReminderCombatOnly()
	{
		return false;
	}

	@ConfigItem(
		keyName = "prayerReminder",
		name = "Prayer reminder",
		description = "Notify when your prayer points run low",
		section = remindersSection,
		position = 2
	)
	default Notification prayerReminder()
	{
		return Notification.ON;
	}

	@Range(min = 1, max = 99)
	@ConfigItem(
		keyName = "prayerReminderPoints",
		name = "Prayer points",
		description = "Remind you when prayer drops to this many points or fewer",
		section = remindersSection,
		position = 3
	)
	default int prayerReminderPoints()
	{
		return 10;
	}

	@ConfigItem(
		keyName = "cannonReminder",
		name = "Cannon reminder",
		description = "Notify when your cannon is running out of cannonballs",
		section = remindersSection,
		position = 4
	)
	default Notification cannonReminder()
	{
		return Notification.ON;
	}

	@Range(min = 0, max = 35)
	@ConfigItem(
		keyName = "cannonReminderBalls",
		name = "Cannonballs left",
		description = "Remind you when the cannon has this many cannonballs or fewer",
		section = remindersSection,
		position = 5
	)
	default int cannonReminderBalls()
	{
		return 5;
	}

	@ConfigItem(
		keyName = "superiorReminder",
		name = "Superior spawn",
		description = "Notify when a superior Slayer monster appears",
		section = remindersSection,
		position = 6
	)
	default Notification superiorReminder()
	{
		return Notification.ON;
	}

	@ConfigItem(
		keyName = "reminderPopup",
		name = "Popup box",
		description = "Also show a small box at the top of the game screen saying what the reminder is for (e.g. Prayer: 10 points left). Shows even while the client is focused.",
		section = remindersSection,
		position = 7
	)
	default boolean reminderPopup()
	{
		return true;
	}

	@ConfigSection(
		name = "Hide skills",
		description = "Advanced: hide single skills from the panel and its totals. They're still tracked.",
		position = 3,
		closedByDefault = true
	)
	String hideSkillsSection = "hideSkills";

	// Keys are "hide" + the category label: hideWoodcutting, hideOther...

	@ConfigItem(
		keyName = "hideWoodcutting",
		name = "Woodcutting",
		description = "Hide Woodcutting from the panel (still tracked)",
		section = hideSkillsSection,
		position = 0
	)
	default boolean hideWoodcutting()
	{
		return false;
	}

	@ConfigItem(
		keyName = "hideFishing",
		name = "Fishing",
		description = "Hide Fishing from the panel (still tracked)",
		section = hideSkillsSection,
		position = 1
	)
	default boolean hideFishing()
	{
		return false;
	}

	@ConfigItem(
		keyName = "hideMining",
		name = "Mining",
		description = "Hide Mining from the panel (still tracked)",
		section = hideSkillsSection,
		position = 2
	)
	default boolean hideMining()
	{
		return false;
	}

	@ConfigItem(
		keyName = "hideHunter",
		name = "Hunter",
		description = "Hide Hunter from the panel (still tracked)",
		section = hideSkillsSection,
		position = 3
	)
	default boolean hideHunter()
	{
		return false;
	}

	@ConfigItem(
		keyName = "hideThieving",
		name = "Thieving",
		description = "Hide Thieving from the panel (still tracked)",
		section = hideSkillsSection,
		position = 4
	)
	default boolean hideThieving()
	{
		return false;
	}

	@ConfigItem(
		keyName = "hideRunecrafting",
		name = "Runecrafting",
		description = "Hide Runecrafting from the panel (still tracked)",
		section = hideSkillsSection,
		position = 5
	)
	default boolean hideRunecrafting()
	{
		return false;
	}

	@ConfigItem(
		keyName = "hideHerblore",
		name = "Herblore",
		description = "Hide Herblore from the panel (still tracked)",
		section = hideSkillsSection,
		position = 6
	)
	default boolean hideHerblore()
	{
		return false;
	}

	@ConfigItem(
		keyName = "hideCooking",
		name = "Cooking",
		description = "Hide Cooking from the panel (still tracked)",
		section = hideSkillsSection,
		position = 7
	)
	default boolean hideCooking()
	{
		return false;
	}

	@ConfigItem(
		keyName = "hideFletching",
		name = "Fletching",
		description = "Hide Fletching from the panel (still tracked)",
		section = hideSkillsSection,
		position = 8
	)
	default boolean hideFletching()
	{
		return false;
	}

	@ConfigItem(
		keyName = "hideCrafting",
		name = "Crafting",
		description = "Hide Crafting from the panel (still tracked)",
		section = hideSkillsSection,
		position = 9
	)
	default boolean hideCrafting()
	{
		return false;
	}

	@ConfigItem(
		keyName = "hideSmithing",
		name = "Smithing",
		description = "Hide Smithing from the panel (still tracked)",
		section = hideSkillsSection,
		position = 10
	)
	default boolean hideSmithing()
	{
		return false;
	}

	@ConfigItem(
		keyName = "hideFiremaking",
		name = "Firemaking",
		description = "Hide Firemaking from the panel (still tracked)",
		section = hideSkillsSection,
		position = 11
	)
	default boolean hideFiremaking()
	{
		return false;
	}

	@ConfigItem(
		keyName = "hideMagic",
		name = "Magic",
		description = "Hide Magic from the panel (still tracked)",
		section = hideSkillsSection,
		position = 12
	)
	default boolean hideMagic()
	{
		return false;
	}

	@ConfigItem(
		keyName = "hidePrayer",
		name = "Prayer",
		description = "Hide Prayer from the panel (still tracked)",
		section = hideSkillsSection,
		position = 13
	)
	default boolean hidePrayer()
	{
		return false;
	}

	@ConfigItem(
		keyName = "hideConstruction",
		name = "Construction",
		description = "Hide Construction from the panel (still tracked)",
		section = hideSkillsSection,
		position = 14
	)
	default boolean hideConstruction()
	{
		return false;
	}

	@ConfigItem(
		keyName = "hideAgility",
		name = "Agility",
		description = "Hide Agility from the panel (still tracked)",
		section = hideSkillsSection,
		position = 15
	)
	default boolean hideAgility()
	{
		return false;
	}

	@ConfigItem(
		keyName = "hideSailing",
		name = "Sailing",
		description = "Hide Sailing from the panel (still tracked)",
		section = hideSkillsSection,
		position = 16
	)
	default boolean hideSailing()
	{
		return false;
	}

	@ConfigItem(
		keyName = "hideOther",
		name = "Other",
		description = "Hide minigame rewards that belong to no skill",
		section = hideSkillsSection,
		position = 17
	)
	default boolean hideOther()
	{
		return false;
	}
}
