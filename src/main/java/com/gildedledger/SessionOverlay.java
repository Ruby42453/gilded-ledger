package com.gildedledger;

import com.gildedledger.data.GpSession;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import net.runelite.api.MenuAction;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayMenuEntry;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.util.QuantityFormatter;

/**
 * The GP/hour session box pinned to the game screen: name, time, profit and GP/hour.
 * Just the information; the session's buttons stay in the side panel.
 */
class SessionOverlay extends Overlay
{
	static final String UNPIN = "Unpin";
	private static final int WIDTH = 150;
	private static final int PAD = 6;
	private static final Color BACKGROUND = new Color(70, 61, 50, 156);
	private static final Color BORDER = new Color(30, 30, 30, 200);
	private static final Color GREEN = new Color(0, 200, 83);
	private static final Color RED = new Color(235, 90, 90);
	private static final Color GRAY = new Color(190, 190, 190);
	private static final Color ORANGE = new Color(255, 144, 64);

	private final Supplier<GpSession> live;
	private final LongSupplier shownTime;

	SessionOverlay(Supplier<GpSession> live, LongSupplier shownTime)
	{
		this.live = live;
		this.shownTime = shownTime;
		setPosition(OverlayPosition.TOP_LEFT);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		getMenuEntries().add(new OverlayMenuEntry(MenuAction.RUNELITE_OVERLAY, UNPIN, "Gilded Ledger session"));
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		GpSession session = live.get();
		if (session == null)
		{
			return null;
		}
		long fresh = shownTime.getAsLong();
		long active = fresh >= 0 ? fresh : session.shownActiveMillis();
		long perHour = GpSession.gpPerHour(session.profit, active);

		Font regular = FontManager.getRunescapeSmallFont();
		Font bold = FontManager.getRunescapeBoldFont();
		g.setFont(bold);
		FontMetrics boldMetrics = g.getFontMetrics();
		g.setFont(regular);
		FontMetrics metrics = g.getFontMetrics();

		List<String> name = wrap(session.title == null ? "Session" : session.title, boldMetrics, WIDTH - PAD * 2);
		int line = Math.max(metrics.getHeight(), boldMetrics.getHeight());
		int height = PAD * 2 + name.size() * boldMetrics.getHeight() + 3 * line;

		g.setColor(BACKGROUND);
		g.fillRect(0, 0, WIDTH, height);
		g.setColor(BORDER);
		g.drawRect(0, 0, WIDTH - 1, height - 1);

		int y = PAD + boldMetrics.getAscent();
		g.setFont(bold);
		g.setColor(Color.WHITE);
		for (String part : name)
		{
			g.drawString(part, PAD, y);
			y += boldMetrics.getHeight();
		}
		y += line - boldMetrics.getHeight();

		g.setFont(regular);
		row(g, metrics, y, duration(active), Color.WHITE, session.isRunning() ? "Running" : "Paused",
			session.isRunning() ? GREEN : ORANGE);
		y += line;
		row(g, metrics, y, "Profit", GRAY, signed(session.profit) + " gp", session.profit >= 0 ? GREEN : RED);
		y += line;
		row(g, metrics, y, "GP/hour", GRAY, QuantityFormatter.quantityToStackSize(perHour) + " gp/hr",
			perHour >= 0 ? GREEN : RED);
		return new Dimension(WIDTH, height);
	}

	private static void row(Graphics2D g, FontMetrics metrics, int y, String left, Color leftColor,
		String right, Color rightColor)
	{
		g.setColor(leftColor);
		g.drawString(left, PAD, y);
		g.setColor(rightColor);
		g.drawString(right, WIDTH - PAD - metrics.stringWidth(right), y);
	}

	/** Breaks text at spaces (and inside a word too long for a line) so no line is wider than width. */
	static List<String> wrap(String text, FontMetrics metrics, int width)
	{
		List<String> lines = new ArrayList<>();
		StringBuilder current = new StringBuilder();
		for (String word : text.trim().split("\\s+"))
		{
			while (metrics.stringWidth(word) > width && word.length() > 1)
			{
				if (current.length() > 0)
				{
					lines.add(current.toString());
					current.setLength(0);
				}
				int cut = word.length() - 1;
				while (cut > 1 && metrics.stringWidth(word.substring(0, cut)) > width)
				{
					cut--;
				}
				lines.add(word.substring(0, cut));
				word = word.substring(cut);
			}
			String candidate = current.length() == 0 ? word : current + " " + word;
			if (metrics.stringWidth(candidate) <= width)
			{
				current.setLength(0);
				current.append(candidate);
			}
			else
			{
				lines.add(current.toString());
				current.setLength(0);
				current.append(word);
			}
		}
		if (current.length() > 0)
		{
			lines.add(current.toString());
		}
		return lines;
	}

	private static String signed(long value)
	{
		return (value > 0 ? "+" : "") + QuantityFormatter.quantityToStackSize(value);
	}

	private static String duration(long millis)
	{
		long seconds = Math.max(0, millis / 1000);
		long h = seconds / 3600;
		long m = (seconds % 3600) / 60;
		long s = seconds % 60;
		return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%d:%02d", m, s);
	}
}
