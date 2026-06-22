package ch.njol.skript.lang.function;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import java.util.Optional;

public final class FunctionEvent<T> extends Event {
	
	// Bukkit stuff
	private final static HandlerList handlers = new HandlerList();
	
	private final Function<? extends T> function;

	private Object executionOwner;
	
	public FunctionEvent(Function<? extends T> function) {
		this.function = function;
	}

	public FunctionEvent(org.skriptlang.skript.common.function.Function<? extends T> function) {
		this.function = (Function<? extends T>) function;
	}

	public Function<? extends T> getFunction() {
		return function;
	}

	public Optional<Object> getExecutionOwner() {
		return Optional.ofNullable(executionOwner);
	}

	public void setExecutionOwner(Object executionOwner) {
		this.executionOwner = executionOwner;
	}
	
	@Override
	public HandlerList getHandlers() {
		return handlers;
	}
	
	public static HandlerList getHandlerList() {
		return handlers;
	}
	
}
