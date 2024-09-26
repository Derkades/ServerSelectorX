package xyz.derkades.serverselectorx;

import java.util.logging.Logger;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import de.tr7zw.changeme.nbtapi.NBT;
import de.tr7zw.changeme.nbtapi.iface.ReadableNBT;
import xyz.derkades.derkutils.Cooldown;
import xyz.derkades.serverselectorx.conditional.ConditionalItem;

public class ItemClickListener implements Listener {

	@EventHandler(priority = EventPriority.HIGH)
	public void onInteract(final PlayerInteractEvent event) {
		final Logger logger = Main.getPlugin().getLogger();

		if (Main.ITEM_DEBUG) {
			logger.info("[Click debug] Player " + event.getPlayer().getName() + " performed action " + event.getAction() + " on item");
		}

		if (event.getAction() == Action.PHYSICAL) {
			if (Main.ITEM_DEBUG) {
				logger.info("[Click debug] Event was ignored because it was not a click event");
			}
			return;
		}

		final FileConfiguration inventory = Main.getConfigurationManager().getInventoryConfiguration();

		if (event.isCancelled() && inventory.getBoolean("ignore-cancelled", false)) {
			if (Main.ITEM_DEBUG) {
				logger.info("[Click debug] Event was ignored because it was cancelled and ignore-cancelled is enabled");
			}
			return;
		}
		
		final ItemStack item = event.getItem();

		if (item == null || item.getType() == Material.AIR) {
			if (Main.ITEM_DEBUG) {
				logger.info("[Click debug] Event was ignored because the item was AIR");
			}
			return;
		}
		
		final ReadableNBT nbt = NBT.readNbt(item);

		if (!nbt.hasTag("SSXActions")) {
			if (Main.ITEM_DEBUG) {
				logger.info("[Click debug] Event was ignored because the clicked item is not an SSX item");
			}
			// Not an SSX item
			return;
		}
		
		final boolean cancel = inventory.getBoolean("cancel-click-event", false);
		if (cancel) {
			if (Main.ITEM_DEBUG) {
				logger.info("[Click debug] The event has been cancelled, because cancel-click-event is enabled");
			}
			event.setCancelled(true);
		}

		final Player player = event.getPlayer();
		
		// this cooldown is added when the menu closes
		final String globalCooldownId = player.getName() + "ssxitemglobal";
		if (Cooldown.getCooldown(globalCooldownId) > 0) {
			if (Main.ITEM_DEBUG) {
				logger.info("[Click debug] Event was ignored because the global cooldown was in effect (this cooldown cannot be disabled)");
			}
			return;
		}
		Cooldown.addCooldown(globalCooldownId, 100);

		ConditionalItem.runActions(event);

		if (Main.ITEM_DEBUG) {
			logger.info("[Click debug] Event handling completed, actions have been run.");
		}
	}

}
