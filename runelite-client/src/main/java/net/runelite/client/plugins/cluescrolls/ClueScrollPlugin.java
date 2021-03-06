/*
 * Copyright (c) 2016-2017, Seth <Sethtroll3@gmail.com>
 * Copyright (c) 2018, Lotto <https://github.com/devLotto>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *   list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *   this list of conditions and the following disclaimer in the documentation
 *   and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.cluescrolls;

import com.google.common.eventbus.Subscribe;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Query;
import net.runelite.api.Region;
import net.runelite.api.Tile;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.queries.InventoryItemQuery;
import net.runelite.api.queries.NPCQuery;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.cluescrolls.clues.AnagramClue;
import net.runelite.client.plugins.cluescrolls.clues.CipherClue;
import net.runelite.client.plugins.cluescrolls.clues.ClueScroll;
import net.runelite.client.plugins.cluescrolls.clues.CoordinateClue;
import net.runelite.client.plugins.cluescrolls.clues.CrypticClue;
import net.runelite.client.plugins.cluescrolls.clues.EmoteClue;
import net.runelite.client.plugins.cluescrolls.clues.FairyRingClue;
import net.runelite.client.plugins.cluescrolls.clues.LocationClueScroll;
import net.runelite.client.plugins.cluescrolls.clues.MapClue;
import net.runelite.client.plugins.cluescrolls.clues.NpcClueScroll;
import net.runelite.client.plugins.cluescrolls.clues.ObjectClueScroll;
import net.runelite.client.plugins.cluescrolls.clues.TextClueScroll;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.util.QueryRunner;

@PluginDescriptor(
	name = "Clue Scroll"
)
@Slf4j
public class ClueScrollPlugin extends Plugin
{
	private static final Duration WAIT_DURATION = Duration.ofMinutes(4);

	@Getter
	private ClueScroll clue;

	@Getter
	private NPC[] npcsToMark;

	@Getter
	private GameObject[] objectsToMark;

	@Getter
	private Item[] equippedItems;

	@Getter
	private Instant clueTimeout;

	@Inject
	@Getter
	private Client client;

	@Inject
	private ItemManager itemManager;

	@Inject
	private QueryRunner queryRunner;

	@Inject
	private ClueScrollOverlay clueScrollOverlay;

	@Inject
	private ClueScrollEmoteOverlay clueScrollEmoteOverlay;

	@Inject
	private ClueScrollWorldOverlay clueScrollWorldOverlay;

	@Override
	public Collection<Overlay> getOverlays()
	{
		return Arrays.asList(clueScrollOverlay, clueScrollEmoteOverlay, clueScrollWorldOverlay);
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.SERVER)
		{
			return;
		}

		if (!event.getMessage().equals("Well done, you've completed the Treasure Trail!"))
		{
			return;
		}

		resetClue();
	}

	@Subscribe
	public void onItemContainerChanged(final ItemContainerChanged event)
	{
		// Check if item was removed from inventory
		if (clue != null && event.getItemContainer() == client.getItemContainer(InventoryID.INVENTORY))
		{
			final Item[] items = event.getItemContainer().getItems();
			boolean found = false;

			// Clue was maybe removed from inventory, check if there is any clue scrolls left
			for (Item item : items)
			{
				final ItemComposition itemContainerDefinition = itemManager.getItemComposition(item.getId());

				// Check if we have any clue scrolls left
				if (itemContainerDefinition.getName().startsWith("Clue scroll"))
				{
					found = true;
					break;
				}
			}

			if (!found)
			{
				resetClue();
			}
		}
	}

	@Subscribe
	public void onGameStateChanged(final GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			resetClue();
		}
	}

	@Subscribe
	public void onGameTick(final GameTick event)
	{
		npcsToMark = null;
		objectsToMark = null;
		equippedItems = null;

		// If we have location clue, set world location before all other types of clues
		// to allow NPCs and objects to override it when needed
		if (clue instanceof LocationClueScroll)
		{
			final WorldPoint location = ((LocationClueScroll) clue).getLocation();

			if (location != null)
			{
				client.setHintArrow(location);
			}
		}

		if (clue instanceof NpcClueScroll)
		{
			String npc = ((NpcClueScroll) clue).getNpc();

			if (npc != null)
			{
				Query query = new NPCQuery().nameContains(npc);
				npcsToMark = queryRunner.runQuery(query);

				// Set hint arrow to first NPC found as there can only be 1 hint arrow
				if (npcsToMark.length >= 1)
				{
					client.setHintArrow(npcsToMark[0]);
				}
			}
		}

		if (clue instanceof ObjectClueScroll)
		{
			final ObjectClueScroll objectClueScroll = (ObjectClueScroll) clue;
			int objectId = objectClueScroll.getObjectId();

			if (objectId != -1)
			{
				// Match object with location every time
				final WorldPoint location = objectClueScroll.getLocation();

				if (location != null)
				{
					final LocalPoint localLocation = LocalPoint.fromWorld(client, location);

					if (localLocation != null)
					{
						final Region region = client.getRegion();
						final Tile[][][] tiles = region.getTiles();
						final Tile tile = tiles[client.getPlane()][localLocation.getRegionX()][localLocation.getRegionY()];

						objectsToMark = Arrays.stream(tile.getGameObjects())
							.filter(object -> object != null && object.getId() == objectId)
							.toArray(GameObject[]::new);

						// Set hint arrow to first object found as there can only be 1 hint arrow
						if (objectsToMark.length >= 1)
						{
							client.setHintArrow(objectsToMark[0].getWorldLocation());
						}
					}
				}
			}
		}

		if (clue instanceof EmoteClue)
		{
			ItemContainer container = client.getItemContainer(InventoryID.EQUIPMENT);
			
			if (container != null)
			{
				equippedItems = container.getItems();
			}
		}

		ClueScroll clue = findClueScroll();

		if (clue == null && this.clue != null)
		{
			// If clue window isn't open, and we don't have a map clue in inventory,
			// but we have recorded the player having a clue,
			// wait for WAIT_DURATION before discarding the knowledge of the player having a clue.
			if (Instant.now().compareTo(clueTimeout.plus(WAIT_DURATION)) < 0)
			{
				return;
			}
		}

		// If we have a clue, save that knowledge
		// so the clue window doesn't have to be open.
		if (clue != null)
		{
			if (clue != this.clue)
			{
				resetClue();
			}

			this.clue = clue;
			this.clueTimeout = Instant.now();
		}
	}

	private void resetClue()
	{
		clue = null;
		client.clearHintArrow();
	}

	private ClueScroll findClueScroll()
	{
		Widget clueScrollText = client.getWidget(WidgetInfo.CLUE_SCROLL_TEXT);

		if (clueScrollText != null)
		{
			// Remove line breaks and also the rare occasion where there are double line breaks
			String text = clueScrollText.getText()
					.replaceAll("-<br>", "-")
					.replaceAll("<br>", " ")
					.replaceAll("[ ]+", " ")
					.toLowerCase();

			if (clue != null && clue instanceof TextClueScroll)
			{
				if (((TextClueScroll) clue).getText().equalsIgnoreCase(text))
				{
					return clue;
				}
			}

			if (text.startsWith("this anagram reveals who to speak to next:"))
			{
				return AnagramClue.forText(text);
			}
			else if (text.startsWith("the cipher reveals who to speak to next:"))
			{
				return CipherClue.forText(text);
			}
			else if (text.contains("degrees") && text.contains("minutes"))
			{
				return coordinatesToWorldPoint(text);
			}
			else
			{
				CrypticClue crypticClue = CrypticClue.forText(text);

				if (crypticClue != null)
				{
					return crypticClue;
				}

				EmoteClue emoteClue = EmoteClue.forText(text);

				if (emoteClue != null)
				{
					return emoteClue;
				}

				return FairyRingClue.forText(text);
			}
		}

		Item[] result = queryRunner.runQuery(new InventoryItemQuery(InventoryID.INVENTORY));

		if (result == null)
		{
			return null;
		}

		for (Item item : result)
		{
			MapClue clue = MapClue.forItemId(item.getId());

			if (clue != null)
			{
				return clue;
			}
		}

		return null;
	}

	/**
	 * Example input: "00 degrees 00 minutes north 07 degrees 13 minutes west"
	 */
	private CoordinateClue coordinatesToWorldPoint(String text)
	{
		String[] splitText = text.split(" ");

		if (splitText.length != 10)
		{
			log.warn("Splitting \"" + text + "\" did not result in an array of 10 cells");
			return null;
		}

		if (!splitText[1].equals("degrees") || !splitText[3].equals("minutes"))
		{
			log.warn("\"" + text + "\" is not a well formed coordinate string");
			return null;
		}

		int degY = Integer.parseInt(splitText[0]);
		int minY = Integer.parseInt(splitText[2]);

		if (splitText[4].equals("south"))
		{
			degY *= -1;
			minY *= -1;
		}

		int degX = Integer.parseInt(splitText[5]);
		int minX = Integer.parseInt(splitText[7]);

		if (splitText[9].equals("west"))
		{
			degX *= -1;
			minX *= -1;
		}

		return new CoordinateClue(text, coordinatesToWorldPoint(degX, minX, degY, minY));
	}

	/**
	 * This conversion is explained on
	 * http://oldschoolrunescape.wikia.com/wiki/Treasure_Trails/Guide/Coordinates
	 */
	private WorldPoint coordinatesToWorldPoint(int degX, int minX, int degY, int minY)
	{
		// Center of the Observatory
		int x2 = 2440;
		int y2 = 3161;

		x2 += degX * 32 + Math.round(minX / 1.875);
		y2 += degY * 32 + Math.round(minY / 1.875);

		return new WorldPoint(x2, y2, 0);
	}
}
