package xyz.derkades.serverselectorx.conditional.condition;

import java.util.List;
import java.util.Map;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.entity.Player;

public class InWorldCondition extends Condition {

    InWorldCondition() {
        super("in-world");
    }

    @Override
    public boolean isTrue(Player player, Map<String, Object> options) throws InvalidConfigurationException {
        if (options.containsKey("world")) {
            if (options.containsKey("worlds")) {
                throw new InvalidConfigurationException("Cannot use 'world' and 'worlds' option at the same time");
            }

            final String worldName = (String) options.get("world");
            return player.getWorld().getName().equals(worldName);
        } else if (options.containsKey("worlds")) {
            @SuppressWarnings("unchecked")
			final
			List<String> worldNames = (List<String>) options.get("worlds");
            for (final String worldName : worldNames) {
                if (player.getWorld().getName().equals(worldName)) {
                    return true;
                }
            }
            return false;
        } else if (options.containsKey("world-regex")) {
            final String worldRegex = (String) options.get("world-regex");
            return player.getWorld().getName().matches(worldRegex);
        } else {
            throw new InvalidConfigurationException("Missing required option: 'worlds' (list of world names), 'world' (single world name) or 'world-regex' (regular expression)");
        }
    }
}
