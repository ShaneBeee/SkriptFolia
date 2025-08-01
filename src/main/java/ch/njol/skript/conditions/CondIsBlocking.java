package ch.njol.skript.conditions;

import org.bukkit.entity.Player;

import ch.njol.skript.conditions.base.PropertyCondition;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Examples;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;

@Name("Is Blocking")
@Description("Checks whether a player is blocking with their shield.")
@Examples({
	"on damage of player:",
	  	"\tvictim is blocking",
	 	"\tdamage attacker by 0.5 hearts"
})
@Since("unknown (before 2.1)")
public class CondIsBlocking extends PropertyCondition<Player> {
	
	static {
		register(CondIsBlocking.class, "(blocking|defending) [with [a] shield]", "players");
	}
	
	@Override
	public boolean check(Player player) {
		return player.isBlocking();
	}
	
	@Override
	protected String getPropertyName() {
		return "blocking";
	}
	
}
