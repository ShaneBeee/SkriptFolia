package ch.njol.skript.lang;

import ch.njol.skript.Skript;
import ch.njol.skript.config.Node;
import ch.njol.skript.lang.function.EffFunctionCall;
import ch.njol.skript.log.ParseLogHandler;
import ch.njol.skript.log.SkriptLogger;
import org.bukkit.event.Event;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.log.runtime.SyntaxRuntimeErrorProducer;

import java.util.Iterator;

/**
 * An effect which is unconditionally executed when reached, and execution will usually continue with the next item of the trigger after this effect is executed (the stop effect
 * for example stops the trigger, i.e. nothing else will be executed after it)
 *
 * @see Skript#registerEffect(Class, String...)
 */
public abstract class Effect extends Statement implements SyntaxRuntimeErrorProducer {

	protected Effect() {}

	private Node node;

	@Override
	public boolean preInit() {
		node = getParser().getNode();
		return super.preInit();
	}

	/**
	 * Executes this effect.
	 * 
	 * @param event The event with which this effect will be executed
	 */
	protected abstract void execute(Event event);

	@Override
	public final boolean run(Event event) {
		execute(event);
		return true;
	}

	public static @Nullable Effect parse(String input, @Nullable String defaultError) {
		try (ParseLogHandler log = SkriptLogger.startParseLogHandler()) {
			EffFunctionCall functionCall = EffFunctionCall.parse(input);
			if (functionCall != null) {
				log.printLog();
				return functionCall;
			} else if (log.hasError()) {
				log.printError();
				return null;
			}
			log.clear();

			EffectSection section = EffectSection.parse(input, null, null, null);
			if (section != null) {
				log.printLog();
				return new EffectSectionEffect(section);
			}
			log.clear();

			var iterator = Skript.instance().syntaxRegistry().syntaxes(org.skriptlang.skript.registration.SyntaxRegistry.EFFECT).iterator();
			//noinspection unchecked,rawtypes
			Effect effect = (Effect) SkriptParser.parse(input, (Iterator) iterator, defaultError);
			if (effect != null) {
				log.printLog();
				return effect;
			}

			log.printError();
			return null;
		}
	}

	@Override
	public Node getNode() {
		return node;
	}

	@Override
	public @NotNull String getSyntaxTypeName() {
		return "effect";
	}

}
