package xyz.derkades.serverselectorx.actions;

import org.bukkit.entity.Player;

import xyz.derkades.serverselectorx.ServerSelectorX;
import xyz.derkades.serverselectorx.placeholders.Server;

public class FirstAvailableServerAction extends Action {

	public FirstAvailableServerAction() {
		super("firstavailableserver", true);
	}

	@Override
	public boolean apply(final Player player, final String value) {
		final String[] serverNames = value.split(":");

		for (final String serverName : serverNames) {
			final Server server = Server.getServer(serverName);

			if (!server.isOnline()) {
				continue;
			}

			if (server.getOnlinePlayers() >= server.getMaximumPlayers()) {
				continue;
			}

			ServerSelectorX.teleportPlayerToServer(player, serverName);
			return false;
		}

		return false;
	}

}
