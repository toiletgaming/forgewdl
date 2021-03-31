/*
 * This file is part of World Downloader: A mod to make backups of your
 * multiplayer worlds.
 * http://www.minecraftforum.net/forums/mapping-and-modding/minecraft-mods/2520465
 *
 * Copyright (c) 2014 nairol, cubic72
 * Copyright (c) 2017 Pokechu22, julialy
 *
 * This project is licensed under the MMPLv2.  The full text of the MMPL can be
 * found in LICENSE.md, or online at https://github.com/iopleke/MMPLv2/blob/master/LICENSE.md
 * For information about this the MMPLv2, see http://stopmodreposts.org/
 *
 * Do not redistribute (in modified or unmodified form) without prior permission.
 */
package wdl.gui;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.resources.I18n;

import org.lwjgl.input.Keyboard;

import wdl.WDL;

/**
 * A GUI for selecting which world the player is currently in.
 *
 * TODO: I might want to move the multiworld setup logic elsewhere.
 */
public class GuiWDLMultiworldSelect extends GuiTurningCameraBase {
	private class WorldGuiButton extends ExtButton {
		public WorldGuiButton(int offset, int x, int y, int width,
				int height) {
			super(offset, x, y, width, height, "");
		}

		@Override
		public void beforeDraw() {
			MultiworldInfo info = getWorldInfo();
			if (info == null) {
				displayString = "";
				enabled = false;
			} else {
				displayString = info.displayName;
				enabled = true;
			}

			if (info != null && info == selectedMultiWorld) {
				drawRect(this.x - 2, this.y - 2,
						this.x + width + 2, this.y + height + 2,
						0xFF007F00);
			}
		}

		@Override
		public void afterDraw() { }

		/**
		 * Gets the world folder name marked by this button.
		 *
		 * @return the world info, or null if there isn't any.
		 */
		public MultiworldInfo getWorldInfo() {
			int location = index + id;
			if (location < 0) {
				return null;
			}
			if (location >= linkedWorldsFiltered.size()) {
				return null;
			}

			return linkedWorldsFiltered.get(location);
		}
	}

	/**
	 * Info for a Multiworld.
	 *
	 * TODO: I may want to move this elsewhere.
	 */
	private static class MultiworldInfo {
		public MultiworldInfo(String folderName, String displayName) {
			this.folderName = folderName;
			this.displayName = displayName;
		}

		public final String folderName;
		public final String displayName;

		private List<String> description;

		/**
		 * Gets some information about this info.
		 */
		public List<String> getDescription() {
			if (description == null) {
				description = new ArrayList<>();

				// TODO: More info than just dimensions - EG if the
				// chunk the player is in is added, etc.
				description.add("Defined dimensions:");
				File savesFolder = new File(WDL.minecraft.gameDir, "saves");
				File world = new File(savesFolder, WDL.getWorldFolderName(folderName));
				File[] subfolders = world.listFiles();

				if (subfolders != null) {
					for (File subfolder : subfolders) {
						if (subfolder.listFiles() == null) {
							// Not a folder
							continue;
						}
						if (subfolder.listFiles().length == 0) {
							// Empty folder - we don't count these.
							continue;
						}
						if (subfolder.getName().equals("region")) {
							description.add(" * Overworld (#0)");
						} else if (subfolder.getName().startsWith("DIM")) {
							String dimension = subfolder.getName().substring(3);
							if (dimension.equals("-1")) {
								description.add(" * Nether (#-1)");
							} else if (dimension.equals("1")) {
								description.add(" * The End (#1)");
							} else {
								description.add(" * #" + dimension);
							}
						}
					}
				}
			}

			return description;
		}
	}

	/**
	 * Interface for a listener when the "cancel" or "use this world" buttons
	 * are clicked.
	 *
	 * Note that implementations must <i>also</i> handle opening the correct
	 * next GUI.
	 */
	public static interface WorldSelectionCallback {
		/**
		 * Called when the cancel button is clicked.
		 */
		public abstract void onCancel();

		/**
		 * Called when the "Use this world" button is clicked.
		 *
		 * @param selectedWorld
		 *            The folder name for the given world.
		 */
		public abstract void onWorldSelected(String selectedWorld);
	}

	/**
	 * The callback for when the done button is clicked.
	 */
	private final WorldSelectionCallback callback;
	/**
	 * Title of the GUI.
	 */
	private final String title;
	/**
	 * The cancel button.
	 */
	private GuiButton cancelBtn;
	/**
	 * The accept button.
	 */
	private GuiButton acceptBtn;
	/**
	 * The "New name" field.
	 */
	private GuiTextField newNameField;
	/**
	 * The "Search" field.  Allows filtering.
	 */
	private GuiTextField searchField;
	/**
	 * The "New world" button.
	 */
	private GuiButton newWorldButton;
	/**
	 * Should the {@link #newNameField} be shown?
	 */
	private boolean showNewWorldTextBox;
	/**
	 * Worlds linked together for the given server.
	 */
	private List<MultiworldInfo> linkedWorlds;
	/**
	 * List of worlds to display.
	 */
	private List<MultiworldInfo> linkedWorldsFiltered;
	/**
	 * The currently selected multiworld.
	 */
	private MultiworldInfo selectedMultiWorld;
	/**
	 * Scrolling index.
	 */
	private int index = 0;
	/**
	 * The next button (scrolls the list right).
	 */
	private GuiButton nextButton;
	/**
	 * The previous button (scrolls the list left).
	 */
	private GuiButton prevButton;
	/**
	 * The number of world buttons there are.
	 */
	private int numWorldButtons;
	/**
	 * Text currently being searched for.
	 */
	private String searchText = "";

	public GuiWDLMultiworldSelect(String title, WorldSelectionCallback callback) {
		this.title = title;
		this.callback = callback;

		// Build a list of world names.
		String[] worldNames = WDL.baseProps.getProperty("LinkedWorlds")
				.split("\\|");
		linkedWorlds = new ArrayList<>();

		for (String worldName : worldNames) {
			if (worldName == null || worldName.isEmpty()) {
				continue;
			}

			Properties props = WDL.loadWorldProps(worldName);

			if (!props.containsKey("WorldName")) {
				continue;
			}

			String displayName = props.getProperty("WorldName", worldName);
			linkedWorlds.add(new MultiworldInfo(worldName, displayName));
		}

		linkedWorldsFiltered = new ArrayList<>();
		linkedWorldsFiltered.addAll(linkedWorlds);
	}

	@Override
	public void initGui() {
		super.initGui();

		numWorldButtons = (this.width - 50) / 155;

		if (numWorldButtons < 1) {
			numWorldButtons = 1;
		}

		int offset = (numWorldButtons * 155 + 45) / 2;
		int y = this.height - 49;

		this.cancelBtn = new GuiButton(-1, this.width / 2 - 155, this.height - 25,
				150, 20, I18n.format("gui.cancel"));
		this.buttonList.add(this.cancelBtn);

		this.acceptBtn = new GuiButton(-2, this.width / 2 + 5, this.height - 25,
				150, 20, I18n.format("wdl.gui.multiworldSelect.done"));
		this.acceptBtn.enabled = (selectedMultiWorld != null);
		this.buttonList.add(this.acceptBtn);

		prevButton = new GuiButton(-4, this.width / 2 - offset, y, 20, 20, "<");
		this.buttonList.add(prevButton);

		for (int i = 0; i < numWorldButtons; i++) {
			this.buttonList.add(new WorldGuiButton(i, this.width / 2 - offset
					+ i * 155 + 25, y, 150, 20));
		}

		nextButton = new GuiButton(-5, this.width / 2 - offset + 25
				+ numWorldButtons * 155, y, 20, 20, ">");
		this.buttonList.add(nextButton);

		this.newWorldButton = new GuiButton(-3, this.width / 2 - 155, 29, 150, 20,
				I18n.format("wdl.gui.multiworldSelect.newName"));
		this.buttonList.add(newWorldButton);

		this.newNameField = new GuiTextField(40, this.fontRenderer,
				this.width / 2 - 155, 29, 150, 20);

		this.searchField = new GuiTextField(41, this.fontRenderer,
				this.width / 2 + 5, 29, 150, 20);
		this.searchField.setText(searchText);
	}

	@Override
	protected void actionPerformed(GuiButton button) {
		if (button.enabled) {
			if (button instanceof WorldGuiButton) {
				selectedMultiWorld = ((WorldGuiButton) button).getWorldInfo();
				if (selectedMultiWorld != null) {
					acceptBtn.enabled = true;
				} else {
					acceptBtn.enabled = false;
				}
			} else if (button.id == -1) {
				callback.onCancel();
			} else if (button.id == -2) {
				callback.onWorldSelected(selectedMultiWorld.folderName);
			} else if (button.id == -3) {
				this.showNewWorldTextBox = true;
			} else if (button.id == -4) {
				index--;
			} else if (button.id == -5) {
				index++;
			}
		}
	}

	/**
	 * Called when the mouse is clicked.
	 */
	@Override
	protected void mouseClicked(int mouseX, int mouseY, int mouseButton)
			throws IOException {
		super.mouseClicked(mouseX, mouseY, mouseButton);

		if (this.showNewWorldTextBox) {
			this.newNameField.mouseClicked(mouseX, mouseY, mouseButton);
		}

		this.searchField.mouseClicked(mouseX, mouseY, mouseButton);
	}

	/**
	 * Fired when a key is typed. This is the equivalent of
	 * KeyListener.keyTyped(KeyEvent e).
	 */
	@Override
	protected void keyTyped(char typedChar, int keyCode) throws IOException {
		if (keyCode == Keyboard.KEY_ESCAPE) {
			callback.onCancel();
		}

		super.keyTyped(typedChar, keyCode);

		if (this.showNewWorldTextBox) {
			this.newNameField.textboxKeyTyped(typedChar, keyCode);

			if (keyCode == Keyboard.KEY_RETURN) {
				String newName = this.newNameField.getText();

				if (newName != null && !newName.isEmpty()) {
					//TODO: Ensure that the new world is in view.
					this.addMultiworld(newName);
					this.newNameField.setText("");
					this.showNewWorldTextBox = false;
				}
			}
		}

		// Return value of this function seems to be whether the text changed.
		if (this.searchField.textboxKeyTyped(typedChar, keyCode)) {
			this.searchText = searchField.getText();
			rebuildFilteredWorlds();
		}
	}

	/**
	 * Called from the main game loop to update the screen.
	 */
	@Override
	public void updateScreen() {
		this.newNameField.updateCursorCounter();
		this.searchField.updateCursorCounter();
		super.updateScreen();
	}

	/**
	 * Draws the screen and all the components in it.
	 */
	@Override
	public void drawScreen(int mouseX, int mouseY, float partialTicks) {
		if (index >= linkedWorlds.size() - numWorldButtons) {
			index = linkedWorlds.size() - numWorldButtons;
			nextButton.enabled = false;
		} else {
			nextButton.enabled = true;
		}
		if (index <= 0) {
			index = 0;
			prevButton.enabled = false;
		} else {
			prevButton.enabled = true;
		}

		Utils.drawBorder(53, 53, 0, 0, height, width);

		this.drawCenteredString(this.fontRenderer, title, this.width / 2, 8,
				0xFFFFFF);

		this.drawCenteredString(this.fontRenderer,
				I18n.format("wdl.gui.multiworldSelect.subtitle"),
				this.width / 2, 18, 0xFF0000);

		if (this.showNewWorldTextBox) {
			this.newNameField.drawTextBox();
		}
		this.searchField.drawTextBox();
		//Hint as to what the text box does
		if (this.searchField.getText().isEmpty() && !this.searchField.isFocused()) {
			drawString(fontRenderer,
					I18n.format("wdl.gui.multiworldSelect.search"),
					searchField.x + 4, searchField.y + 6,
					0x909090);
		}

		newWorldButton.visible = !showNewWorldTextBox;

		super.drawScreen(mouseX, mouseY, partialTicks);

		drawMultiworldDescription();
	}

	/**
	 * Creates a new multiworld, and adds it to the list.
	 */
	private void addMultiworld(String worldName) {
		String folderName = worldName;
		char[] unsafeChars = "\\/:*?\"<>|.".toCharArray();

		//TODO: ReplaceAll with a regular expression may be cleaner
		for (char unsafeChar : unsafeChars) {
			folderName = folderName.replace(unsafeChar, '_');
		}

		Properties worldProps = new Properties(WDL.baseProps);
		worldProps.setProperty("WorldName", worldName);

		String linkedWorldsProp = WDL.baseProps.getProperty("LinkedWorlds");
		linkedWorldsProp += "|" + folderName;

		WDL.baseProps.setProperty("LinkedWorlds", linkedWorldsProp);
		WDL.saveProps(folderName, worldProps);

		linkedWorlds.add(new MultiworldInfo(folderName, worldName));

		rebuildFilteredWorlds();
	}

	/**
	 * Rebuilds the {@link #linkedWorldsFiltered} list after a change
	 * has occurred to the search or the {@link #linkedWorlds} list.
	 */
	private void rebuildFilteredWorlds() {
		String searchFilter = searchText.toLowerCase();
		linkedWorldsFiltered.clear();
		for (MultiworldInfo info : linkedWorlds) {
			if (info.displayName.toLowerCase().contains(searchFilter)) {
				linkedWorldsFiltered.add(info);
			}
		}

	}

	/**
	 * Draws info about the selected world.
	 */
	private void drawMultiworldDescription() {
		if (selectedMultiWorld == null) {
			return;
		}

		String title = "Info about "
				+ selectedMultiWorld.displayName;
		List<String> description = selectedMultiWorld.getDescription();

		int maxWidth = fontRenderer.getStringWidth(title);
		for (String line : description) {
			int width = fontRenderer.getStringWidth(line);
			if (width > maxWidth) {
				maxWidth = width;
			}
		}

		drawRect(2, 61, 5 + maxWidth + 3, height - 61, 0x80000000);

		drawString(fontRenderer, title, 5, 64, 0xFFFFFF);

		int y = 64 + fontRenderer.FONT_HEIGHT;
		for (String s : description) {
			drawString(fontRenderer, s, 5, y, 0xFFFFFF);
			y += fontRenderer.FONT_HEIGHT;
		}
	}
}
