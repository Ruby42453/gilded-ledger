package com.gildedledger.ui;

import com.gildedledger.data.SourceCategory;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.QuantityFormatter;

/**
 * Side panel, top to bottom: total profit, the current Slayer task with a
 * progress bar, the three most profitable sources, then one collapsible
 * category per activity holding Loot Tracker style boxes.
 */
public class GildedLedgerPanel extends PluginPanel
{
	public enum Action
	{
		RESET_SOURCE, RESET_CATEGORY, RESET_ALL, RESET_TASK,
		/** Key is "sourceKey\nitemId": include or exclude a loot item from profit. */
		TOGGLE_LOOT,
		/** Key is "sourceKey\nsupplyLineKey": include or exclude a supply from profit. */
		TOGGLE_SUPPLY
	}

	private static final Color PROFIT_COLOR = ColorScheme.GRAND_EXCHANGE_PRICE;
	private static final Color LOSS_COLOR = new Color(235, 90, 90);
	private static final Color TITLE_BAR = new Color(25, 25, 25);
	private static final Color CATEGORY_BAR = new Color(26, 26, 26);
	private static final Color SLOT = ColorScheme.DARKER_GRAY_COLOR;
	private static final Color SUPPLY_SLOT = new Color(42, 30, 30);
	private static final Color BUBBLE = ColorScheme.DARKER_GRAY_COLOR;
	private static final Color PROGRESS_TRACK = new Color(58, 58, 58);
	private static final int ICON_COLUMNS = 5;
	private static final int TOP_SOURCES = 3;
	/** Usable width inside the panel's padding, for wrapping XP bubbles. */
	private static final int CONTENT_WIDTH = PluginPanel.PANEL_WIDTH - 24;

	private final ItemManager itemManager;
	private final SkillIconManager skillIconManager;
	private final BiConsumer<Action, String> actions;
	private final Map<SourceCategory, ImageIcon> categoryIcons = new EnumMap<>(SourceCategory.class);

	private final JLabel totalProfit = new JLabel();
	private final JLabel totalLoot = new JLabel();
	private final JLabel totalSupplies = new JLabel();
	private final JLabel totalXp = new JLabel();
	private final JPanel taskContainer = new JPanel(new BorderLayout());
	private final JPanel topContainer = new JPanel(new BorderLayout());
	private final JPanel sourceList = new JPanel();

	private final Set<String> collapsed = new HashSet<>();
	private final Set<SourceCategory> collapsedCategories = EnumSet.noneOf(SourceCategory.class);
	private boolean topCollapsed;
	private PanelModel model;
	/** Setting: new entries and categories start minimized. */
	private boolean collapseNew;
	private final Set<String> seenSources = new HashSet<>();
	private final Set<SourceCategory> seenCategories = EnumSet.noneOf(SourceCategory.class);
	private boolean primed;

	public GildedLedgerPanel(ItemManager itemManager, SkillIconManager skillIconManager, BiConsumer<Action, String> actions)
	{
		this.itemManager = itemManager;
		this.skillIconManager = skillIconManager;
		this.actions = actions;

		setBorder(new EmptyBorder(10, 10, 10, 10));
		setLayout(new BorderLayout(0, 10));

		JPanel top = new JPanel();
		top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));

		// Totals: profit as the headline, then loot, supplies and XP on one line.
		JLabel profitTitle = small("Total profit", ColorScheme.LIGHT_GRAY_COLOR);
		profitTitle.setAlignmentX(LEFT_ALIGNMENT);
		top.add(profitTitle);
		totalProfit.setFont(FontManager.getRunescapeBoldFont().deriveFont(24f));
		totalProfit.setAlignmentX(LEFT_ALIGNMENT);
		top.add(totalProfit);
		top.add(Box.createVerticalStrut(6));
		JPanel line = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		line.setAlignmentX(LEFT_ALIGNMENT);
		for (JLabel label : new JLabel[]{totalLoot, totalSupplies, totalXp})
		{
			label.setFont(FontManager.getRunescapeSmallFont());
			label.setBorder(new EmptyBorder(0, 0, 0, 10));
			line.add(label);
		}
		top.add(line);
		top.add(Box.createVerticalStrut(10));

		taskContainer.setAlignmentX(LEFT_ALIGNMENT);
		top.add(taskContainer);
		topContainer.setAlignmentX(LEFT_ALIGNMENT);
		top.add(topContainer);

		sourceList.setLayout(new BoxLayout(sourceList, BoxLayout.Y_AXIS));

		JButton resetAll = new JButton("Reset all sources");
		resetAll.addActionListener(e ->
		{
			int choice = JOptionPane.showConfirmDialog(this,
				"Clear everything tracked in every category, and the Slayer task totals? This cannot be undone.",
				"Reset all", JOptionPane.YES_NO_OPTION);
			if (choice == JOptionPane.YES_OPTION)
			{
				actions.accept(Action.RESET_ALL, null);
			}
		});

		JButton collapseAll = new JButton("Collapse all");
		collapseAll.addActionListener(e -> setAllCollapsed(true));
		JButton expandAll = new JButton("Expand all");
		expandAll.addActionListener(e -> setAllCollapsed(false));
		JPanel folding = new JPanel(new GridLayout(1, 2, 4, 0));
		folding.add(collapseAll);
		folding.add(expandAll);
		JPanel bottom = new JPanel(new GridLayout(2, 1, 0, 4));
		bottom.add(folding);
		bottom.add(resetAll);

		add(top, BorderLayout.NORTH);
		add(sourceList, BorderLayout.CENTER);
		add(bottom, BorderLayout.SOUTH);
	}

	/** Minimizes (or opens) every category, every monster box and Top sources at once. */
	private void setAllCollapsed(boolean collapse)
	{
		collapsed.clear();
		collapsedCategories.clear();
		topCollapsed = collapse;
		if (collapse)
		{
			collapsedCategories.addAll(EnumSet.allOf(SourceCategory.class));
			if (model != null)
			{
				for (PanelModel.SourceView source : model.sources)
				{
					collapsed.add(source.key);
				}
			}
		}
		redraw();
	}

	public void setCollapseNew(boolean collapseNew)
	{
		this.collapseNew = collapseNew;
	}

	/** Called on the Swing thread with a fresh snapshot. */
	public void update(PanelModel model)
	{
		this.model = model;
		noticeNewEntries(model);
		redraw();
	}

	/**
	 * Entries already there when the panel first fills keep their state; anything that
	 * shows up later starts minimized when the "Collapse new entries" setting is on.
	 */
	private void noticeNewEntries(PanelModel model)
	{
		for (PanelModel.SourceView source : model.sources)
		{
			if (seenSources.add(source.key) && primed && collapseNew)
			{
				collapsed.add(source.key);
			}
			if (seenCategories.add(source.category) && primed && collapseNew)
			{
				collapsedCategories.add(source.category);
			}
		}
		primed = primed || model.ready;
	}

	private void redraw()
	{
		if (model == null)
		{
			return;
		}
		List<PanelModel.SourceView> sources = new ArrayList<>(model.sources);
		sources.sort(Comparator.comparingLong((PanelModel.SourceView s) -> s.lastUpdatedMillis).reversed());

		long loot = 0;
		long supplies = 0;
		long xp = 0;
		for (PanelModel.SourceView s : sources)
		{
			loot += s.lootValue;
			supplies += s.supplyGp;
			xp += s.xp;
		}
		setProfit(totalProfit, loot - supplies);
		totalProfit.setText(totalProfit.getText() + " gp");
		totalLoot.setText(html("Loot", gp(loot), "#ffffff"));
		totalSupplies.setText(html("Supplies", gp(supplies), "#eb5a5a"));
		totalXp.setText(html("Total XP", QuantityFormatter.quantityToStackSize(xp), "#ffffff"));

		taskContainer.removeAll();
		if (model.task != null)
		{
			taskContainer.add(taskCard(model.task), BorderLayout.CENTER);
			taskContainer.add(Box.createVerticalStrut(10), BorderLayout.SOUTH);
		}

		topContainer.removeAll();
		List<PanelModel.SourceView> top = topSources(sources);
		if (!top.isEmpty())
		{
			topContainer.add(topSourcesSection(top), BorderLayout.CENTER);
			topContainer.add(Box.createVerticalStrut(10), BorderLayout.SOUTH);
		}

		sourceList.removeAll();
		if (!model.ready)
		{
			sourceList.add(note("Log in to start tracking."));
		}
		else if (sources.isEmpty())
		{
			sourceList.add(note("Nothing tracked yet. Loot counts once you pick it up."));
		}
		for (SourceCategory category : SourceCategory.values())
		{
			List<PanelModel.SourceView> inCategory = new ArrayList<>();
			for (PanelModel.SourceView source : sources)
			{
				if (source.category == category)
				{
					inCategory.add(source);
				}
			}
			if (inCategory.isEmpty())
			{
				continue;
			}
			sourceList.add(categoryHeader(category, inCategory));
			if (!collapsedCategories.contains(category))
			{
				for (PanelModel.SourceView source : inCategory)
				{
					sourceList.add(Box.createVerticalStrut(4));
					// PvM has a box per monster; every other category is one combined table.
					// Teleports and General supplies are one table; everything else has a box per monster or item.
					boolean singleTable = category == SourceCategory.TELEPORTS || category == SourceCategory.GENERAL_SUPPLIES
						|| source.key.startsWith(PanelModel.COMBINED_PREFIX);
					sourceList.add(indented(singleTable ? categoryTable(source) : sourceBox(source)));
				}
			}
			sourceList.add(Box.createVerticalStrut(8));
		}
		revalidate();
		repaint();
	}

	// ---------------------------------------------------------------- Slayer task

	private JPanel taskCard(PanelModel.TaskView task)
	{
		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		card.setBorder(new EmptyBorder(8, 10, 8, 10));

		JPanel head = row();
		JLabel name = new JLabel(task.finished ? "Done: " + task.name : task.name);
		name.setFont(FontManager.getRunescapeBoldFont());
		name.setForeground(task.finished ? ColorScheme.LIGHT_GRAY_COLOR : ColorScheme.BRAND_ORANGE);
		head.add(name, BorderLayout.CENTER);
		JLabel profit = new JLabel();
		profit.setFont(FontManager.getRunescapeBoldFont());
		setProfit(profit, task.profit());
		head.add(profit, BorderLayout.EAST);
		card.add(head);
		card.add(Box.createVerticalStrut(6));

		int total = Math.max(task.initial, task.remaining);
		double done = total <= 0 ? (task.finished ? 1 : 0) : (double) (total - task.remaining) / total;
		card.add(new ProgressStrip(task.finished ? 1 : done));
		card.add(Box.createVerticalStrut(6));

		JPanel foot = row();
		foot.add(small(task.kills + " killed", ColorScheme.LIGHT_GRAY_COLOR), BorderLayout.WEST);
		foot.add(small(task.finished ? "complete" : task.remaining + " / " + total + " left", ColorScheme.LIGHT_GRAY_COLOR),
			BorderLayout.EAST);
		card.add(foot);

		JPopupMenu menu = new JPopupMenu();
		JMenuItem reset = new JMenuItem("Reset task totals");
		reset.addActionListener(e -> actions.accept(Action.RESET_TASK, null));
		menu.add(reset);
		card.setComponentPopupMenu(menu);
		card.setToolTipText("Kills, loot and supplies for task monsters only. Right-click to reset.");
		return card;
	}

	/** A slim orange progress bar. */
	private static final class ProgressStrip extends JComponent
	{
		private final double fraction;

		ProgressStrip(double fraction)
		{
			this.fraction = Math.max(0, Math.min(1, fraction));
			setPreferredSize(new Dimension(CONTENT_WIDTH, 6));
			setMaximumSize(new Dimension(Integer.MAX_VALUE, 6));
			setAlignmentX(LEFT_ALIGNMENT);
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			g.setColor(PROGRESS_TRACK);
			g.fillRect(0, 0, getWidth(), getHeight());
			g.setColor(ColorScheme.BRAND_ORANGE);
			g.fillRect(0, 0, (int) Math.round(getWidth() * fraction), getHeight());
		}
	}

	// ---------------------------------------------------------------- top sources

	private static List<PanelModel.SourceView> topSources(List<PanelModel.SourceView> sources)
	{
		List<PanelModel.SourceView> earners = new ArrayList<>();
		for (PanelModel.SourceView s : sources)
		{
			if (s.profit() > 0 && s.category != SourceCategory.GENERAL_SUPPLIES && s.category != SourceCategory.TELEPORTS)
			{
				earners.add(s);
			}
		}
		earners.sort(Comparator.comparingLong(PanelModel.SourceView::profit).reversed());
		return earners.subList(0, Math.min(TOP_SOURCES, earners.size()));
	}

	private JPanel topSourcesSection(List<PanelModel.SourceView> top)
	{
		JPanel section = new JPanel();
		section.setLayout(new BoxLayout(section, BoxLayout.Y_AXIS));

		JPanel header = new FitHeightPanel(new BorderLayout(6, 0));
		header.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		header.setBorder(new EmptyBorder(6, 8, 6, 8));
		header.setAlignmentX(LEFT_ALIGNMENT);
		JLabel title = new JLabel((topCollapsed ? "▸  " : "▾  ") + "Top sources");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(ColorScheme.BRAND_ORANGE);
		header.add(title, BorderLayout.WEST);
		header.setToolTipText(topCollapsed ? "Show your most profitable sources" : "Hide");
		onPress(header, () ->
		{
			topCollapsed = !topCollapsed;
			redraw();
		}, ColorScheme.DARKER_GRAY_COLOR);
		section.add(header);

		if (!topCollapsed)
		{
			int rank = 1;
			for (PanelModel.SourceView source : top)
			{
				section.add(Box.createVerticalStrut(3));
				section.add(topRow(rank++, source));
			}
		}
		return section;
	}

	private JPanel topRow(int rank, PanelModel.SourceView source)
	{
		JPanel row = new JPanel(new BorderLayout(8, 0));
		row.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row.setBorder(new EmptyBorder(5, 8, 5, 8));
		row.setAlignmentX(LEFT_ALIGNMENT);

		row.add(small(String.valueOf(rank), ColorScheme.MEDIUM_GRAY_COLOR.brighter()), BorderLayout.WEST);

		JPanel text = new JPanel(new GridLayout(2, 1));
		text.setOpaque(false);
		JLabel name = new JLabel(source.name);
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setForeground(Color.WHITE);
		text.add(name);
		String detail = source.category.getLabel() + " · " + countText(source);
		text.add(small(detail, ColorScheme.MEDIUM_GRAY_COLOR.brighter()));
		row.add(text, BorderLayout.CENTER);

		JLabel profit = new JLabel();
		profit.setFont(FontManager.getRunescapeBoldFont());
		setProfit(profit, source.profit());
		row.add(profit, BorderLayout.EAST);
		return row;
	}

	// ---------------------------------------------------------------- categories

	/** A collapsible title bar: icon and name, with profit and XP stacked on the right. */
	private JPanel categoryHeader(SourceCategory category, List<PanelModel.SourceView> sources)
	{
		boolean open = !collapsedCategories.contains(category);
		long loot = 0;
		long supplies = 0;
		long xp = 0;
		for (PanelModel.SourceView s : sources)
		{
			loot += s.lootValue;
			supplies += s.supplyGp;
			xp += s.xp;
		}

		JPanel header = new FitHeightPanel(new BorderLayout(6, 0));
		header.setBackground(CATEGORY_BAR);
		header.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 2, 0, ColorScheme.BRAND_ORANGE),
			new EmptyBorder(5, 8, 5, 8)));
		header.setAlignmentX(LEFT_ALIGNMENT);

		JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		titleRow.setOpaque(false);
		JLabel arrow = new JLabel(open ? "▾" : "▸");
		arrow.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		titleRow.add(arrow);
		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(20, 18));
		icon.setHorizontalAlignment(SwingConstants.CENTER);
		setCategoryIcon(icon, category);
		titleRow.add(icon);
		JLabel title = new JLabel(category.getLabel());
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(Color.WHITE);
		titleRow.add(title);
		header.add(titleRow, BorderLayout.WEST);

		JPanel right = new JPanel(new GridLayout(xp > 0 ? 2 : 1, 1));
		right.setOpaque(false);
		JLabel profit = new JLabel();
		profit.setFont(FontManager.getRunescapeBoldFont());
		profit.setHorizontalAlignment(SwingConstants.RIGHT);
		setProfit(profit, loot - supplies);
		right.add(profit);
		if (xp > 0)
		{
			JLabel xpLabel = small(QuantityFormatter.quantityToStackSize(xp) + " XP", ColorScheme.LIGHT_GRAY_COLOR);
			xpLabel.setHorizontalAlignment(SwingConstants.RIGHT);
			right.add(xpLabel);
		}
		header.add(right, BorderLayout.EAST);

		header.setToolTipText(open
			? "Click to minimize. Right-click to reset this category."
			: "Click to show everything under " + category.getLabel() + ".");
		onPress(header, () ->
		{
			if (!collapsedCategories.remove(category))
			{
				collapsedCategories.add(category);
			}
			redraw();
		}, CATEGORY_BAR);

		JPopupMenu menu = new JPopupMenu();
		JMenuItem reset = new JMenuItem("Reset all " + category.getLabel());
		reset.addActionListener(e ->
		{
			int choice = JOptionPane.showConfirmDialog(this,
				"Clear everything tracked under " + category.getLabel() + "?",
				"Reset " + category.getLabel(), JOptionPane.YES_NO_OPTION);
			if (choice == JOptionPane.YES_OPTION)
			{
				actions.accept(Action.RESET_CATEGORY, category.name());
			}
		});
		menu.add(reset);
		header.setComponentPopupMenu(menu);
		return header;
	}

	/** Skill icon for skill categories; an item icon for the others. */
	private void setCategoryIcon(JLabel label, SourceCategory category)
	{
		ImageIcon cached = categoryIcons.get(category);
		if (cached != null)
		{
			label.setIcon(cached);
			return;
		}
		Skill skill = skillFor(category);
		if (skill != null)
		{
			ImageIcon icon = new ImageIcon(skillIconManager.getSkillImage(skill, true));
			categoryIcons.put(category, icon);
			label.setIcon(icon);
			return;
		}
		int itemId = categoryItemIcon(category);
		if (itemId <= 0)
		{
			return;
		}
		AsyncBufferedImage image = itemManager.getImage(itemId);
		image.onLoaded(() -> SwingUtilities.invokeLater(() ->
		{
			ImageIcon icon = new ImageIcon(ImageUtil.resizeImage(image, 20, 18, true));
			categoryIcons.put(category, icon);
			label.setIcon(icon);
		}));
	}

	private static Skill skillFor(SourceCategory category)
	{
		if (category.getSkillName() == null)
		{
			return null;
		}
		for (Skill skill : Skill.values())
		{
			if (skill.getName().equalsIgnoreCase(category.getSkillName()))
			{
				return skill;
			}
		}
		return null;
	}

	private static int categoryItemIcon(SourceCategory category)
	{
		switch (category)
		{
			case TELEPORTS:
				return ItemID.POH_TABLET_VARROCKTELEPORT;
			case GENERAL_SUPPLIES:
				return ItemID.SHARK;
			case PURCHASE:
				return ItemID.COINS_10000;
			case PROCESSING:
				return ItemID.HAMMER;
			default:
				return -1;
		}
	}

	// ---------------------------------------------------------------- source boxes

	/** Loot Tracker style: title bar, loot grid, red supplies grid, XP bubbles. */
	private JPanel sourceBox(PanelModel.SourceView source)
	{
		JPanel box = new JPanel();
		box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
		box.setAlignmentX(LEFT_ALIGNMENT);

		// The header grows to fit: long names ("Insatiable mutated Bloodveld") wrap onto a second line.
		JPanel header = new FitHeightPanel(new BorderLayout(4, 0));
		header.setBackground(TITLE_BAR);
		header.setBorder(new EmptyBorder(7, 7, 7, 7));
		header.setAlignmentX(LEFT_ALIGNMENT);
		JLabel profit = new JLabel();
		profit.setFont(FontManager.getRunescapeBoldFont());
		profit.setVerticalAlignment(SwingConstants.TOP);
		setProfit(profit, source.profit());
		int nameWidth = Math.max(60, CONTENT_WIDTH - 14 - 4 - 8 - profit.getPreferredSize().width);
		// Swing's HTML renders CSS pixels about a third wider than screen pixels.
		JLabel name = new JLabel("<html><div style='width:" + (nameWidth * 3 / 4) + "px'>" + escapeHtml(source.name)
			+ "&nbsp;&nbsp;<font color='#a5a5a5'>x&nbsp;" + QuantityFormatter.formatNumber(source.count)
			+ "</font></div></html>");
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setForeground(Color.WHITE);
		name.setVerticalAlignment(SwingConstants.TOP);
		header.add(name, BorderLayout.CENTER);
		header.add(profit, BorderLayout.EAST);
		header.setToolTipText(source.category.getCountLabel() + ". Click to collapse, right-click to reset.");
		box.add(header);

		boolean open = !collapsed.contains(source.key);
		onPress(header, () ->
		{
			if (!collapsed.remove(source.key))
			{
				collapsed.add(source.key);
			}
			redraw();
		}, TITLE_BAR);
		JPopupMenu menu = new JPopupMenu();
		JMenuItem reset = new JMenuItem("Reset " + source.name);
		reset.addActionListener(e -> actions.accept(Action.RESET_SOURCE, source.key));
		menu.add(reset);
		header.setComponentPopupMenu(menu);

		if (!open)
		{
			return box;
		}

		if (!source.loot.isEmpty())
		{
			box.add(sectionLine("Loot", gp(source.lootValue), PROFIT_COLOR,
				"GE value, or High Alchemy for untradeables. HA total: " + gp(source.lootHa)));
			box.add(lootGrid(source.key, source.loot));
		}
		if (!source.supplies.isEmpty())
		{
			box.add(sectionLine("Supplies", gp(source.supplyGp), LOSS_COLOR, null));
			box.add(supplyGrid(source.key, source.supplies));
		}
		if (!source.xpBySkill.isEmpty())
		{
			box.add(Box.createVerticalStrut(5));
			box.add(xpBubbles(source.xpBySkill));
		}
		box.add(Box.createVerticalStrut(3));
		return box;
	}

	/** A whole non-PvM category as one table: its count, loot, supplies and XP. */
	private JPanel categoryTable(PanelModel.SourceView source)
	{
		JPanel table = new JPanel();
		table.setLayout(new BoxLayout(table, BoxLayout.Y_AXIS));
		table.setAlignmentX(LEFT_ALIGNMENT);
		JPanel count = row();
		count.setBorder(new EmptyBorder(2, 7, 0, 7));
		count.add(small(countText(source), ColorScheme.LIGHT_GRAY_COLOR), BorderLayout.WEST);
		table.add(count);
		if (!source.loot.isEmpty())
		{
			table.add(sectionLine("Loot", gp(source.lootValue), PROFIT_COLOR,
				"GE value, or High Alchemy for untradeables. HA total: " + gp(source.lootHa)));
			table.add(lootGrid(source.key, source.loot));
		}
		if (!source.supplies.isEmpty())
		{
			table.add(sectionLine("Supplies", gp(source.supplyGp), LOSS_COLOR, null));
			table.add(supplyGrid(source.key, source.supplies));
		}
		if (!source.xpBySkill.isEmpty())
		{
			table.add(Box.createVerticalStrut(5));
			table.add(xpBubbles(source.xpBySkill));
		}
		table.add(Box.createVerticalStrut(3));
		return table;
	}

	private static String countText(PanelModel.SourceView source)
	{
		return QuantityFormatter.formatNumber(source.count) + " " + source.category.getCountLabel().toLowerCase(Locale.ENGLISH);
	}

	private JPanel sectionLine(String label, String value, Color color, String tooltip)
	{
		JPanel line = row();
		line.setBorder(new EmptyBorder(6, 7, 3, 7));
		line.add(small(label, color), BorderLayout.WEST);
		line.add(small(value, color), BorderLayout.EAST);
		line.setToolTipText(tooltip);
		return line;
	}

	private JPanel lootGrid(String sourceKey, List<PanelModel.LootView> items)
	{
		JPanel grid = slotGrid(items.size());
		for (int i = 0; i < gridSlots(items.size()); i++)
		{
			if (i < items.size())
			{
				PanelModel.LootView item = items.get(i);
				grid.add(slot(item.itemId, item.quantity, lootTooltip(item), SLOT, item.excluded,
					() -> actions.accept(Action.TOGGLE_LOOT, sourceKey + "\n" + item.itemId)));
			}
			else
			{
				grid.add(emptySlot(SLOT));
			}
		}
		return grid;
	}

	private JPanel supplyGrid(String sourceKey, List<PanelModel.SupplyView> supplies)
	{
		JPanel grid = slotGrid(supplies.size());
		for (int i = 0; i < gridSlots(supplies.size()); i++)
		{
			if (i < supplies.size())
			{
				PanelModel.SupplyView supply = supplies.get(i);
				grid.add(slot(supply.itemId, supply.quantity, supplyTooltip(supply), SUPPLY_SLOT, supply.excluded,
					() -> actions.accept(Action.TOGGLE_SUPPLY, sourceKey + "\n" + supply.lineKey)));
			}
			else
			{
				grid.add(emptySlot(SUPPLY_SLOT));
			}
		}
		return grid;
	}

	private static int gridSlots(int items)
	{
		return Math.max(1, (items + ICON_COLUMNS - 1) / ICON_COLUMNS) * ICON_COLUMNS;
	}

	private static JPanel slotGrid(int items)
	{
		JPanel grid = new JPanel(new GridLayout(gridSlots(items) / ICON_COLUMNS, ICON_COLUMNS, 1, 1));
		grid.setBackground(ColorScheme.DARK_GRAY_COLOR);
		grid.setAlignmentX(LEFT_ALIGNMENT);
		return grid;
	}

	/**
	 * One item slot. Right-click toggles whether the item counts towards profit;
	 * excluded items are drawn faded, like ignored items in the Loot Tracker.
	 */
	private JPanel slot(int itemId, long quantity, String tooltip, Color background, boolean excluded, Runnable toggle)
	{
		JPanel slot = emptySlot(background);
		JLabel icon = new JLabel();
		icon.setHorizontalAlignment(SwingConstants.CENTER);
		icon.setVerticalAlignment(SwingConstants.CENTER);
		icon.setToolTipText(excluded ? tooltip.replace("</html>", "<br><i>Excluded from profit</i></html>") : tooltip);
		int qty = (int) Math.min(Integer.MAX_VALUE, quantity);
		AsyncBufferedImage image = itemManager.getImage(itemId, qty, qty > 1);
		if (excluded)
		{
			image.onLoaded(() -> SwingUtilities.invokeLater(() ->
				icon.setIcon(new ImageIcon(ImageUtil.alphaOffset(image, 0.3f)))));
		}
		else
		{
			image.addTo(icon);
		}
		slot.add(icon, BorderLayout.CENTER);

		JPopupMenu menu = new JPopupMenu();
		JMenuItem item = new JMenuItem(excluded ? "Include in profit" : "Exclude from profit");
		item.addActionListener(e -> toggle.run());
		menu.add(item);
		slot.setComponentPopupMenu(menu);
		icon.setInheritsPopupMenu(true);
		return slot;
	}

	private static JPanel emptySlot(Color background)
	{
		// BorderLayout, not the default FlowLayout: its 5px gap pushed icons down and cut off their bottom.
		JPanel slot = new JPanel(new BorderLayout());
		slot.setBackground(background);
		slot.setPreferredSize(new Dimension(36, 34));
		return slot;
	}

	/** Small rounded-looking tags, one per skill, wrapped to the panel width. */
	private static JPanel xpBubbles(Map<String, Long> xpBySkill)
	{
		JPanel rows = new JPanel();
		rows.setLayout(new BoxLayout(rows, BoxLayout.Y_AXIS));
		rows.setAlignmentX(LEFT_ALIGNMENT);
		JPanel current = null;
		int used = 0;
		for (Map.Entry<String, Long> entry : xpBySkill.entrySet())
		{
			JLabel bubble = new JLabel("<html>" + entry.getKey() + " <font color='#ffffff'>"
				+ QuantityFormatter.formatNumber(entry.getValue()) + "</font></html>");
			bubble.setFont(FontManager.getRunescapeSmallFont());
			bubble.setForeground(ColorScheme.TEXT_COLOR);
			bubble.setOpaque(true);
			bubble.setBackground(BUBBLE);
			bubble.setBorder(new EmptyBorder(2, 7, 2, 7));
			FontMetrics fm = bubble.getFontMetrics(bubble.getFont());
			int width = fm.stringWidth(entry.getKey() + " " + QuantityFormatter.formatNumber(entry.getValue())) + 18;
			if (current == null || used + width > CONTENT_WIDTH)
			{
				current = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
				current.setAlignmentX(LEFT_ALIGNMENT);
				current.setBorder(new EmptyBorder(0, 0, 3, 0));
				rows.add(current);
				used = 0;
			}
			if (used > 0)
			{
				current.add(Box.createHorizontalStrut(4));
			}
			current.add(bubble);
			used += width + 4;
		}
		return rows;
	}

	// ---------------------------------------------------------------- tooltips

	private static String lootTooltip(PanelModel.LootView item)
	{
		StringBuilder sb = new StringBuilder("<html>");
		sb.append(item.name).append(" x ").append(QuantityFormatter.formatNumber(item.quantity));
		if (item.itemId == ItemID.COINS)
		{
			return sb.append("</html>").toString();
		}
		if (item.tradeable)
		{
			sb.append("<br>GE: ").append(QuantityFormatter.quantityToStackSize(item.geTotal()));
			if (item.quantity > 1)
			{
				sb.append(" (").append(QuantityFormatter.quantityToStackSize(item.geEach)).append(" ea)");
			}
		}
		else
		{
			sb.append("<br>Untradeable, valued at High Alchemy");
		}
		sb.append("<br>HA: ").append(QuantityFormatter.quantityToStackSize(item.haTotal()));
		if (item.quantity > 1)
		{
			sb.append(" (").append(QuantityFormatter.quantityToStackSize(item.haEach)).append(" ea)");
		}
		return sb.append("</html>").toString();
	}

	private static String supplyTooltip(PanelModel.SupplyView supply)
	{
		String unit = supply.unit == null ? "" : " " + supply.unit;
		StringBuilder sb = new StringBuilder("<html>");
		sb.append(supply.name).append(" x ").append(QuantityFormatter.formatNumber(supply.quantity)).append(unit);
		sb.append("<br>Cost: ").append(QuantityFormatter.quantityToStackSize(supply.gp));
		if (supply.quantity > 1)
		{
			sb.append(" (").append(QuantityFormatter.quantityToStackSize(supply.gp / supply.quantity))
				.append(supply.unit == null ? " ea" : " per " + supply.unit.replaceAll("s$", "")).append(")");
		}
		return sb.append("</html>").toString();
	}

	// ---------------------------------------------------------------- helpers

	/**
	 * Toggles on mouse press rather than click: the panel redraws every tick while
	 * you're active, which would lose a click whose press and release straddle a redraw.
	 */
	private static void onPress(JPanel target, Runnable action, Color background)
	{
		target.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mousePressed(MouseEvent e)
			{
				if (e.getButton() == MouseEvent.BUTTON1)
				{
					action.run();
				}
			}

			@Override
			public void mouseEntered(MouseEvent e)
			{
				target.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
			}

			@Override
			public void mouseExited(MouseEvent e)
			{
				target.setBackground(background);
			}
		});
	}

	private static JPanel indented(JPanel box)
	{
		JPanel wrap = new JPanel(new BorderLayout());
		wrap.setBorder(new EmptyBorder(0, 6, 0, 0));
		wrap.setAlignmentX(LEFT_ALIGNMENT);
		wrap.add(box, BorderLayout.CENTER);
		return wrap;
	}

	private static JPanel row()
	{
		JPanel row = new JPanel(new BorderLayout());
		row.setOpaque(false);
		row.setAlignmentX(LEFT_ALIGNMENT);
		return row;
	}

	private static String html(String label, String value, String valueColor)
	{
		return "<html><font color='#a5a5a5'>" + label + "</font> <font color='" + valueColor + "'>" + value + "</font></html>";
	}

	private static String gp(long value)
	{
		return QuantityFormatter.quantityToStackSize(value) + " gp";
	}

	private static void setProfit(JLabel label, long profit)
	{
		label.setText((profit > 0 ? "+" : "") + QuantityFormatter.quantityToStackSize(profit));
		label.setForeground(profit >= 0 ? PROFIT_COLOR : LOSS_COLOR);
	}

	private static JLabel small(String text, Color color)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(color);
		return label;
	}

	private static JLabel note(String text)
	{
		JLabel label = new JLabel("<html>" + text + "</html>");
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setBorder(new EmptyBorder(8, 2, 8, 2));
		label.setAlignmentX(LEFT_ALIGNMENT);
		return label;
	}

	private static String escapeHtml(String text)
	{
		return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	/** Stretches to the panel's width but only ever as tall as its contents need. */
	private static final class FitHeightPanel extends JPanel
	{
		FitHeightPanel(java.awt.LayoutManager layout)
		{
			super(layout);
		}

		@Override
		public Dimension getMaximumSize()
		{
			return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
		}
	}
}
