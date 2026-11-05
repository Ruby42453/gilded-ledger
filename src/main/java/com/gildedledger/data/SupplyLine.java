package com.gildedledger.data;

/**
 * One kind of supply used by a source. Supplies are valued at the moment they
 * are used, so the stored gp never changes afterwards even if prices move.
 */
public class SupplyLine
{
	/** Item shown as the icon, e.g. the potion that was sipped. */
	public int itemId;
	/** Display name with doses stripped, e.g. "Prayer potion". */
	public String name;
	public long quantity;
	public long gp;
	/** Unit for the quantity, e.g. "doses"; null for plain items. */
	public String unit;

	public SupplyLine()
	{
	}

	public SupplyLine(int itemId, String name, String unit)
	{
		this.itemId = itemId;
		this.name = name;
		this.unit = unit;
	}
}
