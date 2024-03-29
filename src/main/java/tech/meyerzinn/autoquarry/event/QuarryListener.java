package tech.meyerzinn.autoquarry.event;

import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.BlockInventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import tech.meyerzinn.autoquarry.AutoQuarryPlugin;
import tech.meyerzinn.autoquarry.QuarryData;
import tech.meyerzinn.autoquarry.QuarryRunner;
import tech.meyerzinn.autoquarry.menu.QuarryMenu;
import tech.meyerzinn.autoquarry.util.BlockLocation;

import java.util.HashMap;
import java.util.Optional;

public class QuarryListener implements Listener {

    private static final JavaPlugin plugin = JavaPlugin.getPlugin(AutoQuarryPlugin.class);

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null) return;
        if (e.getClickedBlock().getType() == Material.DISPENSER && e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            BlockLocation loc = new BlockLocation(e.getClickedBlock().getLocation());
            QuarryData.fromBlockLocation(loc).ifPresent(q -> {
                if (!e.getPlayer().isSneaking()) {
                    e.setCancelled(true);
                    if (AutoQuarryPlugin.perm.has(e.getPlayer(), "autoquarry.use")) {
                        QuarryMenu.getInventory(loc).open(e.getPlayer());
                    } else {
                        e.getPlayer().sendMessage("You do not have permission to use a quarry.");
                    }
                }
            });
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        if (e.getItemInHand().getType() == Material.DISPENSER) {
            BlockLocation location = new BlockLocation(e.getBlock().getLocation());
            Optional<QuarryData> quarryDataOptional = QuarryData.fromItemStack(e.getItemInHand(), location);
            if (quarryDataOptional.isPresent()) {
                if (AutoQuarryPlugin.perm.has(e.getPlayer(), "autoquarry.place")) {
                    QuarryData qd = quarryDataOptional.get();
                    e.getBlock().getWorld().playEffect(e.getBlock().getLocation(), Effect.VILLAGER_PLANT_GROW, 0);
                    qd.placeAsBlock(location);
                } else {
                    e.setCancelled(true);
                    e.getPlayer().sendMessage("You do not have permission to place a quarry.");
                }
            } else {
                if (e.getPlayer().hasPermission("autoquarry.create")) {
                    BlockLocation[] neighbors = location.getNeighbors();
                    for (BlockLocation l : neighbors) {
                        if (l.getBlock().getType() != Material.IRON_BARS) return;
                    }
                    // create a new quarry
                    for (BlockLocation l : neighbors) {
                        l.getBlock().setType(Material.AIR);
                    }
                    QuarryData qd = new QuarryData();
                    qd.placeAsBlock(location);
                    e.getBlock().getWorld().playEffect(e.getBlock().getLocation(), Effect.VILLAGER_PLANT_GROW, 0);
                }
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        if (e.getBlock().getType() == Material.DISPENSER) {
            BlockLocation location = new BlockLocation(e.getBlock().getLocation());
            QuarryData.fromBlockLocation(location).ifPresent(qd -> {
                QuarryRunner.stop(location);
                e.setDropItems(false);
                e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation(), qd.toItemStack());
            });
        }
    }

    @EventHandler
    public void onInventoryMoveItem(InventoryMoveItemEvent e) {
        if (e.getDestination().getHolder() instanceof BlockInventoryHolder) {
            BlockInventoryHolder destinationInventoryHolder = (BlockInventoryHolder) e.getDestination().getHolder();
            BlockLocation location = new BlockLocation(destinationInventoryHolder.getBlock().getLocation());
            if (QuarryData.fromBlockLocation(location).isPresent()) {
                e.setCancelled(true);
                Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, () -> QuarryData.fromBlockLocation(location).ifPresent(quarry -> {
                    if (AutoQuarryPlugin.fuels.containsKey(e.getItem().getType())) {
                        int fuelPerItem = AutoQuarryPlugin.fuels.get(e.getItem().getType());
                        int amount = Math.min((quarry.getFuelCapacity().capacity - quarry.getFuel()) / fuelPerItem, e.getItem().getAmount());
                        HashMap<Integer, ItemStack> excess = e.getSource().removeItem(new ItemStack(e.getItem().getType(), amount));
                        for (ItemStack v : excess.values()) {
                            amount = amount - v.getAmount();
                        }
                        quarry.setFuel(quarry.getFuel() + amount * fuelPerItem);
                    }
                }));
            }
        }
    }
}

