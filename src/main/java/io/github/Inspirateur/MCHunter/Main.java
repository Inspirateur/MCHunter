package io.github.Inspirateur.MCHunter;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import java.util.*;


public class Main extends JavaPlugin implements Plugin, Listener {
	private World world = null;
	private boolean gameStarted = false;
	private boolean gameEnded = false;
	private boolean hunterStarted = false;
	private boolean pause = false;
	private int headStart = 120;
	private int traitors = 0;
	private int compassUpdate = 1;
	private UUID huntee = null;
	private String hunteeName;
	private final Map<UUID, Integer> compasses = new HashMap<>();
	private final Map<World.Environment, Location> traces = new HashMap<>();

	@Override
	public void onDisable() {
		System.out.println("MCHunter disabled");
	}

	@Override
	public void onEnable() {
		Bukkit.getPluginManager().registerEvents(this, this);
		System.out.println("MCHunter enabled");
		for(World w: Bukkit.getServer().getWorlds()) {
			if(w.getEnvironment() == World.Environment.NORMAL) {
				world = w;
			}
		}
		if(world != null) {
			world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
			world.setTime(1000);
		}
	}

	@EventHandler
	public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
		event.setCancelled(
			pause || !gameStarted || (!hunterStarted && !event.getDamager().getUniqueId().equals(huntee))
		);
	}

	@EventHandler
	public void onPlayerInteract(PlayerInteractEvent event) {
		if(event.getPlayer().getGameMode() == GameMode.SURVIVAL) {
			event.setCancelled(
				pause || !gameStarted || (!hunterStarted && !event.getPlayer().getUniqueId().equals(huntee))
			);
		}
	}

	@EventHandler
	public void onPlayerMove(PlayerMoveEvent event) {
		if(event.getPlayer().getGameMode() == GameMode.SURVIVAL) {
			if (!event.getFrom().getBlock().equals(Objects.requireNonNull(event.getTo()).getBlock())) {
				event.setCancelled(
					pause || !gameStarted || (!hunterStarted && !event.getPlayer().getUniqueId().equals(huntee))
				);
			}
		}
		// store the last location of the huntee in every world
		if(gameStarted && event.getPlayer().getUniqueId().equals(huntee)) {
			traces.put(event.getPlayer().getWorld().getEnvironment(), event.getPlayer().getLocation());
		}
	}

	private void endGame() {
		gameEnded = true;
		for(Player player: Bukkit.getServer().getOnlinePlayers()) {
			player.setGameMode(GameMode.SPECTATOR);
		}
	}

	@EventHandler
	public void onPlayerDeath(PlayerDeathEvent event) {
		if(gameStarted && !gameEnded) {
			Player player = event.getEntity();
			if(player.getUniqueId().equals(huntee)) {
				Bukkit.broadcastMessage("The huntee died, the hunters have won !");
				endGame();
			}
		}
	}

	@EventHandler
	public void onEnderDragonDeath(EntityDeathEvent event) {
		if(gameStarted && event.getEntity() instanceof EnderDragon) {
			Bukkit.broadcastMessage("The Ender Dragon has been slain, the huntee has won !");
			endGame();
		}
	}

	@EventHandler
	public void onPlayerChangeWorld(PlayerChangedWorldEvent event) {
		if(gameStarted && !event.getPlayer().getUniqueId().equals(huntee)) {
			giveCompass(event.getPlayer());
		}
	}

	private void giveCompass(Player player) {
		findCompass(player);
		player.getInventory().setItem(compasses.get(player.getUniqueId()), new ItemStack(Material.COMPASS, 1));
	}

	@EventHandler
	public void onPlayerRespawn(PlayerRespawnEvent event) {
		if(gameStarted) {
			Player player = event.getPlayer();
			if(!player.getUniqueId().equals(huntee)) {
				giveCompass(player);
			} else {
				event.setRespawnLocation(player.getLocation());
			}
		}
	}

	@Override
	public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, String label, String[] args) {
		switch (label.toLowerCase()) {
			case "compassupdate":
				compassUpdate(sender, args);
				break;
			case "huntee":
				huntee(sender);
				break;
			case "headstart":
				headStart(sender, args);
				break;
			case "start":
				start(sender);
				break;
			case "creative":
				gamemode(sender, GameMode.CREATIVE);
				break;
			case "spectator":
				gamemode(sender, GameMode.SPECTATOR);
				break;
			case "pause":
				pause();
				break;
			case "traitor":
				traitors(sender, args);
				break;
		}
		return true;
	}

	private void compassUpdate(CommandSender sender, String[] args) {
		if(args.length == 0) {
			sender.sendMessage("You need to provide a time (in seconds), like /compassUpdate 2");
			return;
		}
		try {
			compassUpdate = Integer.parseInt(args[0]);
			Bukkit.broadcastMessage(String.format("Compass refresh time is now set to %d seconds", compassUpdate));
		} catch (NumberFormatException e) {
			sender.sendMessage(String.format("%s is not a valid integer", args[0]));
		}
	}

	private void huntee(CommandSender sender) {
		if (!gameStarted) {
			Player player = (Player)sender;
			huntee = player.getUniqueId();
			hunteeName = player.getName();
			Bukkit.broadcastMessage(String.format("%s is now the huntee", hunteeName));
		}
	}

	private void headStart(CommandSender sender, String[] args) {
		if(args.length == 0) {
			sender.sendMessage("You need to provide a time (in seconds), like /headStart 10");
			return;
		}
		try {
			headStart = Integer.parseInt(args[0]);
			Bukkit.broadcastMessage(String.format("Head start given to the huntee is now set to %d seconds", headStart));
		} catch (NumberFormatException e) {
			sender.sendMessage(String.format("%s is not a valid integer", args[0]));
		}
	}

	private void traitors(CommandSender sender, String[] args) {
		if(args.length == 0) {
			sender.sendMessage("You need to provide an amount of traitors, like /traitor 2");
			return;
		}
		try {
			traitors = Integer.parseInt(args[0]);
			Bukkit.broadcastMessage(String.format("The amount of traitors is now set to %d", traitors));
		} catch (NumberFormatException e) {
			sender.sendMessage(String.format("%s is not a valid integer", args[0]));
		}
	}

	private ItemStack findCompass(Player player) {
		for(int i = 0; i < player.getInventory().getSize(); i++) {
			ItemStack item = player.getInventory().getItem(i);
			if(item != null && item.getType() == Material.COMPASS) {
				compasses.put(player.getUniqueId(), i);
				return item;
			}
		}
		compasses.put(player.getUniqueId(), 0);
		return null;
	}

	private void start(CommandSender sender) {
		if (this.huntee == null) {
			sender.sendMessage("A huntee must be defined before the game can start  (/huntee to become the huntee)");
			return;
		}
		Player hunteeP = Bukkit.getPlayer(this.huntee);
		if (hunteeP == null) {
			sender.sendMessage("The huntee could not be found (is the huntee offline ?), try redefining it again");
			return;
		}
		if(world != null) {
			world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true);
		}
		hunteeP.setFoodLevel(20);
		hunteeP.setSaturation(5);
		hunteeP.setHealth(20);

		ArrayList<Player> hunters = new ArrayList<>();
		for(Player player: Bukkit.getServer().getOnlinePlayers()) {
			if(!player.getUniqueId().equals(huntee) && player.getGameMode() == GameMode.SURVIVAL) {
				hunters.add(player);
				giveCompass(player);
			}
		}
		Random rand = new Random();
		for(int i=0; i<traitors; i++) {
			int idx = rand.nextInt(hunters.size());
			Player traitor = hunters.get(idx);
			traitor.sendMessage(ChatColor.RED + "You are a traitor");
			hunters.remove(idx);
		}

		BukkitRunnable freeHunters = new BukkitRunnable() {
			@Override
			public void run() {
				for(Player player: Bukkit.getServer().getOnlinePlayers()) {
					if(!player.getUniqueId().equals(huntee)) {
						player.setFoodLevel(20);
						player.setSaturation(5);
						player.setHealth(20);
					}
				}
				hunterStarted = true;
				Bukkit.broadcastMessage("The hunters are now free !");
			}
		};
		freeHunters.runTaskLater(this, 20*headStart);
		BukkitRunnable updateCompass = new BukkitRunnable() {
			@Override
			public void run() {
				Player hunteeP = Bukkit.getPlayer(huntee);
				if(hunteeP != null) {
					for(Player player: Bukkit.getServer().getOnlinePlayers()) {
						if(!player.getUniqueId().equals(huntee)) {
							Location location;
							if(player.getWorld().getEnvironment() == hunteeP.getWorld().getEnvironment()) {
								location = hunteeP.getLocation();
							} else {
								location = traces.get(player.getWorld().getEnvironment());
								if(location == null) {
									System.out.println("no memory of huntee here");
									location = player.getLocation();
								}
							}
							if(player.getWorld().getEnvironment() == World.Environment.NORMAL) {
								player.setCompassTarget(location);
							} else {
								int inventorySlot = compasses.get(player.getUniqueId());
								ItemStack compass = player.getInventory().getItem(inventorySlot);
								if(compass == null || !(compass.getType() == Material.COMPASS)) {
									compass = findCompass(player);
								}
								if(compass != null) {
									CompassMeta compassMeta = (CompassMeta) compass.getItemMeta();
									assert compassMeta != null;
									compassMeta.setLodestoneTracked(false);
									compassMeta.setLodestone(location);
									compass.setItemMeta(compassMeta);
									player.getInventory().setItem(compasses.get(player.getUniqueId()), compass);
								}
							}
						}
					}
				}
			}
		};
		updateCompass.runTaskTimer(this, 0, 20*compassUpdate);
		gameStarted = true;
		Bukkit.broadcastMessage(String.format(
			"The Hunt is on !\n" +
			"%s is the huntee\n" +
			"The compass refreshes every %d seconds\n" +
			"The hunters will be able to move in %d seconds",
			hunteeName, compassUpdate, headStart
		));
	}

	private void pause() {
		world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, pause);
		pause = !pause;
		Bukkit.broadcastMessage("The game is " + (pause? "" : "un") + "paused");
	}

	private void gamemode(CommandSender sender, GameMode gameMode) {
		if (gameEnded) {
			if (sender instanceof Player) {
				((Player)sender).setGameMode(gameMode);
			}
		} else {
			sender.sendMessage("You can only do that after the game ends");
		}
	}
}
