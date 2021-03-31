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
package wdl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import javax.annotation.Nullable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Maps;
import com.mojang.realmsclient.RealmsMainScreen;
import com.mojang.realmsclient.dto.RealmsServer;

import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiScreenRealmsProxy;
import net.minecraft.client.multiplayer.ChunkProviderClient;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.resources.I18n;
import net.minecraft.crash.CrashReport;
import net.minecraft.crash.CrashReportCategory;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.Container;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagDouble;
import net.minecraft.nbt.NBTTagFloat;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.NetworkManager;
import net.minecraft.realms.RealmsScreen;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ReportedException;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.MinecraftException;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.EmptyChunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.storage.MapData;
import net.minecraft.world.storage.SaveHandler;
import net.minecraft.world.storage.ThreadedFileIOBase;
import net.minecraft.world.storage.WorldInfo;
import wdl.WorldBackup.WorldBackupType;
import wdl.api.IPlayerInfoEditor;
import wdl.api.ISaveListener;
import wdl.api.IWorldInfoEditor;
import wdl.api.WDLApi;
import wdl.api.WDLApi.ModInfo;
import wdl.gui.GuiWDLMultiworld;
import wdl.gui.GuiWDLMultiworldSelect;
import wdl.gui.GuiWDLOverwriteChanges;
import wdl.gui.GuiWDLSaveProgress;

/**
 * This is the main class that does most of the work.
 */
public class WDL {
	/**
	 * Owning username for the github repository to check for updates against.
	 *
	 * For <code>https://github.com/Pokechu22/WorldDownloader</code>, this would
	 * be <code>Pokechu22/WorldDownloader</code>.
	 *
	 * Note that WDL is licensed under the MMPLv2, which requires modified
	 * versions to be open source if they are released (plus requires permission
	 * for that - <a href="http://www.minecraftforum.net/private-messages/send?recipient=Pokechu22">
	 * send Pokechu22 a message on the Minecraft Forums to get it</a>).
	 *
	 * @see GithubInfoGrabber
	 */
	public static final String GITHUB_REPO = "Pokechu22/WorldDownloader";

	// TODO: This class needs to be split into smaller classes. There is way too
	// much different stuff in here.

	/**
	 * Reference to the Minecraft object.
	 */
	public static Minecraft minecraft;
	/**
	 * Reference to the World object that WDL uses.
	 */
	public static WorldClient worldClient;
	/**
	 * Reference to a connection specific object. Used to detect a new
	 * connection.
	 */
	public static NetworkManager networkManager = null;
	/**
	 * The current player. <br/>
	 * In 1.7.10, a net.minecraft.client.entity.EntityClientPlayerMP was used
	 * here, but now that does not exist, and it appears that the SinglePlayer
	 * type is what is supposed to be used instead.
	 */
	public static EntityPlayerSP thePlayer;

	/**
	 * Reference to the place where all the item stacks end up after receiving
	 * them.
	 */
	public static Container windowContainer;
	/**
	 * The block position clicked most recently.
	 *
	 * Needed for TileEntity creation.
	 */
	public static BlockPos lastClickedBlock;
	/**
	 * Last entity clicked (used for non-block tiles like minecarts with chests)
	 */
	public static Entity lastEntity;

	/**
	 * For player files and the level.dat file.
	 */
	public static SaveHandler saveHandler;
	/**
	 * For the chunks (despite the name it does also SAVE chunks)
	 */
	public static WDLChunkLoader chunkLoader;

	/**
	 * All tile entities that were saved manually, by chunk and then position.
	 */
	public static HashMap<ChunkPos, Map<BlockPos, TileEntity>> newTileEntities = new HashMap<>();

	/**
	 * All entities that were downloaded, by chunk.
	 */
	public static HashMultimap<ChunkPos, Entity> newEntities = HashMultimap.create();

	/**
	 * All of the {@link MapData}s that were sent to the client in the current
	 * world.
	 */
	public static HashMap<Integer, MapData> newMapDatas = new HashMap<>();

	// State variables:
	/**
	 * Whether the world is currently downloading.
	 *
	 * Don't modify this outside of WDL.java. TODO See above -- getters?
	 */
	public static boolean downloading = false;
	/**
	 * Is this a multiworld server?
	 */
	public static boolean isMultiworld = false;
	/**
	 * Are there saved properties available?
	 */
	public static boolean propsFound = false;
	/**
	 * Automatically restart after world changes?
	 */
	public static boolean startOnChange = false;
	/**
	 * Whether to ignore the check as to whether a player
	 * previously modified the world before downloading it.
	 */
	public static boolean overrideLastModifiedCheck = false;

	/**
	 * Is the world currently being saved?
	 */
	public static boolean saving = false;
	/**
	 * Has loading the world been delayed while the old one is being saved?
	 *
	 * Used when going thru portals or otherwise saving data.
	 */
	public static boolean worldLoadingDeferred = false;

	// Names:
	/**
	 * The current world name, if the world is multiworld.
	 */
	public static String worldName = "WorldDownloaderERROR";
	/**
	 * The folder in which worlds are being saved.
	 */
	public static String baseFolderName = "WorldDownloaderERROR";

	// Properties:
	/**
	 * Base properties, shared between each world on a multiworld server.
	 */
	public static Properties baseProps;
	/**
	 * Properties for a single world on a multiworld server, or all worlds
	 * on a single world server.
	 */
	public static Properties worldProps;
	/**
	 * Default properties used for creating baseProps.  Saved and loaded;
	 * shared between all servers.
	 */
	public static final Properties globalProps;
	/**
	 * Default properties that are used to create the global properites.
	 */
	public static final Properties defaultProps;

	private static final Logger LOGGER = LogManager.getLogger();

	// Initialization:
	static {
		minecraft = Minecraft.getMinecraft();
		// Initialize the Properties template:
		defaultProps = new Properties();
		defaultProps.setProperty("ServerName", "");
		defaultProps.setProperty("WorldName", "");
		defaultProps.setProperty("LinkedWorlds", "");
		defaultProps.setProperty("Backup", "ZIP");
		defaultProps.setProperty("AllowCheats", "true");
		defaultProps.setProperty("GameType", "keep");
		defaultProps.setProperty("Time", "keep");
		defaultProps.setProperty("Weather", "keep");
		defaultProps.setProperty("MapFeatures", "false");
		defaultProps.setProperty("RandomSeed", "");
		defaultProps.setProperty("MapGenerator", "void");
		defaultProps.setProperty("GeneratorName", "flat");
		defaultProps.setProperty("GeneratorVersion", "0");
		defaultProps.setProperty("GeneratorOptions", ";0");
		defaultProps.setProperty("Spawn", "player");
		defaultProps.setProperty("SpawnX", "8");
		defaultProps.setProperty("SpawnY", "127");
		defaultProps.setProperty("SpawnZ", "8");
		defaultProps.setProperty("PlayerPos", "keep");
		defaultProps.setProperty("PlayerX", "8");
		defaultProps.setProperty("PlayerY", "127");
		defaultProps.setProperty("PlayerZ", "8");
		defaultProps.setProperty("PlayerHealth", "20");
		defaultProps.setProperty("PlayerFood", "20");

		defaultProps.setProperty("Messages.enableAll", "true");

		//Set up entities.
		defaultProps.setProperty("Entity.TrackDistanceMode", "server");

		//Don't save these entities by default -- they're problematic.
		defaultProps.setProperty("Entity.FireworksRocketEntity.Enabled", "false");
		defaultProps.setProperty("Entity.EnderDragon.Enabled", "false");
		defaultProps.setProperty("Entity.WitherBoss.Enabled", "false");
		defaultProps.setProperty("Entity.PrimedTnt.Enabled", "false");
		defaultProps.setProperty("Entity.null.Enabled", "false"); // :(

		//Groups
		defaultProps.setProperty("EntityGroup.Other.Enabled", "true");
		defaultProps.setProperty("EntityGroup.Hostile.Enabled", "true");
		defaultProps.setProperty("EntityGroup.Passive.Enabled", "true");

		//Last saved time, so that you can tell if the world was modified.
		defaultProps.setProperty("LastSaved", "-1");

		// Whether the 1-time tutorial has been shown.
		defaultProps.setProperty("TutorialShown", "false");

		globalProps = new Properties(defaultProps);

		File dataFile = new File(minecraft.gameDir, "WorldDownloader.txt");
		try (FileReader reader = new FileReader(dataFile)) {
			globalProps.load(reader);
		} catch (Exception e) {
			LOGGER.debug("Failed to load global properties", e);
		}
		baseProps = new Properties(globalProps);
		worldProps = new Properties(baseProps);
	}

	/**
	 * Starts the download.
	 */
	public static void startDownload() {
		worldClient = minecraft.world;

		if (!WDLPluginChannels.canDownloadAtAll()) {
			return;
		}

		if (isMultiworld && worldName.isEmpty()) {
			// Ask the user which world is loaded
			minecraft.displayGuiScreen(new GuiWDLMultiworldSelect(I18n
					.format("wdl.gui.multiworldSelect.title.startDownload"),
					new GuiWDLMultiworldSelect.WorldSelectionCallback() {
				@Override
				public void onWorldSelected(String selectedWorld) {
					WDL.worldName = selectedWorld;
					WDL.isMultiworld = true;
					WDL.propsFound = true;

					minecraft.displayGuiScreen(null);
					startDownload();
				}

				@Override
				public void onCancel() {
					minecraft.displayGuiScreen(null);
					cancelDownload();
				}
			}));
			return;
		}

		if (!propsFound) {
			// Never seen this server before. Ask user about multiworlds:
			minecraft.displayGuiScreen(new GuiWDLMultiworld(new GuiWDLMultiworld.MultiworldCallback() {
				@Override
				public void onSelect(boolean enableMutliworld) {
					isMultiworld = enableMutliworld;

					if (isMultiworld) {
						// Ask the user which world is loaded
						// TODO: Copy-pasted code from above -- suboptimal.
						minecraft.displayGuiScreen(new GuiWDLMultiworldSelect(I18n
								.format("wdl.gui.multiworldSelect.title.startDownload"),
								new GuiWDLMultiworldSelect.WorldSelectionCallback() {
							@Override
							public void onWorldSelected(String selectedWorld) {
								WDL.worldName = selectedWorld;
								WDL.isMultiworld = true;
								WDL.propsFound = true;

								minecraft.displayGuiScreen(null);
								startDownload();
							}

							@Override
							public void onCancel() {
								minecraft.displayGuiScreen(null);
								cancelDownload();
							}
						}));
					} else {
						baseProps.setProperty("LinkedWorlds", "");
						saveProps();
						propsFound = true;

						minecraft.displayGuiScreen(null);
						WDL.startDownload();
					}
				}

				@Override
				public void onCancel() {
					minecraft.displayGuiScreen(null);
					cancelDownload();
				}
			}));
			return;
		}

		worldProps = loadWorldProps(worldName);
		saveHandler = (SaveHandler) minecraft.getSaveLoader().getSaveLoader(
				getWorldFolderName(worldName), true);

		long lastSaved = Long.parseLong(worldProps.getProperty("LastSaved",
				"-1"));
		long lastPlayed;
		// Can't directly use worldClient.getWorldInfo, as that doesn't use
		// the saved version.
		File levelDatFile = new File(saveHandler.getWorldDirectory(), "level.dat");
		try (FileInputStream stream = new FileInputStream(levelDatFile)) {
			NBTTagCompound compound = CompressedStreamTools.readCompressed(stream);
			lastPlayed = compound.getCompoundTag("Data").getLong("LastPlayed");
		} catch (Exception e) {
			LOGGER.warn("Error while checking if the map has been played and " +
					"needs to be backed up (this is normal if this world " +
					"has not been saved before): ", e);
			lastPlayed = -1;
		}
		if (!overrideLastModifiedCheck && lastPlayed > lastSaved) {
			// The world was played later than it was saved; confirm that the
			// user is willing for possible changes they made to be overwritten.
			minecraft.displayGuiScreen(new GuiWDLOverwriteChanges(
					lastSaved, lastPlayed));
			return;
		}

		runSanityCheck();
		
		custom_ChunkRenderer.setState( true );

		WDL.minecraft.displayGuiScreen((GuiScreen) null);
		WDL.minecraft.setIngameFocus();
		chunkLoader = WDLChunkLoader.create(saveHandler, worldClient.provider);
		newTileEntities = new HashMap<>();
		newEntities = HashMultimap.create();
		newMapDatas = new HashMap<>();

		if (baseProps.getProperty("ServerName").isEmpty()) {
			baseProps.setProperty("ServerName", getServerName());
		}

		startOnChange = true;
		downloading = true;
		WDLMessages.chatMessageTranslated(WDLMessageTypes.INFO,
				"wdl.messages.generalInfo.downloadStarted");
	}

	/**
	 * Stops the download, and saves.
	 */
	public static void stopDownload() {
		if (downloading) {
			// Indicate that downloading has stopped
			custom_ChunkRenderer.setState( false );
			downloading = false;
			startOnChange = false;
			WDLMessages.chatMessageTranslated(WDLMessageTypes.INFO,
					"wdl.messages.generalInfo.downloadStopped");
			startSaveThread();
		}
	}

	/**
	 * Cancels the download.
	 */
	public static void cancelDownload() {
		boolean wasDownloading = downloading;

		if (wasDownloading) {
			minecraft.getSaveLoader().flushCache();
			saveHandler.flush();
			startOnChange = false;
			saving = false;
			downloading = false;
			worldLoadingDeferred = false;

			WDLMessages.chatMessageTranslated(WDLMessageTypes.INFO,
					"wdl.messages.generalInfo.downloadCanceled");
		}
	}

	/**
	 * Starts the asnchronous save thread.
	 */
	static void startSaveThread() {
		// Indicate that we are saving
		WDLMessages.chatMessageTranslated(WDLMessageTypes.INFO,
				"wdl.messages.generalInfo.saveStarted");
		WDL.saving = true;
		Thread thread = new Thread(() -> {
			try {
				WDL.saveEverything();
				WDL.saving = false;
				WDL.onSaveComplete();
			} catch (Throwable e) {
				WDL.crashed(e, "World Downloader Mod: Saving world");
			}
		}, "WDL Save Thread");
		thread.start();
	}

	/**
	 * Called when the world has loaded.
	 *
	 * @return Whether on the same server.
	 */
	public static boolean loadWorld() {
		worldName = ""; // The new (multi-)world name is unknown at the moment
		worldClient = minecraft.world;
		thePlayer = minecraft.player;
		windowContainer = thePlayer.openContainer;
		overrideLastModifiedCheck = false;

		NetworkManager newNM = thePlayer.connection.getNetworkManager();

		// Handle checking if the server changes here so that
		// messages are loaded FIRST.
		if (networkManager != newNM) {
			loadBaseProps();
			WDLMessages.onNewServer();
		}

		WDLPluginChannels.onWorldLoad();

		// Is this a different server?
		if (networkManager != newNM) {
			// Different server, different world!
			WDLMessages.chatMessageTranslated(WDLMessageTypes.ON_WORLD_LOAD,
					"wdl.messages.onWorldLoad.differentServer");

			networkManager = newNM;

			if (isSpigot()) {
				WDLMessages.chatMessageTranslated(
						WDLMessageTypes.ON_WORLD_LOAD,
						"wdl.messages.onWorldLoad.spigot",
						thePlayer.getServerBrand());
			} else {
				WDLMessages.chatMessageTranslated(
						WDLMessageTypes.ON_WORLD_LOAD,
						"wdl.messages.onWorldLoad.vanilla",
						thePlayer.getServerBrand());
			}

			startOnChange = false;

			return true;
		} else {
			// Same server, different world!
			WDLMessages.chatMessageTranslated(WDLMessageTypes.ON_WORLD_LOAD,
					"wdl.messages.onWorldLoad.sameServer");

			if (isSpigot()) {
				WDLMessages.chatMessageTranslated(
						WDLMessageTypes.ON_WORLD_LOAD,
						"wdl.messages.onWorldLoad.spigot",
						thePlayer.getServerBrand());
			} else {
				WDLMessages.chatMessageTranslated(
						WDLMessageTypes.ON_WORLD_LOAD,
						"wdl.messages.onWorldLoad.vanilla",
						thePlayer.getServerBrand());
			}

			if (startOnChange) {
				startDownload();
			}

			return false;
		}
	}

	/**
	 * Called after saving has finished.
	 */
	public static void onSaveComplete() {
		WDL.minecraft.getSaveLoader().flushCache();
		WDL.saveHandler.flush();
		WDL.worldClient = null;

		worldLoadingDeferred = false;

		// If still downloading, load the current world and keep on downloading
		if (downloading) {
			WDLMessages.chatMessageTranslated(WDLMessageTypes.INFO,
					"wdl.messages.generalInfo.saveComplete.startingAgain");
			WDL.loadWorld();
			return;
		}

		WDLMessages.chatMessageTranslated(WDLMessageTypes.INFO,
				"wdl.messages.generalInfo.saveComplete.done");
	}

	/**
	 * Saves all remaining chunks, world info and player info. Usually called
	 * when stopping.
	 */
	public static void saveEverything() throws Exception {
		if (!WDLPluginChannels.canDownloadAtAll()) {
			WDLMessages.chatMessageTranslated(WDLMessageTypes.ERROR,
					"wdl.messages.generalError.forbidden");
			return;
		}

		WorldBackupType backupType =
				WorldBackupType.match(baseProps.getProperty("Backup", "ZIP"));

		final GuiWDLSaveProgress progressScreen = new GuiWDLSaveProgress(
				I18n.format("wdl.saveProgress.title"),
				(backupType != WorldBackupType.NONE ? 6 : 5)
				+ WDLApi.getImplementingExtensions(ISaveListener.class).size());

		// Schedule this as a task to avoid threading issues.
		// If directly displayed, in some rare cases the GUI will be drawn before it has been
		// initialized, causing a crash.  Using a task stops that.
		minecraft.addScheduledTask(() -> { minecraft.displayGuiScreen(progressScreen); });

		saveProps();

		try {
			saveHandler.checkSessionLock();
		} catch (MinecraftException e) {
			throw new RuntimeException(
					"WorldDownloader: Couldn't get session lock for saving the world!", e);
		}

		// Player NBT is stored both in a separate file and level.dat.
		NBTTagCompound playerNBT = savePlayer(progressScreen);
		saveWorldInfo(progressScreen, playerNBT);

		saveMapData(progressScreen);
		saveChunks(progressScreen);

		saveProps();

		for (ModInfo<ISaveListener> info : WDLApi
				.getImplementingExtensions(ISaveListener.class)) {
			progressScreen.startMajorTask(
					I18n.format("wdl.saveProgress.extension.title",
							info.getDisplayName()), 1);
			info.mod.afterChunksSaved(saveHandler.getWorldDirectory());
		}

		try {
			WDLMessages.chatMessageTranslated(WDLMessageTypes.SAVING,
					"wdl.messages.saving.flushingIO");

			progressScreen.startMajorTask(
					I18n.format("wdl.saveProgress.flushingIO.title"), 1);
			progressScreen.setMinorTaskProgress(() -> {
				return I18n.format("wdl.saveProgress.flushingIO.subtitle", chunkLoader.getNumPendingChunks());
			}, 1);

			ThreadedFileIOBase.getThreadedIOInstance().waitForFinish();
		} catch (Exception e) {
			throw new RuntimeException("Threw exception waiting for asynchronous IO to finish. Hmmm.", e);
		}

		if (backupType != WorldBackupType.NONE) {
			WDLMessages.chatMessageTranslated(WDLMessageTypes.SAVING,
					"wdl.messages.saving.backingUp");
			progressScreen.startMajorTask(
					backupType.getTitle(), 1);
			progressScreen.setMinorTaskProgress(
					I18n.format("wdl.saveProgress.backingUp.preparing"), 1);

			class BackupState implements WorldBackup.IBackupProgressMonitor {
				int curFile = 0;
				@Override
				public void setNumberOfFiles(int num) {
					progressScreen.setMinorTaskCount(num);
				}
				@Override
				public void onNextFile(String name) {
					progressScreen.setMinorTaskProgress(
							I18n.format("wdl.saveProgress.backingUp.file", name), curFile++);
				}
			}

			try {
				WorldBackup.backupWorld(saveHandler.getWorldDirectory(),
						getWorldFolderName(worldName), backupType, new BackupState());
			} catch (IOException e) {
				WDLMessages.chatMessageTranslated(WDLMessageTypes.ERROR,
						"wdl.messages.generalError.failedToBackUp");
			}
		}

		progressScreen.setDoneWorking();
	}

	/**
	 * Save the player (position, health, inventory, ...) into its own file in
	 * the players directory, and applies needed overrides to the player info.
	 *
	 * @return The player NBT tag.  Needed for later use in the world info.
	 */
	public static NBTTagCompound savePlayer(GuiWDLSaveProgress progressScreen) {
		if (!WDLPluginChannels.canDownloadAtAll()) { return new NBTTagCompound(); }

		progressScreen.startMajorTask(
				I18n.format("wdl.saveProgress.playerData.title"),
				3 + WDLApi.getImplementingExtensions(IPlayerInfoEditor.class).size());
		WDLMessages.chatMessageTranslated(WDLMessageTypes.SAVING,
				"wdl.messages.saving.savingPlayer");

		progressScreen.setMinorTaskProgress(
				I18n.format("wdl.saveProgress.playerData.creatingNBT"), 1);

		NBTTagCompound playerNBT = new NBTTagCompound();
		thePlayer.writeToNBT(playerNBT);

		progressScreen.setMinorTaskProgress(
				I18n.format("wdl.saveProgress.playerData.editingNBT"), 2);
		applyOverridesToPlayer(playerNBT);

		int taskNum = 3;
		for (ModInfo<IPlayerInfoEditor> info : WDLApi
				.getImplementingExtensions(IPlayerInfoEditor.class)) {
			progressScreen.setMinorTaskProgress(
					I18n.format("wdl.saveProgress.playerData.extension",
							info.getDisplayName()), taskNum);

			info.mod.editPlayerInfo(thePlayer, saveHandler, playerNBT);

			taskNum++;
		}

		progressScreen.setMinorTaskProgress(
				I18n.format("wdl.saveProgress.playerData.writingNBT"), taskNum);

		File playersDirectory = new File(saveHandler.getWorldDirectory(),
				"playerdata");
		File playerFileTmp = new File(playersDirectory, thePlayer
				.getUniqueID().toString() + ".dat.tmp");
		File playerFile = new File(playersDirectory, thePlayer
				.getUniqueID().toString() + ".dat");

		try (FileOutputStream stream = new FileOutputStream(playerFileTmp)) {

			CompressedStreamTools.writeCompressed(playerNBT, stream);

			// Remove the old player file to make space for the new one.
			if (playerFile.exists()) {
				playerFile.delete();
			}

			playerFileTmp.renameTo(playerFile);
		} catch (Exception e) {
			throw new RuntimeException("Couldn't save the player!", e);
		}

		WDLMessages.chatMessageTranslated(WDLMessageTypes.SAVING,
				"wdl.messages.saving.playerSaved");

		return playerNBT;
	}

	/**
	 * Hardcoded, unchanging anvil save version ID.
	 *
	 * 19132: McRegion; 19133: Anvil.  If it's necessary to specify a new
	 * version, many other parts of the mod will be broken anyways.
	 */
	private static final int ANVIL_SAVE_VERSION = 19133;

	/**
	 * Save the world metadata (time, gamemode, seed, ...) into the level.dat
	 * file.
	 */
	public static void saveWorldInfo(GuiWDLSaveProgress progressScreen,
			NBTTagCompound playerInfoNBT) {
		if (!WDLPluginChannels.canDownloadAtAll()) { return; }

		progressScreen.startMajorTask(
				I18n.format("wdl.saveProgress.worldMetadata.title"),
				3 + WDLApi.getImplementingExtensions(IWorldInfoEditor.class).size());
		WDLMessages.chatMessageTranslated(WDLMessageTypes.SAVING,
				"wdl.messages.saving.savingWorld");

		progressScreen.setMinorTaskProgress(
				I18n.format("wdl.saveProgress.worldMetadata.creatingNBT"), 1);

		// Set the save version, which isn't done automatically for some
		// strange reason.
		worldClient.getWorldInfo().setSaveVersion(ANVIL_SAVE_VERSION);

		// cloneNBTCompound takes the PLAYER's nbt file, and puts it in the
		// right place.
		// This is needed because single player uses that data.
		NBTTagCompound worldInfoNBT = worldClient.getWorldInfo()
				.cloneNBTCompound(playerInfoNBT);

		// There's a root tag that stores the above one.
		NBTTagCompound rootWorldInfoNBT = new NBTTagCompound();
		rootWorldInfoNBT.setTag("Data", worldInfoNBT);

		progressScreen.setMinorTaskProgress(
				I18n.format("wdl.saveProgress.worldMetadata.editingNBT"), 2);
		applyOverridesToWorldInfo(worldInfoNBT, rootWorldInfoNBT);

		int taskNum = 3;
		for (ModInfo<IWorldInfoEditor> info : WDLApi
				.getImplementingExtensions(IWorldInfoEditor.class)) {
			progressScreen.setMinorTaskProgress(
					I18n.format("wdl.saveProgress.worldMetadata.extension",
							info.getDisplayName()), taskNum);

			info.mod.editWorldInfo(worldClient, worldClient.getWorldInfo(),
					saveHandler, worldInfoNBT);

			taskNum++;
		}

		progressScreen.setMinorTaskProgress(
				I18n.format("wdl.saveProgress.worldMetadata.writingNBT"), taskNum);
		File saveDirectory = saveHandler.getWorldDirectory();

		worldProps.setProperty("LastSaved",
				Long.toString(worldInfoNBT.getLong("LastPlayed")));

		File dataFile = new File(saveDirectory, "level.dat_new");
		File dataFileBackup = new File(saveDirectory, "level.dat_old");
		File dataFileOld = new File(saveDirectory, "level.dat");

		try (FileOutputStream stream = new FileOutputStream(dataFile)) {
			CompressedStreamTools.writeCompressed(rootWorldInfoNBT, stream);

			if (dataFileBackup.exists()) {
				dataFileBackup.delete();
			}

			dataFileOld.renameTo(dataFileBackup);

			if (dataFileOld.exists()) {
				dataFileOld.delete();
			}

			dataFile.renameTo(dataFileOld);

			if (dataFile.exists()) {
				dataFile.delete();
			}
		} catch (Exception e) {
			throw new RuntimeException("Couldn't save the world metadata!", e);
		}

		WDLMessages.chatMessageTranslated(WDLMessageTypes.SAVING,
				"wdl.messages.saving.worldSaved");
	}

	/**
	 * Calls saveChunk for all currently loaded chunks
	 *
	 * @throws IllegalAccessException
	 * @throws IllegalArgumentException
	 */
	public static void saveChunks(GuiWDLSaveProgress progressScreen)
			throws IllegalArgumentException, IllegalAccessException {
		if (!WDLPluginChannels.canDownloadAtAll()) { return; }

		WDLMessages.chatMessageTranslated(WDLMessageTypes.SAVING,
				"wdl.messages.saving.savingChunks");

		// Get the ChunkProviderClient from WorldClient
		ChunkProviderClient chunkProvider = worldClient
				.getChunkProvider();
		// Get the list of loaded chunks
		Object obj = ReflectionUtils.findAndGetPrivateField(chunkProvider, VersionedProperties.getChunkListClass());
		List<Chunk> chunks;
		if (obj instanceof List<?>) {
			@SuppressWarnings("unchecked")
			List<Chunk> chunkList = (List<Chunk>)obj;
			chunks = new ArrayList<>(chunkList);
		} else if (obj instanceof Map<?, ?>) {
			@SuppressWarnings("unchecked")
			Map<?, Chunk> chunkMap = (Map<?, Chunk>)obj;
			chunks = new ArrayList<>(chunkMap.values());
		} else {
			// Shouldn't ever happen
			throw new RuntimeException("Could not get ChunkProviderClient's chunk list: unexpected type for object " + obj);
		}

		progressScreen.startMajorTask(I18n.format("wdl.saveProgress.chunk.title"),
				chunks.size());

		for (int currentChunk = 0; currentChunk < chunks.size(); currentChunk++) {
			Chunk c = chunks.get(currentChunk);
			if (c != null) {
				//Serverside restrictions check
				if (!WDLPluginChannels.canSaveChunk(c)) {
					continue;
				}

				progressScreen.setMinorTaskProgress(I18n.format(
						"wdl.saveProgress.chunk.saving", c.x,
						c.z), currentChunk);

				saveChunk(c);
			}
		}
		WDLMessages.chatMessageTranslated(WDLMessageTypes.SAVING,
				"wdl.messages.saving.chunksSaved");
	}

	/**
	 * Import all non-overwritten TileEntities, then save the chunk
	 */
	public static void saveChunk(Chunk c) {
		if (!WDLPluginChannels.canDownloadAtAll()) { return; }

		if (!WDLPluginChannels.canSaveChunk(c)) { return; }

		try {
			if (isEmpty(c)) {
				LOGGER.warn("[WDL] Tried to save empty chunk! (" + c + "@" + c.x + "," + c.z + ")");
				return;
			}
			chunkLoader.saveChunk(worldClient, c);
		} catch (Exception e) {
			// Better tell the player that something didn't work:
			WDLMessages.chatMessageTranslated(WDLMessageTypes.ERROR,
					"wdl.messages.generalError.failedToSaveChunk",
					c.x, c.z, e);
		}
	}

	private static boolean isEmpty(Chunk c) {
		if (c.isEmpty() || c instanceof EmptyChunk) {
			return true;
		}
		ExtendedBlockStorage[] array = c.getBlockStorageArray();
		for (int i = 1; i < array.length; i++) {
			if (array[i] != Chunk.NULL_BLOCK_STORAGE) {
				return false;
			}
		}
		if (array[0] != Chunk.NULL_BLOCK_STORAGE) {
			// All-air empty chunks sometimes are sent with a bottom section;
			// handle that and a few other special cases.
			for (int y = 0; y < 16; y++) {
				for (int z = 0; z < 16; z++) {
					for (int x = 0; x < 16; x++) {
						int id = Block.getStateId(array[0].get(x, y, z));
						// Convert to standard global palette form
						id = (id & 0xFFF) << 4 | (id & 0xF000) >> 12;
						if ((id > 0x00F) && (id < 0x1A0 || id > 0x1AF)) {
							// Contains a non-airoid; stop
							return false;
						}
					}
				}
			}
			// Only composed of airoids; treat as empty
			LOGGER.warn("[WDL] Skipping airoid empty chunk at " + c.x + ", " + c.z);
		} else {
			// Definitely empty
			LOGGER.warn("[WDL] Skipping chunk with all null sections at " + c.x + ", " + c.z);
		}
		return true;
	}

	/**
	 * Loads the sever-shared properties, which act as a default
	 * for the properties of each individual world in a multiworld server.
	 */
	public static void loadBaseProps() {
		baseFolderName = getBaseFolderName();
		baseProps = new Properties(globalProps);

		File savesFolder = new File(minecraft.gameDir, "saves");
		File baseFolder = new File(savesFolder, baseFolderName);
		File dataFile = new File(baseFolder, "WorldDownloader.txt");
		try (FileReader reader = new FileReader(dataFile)) {
			baseProps.load(reader);
			propsFound = true;
		} catch (Exception e) {
			propsFound = false;
			LOGGER.debug("Failed to load base properties", e);
		}

		if (baseProps.getProperty("LinkedWorlds").isEmpty()) {
			isMultiworld = false;
			worldProps = new Properties(baseProps);
		} else {
			isMultiworld = true;
		}
	}

	/**
	 * Loads the properties for the given world, and returns it.
	 *
	 * Returns an empty Properties that inherits from baseProps if the specific
	 * world cannot be found.
	 */
	public static Properties loadWorldProps(String theWorldName) {
		Properties ret = new Properties(baseProps);

		if (theWorldName.isEmpty()) {
			return ret;
		}

		File savesDir = new File(minecraft.gameDir, "saves");

		String folder = getWorldFolderName(theWorldName);
		File worldFolder = new File(savesDir, folder);
		File dataFile = new File(worldFolder, "WorldDownloader.txt");

		try (FileReader reader = new FileReader(dataFile)) {
			ret.load(reader);

			return ret;
		} catch (Exception e) {
			LOGGER.debug("Failed to load world props for " + worldName, e);
			return ret;
		}
	}

	/**
	 * Saves the currently used base and world properties in their corresponding
	 * folders.
	 */
	public static void saveProps() {
		saveProps(worldName, worldProps);
	}

	/**
	 * Saves the specified world properties, and the base properties, in their
	 * corresponding folders.
	 */
	public static void saveProps(String theWorldName, Properties theWorldProps) {
		File savesDir = new File(minecraft.gameDir, "saves");

		if (theWorldName.length() > 0) {
			String folder = getWorldFolderName(theWorldName);

			File worldFolder = new File(savesDir, folder);
			worldFolder.mkdirs();
			File worldPropsFile = new File(worldFolder, "WorldDownloader.txt");
			try (FileWriter writer = new FileWriter(worldPropsFile)) {
				theWorldProps.store(writer, I18n.format("wdl.props.world.title"));
			} catch (Exception e) {
				LOGGER.warn("Failed to write world props!", e);
			}
		} else if (!isMultiworld) {
			baseProps.putAll(theWorldProps);
		}

		File baseFolder = new File(savesDir, baseFolderName);
		baseFolder.mkdirs();

		File basePropsFile = new File(baseFolder, "WorldDownloader.txt");
		try (FileWriter writer = new FileWriter(basePropsFile)) {
			baseProps.store(writer, I18n.format("wdl.props.base.title"));
		} catch (Exception e) {
			LOGGER.warn("Failed to write base props!", e);
		}

		saveGlobalProps();
	}

	/**
	 * Saves the global properties, which are used for all servers.
	 */
	public static void saveGlobalProps() {
		File globalPropsFile = new File(minecraft.gameDir, "WorldDownloader.txt");
		try (FileWriter writer = new FileWriter(globalPropsFile)) {
			globalProps.store(writer, I18n.format("wdl.props.global.title"));
		} catch (Exception e) {
			LOGGER.warn("Failed to write globalprops!", e);
		}
	}

	/**
	 * Change player specific fields according to the overrides found in the
	 * properties file.
	 */
	public static void applyOverridesToPlayer(NBTTagCompound playerNBT) {
		// Health
		String health = worldProps.getProperty("PlayerHealth");

		if (!health.equals("keep")) {
			short h = Short.parseShort(health);
			playerNBT.setShort("Health", h);
		}

		// foodLevel, foodTimer, foodSaturationLevel, foodExhaustionLevel
		String food = worldProps.getProperty("PlayerFood");

		if (!food.equals("keep")) {
			int f = Integer.parseInt(food);
			playerNBT.setInteger("foodLevel", f);
			playerNBT.setInteger("foodTickTimer", 0);

			if (f == 20) {
				playerNBT.setFloat("foodSaturationLevel", 5.0f);
			} else {
				playerNBT.setFloat("foodSaturationLevel", 0.0f);
			}

			playerNBT.setFloat("foodExhaustionLevel", 0.0f);
		}

		// Player Position
		String playerPos = worldProps.getProperty("PlayerPos");

		if (playerPos.equals("xyz")) {
			int x = Integer.parseInt(worldProps.getProperty("PlayerX"));
			int y = Integer.parseInt(worldProps.getProperty("PlayerY"));
			int z = Integer.parseInt(worldProps.getProperty("PlayerZ"));
			//Positions are offset to center of block,
			//or player height.
			NBTTagList pos = new NBTTagList();
			pos.appendTag(new NBTTagDouble(x + 0.5D));
			pos.appendTag(new NBTTagDouble(y + 0.621D));
			pos.appendTag(new NBTTagDouble(z + 0.5D));
			playerNBT.setTag("Pos", pos);
			NBTTagList motion = new NBTTagList();
			motion.appendTag(new NBTTagDouble(0.0D));
			//Force them to land on the ground?
			motion.appendTag(new NBTTagDouble(-0.0001D));
			motion.appendTag(new NBTTagDouble(0.0D));
			playerNBT.setTag("Motion", motion);
			NBTTagList rotation = new NBTTagList();
			rotation.appendTag(new NBTTagFloat(0.0f));
			rotation.appendTag(new NBTTagFloat(0.0f));
			playerNBT.setTag("Rotation", rotation);
		}

		// If the player is able to fly, spawn them flying.
		// Helps ensure they don't fall out of the world.
		if (thePlayer.capabilities.allowFlying) {
			playerNBT.getCompoundTag("abilities").setBoolean("flying", true);
		}
	}

	/**
	 * Change world and generator specific fields according to the overrides
	 * found in the properties file.
	 *
	 * @param worldInfoNBT The main world info, generated by {@link WorldInfo#cloneNBTCompound}.
	 * @param rootWorldInfoNBT The root tag containing worldInfoNBT as "<code>Data</code>"
	 */
	public static void applyOverridesToWorldInfo(NBTTagCompound worldInfoNBT, NBTTagCompound rootWorldInfoNBT) {
		// LevelName
		String baseName = baseProps.getProperty("ServerName");
		String worldName = worldProps.getProperty("WorldName");

		if (worldName.isEmpty()) {
			worldInfoNBT.setString("LevelName", baseName);
		} else {
			worldInfoNBT.setString("LevelName", baseName + " - " + worldName);
		}

		// Cheats
		if (worldProps.getProperty("AllowCheats").equals("true")) {
			worldInfoNBT.setBoolean("allowCommands", true);
		} else {
			worldInfoNBT.setBoolean("allowCommands", false);
		}

		// GameType
		String gametypeOption = worldProps.getProperty("GameType");

		if (gametypeOption.equals("keep")) {
			if (thePlayer.capabilities.isCreativeMode) { // capabilities
				worldInfoNBT.setInteger("GameType", 1); // Creative
			} else {
				worldInfoNBT.setInteger("GameType", 0); // Survival
			}
		} else if (gametypeOption.equals("survival")) {
			worldInfoNBT.setInteger("GameType", 0);
		} else if (gametypeOption.equals("creative")) {
			worldInfoNBT.setInteger("GameType", 1);
		} else if (gametypeOption.equals("hardcore")) {
			worldInfoNBT.setInteger("GameType", 0);
			worldInfoNBT.setBoolean("hardcore", true);
		}

		// Time
		String timeOption = worldProps.getProperty("Time");

		if (!timeOption.equals("keep")) {
			long t = Integer.parseInt(timeOption);
			worldInfoNBT.setLong("Time", t);
		}

		// RandomSeed
		String randomSeed = worldProps.getProperty("RandomSeed");
		long seed = 0;

		if (!randomSeed.isEmpty()) {
			try {
				seed = Long.parseLong(randomSeed);
			} catch (NumberFormatException numberformatexception) {
				seed = randomSeed.hashCode();
			}
		}

		worldInfoNBT.setLong("RandomSeed", seed);
		// MapFeatures
		boolean mapFeatures = Boolean.parseBoolean(worldProps
				.getProperty("MapFeatures"));
		worldInfoNBT.setBoolean("MapFeatures", mapFeatures);
		// generatorName
		String generatorName = worldProps.getProperty("GeneratorName");
		worldInfoNBT.setString("generatorName", generatorName);
		// generatorOptions
		String generatorOptions = worldProps.getProperty("GeneratorOptions");
		worldInfoNBT.setString("generatorOptions", generatorOptions);
		// generatorVersion
		int generatorVersion = Integer.parseInt(worldProps
				.getProperty("GeneratorVersion"));
		worldInfoNBT.setInteger("generatorVersion", generatorVersion);
		// Weather
		String weather = worldProps.getProperty("Weather");

		if (weather.equals("sunny")) {
			worldInfoNBT.setBoolean("raining", false);
			worldInfoNBT.setInteger("rainTime", 0);
			worldInfoNBT.setBoolean("thundering", false);
			worldInfoNBT.setInteger("thunderTime", 0);
		} else if (weather.equals("rain")) {
			worldInfoNBT.setBoolean("raining", true);
			worldInfoNBT.setInteger("rainTime", 24000);
			worldInfoNBT.setBoolean("thundering", false);
			worldInfoNBT.setInteger("thunderTime", 0);
		} else if (weather.equals("thunderstorm")) {
			worldInfoNBT.setBoolean("raining", true);
			worldInfoNBT.setInteger("rainTime", 24000);
			worldInfoNBT.setBoolean("thundering", true);
			worldInfoNBT.setInteger("thunderTime", 24000);
		}

		// Spawn
		String spawn = worldProps.getProperty("Spawn");

		if (spawn.equals("player")) {
			int x = MathHelper.floor(thePlayer.posX);
			int y = MathHelper.floor(thePlayer.posY);
			int z = MathHelper.floor(thePlayer.posZ);
			worldInfoNBT.setInteger("SpawnX", x);
			worldInfoNBT.setInteger("SpawnY", y);
			worldInfoNBT.setInteger("SpawnZ", z);
			worldInfoNBT.setBoolean("initialized", true);
		} else if (spawn.equals("xyz")) {
			int x = Integer.parseInt(worldProps.getProperty("SpawnX"));
			int y = Integer.parseInt(worldProps.getProperty("SpawnY"));
			int z = Integer.parseInt(worldProps.getProperty("SpawnZ"));
			worldInfoNBT.setInteger("SpawnX", x);
			worldInfoNBT.setInteger("SpawnY", y);
			worldInfoNBT.setInteger("SpawnZ", z);
			worldInfoNBT.setBoolean("initialized", true);
		}

		// Gamerules (most of these are already populated)
		NBTTagCompound gamerules = worldInfoNBT.getCompoundTag("GameRules");
		for (String prop : worldProps.stringPropertyNames()) {
			if (!prop.startsWith("GameRule.")) {
				continue;
			}
			String rule = prop.substring("GameRule.".length());
			gamerules.setString(rule, worldProps.getProperty(prop));
		}

		// Forge (TODO: move this elsewhere!)
		try {
			LOGGER.debug("Trying to call FML writeVersionData");
			NBTTagCompound versionInfo = worldInfoNBT.getCompoundTag("Version");

			Class<?> fmlCommonHandler = Class.forName("net.minecraftforge.fml.common.FMLCommonHandler");
			Object instance = fmlCommonHandler.getMethod("instance").invoke(null);
			Object dataFixer = fmlCommonHandler.getMethod("getDataFixer").invoke(instance);
			Method writeVersionData = dataFixer.getClass()
					.getMethod("writeVersionData", NBTTagCompound.class);
			writeVersionData.invoke(dataFixer, versionInfo);

			LOGGER.debug("Called FML writeVersionData");
		} catch (Throwable ex) {
			LOGGER.info("Failed to call FML writeVersionData", ex);
		}

		try {
			LOGGER.debug("Trying to call FML handleWorldDataSave");

			Class<?> fmlCommonHandler = Class.forName("net.minecraftforge.fml.common.FMLCommonHandler");
			Object instance = fmlCommonHandler.getMethod("instance").invoke(null);
			Method handleWorldDataSave = fmlCommonHandler.getMethod("handleWorldDataSave",
					SaveHandler.class, WorldInfo.class, NBTTagCompound.class);
			handleWorldDataSave.invoke(instance, WDL.saveHandler, WDL.worldClient.getWorldInfo(), rootWorldInfoNBT);

			LOGGER.debug("Called FML handleWorldDataSave!  Keys are now " + rootWorldInfoNBT.getKeySet());
		} catch (Throwable ex) {
			LOGGER.info("Failed to call FML handleWorldDataSave", ex);
		}
	}

	/**
	 * Saves existing map data.  Map data refering to the items
	 * that contain pictures.
	 *
	 * TODO: Overwrite / create IDCounts.dat.
	 */
	public static void saveMapData(GuiWDLSaveProgress progressScreen) {
		if (!WDLPluginChannels.canSaveMaps()) { return; }

		File dataDirectory = new File(saveHandler.getWorldDirectory(),
				"data");
		dataDirectory.mkdirs();

		progressScreen.startMajorTask(
				I18n.format("wdl.saveProgress.map.title"), newMapDatas.size());

		WDLMessages.chatMessageTranslated(WDLMessageTypes.SAVING,
				"wdl.messages.saving.savingMapItemData");

		int count = 0;
		for (Map.Entry<Integer, MapData> e : newMapDatas.entrySet()) {
			count++;

			progressScreen.setMinorTaskProgress(
					I18n.format("wdl.saveProgress.map.saving", e.getKey()),
					count);

			File mapFile = new File(dataDirectory, "map_" + e.getKey() + ".dat");

			NBTTagCompound mapNBT = new NBTTagCompound();
			NBTTagCompound data = new NBTTagCompound();

			e.getValue().writeToNBT(data);

			mapNBT.setTag("data", data);

			try (FileOutputStream stream = new FileOutputStream(mapFile)) {
				CompressedStreamTools.writeCompressed(mapNBT, stream);
			} catch (IOException ex) {
				throw new RuntimeException("WDL: Exception while writing " +
						"map data for map " + e.getKey() + "!", ex);
			}
		}

		WDLMessages.chatMessageTranslated(WDLMessageTypes.SAVING,
				"wdl.messages.saving.mapItemDataSaved");
	}

	/**
	 * Gets the name of the server, either from the name in the server list,
	 * or using the server's IP.
	 */
	public static String getServerName() {
		try {
			if (minecraft.getCurrentServerData() != null) {
				String name = minecraft.getCurrentServerData().serverName;

				if (name.equals(I18n.format("selectServer.defaultName"))) {
					// Direct connection using domain name or IP (and port)
					name = minecraft.getCurrentServerData().serverIP;
				}

				return name;
			} else if (minecraft.isConnectedToRealms()) {
				String realmName = getRealmName();
				if (realmName != null) {
					return realmName;
				} else {
					LOGGER.warn("getServerName: getRealmName returned null!");
				}
			} else {
				LOGGER.warn("getServerName: Not connected to either a real server or realms!");
			}
		} catch (Exception e) {
			LOGGER.warn("Exception while getting server name: ", e);
		}

		return "Unidentified Server";
	}

	/**
	 * Gets the name of the realm that the player is currently connected to, or <code>null</code> if they are not connected to a realm.
	 *
	 * @return The name of the connected realm, or null.
	 */
	@Nullable
	public static String getRealmName() {
		if (!minecraft.isConnectedToRealms()) {
			LOGGER.warn("getRealmName: Not currently connected to realms!");
		}
		// Is this the only way to get the name of the Realms server? Really Mojang?
		// If this function turns out to be a pain to update, just remove Realms support completely.
		// I doubt anyone will need this anyway since Realms support downloading the world out of the box.

		// Try to get the value of NetHandlerPlayClient.guiScreenServer:
		GuiScreen screen = ReflectionUtils.findAndGetPrivateField(minecraft.getConnection(), GuiScreen.class);

		// If it is not a GuiScreenRealmsProxy we are not using a Realms server
		if (!(screen instanceof GuiScreenRealmsProxy)) {
			LOGGER.warn("getRealmName: screen {} is not an instance of GuiScreenRealmsProxy", screen);
			return null;
		}

		// Get the proxy's RealmsScreen object
		GuiScreenRealmsProxy screenProxy = (GuiScreenRealmsProxy) screen;
		RealmsScreen rs = screenProxy.getProxy();

		// It needs to be of type RealmsMainScreen (this should always be the case)
		if (!(rs instanceof RealmsMainScreen)) {
			LOGGER.warn("getRealmName: realms screen {} (instance of {}) not an instance of RealmsMainScreen!", rs, (rs != null ? rs.getClass() : null));
			return null;
		}

		RealmsMainScreen rms = (RealmsMainScreen) rs;
		RealmsServer mcos = null;
		try {
			// Find the ID of the selected Realms server. Fortunately unobfuscated names!
			Field selectedServerId = rms.getClass().getDeclaredField("selectedServerId");
			selectedServerId.setAccessible(true);
			if (!selectedServerId.getType().equals(long.class)) {
				LOGGER.warn("getRealmName: RealmsMainScreen selectedServerId field ({}) is not of type `long` ({})!", selectedServerId, selectedServerId.getType());
				return null;
			}
			long id = selectedServerId.getLong(rms);

			// Get the McoServer instance that was selected
			Method findServer = rms.getClass().getDeclaredMethod("findServer", long.class);
			findServer.setAccessible(true);
			Object obj = findServer.invoke(rms, id);
			if (!(obj instanceof RealmsServer)) {
				LOGGER.warn("getRealmName: RealmsMainScreen findServer method ({}) returned something other than a RealmsServer! ({})", findServer, obj);
				return null;
			}
			mcos = (RealmsServer) obj;
		} catch (Exception e) {
			LOGGER.warn("getRealmName: Unexpected exception!", e);
			return null;
		}

		// Return its name. Not sure if this is the best naming scheme...
		return mcos.name;
	}



	/**
	 * Get the base folder name for the server we are connected to.
	 */
	public static String getBaseFolderName() {
		return getServerName().replaceAll("\\W+", "_");
	}

	/**
	 * Get the folder name for the specified world.
	 */
	public static String getWorldFolderName(String theWorldName) {
		if (theWorldName.isEmpty()) {
			return baseFolderName;
		} else {
			return baseFolderName + " - " + theWorldName;
		}
	}

	/**
	 * Adds the given tile entity to {@link #newTileEntities}.
	 *
	 * @param pos
	 *            The position of the tile entity
	 * @param te
	 *            The tile entity to add
	 */
	public static void saveTileEntity(BlockPos pos, TileEntity te) {
		int chunkX = pos.getX() >> 4;
		int chunkZ = pos.getZ() >> 4;

		ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);

		if (!newTileEntities.containsKey(chunkPos)) {
			newTileEntities.put(chunkPos, new HashMap<BlockPos, TileEntity>());
		}
		newTileEntities.get(chunkPos).put(pos, te);
	}

	/**
	 * Runs a sanity check. Even if the check fails, processing continues, but
	 * the user is warned in chat.
	 *
	 * @see SanityCheck
	 */
	private static void runSanityCheck() {
		Map<SanityCheck, Exception> failures = Maps.newEnumMap(SanityCheck.class);

		for (SanityCheck check : SanityCheck.values()) {
			try {
				LOGGER.trace("Running {}", check);
				check.run();
			} catch (Exception ex) {
				LOGGER.trace("{} failed", check, ex);
				failures.put(check, ex);
			}
		}
		if (!failures.isEmpty()) {
			WDLMessages.chatMessageTranslated(WDLMessageTypes.ERROR, "wdl.sanity.failed");
			for (Map.Entry<SanityCheck, Exception> failure : failures.entrySet()) {
				WDLMessages.chatMessageTranslated(WDLMessageTypes.ERROR, failure.getKey().errorMessage, failure.getValue());
			}
			if (failures.containsKey(SanityCheck.TRANSLATION)) {
				// Err, we can't put translated stuff into chat.  So redo those messages, without translation.
				// For obvious reasons these messages aren't translated.
				WDLMessages.chatMessage(WDLMessageTypes.ERROR, "----- SANITY CHECKS FAILED! -----");
				for (Map.Entry<SanityCheck, Exception> failure : failures.entrySet()) {
					WDLMessages.chatMessage(WDLMessageTypes.ERROR, failure.getKey() + ": " + failure.getValue());
				}
				WDLMessages.chatMessage(WDLMessageTypes.ERROR, "Please check the log for more info.");
			}
		}
	}

	/**
	 * Is the current server running spigot?
	 *
	 * This is detected based off of the server brand.
	 */
	public static boolean isSpigot() {
		//getClientBrand() returns the server brand; blame MCP.
		if (thePlayer != null && thePlayer.getServerBrand() != null) {
			return thePlayer.getServerBrand().toLowerCase().contains("spigot");
		}
		return false;
	}

	/**
	 * Gets the current setup information.
	 */
	public static String getDebugInfo() {
		Exception ex = new Exception();
		ex.setStackTrace(new StackTraceElement[0]);
		CrashReport report = new CrashReport("Wrapper crash report", ex);
		addInfoToCrash(report);
		StringBuilder sb = new StringBuilder();
		report.getSectionsInStringBuilder(sb);
		return sb.toString();
	}
	/**
	 * Adds information to the given crash report.
	 * @param report The report to add sections to.
	 */
	public static void addInfoToCrash(CrashReport report) {
		// Trick the crash report handler into not storing a stack trace
		// (we don't want it)
		int stSize;
		try {
			stSize = Thread.currentThread().getStackTrace().length - 1;
		} catch (Exception e) {
			// Ignore
			stSize = 0;
		}
		CrashReportCategory core = report.makeCategoryDepth(
				"World Downloader Mod - Core", stSize);
		core.addCrashSection("WDL version", VersionConstants.getModVersion());
		core.addCrashSection("Minecraft version", VersionConstants.getMinecraftVersionInfo());
		core.addCrashSection("Expected version", VersionConstants.getExpectedVersion());
		core.addCrashSection("Protocol version", VersionConstants.getProtocolVersion());
		core.addCrashSection("Data version", VersionConstants.getDataVersion());
		core.addDetail("File location", () -> {
			//http://stackoverflow.com/q/320542/3991344
			String path = new File(WDL.class.getProtectionDomain()
					.getCodeSource().getLocation().toURI()).getPath();

			//Censor username.
			String username = System.getProperty("user.name");
			path = path.replace(username, "<USERNAME>");

			return path;
		});

		CrashReportCategory ext = report.makeCategoryDepth(
				"World Downloader Mod - Extensions", stSize);
		Map<String, ModInfo<?>> extensions = WDLApi.getWDLMods();
		ext.addCrashSection("Number loaded", extensions.size());
		for (Map.Entry<String, ModInfo<?>> e : extensions.entrySet()) {
			ext.addDetail(e.getKey(), e.getValue()::getInfo);
		}

		CrashReportCategory state = report.makeCategoryDepth(
				"World Downloader Mod - State", stSize);
		state.addCrashSection("minecraft", minecraft);
		state.addCrashSection("worldClient", worldClient);
		state.addCrashSection("networkManager", networkManager);
		state.addCrashSection("thePlayer", thePlayer);
		state.addCrashSection("windowContainer", windowContainer);
		state.addCrashSection("lastClickedBlock", lastClickedBlock);
		state.addCrashSection("lastEntity", lastEntity);
		state.addCrashSection("saveHandler", saveHandler);
		state.addCrashSection("chunkLoader", chunkLoader);
		state.addCrashSection("newTileEntities", newTileEntities);
		state.addCrashSection("newEntities", newEntities);
		state.addCrashSection("newMapDatas", newMapDatas);
		state.addCrashSection("downloading", downloading);
		state.addCrashSection("isMultiworld", isMultiworld);
		state.addCrashSection("propsFound", propsFound);
		state.addCrashSection("startOnChange", startOnChange);
		state.addCrashSection("overrideLastModifiedCheck", overrideLastModifiedCheck);
		state.addCrashSection("saving", saving);
		state.addCrashSection("worldLoadingDeferred", worldLoadingDeferred);
		state.addCrashSection("worldName", worldName);
		state.addCrashSection("baseFolderName", baseFolderName);

		CrashReportCategory base = report.makeCategoryDepth(
				"World Downloader Mod - Base properties", stSize);
		if (baseProps != null) {
			if (!baseProps.isEmpty()) {
				for (Map.Entry<Object, Object> e : baseProps.entrySet()) {
					if (!(e.getKey() instanceof String)) {
						LOGGER.warn("Non-string key " + e.getKey() + " in baseProps");
						continue;
					}
					base.addCrashSection((String)e.getKey(), e.getValue());
				}
			} else {
				base.addCrashSection("-", "empty");
			}
		} else {
			base.addCrashSection("-", "null");
		}
		CrashReportCategory world = report.makeCategoryDepth(
				"World Downloader Mod - World properties", stSize);
		if (worldProps != null) {
			if (!worldProps.isEmpty()) {
				for (Map.Entry<Object, Object> e : worldProps.entrySet()) {
					if (!(e.getKey() instanceof String)) {
						LOGGER.warn("Non-string key " + e.getKey() + " in worldProps");
						continue;
					}
					world.addCrashSection((String)e.getKey(), e.getValue());
				}
			} else {
				world.addCrashSection("-", "empty");
			}
		} else {
			world.addCrashSection("-", "null");
		}
		CrashReportCategory global = report.makeCategoryDepth(
				"World Downloader Mod - Global properties", stSize);
		if (globalProps != null) {
			if (!globalProps.isEmpty()) {
				for (Map.Entry<Object, Object> e : globalProps.entrySet()) {
					if (!(e.getKey() instanceof String)) {
						LOGGER.warn("Non-string key " + e.getKey() + " in globalProps");
						continue;
					}
					global.addCrashSection((String)e.getKey(), e.getValue());
				}
			} else {
				global.addCrashSection("-", "empty");
			}
		} else {
			global.addCrashSection("-", "null");
		}
	}

	/**
	 * Call to properly crash the game when an exception is caught in WDL code.
	 *
	 * @param category
	 */
	public static void crashed(Throwable t, String category) {
		CrashReport report;

		if (t instanceof ReportedException) {
			CrashReport oldReport =
					((ReportedException) t).getCrashReport();

			report = CrashReport.makeCrashReport(oldReport.getCrashCause(),
					category + " (" + oldReport.getCauseStackTraceOrString() + ")");

			try {
				//Steal crashReportSections, and replace it.
				@SuppressWarnings("unchecked")
				List<CrashReportCategory> crashReportSectionsOld = ReflectionUtils
				.findAndGetPrivateField(oldReport, List.class);
				@SuppressWarnings("unchecked")
				List<CrashReportCategory> crashReportSectionsNew = ReflectionUtils
				.findAndGetPrivateField(report, List.class);

				crashReportSectionsNew.addAll(crashReportSectionsOld);
			} catch (Exception e) {
				//Well... some kind of reflection error.
				//No use trying to do anything else.
				report.makeCategory(
						"An exception occured while trying to copy " +
						"the origional categories.")
						.addCrashSectionThrowable(":(", e);
			}
		} else {
			report = CrashReport.makeCrashReport(t, category);
		}
		minecraft.crashed(report);
	}
}
