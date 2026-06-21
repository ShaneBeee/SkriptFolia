package ch.njol.skript.effects;

import ch.njol.skript.Skript;
import ch.njol.skript.command.CommandEvent;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Example;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.Literal;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.Trigger;
import ch.njol.skript.lang.TriggerItem;
import ch.njol.skript.timings.SkriptTimings;
import ch.njol.skript.util.Timespan;
import ch.njol.skript.util.region.TaskUtils;
import ch.njol.skript.util.region.scheduler.Scheduler;
import ch.njol.skript.variables.Variables;
import ch.njol.util.Kleenean;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.event.Event;
import org.bukkit.event.block.BlockEvent;
import org.bukkit.event.entity.EntityEvent;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

@Name("Delay")
@Description("Delays the script's execution by a given timespan. Please note that delays are not persistent, e.g. trying to create a tempban script with <code>ban player → wait 7 days → unban player</code> will not work if you restart your server anytime within these 7 days. You also have to be careful even when using small delays!")
@Example("wait 2 minutes")
@Example("halt for 5 minecraft hours")
@Example("wait a tick")
@Since("1.4")
public class Delay extends Effect {

	static {
		Skript.registerEffect(Delay.class, "(wait|halt) [for] %timespan% [for %-entity/location/block%]");
	}

	@SuppressWarnings("NotNullFieldNotInitialized")
	protected Expression<Timespan> duration;
	private Expression<Object> object;

	@SuppressWarnings({"unchecked", "null"})
	@Override
	public boolean init(Expression<?>[] exprs, int matchedPattern, Kleenean isDelayed, ParseResult parseResult) {
		getParser().setHasDelayBefore(Kleenean.TRUE);

		duration = (Expression<Timespan>) exprs[0];
		if (duration instanceof Literal) { // If we can, do sanity check for delays
			Timespan timespan = ((Literal<Timespan>) duration).getSingle();
			if (timespan.isInfinite()) {
				Skript.error("Delaying for an eternity is not allowed. Use the 'stop' effect instead.");
				return false;
			}
			long millis = timespan.getAs(Timespan.TimePeriod.MILLISECOND);
			if (millis < 50) {
				Skript.warning("Delays less than one tick are not possible, defaulting to one tick.");
			}
		}
		this.object = (Expression<Object>) exprs[1];

		return true;
	}

	@Override
	@Nullable
	protected TriggerItem walk(Event event) {
		debug(event, true);
		long start = Skript.debug() ? System.nanoTime() : 0;
		TriggerItem next = getNext();
		if (next != null && Skript.getInstance().isEnabled()) { // See https://github.com/SkriptLang/Skript/issues/3702

			Timespan duration = this.duration.getSingle(event);
			if (duration == null)
				return null;

			Object object = null;
			if (this.object != null) {
				object = this.object.getOptionalSingle(event).orElse(null);
			}

			// Back up local variables
			Object localVars = Variables.removeLocals(event);
			Scheduler<?> scheduler = getScheduler(event, object, localVars);

			scheduler.runTaskLater(() -> {
        addDelayedEvent(event);
				Skript.debug(getIndentation() + "... continuing after " + (System.nanoTime() - start) / 1_000_000_000. + "s");

				// Re-set local variables
				if (localVars != null)
					Variables.setLocalVariables(event, localVars);

				Object timing = null; // Timings reference must be kept so that it can be stopped after TriggerItem execution
				if (SkriptTimings.enabled()) { // getTrigger call is not free, do it only if we must
					Trigger trigger = getTrigger();
					if (trigger != null)
						timing = SkriptTimings.start(trigger.getDebugLabel());
				}

				TriggerItem.walk(next, event);
				Variables.removeLocals(event); // Clean up local vars, we may be exiting now

				SkriptTimings.stop(timing); // Stop timing if it was even started
			}, Math.max(duration.getAs(Timespan.TimePeriod.TICK), 1)); // Minimum delay is one tick, less than it is useless!
		}
		return null;
	}

	@Override
	protected void execute(Event event) {
		throw new UnsupportedOperationException();
	}

	@Override
	public String toString(@Nullable Event event, boolean debug) {
		return "wait for " + duration.toString(event, debug) + (event == null ? "" : "...");
	}

	private static Scheduler<?> getScheduler(Event event, @Nullable Object object, @Nullable Object localVars) {
		if (object != null)
			return TaskUtils.getScheduler(object);

		if (TaskUtils.isFoliaSchedulersEnabled()) {
			Optional<Object> localOwner = Variables.findLocalExecutionOwner(localVars);
			if (localOwner.isPresent())
				return TaskUtils.getScheduler(localOwner.get());

			Optional<Object> eventOwner = getEventExecutionOwner(event);
			if (eventOwner.isPresent())
				return TaskUtils.getScheduler(eventOwner.get());
		}

		return TaskUtils.getGlobalScheduler();
	}

	private static Optional<Object> getEventExecutionOwner(Event event) {
		if (event instanceof CommandEvent commandEvent) {
			CommandSender sender = commandEvent.getSender();
			if (sender instanceof Entity entity)
				return Optional.of(entity);
			if (sender instanceof BlockCommandSender blockCommandSender)
				return Optional.of(blockCommandSender.getBlock());
		}

		if (event instanceof PlayerEvent playerEvent)
			return Optional.of(playerEvent.getPlayer());

		if (event instanceof EntityEvent entityEvent)
			return Optional.of(entityEvent.getEntity());

		if (event instanceof BlockEvent blockEvent)
			return Optional.of(blockEvent.getBlock());

		return Optional.empty();
	}

	private static final Set<Event> DELAYED =
		Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

	/**
	 * The main method for checking if the execution of {@link TriggerItem}s has been delayed.
	 * @param event The event to check for a delay.
	 * @return Whether {@link TriggerItem} execution has been delayed.
	 */
	public static boolean isDelayed(Event event) {
		return DELAYED.contains(event);
	}

	/**
	 * The main method for marking the execution of {@link TriggerItem}s as delayed.
	 * @param event The event to mark as delayed.
	 */
	public static void addDelayedEvent(Event event) {
		DELAYED.add(event);
	}

}
