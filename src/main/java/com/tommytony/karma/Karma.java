package com.tommytony.karma;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public class Karma extends JavaPlugin {

	protected Map<String, KarmaPlayer> players;
	protected Database db;
	protected KarmaGroup startGroup;
	public FileConfiguration config;
	private Random random = new Random();
	public boolean warenabled = false;

	public void onDisable() {
		this.getServer().getScheduler().cancelTasks(this);
		this.db.putAll();
		players.clear();

		this.getServer().getLogger().log(Level.INFO, "Karma> Disabled.");
	}

	public void onEnable() {
		// Init data
		this.players = new HashMap<String, KarmaPlayer>();
		this.db = new Database(this);
		this.db.initialize();

		PluginManager manager = this.getServer().getPluginManager();

		// Setup config
		if (!new File("plugins/Karma").exists()) {
			new File("plugins/Karma").mkdir();
		}
		if (!new File("plugins/Karma/config.yml").exists()) {
			saveDefaultConfig();
		}
		this.config = getConfig();
		try {
			config.load(new File("plugins/Karma/config.yml"));
		} catch (IOException e) {
			e.printStackTrace();
			this.getServer()
					.getLogger()
					.log(Level.INFO,
							"Karma> Your computer cannot read the config! Disabling..");
			manager.disablePlugin(this);
			return;
		} catch (InvalidConfigurationException e) {
			this.getServer().getLogger()
					.log(Level.INFO, "Karma> Bad configuration! Disabling..");
			manager.disablePlugin(this);
			return;
		}

		// Load karma groups
		this.loadKarmaGroups();

		// Register events

		KarmaWorldListener worldListener = new KarmaWorldListener(this);
		manager.registerEvents(worldListener, this);

		KarmaPlayerListener playerListener = new KarmaPlayerListener(this);
		manager.registerEvents(playerListener, this);

		// Load online players
		this.getServer().getScheduler()
				.scheduleSyncDelayedTask(this, new LoadPlayers(this));

		// Check for war and enable bonuses if enabled in config
		try {
			Class.forName("com.tommytony.war.War");
			if (config.getBoolean("war.bonus"))
				warenabled = true;
		} catch (ClassNotFoundException e) {
			warenabled = false;
			if (config.getBoolean("war.bonus")) {
				this.getServer()
						.getLogger()
						.log(Level.WARNING,
								"Karma> war.bonus enabled in configuration but the War plugin was not found! Bonuses have been disabled.");
			}
		}

		// Launch karma party train!!
		this.getServer()
				.getScheduler()
				.scheduleSyncDelayedTask(this, new KarmaParty(this),
						this.getNextRandomKarmaPartyDelay());

		this.getServer().getLogger().log(Level.INFO, "Karma> Enabled.");
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command,
			String label, String[] args) {
		try {
			if (command.getName().equals("karma")
					|| command.getName().equals("k")) {
				if (args.length == 0) {
					// Check their own karma
					if (!(sender instanceof Player)) {
						sender.sendMessage("This command cannot be used by console");
						return true;
					}
					KarmaPlayer karmaCheckPlayer = this.players
							.get(((Player) sender).getName());
					if (karmaCheckPlayer != null) {
						this.msg(
								sender,
								config.getString("check.self.message")
										.replace(
												"<points>",
												karmaCheckPlayer.getKarmaPoints()
														+ "")
										.replace(
												"<curgroup>",
												getPlayerGroupString(karmaCheckPlayer))
										.replace(
												"<nextgroup>",
												getPlayerNextGroupString(karmaCheckPlayer))
										.replace(
												"<curgroupcolor>",
												getPlayerGroupColor(karmaCheckPlayer)
														.toString())
										.replace(
												"<nextgroupcolor>",
												getPlayerNextGroupColor(
														karmaCheckPlayer).toString()));
					}
					karmaCheckPlayer = null;
					return true;
				}
				
				if (args[0].equalsIgnoreCase("ranks")) {
					String ranksString = config.getString("viewranks.prefix");
					KarmaGroup group = this.startGroup;
					while (group != null) {
						ranksString += group.getChatColor() + group.getGroupName() + ChatColor.GRAY + "("
								+ ChatColor.YELLOW + group.getKarmaPoints()
								+ ChatColor.GRAY + ")";
						if (group.getNext() != null)
							ranksString += ChatColor.WHITE + " -> "
									+ ChatColor.GRAY;
						group = group.getNext();
					}
					this.msg(sender, ranksString);
					return true;
				}
				if (args[0].equalsIgnoreCase("help")) {
					for (String line : config.getStringList("help")) {
						this.msg(sender, line);
					}
					return true;
				}
				if (args[0].equalsIgnoreCase("gift")) {
					if (!sender.hasPermission("karma.gift")) {
						this.msg(sender,
								config.getString("errors.nopermission"));
						return true;
					}
					if (args.length < 2) {
						this.msg(sender, config.getString("errors.badargs"));
						return true;
					}
					Player giftTarget = this.getServer().getPlayer(args[1]);
					if (giftTarget == null) {
						this.msg(sender, config.getString("errors.noplayer"));
						return true;
					}
					KarmaPlayer karmaGiver = null;
					if (sender instanceof Player) {
						karmaGiver = this.players.get(((Player) sender)
								.getName());
					}

					if (karmaGiver == null || karmaGiver.getKarmaPoints() > 0) {
						KarmaPlayer karmaGiftReceiver = this.getPlayers().get(
								giftTarget.getName());

						if (karmaGiftReceiver != null
								&& !sender.getName().equals(
										giftTarget.getName())) {

							String gifterName = "server";
							if (karmaGiver != null) {
								gifterName = ((Player) sender).getName();
								if (karmaGiver.canGift()) {
									karmaGiver.updateLastGiftTime();
									karmaGiver.removeKarma(config
											.getInt("gift.amount"));
									this.msg(
											sender,
											config.getString(
													"gift.messages.togifter")
													.replace(
															"<player>",
															karmaGiftReceiver
																	.getName())
													.replace(
															"<points>",
															config.getInt("gift.amount")
																	+ ""));
								} else {
									long since = (System.currentTimeMillis() - karmaGiver
											.getLastGiftTime()) / 1000;
									this.msg(
											sender,
											config.getString(
													"gift.messages.cooldown")
													.replace(
															"<minutes>",
															((3600 - since) / 60)
																	+ ""));
									return true;
								}
							}

							karmaGiftReceiver.addKarma(config
									.getInt("gift.amount"));
							this.msg(
									giftTarget,
									config.getString("gift.messages.toreceiver")
											.replace("<player>", gifterName)
											.replace(
													"<points>",
													config.getInt("gift.amount")
															+ ""));

							this.getServer()
									.getLogger()
									.log(Level.INFO,
											"Karma> "
													+ gifterName
													+ " gave "
													+ config.getInt("gift.amount")
													+ " karma to "
													+ giftTarget.getName());

							return true;
						} else {
							this.getServer()
									.getLogger()
									.log(Level.WARNING,
											"Karma> Couldn't find target or targetted self.");
						}
					}
					return true;
				}
				if (args[0].equalsIgnoreCase("promote") || args[0].equalsIgnoreCase("promo")) {
					if (args.length < 2) {
						this.msg(sender, config.getString("errors.badargs"));
						return true;
					}
					Player promoteTarget = this.getServer().getPlayer(args[1]);
					KarmaGroup currentGroup = this.startGroup;
					if (promoteTarget == null) {
						this.msg(sender,
								config.getString("promote.messages.noplayer"));
						return true;
					}
					KarmaPlayer karmaPromoteTarget = this.players.get(promoteTarget
							.getName());
					if (karmaPromoteTarget == null)
						return true;
					while (currentGroup != null) {
						if (karmaPromoteTarget.getKarmaPoints() < currentGroup
								.getKarmaPoints()) {
							if (sender.hasPermission("karma.promote."
									+ currentGroup.getGroupName())) {
								karmaPromoteTarget.addKarma(currentGroup
										.getKarmaPoints()
										- karmaPromoteTarget.getKarmaPoints());
								this.msg(
										promoteTarget,
										config.getString(
												"promocommand.messages.promoted")
												.replace("<player>",
														promoteTarget.getName())
												.replace(
														"<group>",
														currentGroup
																.getGroupName()));
								return true;
							} else {
								this.msg(sender, config
										.getString("errors.nopermission"));
								return true;
							}

						}
						currentGroup = currentGroup.getNext();
					}
				}
				if (args[0].equalsIgnoreCase("set")) {
					if (args.length < 3) {
						this.msg(sender, config.getString("errors.badargs"));
						return true;
					}

					try {
						Integer.parseInt(args[2]);
					} catch (NumberFormatException e) {
						sender.sendMessage(ChatColor.RED
								+ "The third argument must be an integer!");
						return true;
					}
					List<Player> matches3 = this.getServer().matchPlayer(
							args[1]);
					if (!matches3.isEmpty() && Integer.parseInt(args[2]) >= 0
							&& sender.hasPermission("karma.set")) {
						return this.setAmount(matches3,
								Integer.parseInt(args[2]));
					}
					return true;
				}				
                                if (args[0].equalsIgnoreCase("add")) {
					if (args.length < 3) {
						this.msg(sender, config.getString("errors.badargs"));
						return true;
					}

					try {
						Integer.parseInt(args[2]);
					} catch (NumberFormatException e) {
						sender.sendMessage(ChatColor.RED
								+ "The third argument must be an integer!");
						return true;
					}
					List<Player> matches3 = this.getServer().matchPlayer(
							args[1]);
					if (!matches3.isEmpty() && Integer.parseInt(args[2]) >= 0
							&& sender.hasPermission("karma.set")) {
                                                KarmaPlayer playerToAddKarma = this.players.get(matches3.get(0).getName());
						return this.setAmount(matches3,
								Integer.parseInt(args[2]) + playerToAddKarma.getKarmaPoints());
					}
					return true;
				}
				if (args.length == 1) {
					// Check other players karma
					Player checkOtherTarget = this.getServer().getPlayer(
							args[0]);
					if (checkOtherTarget == null) {
						this.msg(sender, config.getString("errors.noplayer"));
						return true;
					}
					KarmaPlayer karmaCheckOtherTarget = this.players.get(checkOtherTarget
							.getName());
					if (karmaCheckOtherTarget != null) {
						this.msg(
								sender,
								config.getString("check.others.message")
										.replace("<player>",
												checkOtherTarget.getName())
										.replace(
												"<points>",
												karmaCheckOtherTarget.getKarmaPoints()
														+ "")
										.replace(
												"<curgroupcolor>",
												getPlayerGroupColor(karmaCheckOtherTarget)
														.toString()));
					} else {
						this.msg(sender, config.getString("errors.noplayer"));
					}
					checkOtherTarget = null;
					karmaCheckOtherTarget = null;

					return true;
				}
				
					this.msg(sender, config.getString("errors.unknowncommand"));
					return true;
					
				}
			
		} catch (Exception e) {
			this.msg(sender, config.getString("errors.commandexception")
					.replace("<exception>", e.toString()));
			e.printStackTrace();
			System.out.println("Karma> Error encountered.");
		}

		return true;
	}

	public void saveConfig() {
		try {
			config.save(new File("plugins/Karma/config.yml"));
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private boolean setAmount(List<Player> matches, int amount) {
		Player playerTarget = matches.get(0);
		return this.setAmount(playerTarget, amount);
	}

	private boolean setAmount(Player playerTarget, int amount) {
		KarmaPlayer karmaTarget = this.getPlayers().get(playerTarget.getName());
		if (karmaTarget != null && amount != karmaTarget.getKarmaPoints()) {
			int before = karmaTarget.getKarmaPoints();
			if (amount > karmaTarget.getKarmaPoints()) {
				karmaTarget.addKarma(amount - karmaTarget.getKarmaPoints());
			} else {
				karmaTarget.removeKarma(karmaTarget.getKarmaPoints() - amount);
			}
			this.msg(playerTarget, "Your karma was set to " + ChatColor.GREEN
					+ karmaTarget.getKarmaPoints() + ChatColor.GRAY + ".");
			this.getServer()
					.getLogger()
					.log(Level.INFO,
							"Karma> " + playerTarget.getName()
									+ " karma set to "
									+ karmaTarget.getKarmaPoints() + " from "
									+ before);

			return true;
		}
		return false;

	}

	public void loadOrCreateKarmaPlayer(Player player) {
		String playerName = player.getName();
		if (this.db.exists(playerName)) {
			// existing player
			KarmaPlayer karmaPlayer = this.db.get(playerName);
			if (karmaPlayer != null) {
				this.players.put(playerName, karmaPlayer);

				// check if player needs a promo, in case perms got wiped
				// BONUS: this lets you change perms systems without having
				// to migrate - ex: just change the GM commands to bPerms
				KarmaGroup currentGroup = this.startGroup;
				while (currentGroup != null) {
					if (karmaPlayer.getKarmaPoints() >= currentGroup
							.getKarmaPoints()
							&& !player.hasPermission("karma."
									+ currentGroup.getGroupName())
							&& !(currentGroup.getNext() != null && karmaPlayer
									.getKarmaPoints() >= currentGroup.getNext()
									.getKarmaPoints())) {
						// either doesn't have a next rank or can't beat the
						// next rank's k points, we found the right rank
						for (String cmd : config.getStringList("promotion.commands")) {
							this.runCommand(cmd.replace("<player>", playerName)
								.replace("<group>", currentGroup.getGroupName()));
						}
						for (Player playerOnline : this.getServer()
								.getOnlinePlayers()) {
							this.msg(
									playerOnline,
									config.getString("promotion.message")
											.replace("<player>", playerName)
											.replace("<group>",
													currentGroup.getGroupName())
											.replace(
													"<groupcolor>",
													currentGroup.getChatColor()
															.toString()));
						}//end for

					}//end if

					currentGroup = currentGroup.getNext();
				}//end while	
                                // Check if a player has enough karma points for his rank, if not, add them
                                // This allows easy installation of karma: no having to change preexisting users' karma to their rank's karma	
                                currentGroup = this.startGroup;		
                                while (currentGroup != null) {
					if (karmaPlayer.getKarmaPoints() < currentGroup.getKarmaPoints()
					    && player.hasPermission("karma." + currentGroup.getGroupName())) {

						this.setAmount(player, currentGroup.getKarmaPoints());
						

					}//end if

					currentGroup = currentGroup.getNext();
				}//end while

				// check for last activity, remove one karma point per day off
				long gone = System.currentTimeMillis()
						- karmaPlayer.getLastActivityTime();
				int howManyDays = (int) Math.floor(gone / 86400000L);

				if (howManyDays > 0) {
					int before = karmaPlayer.getKarmaPoints();
					karmaPlayer.removeKarmaAutomatic(howManyDays);
					this.getServer()
							.getLogger()
							.log(Level.INFO,
									"Karma> "
											+ player.getName()
											+ " lost "
											+ (before - karmaPlayer
													.getKarmaPoints())
											+ " karma points");
				}

				// update last activity
				karmaPlayer.ping();
				this.db.put(karmaPlayer);
			}
		} else {
			// create player
			int initialKarma = this.getInitialKarma(player);
			KarmaPlayer karmaPlayer = new KarmaPlayer(this, player.getName(),
					initialKarma, System.currentTimeMillis(), 0);
			this.players.put(player.getName(), karmaPlayer);
			this.db.put(karmaPlayer);

			this.msg(player, config.getString("newplayer.message"));
			this.getServer()
					.getLogger()
					.log(Level.INFO,
							"Karma> " + player.getName() + " created with "
									+ initialKarma + " karma points");
		}
	}

	private int getInitialKarma(Player player) {
		KarmaGroup group = this.startGroup;
		int initialKarma = 0;
		int karmaToNext = 0;
		while (group != null) {
			String perm = "karma." + group.getGroupName();
			if (player.hasPermission(perm)) {
				initialKarma = group.getKarmaPoints();
				if (group.getNext() != null) {
					karmaToNext = group.getNext().getKarmaPoints()
							- group.getKarmaPoints();
				} else {
					// greybeards only initialize with 2020
					karmaToNext = 100;
				}
			} else {
				this.getServer().getLogger()
						.log(Level.INFO, "Karma> Doesn't have " + perm);
				break;
			}
			group = group.getNext();
		}
		initialKarma += (int) (0.2 * karmaToNext); // start bonus of 20% to next
													// rank
		return initialKarma;
	}

	public void checkForPromotion(String playerName, int before, int after) {
		KarmaGroup group = this.startGroup;
		Player playerForPromotion = this.findPlayer(playerName);
		while (group != null && playerForPromotion != null) {
			String perm = "karma." + group.getGroupName();
			if (before < group.getKarmaPoints()
					&& after >= group.getKarmaPoints()
					&& !playerForPromotion.hasPermission(perm)) {
				// promotion
				for (String cmd : config.getStringList("promotion.commands")) {
					this.runCommand(cmd.replace("<player>", playerName)
						.replace("<group>", group.getGroupName()));
				}
				for (Player player : this.getServer().getOnlinePlayers()) {
					this.msg(
							player,
							config.getString("promotion.message")
									.replace("<player>", playerName)
									.replace("<group>", group.getGroupName())
									.replace("<groupcolor>",
											group.getChatColor().toString()));
				}
				this.getServer()
						.getLogger()
						.log(Level.INFO,
								"Karma> " + playerName + " promoted to "
										+ group.getGroupName());
			}
			group = group.getNext();
		}
	}

	public void checkForDemotion(String playerName, int before, int after,
			boolean automatic) {
		KarmaGroup group = this.startGroup;
		Player playerForDemotion = this.findPlayer(playerName);
		while (group != null && playerForDemotion != null) {
			if (group.getNext() != null
					&& before >= group.getNext().getKarmaPoints()
					&& after < group.getNext().getKarmaPoints()) {
				String perm = "karma." + group.getNext().getGroupName();
				if (config.getBoolean("groups." + group.getGroupName()
						+ ".first")
						&& automatic)
					return; // Prevents players from being demoted to the first
							// rank
				if (playerForDemotion.hasPermission(perm)) {
					// demotion
					for (String cmd : config.getStringList("demotion.commands")) {
						this.runCommand(cmd.replace("<player>", playerName)
							.replace("<group>", group.getGroupName()));
					}
					for (Player player : this.getServer().getOnlinePlayers()) {
						this.msg(
								player,
								config.getString("demotion.message")
										.replace("<player>", playerName)
										.replace("<group>",
												group.getGroupName())
										.replace("<groupcolor>",
												group.getChatColor().toString()));
					}
					this.getServer()
							.getLogger()
							.log(Level.INFO,
									"Karma> " + playerName + " demoted to "
											+ group.getGroupName());
					break;
				}
			}
			group = group.getNext();
		}
	}

	public void msg(CommandSender destination, String message) {
		if (message == null || message == "")
			return;
		if (message.contains("<NEWLINE>")) {
			for (String s : message.split("<NEWLINE>")) {
				destination.sendMessage(parseColor(config.getString("prefix")
						+ s));
			}
			return;
		}
		destination
				.sendMessage(parseColor(config.getString("prefix") + message));
	}

	private String parseColor(String message) {
		return message
				.replaceAll("&([0-9a-zA-Z])", ChatColor.COLOR_CHAR + "$1");
	}

	private String getPlayerNextGroupString(KarmaPlayer karmaPlayer) {
		Player player = this.findPlayer(karmaPlayer.getName());
		KarmaGroup group = this.startGroup;
		while (group != null) {
			String perm = "karma." + group.getGroupName();
			if (!player.hasPermission(perm)) {
				return group.getGroupName() + " (" + ChatColor.GREEN
						+ group.getKarmaPoints() + ChatColor.GRAY + ")";
			}
			group = group.getNext();
		}
		return "none";
	}

	private String getPlayerGroupString(KarmaPlayer karmaPlayer) {
		Player player = this.findPlayer(karmaPlayer.getName());
		KarmaGroup group = this.startGroup;
		KarmaGroup lastGroup = null; // first group is recruit
		while (group != null) {
			String perm = "karma." + group.getGroupName();
			if (!player.hasPermission(perm) && group == startGroup) {
                                this.getServer().getLogger().severe(karmaPlayer.getName() + " does not have permissions for the start group! Permissions configured incorrectly (Did you forget inheritance?).");
				return "PERMISSIONS CONFIGURED INCORRECTLY";
			}
                        if (!player.hasPermission(perm)) {
				return lastGroup.getChatColor() + lastGroup.getGroupName() + " (" + ChatColor.YELLOW
						+ lastGroup.getKarmaPoints() + ChatColor.GRAY + ")";
			}
			lastGroup = group;
			if (group.getNext() == null) {
				return group.getChatColor() + group.getGroupName() + " (" + ChatColor.YELLOW
				+ group.getKarmaPoints() + ChatColor.GRAY + ")";
			}
			group = group.getNext();
		}
		return "none";
	}

	private ChatColor getPlayerNextGroupColor(KarmaPlayer karmaPlayer) {
		Player player = this.findPlayer(karmaPlayer.getName());
		KarmaGroup group = this.startGroup;
		while (group != null) {
			String perm = "karma." + group.getGroupName();

			if (!player.hasPermission(perm)) {
				return group.getChatColor();
			}
			group = group.getNext();
		}
		return ChatColor.WHITE;
	}

	private ChatColor getPlayerGroupColor(KarmaPlayer karmaPlayer) {
		Player player = this.findPlayer(karmaPlayer.getName());
		KarmaGroup group = this.startGroup;
		KarmaGroup lastGroup = null; // first group is recruit
		while (group != null) {
			String perm = "karma." + group.getGroupName();
			if (!player.hasPermission(perm) && group == startGroup) {

				return ChatColor.RED;
			}
			if (!player.hasPermission(perm)) {
				return lastGroup.getChatColor();
			}
			lastGroup = group;
			if (group.getNext() == null) {
				return group.getChatColor();
			}
			group = group.getNext();
		}
		return ChatColor.WHITE;
	}

	public Player findPlayer(String playerName) {
		for (Player player : this.getServer().getOnlinePlayers()) {
			if (player.getName().equals(playerName)) {
				return player;
			}
		}
		return null;
	}

	public int getNextRandomKarmaPartyDelay() {
		// on average 20, between 10 min and 30 min
		int minutes = config.getInt("party.time.minimum")
				+ this.random.nextInt(config.getInt("party.time.maximum")
						- config.getInt("party.time.minimum"));
		// 20 ticks/second, 60 seconds/min
		int ticks = minutes * 20 * 60;
		this.getServer()
				.getLogger()
				.log(Level.INFO,
						"Karma> Next karma party in " + minutes
								+ " minutes or " + ticks + " ticks.");
		return ticks;
	}

	public Map<String, KarmaPlayer> getPlayers() {
		return this.players;
	}

	public Database getKarmaDatabase() {
		return this.db;
	}

	private void loadKarmaGroups() {
		Set<String> groups = config.getConfigurationSection("groups").getKeys(
				false);
		int curgroup = 0;
		KarmaGroup nextgroup = null;
		for (String group : groups) {
			nextgroup = new KarmaGroup(group, config.getInt("groups." + group
					+ ".points"), nextgroup, ChatColor.getByChar(config
					.getString("groups." + group + ".color")));
			if (config.getBoolean("groups." + group + ".first")) {
				this.startGroup = nextgroup;
			}
			curgroup++;
		}
	}

	public void runCommand(String command) {
		if (command.contains("<NEWLINE>")) {
			for (String c : command.split("<NEWLINE>")) {
				this.getServer().dispatchCommand(
						this.getServer().getConsoleSender(), c);
				return;
			}
		}
		this.getServer().dispatchCommand(this.getServer().getConsoleSender(),
				command);
	}
}
