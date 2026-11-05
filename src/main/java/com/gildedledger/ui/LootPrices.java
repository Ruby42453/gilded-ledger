package com.gildedledger.ui;

/**
 * Per-item prices for loot. The value used for profit is the Grand Exchange
 * price, or the High Alchemy price when the item can't be traded.
 */
public interface LootPrices
{
	long ge(int itemId);

	long highAlch(int itemId);

	boolean tradeable(int itemId);

	/** GE price for tradeable items with a price; High Alchemy price otherwise. */
	default long value(int itemId)
	{
		long ge = ge(itemId);
		return tradeable(itemId) && ge > 0 ? ge : highAlch(itemId);
	}
}
