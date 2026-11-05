package com.gildedledger;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.WidgetNode;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetModalMode;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.util.ColorUtil;

/**
 * The small box that slides in at the top of the game screen (the one the game uses
 * for collection log entries), saying what a reminder is for.
 *
 * <p>Technique adapted from PvM Toolkit (BSD-2): open the game's notification
 * interface and run its own script to fill in the title and text.
 */
@Singleton
class ReminderPopup
{
	/** The game's script that animates the notification box with a title and text. */
	private static final int NOTIFICATION_SCRIPT = 3343;
	private static final int RESIZED_CHILD = 13;
	private static final int FIXED_CHILD = 43;
	private static final int CONTENT_CHILD = 1;

	private final Client client;
	private final ClientThread clientThread;
	private WidgetNode open;

	@Inject
	ReminderPopup(Client client, ClientThread clientThread)
	{
		this.client = client;
		this.clientThread = clientThread;
	}

	/** Shows the box. Must be called on the client thread. */
	void show(String title, String message)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}
		int topLevel = client.getTopLevelInterfaceId();
		if (topLevel < 0)
		{
			return;
		}
		close();
		int component = topLevel << 16 | (client.isResized() ? RESIZED_CHILD : FIXED_CHILD);
		WidgetNode node = client.openInterface(component, InterfaceID.NOTIFICATION_DISPLAY, WidgetModalMode.MODAL_CLICKTHROUGH);
		open = node;
		client.runScript(NOTIFICATION_SCRIPT, ColorUtil.wrapWithColorTag(title, ColorUtil.fromHex("ffb000")),
			ColorUtil.wrapWithColorTag(message, ColorUtil.fromHex("ffffff")), -1);
		// Close the interface once the box has finished sliding away.
		clientThread.invokeLater(() ->
		{
			Widget content = client.getWidget(InterfaceID.NOTIFICATION_DISPLAY, CONTENT_CHILD);
			if (content != null && content.getWidth() > 0)
			{
				return false;
			}
			if (open == node)
			{
				close();
			}
			return true;
		});
	}

	void close()
	{
		WidgetNode node = open;
		open = null;
		if (node == null)
		{
			return;
		}
		try
		{
			client.closeInterface(node, true);
		}
		catch (IllegalArgumentException ignored)
		{
			// Already closed by the game.
		}
	}
}
