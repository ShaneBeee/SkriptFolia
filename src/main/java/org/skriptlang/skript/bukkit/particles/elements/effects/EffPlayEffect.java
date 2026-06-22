package org.skriptlang.skript.bukkit.particles.elements.effects;

import ch.njol.skript.config.Node;
import ch.njol.skript.doc.Description;
import ch.njol.skript.doc.Example;
import ch.njol.skript.doc.Name;
import ch.njol.skript.doc.Since;
import ch.njol.skript.entity.EntityData;
import ch.njol.skript.lang.Effect;
import ch.njol.skript.lang.Expression;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.SyntaxStringBuilder;
import ch.njol.skript.registrations.Classes;
import ch.njol.skript.util.Direction;
import ch.njol.skript.util.region.RegionUtils;
import ch.njol.skript.util.region.TaskUtils;
import ch.njol.util.Kleenean;
import org.bukkit.EntityEffect;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.skriptlang.skript.bukkit.particles.GameEffect;
import org.skriptlang.skript.bukkit.particles.particleeffects.ParticleEffect;
import org.skriptlang.skript.registration.SyntaxInfo;
import org.skriptlang.skript.registration.SyntaxRegistry;

@Name("Play or Draw an Effect")
@Description("""
	Plays or draws a specific effect at a location, to a player, or on an entity.
	Effects can be:
	* Particles.
	* Game effects, which consist of combinations of particles and sounds, like the bone meal particles, \
	the sound of footsteps on a specific block, or the particles and sound of breaking a splash potion.
	* Entity effects, which are particles or animations that are entity-specific and can only be played on \
	a compatible entity. For example, the ravager attack animation can be played with this effect.

	All effects vary significantly in availability from version to version, and some may simply not function on your \
	version of Minecraft. Some effects, like the death animation entity effect, may cause client glitches and should be \
	used carefully!
	""")
@Example("draw 2 smoke particles at player")
@Example("force draw 10 red dust particles of size 3 for player")
@Example("play blue instant splash potion break effect with a view radius of 10")
@Example("play ravager attack animation on player's target")
@Since("2.14")
public class EffPlayEffect extends Effect {

	public static void register(SyntaxRegistry registry) {
		registry.register(SyntaxRegistry.EFFECT, SyntaxInfo.builder(EffPlayEffect.class)
				.addPatterns(
					"[:force] (play|show|draw) %gameeffects/particles% [%-directions% %locations%] [as %-player%]",
					"[:force] (play|draw) %gameeffects/particles% [%-directions% %locations%] (for|to) %-players% [as %-player%]", // show is omitted to avoid conflicts with EffOpenInv
					"(play|show|draw) %gameeffects% [%-directions% %locations%] (in|with) [a] [view] (radius|range) [of] %number%",
					"(play|show|draw) %entityeffects% on %entities%",
					"[:force] (play|show|draw) %number% [of] %particles% [%-directions% %locations%] [as %-player%]",
					"[:force] (play|draw) %number% [of] %particles% [%-directions% %locations%] (for|to) %-players% [as %-player%]",
					"[:force] (play|show|draw) %number% [of] %particles% [%-directions% %locations%] with offset %vector% [with extra %-number%] [(force:with force)]",
					"[:force] (play|show|draw) %number% [of] %particles% [%-directions% %locations%] with extra %number% [(force:with force)]"
				)
				.supplier(EffPlayEffect::new)
				.build());
	}

	private Expression<?> toDraw;
	private @Nullable Expression<Location> locations;
	private @Nullable Expression<Player> toPlayers;
	private @Nullable Expression<Player> asPlayer;
	private @Nullable Expression<Number> radius;
	private @Nullable Expression<Number> count;
	private @Nullable Expression<Vector> compatOffset;
	private @Nullable Expression<Number> compatExtra;
	private boolean force;

	// for entity effects
	private @Nullable Expression<Entity> entities;

	private Node node;

	@Override
	@SuppressWarnings("unchecked")
	public boolean init(Expression<?>[] expressions, int matchedPattern, Kleenean isDelayed, ParseResult parseResult) {
		this.force = parseResult.hasTag("force");
		this.toDraw = expressions[0];
		switch (matchedPattern) {
			case 0 -> {
				this.locations = Direction.combine((Expression<? extends Direction>) expressions[1], (Expression<Location>) expressions[2]);
				this.asPlayer = (Expression<Player>) expressions[3];
			}
			case 1 -> {
				this.locations = Direction.combine((Expression<? extends Direction>) expressions[1], (Expression<Location>) expressions[2]);
				this.toPlayers = (Expression<Player>) expressions[3];
				this.asPlayer = (Expression<Player>) expressions[4];
			}
			case 2 -> {
				this.locations = Direction.combine((Expression<? extends Direction>) expressions[1], (Expression<Location>) expressions[2]);
				this.radius = (Expression<Number>) expressions[3];
			}
			case 3 -> this.entities = (Expression<Entity>) expressions[1];
			case 4 -> {
				this.count = (Expression<Number>) expressions[0];
				this.toDraw = expressions[1];
				this.locations = Direction.combine((Expression<? extends Direction>) expressions[2], (Expression<Location>) expressions[3]);
				this.asPlayer = (Expression<Player>) expressions[4];
			}
			case 5 -> {
				this.count = (Expression<Number>) expressions[0];
				this.toDraw = expressions[1];
				this.locations = Direction.combine((Expression<? extends Direction>) expressions[2], (Expression<Location>) expressions[3]);
				this.toPlayers = (Expression<Player>) expressions[4];
				this.asPlayer = (Expression<Player>) expressions[5];
			}
			case 6 -> {
				this.count = (Expression<Number>) expressions[0];
				this.toDraw = expressions[1];
				this.locations = Direction.combine((Expression<? extends Direction>) expressions[2], (Expression<Location>) expressions[3]);
				this.compatOffset = (Expression<Vector>) expressions[4];
				this.compatExtra = (Expression<Number>) expressions[5];
			}
			case 7 -> {
				this.count = (Expression<Number>) expressions[0];
				this.toDraw = expressions[1];
				this.locations = Direction.combine((Expression<? extends Direction>) expressions[2], (Expression<Location>) expressions[3]);
				this.compatExtra = (Expression<Number>) expressions[4];
			}
		}
		this.node = getParser().getNode();
		return true;
	}

	@Override
	protected void execute(Event event) {
		// entity effect
		if (this.entities != null) {
			Entity[] entities = this.entities.getArray(event);
			EntityEffect[] effects = (EntityEffect[]) toDraw.getArray(event);
			drawEntityEffects(effects, entities);
			return;
		}

		// game effects / particles
		assert this.locations != null;
		Number radius = this.radius != null ? this.radius.getSingle(event) : null; // a null radius means no radius limit
		Location[] locations = this.locations.getArray(event);
		Object[] toDraw = this.toDraw.getArray(event);
		Player[] players = toPlayers != null ? toPlayers.getArray(event) : null;
		Player asPlayer = this.asPlayer != null ? this.asPlayer.getSingle(event) : null;
		Number countValue = this.count != null ? this.count.getSingle(event) : null;

		for (Object draw : toDraw) {
			// Game effects
			if (draw instanceof GameEffect gameEffect) {
				// in radius
				if (players == null) {
					for (Location location : locations)
						runAtLocation(location, () -> gameEffect.draw(location, radius));
				// for players
				} else {
					for (Player player : players) {
						for (Location location : locations)
							runAtLocation(location, () -> gameEffect.drawForPlayer(location, player));
					}
				}
			// Particles
			} else if (draw instanceof ParticleEffect particleEffect) {
				ParticleEffect baseEffect = particleEffect.copy(); // avoid modifying the original effect
				if (asPlayer != null)
					baseEffect.source(asPlayer);
				baseEffect.force(force);
				baseEffect.receivers(players);
				if (countValue != null) {
					int clamped = (int) Math.max(0L, Math.min(countValue.longValue(), 16384L));
					baseEffect.count(clamped);
				}
				Vector offsetValue = compatOffset != null ? compatOffset.getSingle(event) : null;
				if (offsetValue != null)
					baseEffect.offset(offsetValue.toVector3d());
				Number extraValue = compatExtra != null ? compatExtra.getSingle(event) : null;
				if (extraValue != null)
					baseEffect.extra(extraValue.doubleValue());
				for (Location location : locations) {
					ParticleEffect scheduledEffect = baseEffect.copy();
					runAtLocation(location, () -> scheduledEffect.spawn(location));
				}
			}
		}
	}

	private void runAtLocation(Location location, Runnable runnable) {
		if (location == null) {
			runnable.run();
			return;
		}
		if (TaskUtils.isFoliaSchedulersEnabled() && !RegionUtils.isOwnedByCurrentRegion(location)) {
			TaskUtils.getRegionalScheduler(location).runTask(runnable);
			return;
		}
		runnable.run();
	}

	private void runAtEntity(Entity entity, Runnable runnable) {
		if (entity == null) {
			runnable.run();
			return;
		}
		if (TaskUtils.isFoliaSchedulersEnabled() && !RegionUtils.isOwnedByCurrentRegion(entity)) {
			TaskUtils.getEntityScheduler(entity).runTask(runnable);
			return;
		}
		runnable.run();
	}

	/**
	 * Helper method to draw entity effects on entities. Provides a runtime warning if no provided entities are applicable
	 * @param effects the effects to draw
	 * @param entities the entities to draw the effects on
	 */
	private void drawEntityEffects(EntityEffect @NotNull [] effects, Entity @NotNull [] entities) {
		for (EntityEffect effect : effects) {
			boolean played = false;
			for (Entity entity : entities) {
				if (effect.isApplicableTo(entity)) {
					final EntityEffect scheduledEffect = effect;
					runAtEntity(entity, () -> entity.playEffect(scheduledEffect));
					played = true;
				}
			}
			if (entities.length > 0 && !played) {
				// todo: cache?
				String[] applicableClasses = effect.getApplicableClasses().stream()
					.map(EntityData::toString)
					.distinct().toArray(String[]::new);
				assert this.entities != null;
				warning("The '" + Classes.toString(effect) + "' is not applicable to any of the given entities " +
					"(" + Classes.toString(entities, this.entities.getAnd()) + "), " +
					"only to " + Classes.toString(applicableClasses, false) + ".");
			}
		}
	}

	@Override
	public String toString(@Nullable Event event, boolean debug) {
		return new SyntaxStringBuilder(event, debug)
			.append("play", toDraw)
			.appendIf(locations != null, locations)
			.appendIf(toPlayers != null, "for", toPlayers)
			.toString();
	}

	@Override
	public Node getNode() {
		return node;
	}

}
