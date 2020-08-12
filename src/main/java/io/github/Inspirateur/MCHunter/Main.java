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
	private boolean hunterStarted = false;
	private int headStart = 10;
	private int compassUpdate = 1;
	private UUID huntee = null;
	private String hunteeName;
	private Map<UUID, Integer> compasses = new HashMap<>();
	private Map<World.Environment, Location> traces = new HashMap<>();

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
		if (!gameStarted) {
			event.setCancelled(true);
		} else if(!hunterStarted && !event.getDamager().getUniqueId().equals(huntee)) {
			event.setCancelled(true);
		}
	}

	@EventHandler
	public void onPlayerInteract(PlayerInteractEvent event) {
		if (!gameStarted) {
			event.setCancelled(true);
		} else if(!hunterStarted) {
			if(!event.getPlayer().getUniqueId().equals(huntee)) {
				event.setCancelled(true);
			}
		}
	}

	@EventHandler
	public void onPlayerMove(PlayerMoveEvent event) {
		if(event.getPlayer().getGameMode() != GameMode.SPECTATOR) {
			if (!event.getFrom().getBlock().equals(Objects.requireNonNull(event.getTo()).getBlock())) {
				if (!gameStarted) {
					event.setCancelled(true);
				} else if (!hunterStarted) {
					if (!event.getPlayer().getUniqueId().equals(huntee)) {
						event.setCancelled(true);
					}
				}
			}
		}
		// store the last location of the huntee in every world
		if(gameStarted && event.getPlayer().getUniqueId().equals(huntee)) {
			traces.put(event.getPlayer().getWorld().getEnvironment(), event.getPlayer().getLocation());
		}
	}

	private void endGame() {
		gameStarted = false;
		hunterStarted = false;
		for(Player player: Bukkit.getServer().getOnlinePlayers()) {
			player.setGameMode(GameMode.SPECTATOR);
		}
	}

	@EventHandler
	public void onPlayerDeath(PlayerDeathEvent event) {
		if(gameStarted) {
			Player player = event.getEntity();
			if(player.getUniqueId().equals(huntee)) {
				player.setBedSpawnLocation(player.getLocation());
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

	private void giveCompass(Player player) {
		ItemStack compass = findCompass(player);
		if(compass == null) {
			compass = new ItemStack(Material.COMPASS, 1);
		}
		CompassMeta compassMeta = (CompassMeta) compass.getItemMeta();
		if(compassMeta != null) {
			compassMeta.setLodestoneTracked(false);
			compass.setItemMeta(compassMeta);
		}
		player.getInventory().setItem(compasses.get(player.getUniqueId()), compass);
		compasses.put(player.getUniqueId(), 0);
	}

	@EventHandler
	public void onPlayerRespawn(PlayerRespawnEvent event) {
		if(gameStarted) {
			Player player = event.getPlayer();
			if(!player.getUniqueId().equals(huntee)) {
				giveCompass(player);
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
		Player player = (Player)sender;
		huntee = player.getUniqueId();
		hunteeName = player.getName();
		Bukkit.broadcastMessage(String.format("%s is now the huntee", hunteeName));
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
		Player huntee = Bukkit.getPlayer(this.huntee);
		if (huntee == null) {
			sender.sendMessage("The huntee could not be found (is the huntee offline ?), try redefining it again");
			return;
		}
		if(world != null) {
			world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true);
		}
		huntee.setFoodLevel(20);
		huntee.setSaturation(5);
		huntee.setHealth(20);

		for(Player player: Bukkit.getServer().getOnlinePlayers()) {
			if(!player.getUniqueId().equals(huntee.getUniqueId())) {
				giveCompass(player);
			}
		}

		BukkitRunnable freeHunters = new BukkitRunnable() {
			@Override
			public void run() {
				for(Player player: Bukkit.getServer().getOnlinePlayers()) {
					if(!player.getUniqueId().equals(huntee.getUniqueId())) {
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
				for(Player player: Bukkit.getServer().getOnlinePlayers()) {
					if(!player.getUniqueId().equals(huntee.getUniqueId())) {
						Location location;
						if(player.getWorld().getEnvironment() == huntee.getWorld().getEnvironment()) {
							location = huntee.getLocation();
						} else {
							location = traces.get(huntee.getWorld().getEnvironment());
						}
						if(player.getWorld().getEnvironment().equals(World.Environment.NORMAL)) {
							player.setCompassTarget(location);
						} else {
							int inventorySlot = compasses.get(player.getUniqueId());
							ItemStack compass = player.getInventory().getItem(inventorySlot);
							if(compass == null || !(compass.getType() == Material.COMPASS)) {
								compass = findCompass(player);
							}
							if(compass != null) {
								CompassMeta compassMeta = (CompassMeta) compass.getItemMeta();
								compassMeta.setLodestone(location);
								compass.setItemMeta(compassMeta);
								player.getInventory().setItem(compasses.get(player.getUniqueId()), compass);
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
}
