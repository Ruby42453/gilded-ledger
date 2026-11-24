package com.gildedledger.ui;

/**
 * Per-item prices for loot. The value used for profit is the Grand Exchange price.
 * Untradeable items are worth nothing, except the few that are really worth their
 * alch price (tarnished items, which polish into tradeable ones).
 */
public interface LootPrices
{
	long ge(int itemId);

	long highAlch(int itemId);

	boolean tradeable(int itemId);

	/** Untradeable items valued at their High Alchemy price anyway. */
	default boolean valuedAtAlch(int itemId)
	{
		return false;
	}

	/**
	 * GE price for tradeable items (their alch price if the GE has none); 0 for untradeable
	 * items unless {@link #valuedAtAlch} says otherwise.
	 */
	default long value(int itemId)
	{
		if (tradeable(itemId))
		{
			long ge = ge(itemId);
			return ge > 0 ? ge : highAlch(itemId);
		}
		return valuedAtAlch(itemId) ? highAlch(itemId) : 0;
	}
}
