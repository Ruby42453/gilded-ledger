package com.gildedledger.ui;

import java.awt.Font;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import net.runelite.client.ui.FontManager;

/**
 * The fonts the panel draws with, picked in the plugin's settings: RuneLite's RuneScape
 * fonts, a font already on the computer (Arial, Times New Roman, Courier New), or one of
 * the open-licence fonts that come with the plugin (see fonts/licenses).
 */
public final class Fonts
{
	/** Fonts packed with the plugin: family → {regular file, bold file or null, size scale}. */
	private static final Map<String, Object[]> BUNDLED = Map.of(
		"Pixelify Sans", new Object[]{"PixelifySans.ttf", null, 1.0f},
		"Silkscreen", new Object[]{"Silkscreen-Regular.ttf", "Silkscreen-Bold.ttf", 0.85f},
		"VT323", new Object[]{"VT323-Regular.ttf", null, 1.3f},
		"Roboto Condensed", new Object[]{"RobotoCondensed.ttf", null, 1.0f},
		"Barlow Semi Condensed", new Object[]{"BarlowSemiCondensed-Regular.ttf", "BarlowSemiCondensed-Bold.ttf", 1.05f},
		"IBM Plex Sans", new Object[]{"IBMPlexSans.ttf", null, 0.95f},
		"Source Sans 3", new Object[]{"SourceSans3-Regular.ttf", "SourceSans3-Bold.ttf", 1.05f},
		"JetBrains Mono", new Object[]{"JetBrainsMono.ttf", null, 0.9f},
		"Cinzel", new Object[]{"Cinzel.ttf", null, 0.95f});

	private static final Map<String, Font> LOADED = new HashMap<>();

	/** "RuneScape", "RuneScape (larger)", a font on the computer, or a bundled family. */
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

	private static float scale()
	{
		Object[] bundled = BUNDLED.get(family);
		return bundled == null ? 1f : (Float) bundled[2];
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
		return font(false, 12f * scale());
	}

	/** Titles and profit figures. */
	public static Font bold()
	{
		return runescape() ? FontManager.getRunescapeBoldFont() : font(true, 12f * scale());
	}

	/** Big headline numbers (total profit, the session clock). */
	public static Font bold(float size)
	{
		return runescape() ? FontManager.getRunescapeBoldFont().deriveFont(size) : font(true, size * 0.8f * scale());
	}

	/**
	 * A font that can draw these characters: the given one if it can, otherwise the
	 * standard font at the same size (several fonts have no ▸ or ▾ arrows).
	 */
	public static Font thatCanShow(Font font, String text)
	{
		return font.canDisplayUpTo(text) == -1 ? font : new Font(Font.DIALOG, font.getStyle(), font.getSize());
	}

	private static Font font(boolean bold, float size)
	{
		Object[] bundled = BUNDLED.get(family);
		if (bundled == null)
		{
			// A font on the computer (Java falls back to a standard font if it isn't installed).
			return new Font(family, bold ? Font.BOLD : Font.PLAIN, Math.round(size));
		}
		String file = bold && bundled[1] != null ? (String) bundled[1] : (String) bundled[0];
		Font base = load(file);
		if (base == null)
		{
			return new Font(Font.SANS_SERIF, bold ? Font.BOLD : Font.PLAIN, Math.round(size));
		}
		// A family without its own bold file is made bold by Java.
		int style = bold && bundled[1] == null ? Font.BOLD : Font.PLAIN;
		return base.deriveFont(style, size);
	}

	private static synchronized Font load(String file)
	{
		return LOADED.computeIfAbsent(file, f ->
		{
			try (InputStream in = Fonts.class.getResourceAsStream("/com/gildedledger/fonts/" + f))
			{
				if (in == null)
				{
					return null;
				}
				Font font = Font.createFont(Font.TRUETYPE_FONT, in);
				// Registered so text Swing draws by family name (labels with HTML, like entry
				// names and XP bubbles) finds it too.
				java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(font);
				return font;
			}
			catch (Exception e)
			{
				return null;
			}
		});
	}
}
