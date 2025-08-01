package org.skriptlang.skript.log.runtime;

import ch.njol.skript.Skript;
import ch.njol.skript.SkriptConfig;
import ch.njol.skript.util.Timespan;
import ch.njol.skript.util.region.TaskUtils;
import ch.njol.skript.util.region.scheduler.task.Task;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.skriptlang.skript.log.runtime.Frame.FrameLimit;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;

/**
 * Handles passing runtime errors between producers and consumers via a frame collection system.
 * <br>
 * The manager should be treated as a singleton and accessed via {@link #getInstance()}
 * or {@link Skript#getRuntimeErrorManager()}. Changing the frame length or limits requires edits to the
 * {@link SkriptConfig} values and a call to {@link #refresh()}. Reloading the config will automatically
 * call {@link #refresh()}.
 *
 * @see RuntimeErrorConsumer
 * @see RuntimeErrorProducer
 * @see Frame
 */
public class RuntimeErrorManager implements Closeable {

	private static RuntimeErrorManager instance;

	/**
	 * Prefer using {@link Skript#getRuntimeErrorManager()} instead.
	 * @return The singleton instance of the runtime error manager.
	 */
	@ApiStatus.Internal
	public static RuntimeErrorManager getInstance() {
		return instance;
	}

	/**
	 * Refreshes the runtime error manager for Skript, pulling from the config values.
	 * Tracked consumers are maintained during refreshes.
	 */
	public static void refresh() {
		long frameLength = SkriptConfig.runtimeErrorFrameDuration.value().getAs(Timespan.TimePeriod.TICK);

		int errorLimit = SkriptConfig.runtimeErrorLimitTotal.value();
		int errorLineLimit = SkriptConfig.runtimeErrorLimitLine.value();
		int errorLineTimeout = SkriptConfig.runtimeErrorLimitLineTimeout.value();
		int errorTimeoutLength = Math.max(SkriptConfig.runtimeErrorTimeoutDuration.value(), 1);
		FrameLimit errorFrame = new FrameLimit(errorLimit, errorLineLimit, errorLineTimeout, errorTimeoutLength);

		int warningLimit = SkriptConfig.runtimeWarningLimitTotal.value();
		int warningLineLimit = SkriptConfig.runtimeWarningLimitLine.value();
		int warningLineTimeout = SkriptConfig.runtimeWarningLimitLineTimeout.value();
		int warningTimeoutLength = Math.max(SkriptConfig.runtimeWarningTimeoutDuration.value(), 1);
		FrameLimit warningFrame = new FrameLimit(warningLimit, warningLineLimit, warningLineTimeout, warningTimeoutLength);

		List<RuntimeErrorConsumer> oldConsumers = List.of();
		if (instance != null) {
			instance.close();
			oldConsumers = instance.consumers;
		}
		instance = new RuntimeErrorManager(Math.max((int) frameLength, 1), errorFrame, warningFrame);
		oldConsumers.forEach(consumer -> instance.addConsumer(consumer));
	}

	private final Frame errorFrame, warningFrame;
	private final Task<?> task;

	private final List<RuntimeErrorConsumer> consumers = new ArrayList<>();

	/**
	 * Creates a new error manager, which also creates its own frames.
	 * <br>
	 * Must be closed when no longer being used.
	 *
	 * @param frameLength The length of a frame in ticks.
	 * @param errorLimits The limits to the error frame.
	 * @param warningLimits The limits to the warning frame.
	 */
	public RuntimeErrorManager(int frameLength, FrameLimit errorLimits, FrameLimit warningLimits) {
		errorFrame = new Frame(errorLimits);
		warningFrame = new Frame(warningLimits);
		this.task = TaskUtils.getGlobalScheduler().runTaskTimerAsync(() -> {
			consumers.forEach(consumer -> consumer.printFrameOutput(errorFrame.getFrameOutput(), Level.SEVERE));
			errorFrame.nextFrame();

			consumers.forEach(consumer -> consumer.printFrameOutput(warningFrame.getFrameOutput(), Level.WARNING));
			warningFrame.nextFrame();
		}, frameLength, frameLength);
	}

	/**
	 * Emits a warning or error depending on severity. Errors are passed to their respective {@link Frame}s for processing.
	 * @param error The error to emit.
	 */
	public void error(@NotNull RuntimeError error) {
		// print if < limit
		if ((error.level() == Level.SEVERE && errorFrame.add(error))
			|| (error.level() == Level.WARNING && warningFrame.add(error))) {
			consumers.forEach((consumer -> consumer.printError(error)));
		}
	}

	/**
	 * @return The frame containing emitted errors.
	 */
	public Frame getErrorFrame() {
		return errorFrame;
	}

	/**
	 * @return The frame containing emitted warnings.
	 */
	public Frame getWarningFrame() {
		return warningFrame;
	}

	/**
	 * Adds a {@link RuntimeErrorConsumer} that will receive the emitted errors and frame output data.
	 * Consumers will be maintained when the manager is refreshed.
	 * @param consumer The consumer to add.
	 */
	public void addConsumer(RuntimeErrorConsumer consumer) {
		synchronized (consumers) {
			consumers.add(consumer);
		}
	}

	/**
	 * Adds multiple {@link RuntimeErrorConsumer}s that will receive the emitted errors and frame output data.
	 * Consumers will be maintained when the manager is refreshed.
	 * @param newConsumers The {@link RuntimeErrorConsumer}s to add.
	 */
	public void addConsumers(RuntimeErrorConsumer... newConsumers) {
		synchronized (consumers) {
			consumers.addAll(Arrays.asList(newConsumers));
		}
	}

	/**
	 * Removes a {@link RuntimeErrorConsumer} from the tracked list.
	 * @param consumer The consumer to remove.
	 * @return {@code true} If the {@code consumer} was removed.
	 */
	public boolean removeConsumer(RuntimeErrorConsumer consumer) {
		synchronized (consumers) {
			return consumers.remove(consumer);
		}
	}

	/**
	 * Removes all {@link RuntimeErrorConsumer}s that receive emitted errors and frame output data.
	 * @return All {@link RuntimeErrorConsumer}s removed.
	 */
	public List<RuntimeErrorConsumer> removeAllConsumers() {
		synchronized (consumers) {
			List<RuntimeErrorConsumer> currentConsumers = List.copyOf(consumers);
			consumers.clear();
			return currentConsumers;
		}
	}

	@Override
	public void close() {
		task.cancel();
	}

}
