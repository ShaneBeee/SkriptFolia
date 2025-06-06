package ch.njol.skript.events;

import ch.njol.skript.Skript;
import ch.njol.skript.events.bukkit.ScriptEvent;
import ch.njol.skript.lang.Literal;
import ch.njol.skript.lang.SkriptEvent;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.Trigger;
import ch.njol.skript.util.region.TaskUtils;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;

public class EvtScript extends SkriptEvent {
	
	static {
		Skript.registerEvent("Script Load/Unload", EvtScript.class, ScriptEvent.class,
			"[:async] [script] (load|init|enable)",
			"[:async] [script] (unload|stop|disable)"
			).description(
				"Called directly after the trigger is loaded, or directly before the whole script is unloaded.",
				"The keyword 'async' indicates the trigger can be ran asynchronously, "
			).examples(
				"on load:",
				"\tset {running::%script%} to true",
				"on unload:",
				"\tset {running::%script%} to false"
			).since("2.0");
	}
	
	private boolean async;
	private boolean load;
	
	@Override
	public boolean init(Literal<?>[] args, int matchedPattern, ParseResult parseResult) {
		async = parseResult.hasTag("async");
		load = matchedPattern == 0;
		return true;
	}

	@Override
	public boolean postLoad() {
		if (load)
			runTrigger(trigger, new ScriptEvent());
		return true;
	}

	@Override
	public void unload() {
		if (!load)
			runTrigger(trigger, new ScriptEvent());
	}

	@Override
	public boolean check(Event event) {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isEventPrioritySupported() {
		return false;
	}
	
	@Override
	public String toString(@Nullable Event event, boolean debug) {
		return (async ? "async " : "") + "script " + (load ? "" : "un") + "load";
	}

	private void runTrigger(Trigger trigger, Event event) {
		if (async || Bukkit.isPrimaryThread()) {
			trigger.execute(event);
		} else {
			if (Skript.getInstance().isEnabled())
				TaskUtils.getGlobalScheduler().runTask(() -> trigger.execute(event));
		}
	}
	
}
