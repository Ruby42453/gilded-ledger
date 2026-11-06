package com.gildedledger.ui;

import java.awt.Font;
import net.runelite.client.ui.FontManager;

/** The fonts the panel draws with, picked in the plugin's settings. */
public final class Fonts
{
	/** "RuneScape", "RuneScape (larger)", or a Java font family such as "SansSerif". */
	private static String family = "RuneScape";

	private Fonts()
	{
	}

	/** Must be set before the panel is built. */
	public static void use(String fontFamily)
	{
		family = fontFamily == null ? "RuneScape" : fontFamily;
	}

	private static boolean runescape()
	{
		return family.startsWith("RuneScape");
	}

	/** Labels, numbers and notes. */
	public static Font small()
	{
		if (family.equals("RuneScape"))
		{
			return FontManager.getRunescapeSmallFont();
		}
		if (runescape())
		{
			return FontManager.getRunescapeFont();
		}
		return new Font(family, Font.PLAIN, 11);
	}

	/** Titles and profit figures. */
	public static Font bold()
	{
		return runescape() ? FontManager.getRunescapeBoldFont() : new Font(family, Font.BOLD, 12);
	}

	/** Big headline numbers (total profit, the session clock). */
	public static Font bold(float size)
	{
		return bold().deriveFont(runescape() ? size : size * 0.8f);
	}
}
