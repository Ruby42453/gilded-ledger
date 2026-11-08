package com.gildedledger.ui;

import com.gildedledger.data.GpSession;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.function.BiConsumer;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.Timer;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.util.QuantityFormatter;

/**
 * GP/hour sessions: the card on the main view (timer, profit, GP/hour and the
 * start / pause / resume / end buttons) and the session log view.
 */
class SessionPanel
{
	private static final Color PROFIT_COLOR = ColorScheme.GRAND_EXCHANGE_PRICE;
	private static final Color LOSS_COLOR = new Color(235, 90, 90);
	private static final String SORT_GP_HOUR = "Sort by GP/hour";
	private static final String SORT_LATEST = "Sort by latest";

	private final BiConsumer<GildedLedgerPanel.Action, String> actions;
	private final Runnable showLog;
	private final Runnable showMain;
	private final java.util.function.Consumer<GpSession> openSession;

	final JPanel card = new JPanel();
	final JPanel logView = new JPanel(new BorderLayout(0, 8));

	private final JLabel status = small("", ColorScheme.LIGHT_GRAY_COLOR);
	private final JLabel timer = new JLabel();
	private final JLabel profit = new JLabel();
	private final JLabel rate = small("", ColorScheme.LIGHT_GRAY_COLOR);
	private final JLabel title = small("", ColorScheme.LIGHT_GRAY_COLOR);
	private final JPanel stats = new JPanel();
	private final JPanel buttons = new JPanel(new GridLayout(1, 2, 4, 0));
	private final JPanel logList = new JPanel();
	private final JComboBox<String> sort = new JComboBox<>(new String[]{SORT_GP_HOUR, SORT_LATEST});

	private GpSession live;
	private List<GpSession> log = new ArrayList<>();
	/** Which buttons are showing, so they're only rebuilt when that changes. */
	private String buttonState = "";

	SessionPanel(BiConsumer<GildedLedgerPanel.Action, String> actions, Runnable showLog, Runnable showMain,
		java.util.function.Consumer<GpSession> openSession)
	{
		this.actions = actions;
		this.openSession = openSession;
		this.showLog = showLog;
		this.showMain = showMain;
		buildCard();
		buildLogView();
		// The clock ticks every second while a session runs.
		new Timer(1000, e -> tick()).start();
	}

	// ---------------------------------------------------------------- the card

	private void buildCard()
	{
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(new EmptyBorder(8, 10, 8, 10));
		card.setAlignmentX(JPanel.LEFT_ALIGNMENT);

		JPanel head = row();
		head.add(small("GP/hour session", ColorScheme.LIGHT_GRAY_COLOR), BorderLayout.WEST);
		head.add(status, BorderLayout.EAST);
		card.add(head);

		stats.setLayout(new BoxLayout(stats, BoxLayout.Y_AXIS));
		stats.setOpaque(false);
		stats.setAlignmentX(JPanel.LEFT_ALIGNMENT);
		JPanel clock = row();
		timer.setFont(Fonts.bold(20f));
		timer.setForeground(java.awt.Color.WHITE);
		profit.setFont(Fonts.bold());
		clock.add(timer, BorderLayout.WEST);
		clock.add(profit, BorderLayout.EAST);
		stats.add(Box.createVerticalStrut(4));
		stats.add(clock);
		JPanel detail = row();
		detail.add(title, BorderLayout.WEST);
		detail.add(rate, BorderLayout.EAST);
		stats.add(detail);
		card.add(stats);

		card.add(Box.createVerticalStrut(6));
		buttons.setOpaque(false);
		buttons.setAlignmentX(JPanel.LEFT_ALIGNMENT);
		card.add(buttons);
		card.add(Box.createVerticalStrut(4));
		JButton seeLog = new JButton("See session log");
		seeLog.addActionListener(e -> showLog.run());
		JPanel logRow = new JPanel(new GridLayout(1, 1));
		logRow.setOpaque(false);
		logRow.setAlignmentX(JPanel.LEFT_ALIGNMENT);
		logRow.add(seeLog);
		card.add(logRow);
		setButtons();
	}

	private void setButtons()
	{
		String state = live == null ? "none" : live.isRunning() ? "running" : "paused";
		if (state.equals(buttonState))
		{
			return;
		}
		buttonState = state;
		buttons.removeAll();
		switch (state)
		{
			case "none":
				buttons.setLayout(new GridLayout(1, 1));
				buttons.add(button("Start session", GildedLedgerPanel.Action.SESSION_START));
				break;
			case "running":
				buttons.setLayout(new GridLayout(1, 2, 4, 0));
				buttons.add(button("Pause", GildedLedgerPanel.Action.SESSION_PAUSE));
				buttons.add(button("End session", GildedLedgerPanel.Action.SESSION_END));
				break;
			default:
				buttons.setLayout(new GridLayout(1, 2, 4, 0));
				buttons.add(button("Resume", GildedLedgerPanel.Action.SESSION_RESUME));
				buttons.add(button("End session", GildedLedgerPanel.Action.SESSION_END));
		}
		buttons.revalidate();
		buttons.repaint();
	}

	private JButton button(String text, GildedLedgerPanel.Action action)
	{
		JButton b = new JButton(text);
		b.addActionListener(e -> actions.accept(action, null));
		return b;
	}

	/** Called on the Swing thread with the session as it stands, and the log. */
	void update(GpSession live, List<GpSession> log)
	{
		this.live = live;
		this.log = new ArrayList<>(log);
		setButtons();
		stats.setVisible(live != null);
		tick();
		redrawLog();
	}

	/** Refreshes the clock and GP/hour from the last numbers, once a second. */
	private void tick()
	{
		if (live == null)
		{
			status.setText("");
			return;
		}
		long active = live.activeMillis + (live.isRunning() ? Math.max(0, System.currentTimeMillis() - live.runningSinceMillis) : 0);
		timer.setText(duration(active));
		setProfit(profit, live.profit);
		long perHour = GpSession.gpPerHour(live.profit, active);
		rate.setText(QuantityFormatter.quantityToStackSize(perHour) + " gp/hr");
		rate.setForeground(perHour >= 0 ? PROFIT_COLOR : LOSS_COLOR);
		title.setText(live.title == null ? "" : live.title);
		status.setText(live.isRunning() ? "Running" : "Paused");
		status.setForeground(live.isRunning() ? PROFIT_COLOR : ColorScheme.BRAND_ORANGE);
	}

	// ---------------------------------------------------------------- the log

	private void buildLogView()
	{
		logView.setOpaque(false);
		JPanel top = new JPanel(new BorderLayout(6, 0));
		JButton back = new JButton("Back");
		back.addActionListener(e -> showMain.run());
		top.add(back, BorderLayout.WEST);
		JLabel heading = new JLabel("Session log");
		heading.setFont(Fonts.bold());
		heading.setForeground(java.awt.Color.WHITE);
		top.add(heading, BorderLayout.CENTER);

		JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		top.setAlignmentX(JPanel.LEFT_ALIGNMENT);
		header.add(top);
		header.add(Box.createVerticalStrut(6));
		sort.setAlignmentX(JPanel.LEFT_ALIGNMENT);
		sort.addActionListener(e -> redrawLog());
		header.add(sort);

		logList.setLayout(new BoxLayout(logList, BoxLayout.Y_AXIS));

		JButton clear = new JButton("Clear log");
		clear.addActionListener(e ->
		{
			if (JOptionPane.showConfirmDialog(logView, "Delete every session in the log? This cannot be undone.",
				"Clear log", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION)
			{
				actions.accept(GildedLedgerPanel.Action.SESSION_CLEAR, null);
			}
		});

		logView.add(header, BorderLayout.NORTH);
		logView.add(logList, BorderLayout.CENTER);
		logView.add(clear, BorderLayout.SOUTH);
	}

	private void redrawLog()
	{
		logList.removeAll();
		List<GpSession> sessions = new ArrayList<>(log);
		if (SORT_GP_HOUR.equals(sort.getSelectedItem()))
		{
			sessions.sort(Comparator.comparingLong((GpSession s) -> s.gpPerHour()).reversed());
		}
		else
		{
			sessions.sort(Comparator.comparingLong((GpSession s) -> s.endedMillis).reversed());
		}
		if (sessions.isEmpty())
		{
			javax.swing.JTextArea empty = GildedLedgerPanel.wrapping("No sessions yet. Start one from the main page.",
				ColorScheme.LIGHT_GRAY_COLOR);
			empty.setAlignmentX(JPanel.LEFT_ALIGNMENT);
			logList.add(empty);
		}
		for (GpSession session : sessions)
		{
			logList.add(sessionRow(session));
			logList.add(Box.createVerticalStrut(4));
		}
		logList.revalidate();
		logList.repaint();
	}

	private JPanel sessionRow(GpSession session)
	{
		JPanel box = new JPanel();
		box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
		box.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		box.setBorder(new EmptyBorder(6, 8, 6, 8));
		box.setAlignmentX(JPanel.LEFT_ALIGNMENT);

		// Name on its own line (wrapping if long), then when and how long, then profit and GP/hour,
		// so nothing overlaps on a narrow panel.
		javax.swing.JTextArea name = GildedLedgerPanel.wrapping(session.title == null ? "Session" : session.title,
			java.awt.Color.WHITE);
		name.setFont(Fonts.bold());
		box.add(name);

		JPanel second = row();
		String when = new SimpleDateFormat("MMM d, h:mm a").format(new Date(session.endedMillis));
		second.add(small(when, ColorScheme.LIGHT_GRAY_COLOR), BorderLayout.WEST);
		second.add(small(duration(session.activeMillis), ColorScheme.LIGHT_GRAY_COLOR), BorderLayout.EAST);
		box.add(second);

		JPanel third = row();
		JLabel total = small("", PROFIT_COLOR);
		setProfit(total, session.profit);
		third.add(total, BorderLayout.WEST);
		long perHour = session.gpPerHour();
		third.add(small(QuantityFormatter.quantityToStackSize(perHour) + " gp/hr", perHour >= 0 ? PROFIT_COLOR : LOSS_COLOR),
			BorderLayout.EAST);
		box.add(third);

		JPopupMenu menu = new JPopupMenu();
		JMenuItem rename = new JMenuItem("Rename session");
		rename.addActionListener(e ->
		{
			Object answer = JOptionPane.showInputDialog(logView, "New name for this session:", "Rename session",
				JOptionPane.PLAIN_MESSAGE, null, null, session.title);
			if (answer != null && !answer.toString().trim().isEmpty())
			{
				actions.accept(GildedLedgerPanel.Action.SESSION_RENAME, session.id + "\n" + answer.toString().trim());
			}
		});
		JMenuItem delete = new JMenuItem("Delete session");
		delete.addActionListener(e -> actions.accept(GildedLedgerPanel.Action.SESSION_DELETE, session.id));
		menu.add(rename);
		menu.add(delete);
		box.setComponentPopupMenu(menu);
		for (java.awt.Component child : box.getComponents())
		{
			((javax.swing.JComponent) child).setInheritsPopupMenu(true);
			if (child instanceof JPanel)
			{
				for (java.awt.Component label : ((JPanel) child).getComponents())
				{
					((javax.swing.JComponent) label).setInheritsPopupMenu(true);
				}
			}
		}
		box.setToolTipText("Click to see its loot and supplies; right-click to rename or delete");
		box.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		java.awt.event.MouseAdapter open = new java.awt.event.MouseAdapter()
		{
			@Override
			public void mouseClicked(java.awt.event.MouseEvent e)
			{
				if (javax.swing.SwingUtilities.isLeftMouseButton(e))
				{
					openSession.accept(session);
				}
			}
		};
		box.addMouseListener(open);
		// The wrapping title is a text area, which takes its own clicks: pass them on.
		name.addMouseListener(open);
		name.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
		return box;
	}

	// ---------------------------------------------------------------- helpers

	/** 1:02:33, or 4:05 under an hour. */
	static String duration(long millis)
	{
		long seconds = Math.max(0, millis / 1000);
		long h = seconds / 3600;
		long m = (seconds % 3600) / 60;
		long s = seconds % 60;
		return h > 0 ? String.format("%d:%02d:%02d", h, m, s) : String.format("%d:%02d", m, s);
	}

	private static JPanel row()
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setOpaque(false);
		row.setAlignmentX(JPanel.LEFT_ALIGNMENT);
		return row;
	}

	private static JLabel small(String text, Color color)
	{
		JLabel label = new JLabel(text);
		label.setFont(Fonts.small());
		label.setForeground(color);
		return label;
	}

	private static void setProfit(JLabel label, long value)
	{
		label.setText((value > 0 ? "+" : "") + QuantityFormatter.quantityToStackSize(value) + " gp");
		label.setForeground(value >= 0 ? PROFIT_COLOR : LOSS_COLOR);
	}
}
