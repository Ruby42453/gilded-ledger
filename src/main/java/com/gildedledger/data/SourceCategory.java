package com.gildedledger.data;

/**
 * The kind of activity a tracked source represents. The panel groups sources
 * by category, in this order. Skilling categories are named after the skill
 * that gave the most XP for the action.
 */
public enum SourceCategory
{
	COMBAT("PvM", "Kills", "Attack"),
	FARMING("Farming", "Harvests", "Farming"),
	WOODCUTTING("Woodcutting", "Logs cut", "Woodcutting"),
	FISHING("Fishing", "Catches", "Fishing"),
	MINING("Mining", "Ores mined", "Mining"),
	HUNTER("Hunter", "Catches", "Hunter"),
	THIEVING("Thieving", "Successes", "Thieving"),
	RUNECRAFTING("Runecrafting", "Crafts", "Runecraft"),
	HERBLORE("Herblore", "Made", "Herblore"),
	COOKING("Cooking", "Made", "Cooking"),
	FLETCHING("Fletching", "Made", "Fletching"),
	CRAFTING("Crafting", "Made", "Crafting"),
	SMITHING("Smithing", "Made", "Smithing"),
	FIREMAKING("Firemaking", "Burned", "Firemaking"),
	MAGIC("Magic", "Casts", "Magic"),
	PRAYER("Prayer", "Used", "Prayer"),
	CONSTRUCTION("Construction", "Built", "Construction"),
	AGILITY("Agility", "Laps", "Agility"),
	SAILING("Sailing", "Actions", "Sailing"),
	/** Older saves filed all processing here; kept so they still load. */
	PROCESSING("Processing", "Made", null),
	TELEPORTS("Teleports", "Uses", null),
	GENERAL_SUPPLIES("General supplies", "Uses", null),
	PURCHASE("Purchases", "Purchases", null);

	private final String label;
	private final String countLabel;
	/** RuneLite skill name for the icon and XP matching, or null. */
	private final String skillName;

	SourceCategory(String label, String countLabel, String skillName)
	{
		this.label = label;
		this.countLabel = countLabel;
		this.skillName = skillName;
	}

	public String getLabel()
	{
		return label;
	}

	public String getCountLabel()
	{
		return countLabel;
	}

	public String getSkillName()
	{
		return skillName;
	}

	/** The skilling category for a skill name such as "Fletching", or null if there is none. */
	public static SourceCategory forSkill(String skillName)
	{
		if (skillName == null)
		{
			return null;
		}
		for (SourceCategory category : values())
		{
			if (category != COMBAT && skillName.equalsIgnoreCase(category.skillName))
			{
				return category;
			}
		}
		return null;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
