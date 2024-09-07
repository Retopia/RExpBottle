package rexpbottle;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownExpBottle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ExpBottleEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class RExpBottle extends JavaPlugin implements Listener {

    private final NamespacedKey expKey = new NamespacedKey(this, "expAmount");

    @Override
    public void onEnable() {
        this.getCommand("rbottle").setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command can only be run by a player.");
            return true;
        }

        Player player = (Player) sender;
        int playerExp = getPlayerTotalExp(player);

        if (args.length == 0) {
            player.sendMessage(ChatColor.RED + "Usage: /rbottle <amount> [number of bottles] or /rbottle all");
            return true;
        }

        if (args[0].equalsIgnoreCase("all")) {
            int totalExp = getPlayerTotalExp(player);
            createExpBottle(player, totalExp);
            setPlayerTotalExp(player, 0);
            player.sendMessage(ChatColor.GREEN + "Converted all experience points into a single bottle.");
            return true;
        }

        try {
            int amount = Integer.parseInt(args[0]);
            int numBottles = (args.length > 1) ? Integer.parseInt(args[1]) : 1;

            if (amount > 0 && numBottles > 0) {
                int totalExpNeeded = amount * numBottles;

                if (totalExpNeeded <= playerExp) {
                    int addedToInventory = 0;

                    for (int i = 0; i < numBottles; i++) {
                        boolean addedToInv = createExpBottle(player, amount);
                        if (addedToInv) {
                            addedToInventory++;
                        }
                    }

                    setPlayerTotalExp(player, playerExp - totalExpNeeded);

                    if (addedToInventory == numBottles) {
                        player.sendMessage(ChatColor.GREEN + "Converted " + totalExpNeeded + " experience points into "
                                + numBottles + " bottle(s) in your inventory.");
                    } else if (addedToInventory == 0) {
                        player.sendMessage(ChatColor.YELLOW + "Converted " + totalExpNeeded + " experience points into "
                                + numBottles + " bottle(s). All bottles were dropped due to full inventory.");
                    } else {
                        player.sendMessage(ChatColor.YELLOW + "Converted " + totalExpNeeded + " experience points into "
                                + numBottles + " bottle(s). " + addedToInventory + " added to inventory, "
                                + (numBottles - addedToInventory) + " dropped due to full inventory.");
                    }
                } else {
                    player.sendMessage(ChatColor.RED + "You do not have enough experience.");
                }
            } else {
                player.sendMessage(ChatColor.RED + "Invalid number.");
            }
        } catch (NumberFormatException e) {
            player.sendMessage(ChatColor.RED + "Invalid number.");
        }

        return true;
    }

    private boolean createExpBottle(Player player, int amount) {
        ItemStack bottle = new ItemStack(Material.EXPERIENCE_BOTTLE, 1);
        ItemMeta meta = bottle.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "Experience Bottle");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GREEN + "Contains " + ChatColor.WHITE + amount + " EXP");
            lore.add(ChatColor.GREEN + "From: " + ChatColor.WHITE + player.getName());
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(expKey, PersistentDataType.INTEGER, amount);
            bottle.setItemMeta(meta);
        }

        Inventory inventory = player.getInventory();
        HashMap<Integer, ItemStack> leftover = inventory.addItem(bottle);

        if (leftover.isEmpty()) {
            return true;
        } else {
            for (ItemStack item : leftover.values()) {
                player.getWorld().dropItem(player.getLocation(), item);
            }
            return false;
        }
    }

    @EventHandler
    public void onPlayerUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            if (item != null && item.getType() == Material.EXPERIENCE_BOTTLE) {
                ItemMeta meta = item.getItemMeta();
                if (meta != null && meta.getPersistentDataContainer().has(expKey, PersistentDataType.INTEGER)) {
                    int amount = meta.getPersistentDataContainer().get(expKey, PersistentDataType.INTEGER);

                    ThrownExpBottle thrownBottle = player.launchProjectile(ThrownExpBottle.class);
                    thrownBottle.getPersistentDataContainer().set(expKey, PersistentDataType.INTEGER, amount);
                    item.setAmount(item.getAmount() - 1);
                    event.setCancelled(true);
                }
            }
        }
    }

    @EventHandler
    public void onBottleBreak(ExpBottleEvent event) {
        Entity entity = event.getEntity();
        if (entity.getType() == EntityType.EXPERIENCE_BOTTLE
                && entity.getPersistentDataContainer().has(expKey, PersistentDataType.INTEGER)) {
            int amount = entity.getPersistentDataContainer().get(expKey, PersistentDataType.INTEGER);

            event.setExperience(0);
            event.setShowEffect(true);

            ExperienceOrb orb = (ExperienceOrb) entity.getWorld().spawn(entity.getLocation(), ExperienceOrb.class);
            orb.setExperience(amount);
        }
    }

    private int getPlayerTotalExp(Player player) {
        int exp = 0;
        int level = player.getLevel();
        double progress = player.getExp();

        // Calculate experience from levels
        if (level >= 31) {
            exp = (int) (4.5 * Math.pow(level, 2) - 162.5 * level + 2220);
        } else if (level >= 16) {
            exp = (int) (2.5 * Math.pow(level, 2) - 40.5 * level + 360);
        } else {
            exp = (int) (Math.pow(level, 2) + 6 * level);
        }

        // Add progress within the level
        exp += Math.round(player.getExpToLevel() * progress);

        return exp;
    }

    private void setPlayerTotalExp(Player player, int exp) {
        player.setExp(0);
        player.setLevel(0);

        while (exp > player.getExpToLevel()) {
            exp -= player.getExpToLevel();
            player.setLevel(player.getLevel() + 1);
        }

        player.setExp((float) exp / player.getExpToLevel());
    }
}
