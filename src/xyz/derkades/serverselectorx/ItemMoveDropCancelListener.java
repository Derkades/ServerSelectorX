package xyz.derkades.serverselectorx;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;

import de.tr7zw.changeme.nbtapi.NBT;
import de.tr7zw.changeme.nbtapi.iface.ReadableNBT;

public class ItemMoveDropCancelListener implements Listener {

	public ItemMoveDropCancelListener() {
		Bukkit.getPluginManager().registerEvents(this, Main.getPlugin());
	}

	private boolean isCancelledWorld(final World world) {
		final List<String> onlyIn = Main.getConfigurationManager().getInventoryConfiguration().getStringList("only-in-worlds");
		final List<String> exceptions = Main.getConfigurationManager().getInventoryConfiguration().getStringList("world-exceptions");
		return (onlyIn.isEmpty() || onlyIn.contains(world.getName())) && !exceptions.contains(world.getName());
	}

	private boolean isSsxItem(final ItemStack item) {
		if (item == null || item.getType() == Material.AIR) {
			return false;
		} else {
			final ReadableNBT nbt = NBT.readNbt(item);
			// "SSXItem" and "SSXActions" is for items from older versions, this can be removed later
			return nbt.hasTag("SSXItem") ||
					nbt.hasTag("SSXActions") ||
					nbt.hasTag("SSXItemConfigName");
		}
	}

	@EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
	public void onDrop(final PlayerDropItemEvent event) {
		if (!Main.getConfigurationManager().getInventoryConfiguration().getBoolean("cancel-item-drop")) {
			return;
		}

		if (!event.getPlayer().hasPermission("ssx.drop") &&
				this.isCancelledWorld(event.getPlayer().getWorld()) &&
				(
						!Main.getConfigurationManager().getInventoryConfiguration().getBoolean("ssx-items-only") ||
						this.isSsxItem(event.getItemDrop().getItemStack())
				)
		) {
			event.setCancelled(true);
			event.getItemDrop().remove();
		}
	}

	@EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
	public void onItemMove(final InventoryClickEvent event) {
		event.setCancelled(
				Main.getConfigurationManager().getInventoryConfiguration().getBoolean("cancel-item-move") &&
				event.getClickedInventory() != null &&
				!event.getWhoClicked().hasPermission("ssx.move") &&
				this.isCancelledWorld(event.getWhoClicked().getWorld()) &&
				(
						!Main.getConfigurationManager().getInventoryConfiguration().getBoolean("ssx-items-only") ||
						this.isSsxItem(event.getCursor()) ||
						this.isSsxItem(event.getCurrentItem()) ||
						(
								event.getClick() == ClickType.NUMBER_KEY &&
								this.isSsxItem(event.getWhoClicked().getInventory().getItem(event.getHotbarButton()))
						)
				)
		);
	}

	@EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
	public void onItemMove(final InventoryDragEvent event) {
		event.setCancelled(
				Main.getConfigurationManager().getInventoryConfiguration().getBoolean("cancel-item-move") &&
				!event.getWhoClicked().hasPermission("ssx.move") &&
				this.isCancelledWorld(event.getWhoClicked().getWorld()) &&
				(
						!Main.getConfigurationManager().getInventoryConfiguration().getBoolean("ssx-items-only") ||
						this.isSsxItem(event.getCursor())
				)
		);
	}

	@EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
	public void onDeath(final PlayerDeathEvent event) {
		if (Main.getConfigurationManager().getInventoryConfiguration().getBoolean("remove-ssx-items-on-death")) {
			event.getDrops().removeIf(this::isSsxItem);
		}
	}

}
