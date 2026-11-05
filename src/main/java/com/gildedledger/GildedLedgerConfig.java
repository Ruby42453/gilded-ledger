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
		keyName = "trackSkilling",
		name = "Track skilling",
		description = "Track Woodcutting, Fishing, Mining, Hunter and Thieving per target, and processing (cleaning, cooking, fletching, smelting, alching...) per product",
		section = trackingSection,
		position = 2
	)
	default boolean trackSkilling()
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
}
