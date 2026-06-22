package ch.njol.skript.sections;

import ch.njol.skript.Skript;
import ch.njol.skript.config.SectionNode;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Example;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.Section;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.Trigger;
import ch.njol.skript.lang.TriggerItem;
import ch.njol.skript.timings.SkriptTimings;
import ch.njol.skript.util.Timespan;
import ch.njol.skript.util.region.TaskUtils;
import ch.njol.skript.variables.Variables;
import ch.njol.util.Kleenean;
import org.bukkit.event.Event;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@Name("Run Task Later For Owner")
@Description("Runs a section later on the Folia owner of an entity, location, or block. On non-Folia servers this uses the normal Bukkit scheduler through Skript's scheduler bridge.")
@Example("run task 1 tick later for {_loc}:")
@Example("run task 1 tick later for player:")
@Since("2.15.3-custom")
public class SecRunTaskLater extends Section {

	static {
		Skript.registerSection(SecRunTaskLater.class, "(run|execute) task %timespan% later for %entity/location/block%");
	}

	@SuppressWarnings("NotNullFieldNotInitialized")
	private Expression<Timespan> duration;
	@SuppressWarnings("NotNullFieldNotInitialized")
	private Expression<Object> owner;

	@Override
	@SuppressWarnings({"unchecked", "null"})
	public boolean init(Expression<?>[] expressions, int matchedPattern, Kleenean isDelayed, ParseResult parseResult,
						SectionNode sectionNode, List<TriggerItem> triggerItems) {
		this.duration = (Expression<Timespan>) expressions[0];
		this.owner = (Expression<Object>) expressions[1];
		loadOptionalCode(sectionNode);
		return true;
	}

	@Override
	protected @Nullable TriggerItem walk(Event event) {
		debug(event, true);
		Timespan timespan = duration.getSingle(event);
		Object resolvedOwner = owner.getOptionalSingle(event).orElse(null);
		if (timespan == null || resolvedOwner == null || first == null)
			return getNext();

		Object localVars = Variables.copyLocalVariables(event);
		long delay = Math.max(timespan.getAs(Timespan.TimePeriod.TICK), 1L);
		TaskUtils.getScheduler(resolvedOwner).runTaskLater(() -> {
			if (localVars != null)
				Variables.setLocalVariables(event, localVars);

			Object timing = null;
			Trigger trigger = getTrigger();
			if (SkriptTimings.enabled() && trigger != null)
				timing = SkriptTimings.start(trigger.getDebugLabel());

			TriggerItem.walk(first, event);
			Variables.removeLocals(event);

			if (timing != null)
				SkriptTimings.stop(timing);
		}, delay);
		return getNext();
	}

	@Override
	public String toString(@Nullable Event event, boolean debug) {
		return "run task " + duration.toString(event, debug) + " later for " + owner.toString(event, debug);
	}

}
