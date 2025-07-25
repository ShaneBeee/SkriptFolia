package ch.njol.skript.entity;

import ch.njol.skript.aliases.ItemType;
import ch.njol.skript.lang.Literal;
import ch.njol.skript.lang.SkriptParser.ParseResult;
import ch.njol.skript.lang.util.SimpleExpression;
import ch.njol.skript.localization.ArgsMessage;
import ch.njol.skript.registrations.Classes;
import ch.njol.util.coll.CollectionUtils;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Enderman;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.function.Predicate;

@SuppressWarnings("deprecation")
public class EndermanData extends EntityData<Enderman> {

	static {
		EntityData.register(EndermanData.class, "enderman", Enderman.class, "enderman");
	}

	@Nullable
	private ItemType[] hand = null;

	public EndermanData() {}

	public EndermanData(@Nullable ItemType[] hand) {
		this.hand = hand;
	}

	@SuppressWarnings("unchecked")
	@Override
	protected boolean init(final Literal<?>[] exprs, final int matchedPattern, final ParseResult parseResult) {
		if (exprs[0] != null)
			hand = ((Literal<ItemType>) exprs[0]).getAll();
		return true;
	}

	@Override
	protected boolean init(final @Nullable Class<? extends Enderman> c, final @Nullable Enderman e) {
		if (e != null) {
			BlockData data = e.getCarriedBlock();
			if (data != null) {
				Material type = data.getMaterial();
				assert type != null;
				hand = new ItemType[] {new ItemType(type)};
			}
		}
		return true;
	}

	@Override
	public void set(final Enderman entity) {
		if (hand != null) {
			final ItemType t = CollectionUtils.getRandom(hand);
			assert t != null;
			final ItemStack i = t.getBlock().getRandom();
			if (i != null) {
				// 1.13: item->block usually keeps only material
				entity.setCarriedBlock(Bukkit.createBlockData(i.getType()));
			}
		}

	}

	@Override
	public boolean match(final Enderman entity) {
		return hand == null || SimpleExpression.check(hand, type -> {
			// TODO {Block/Material}Data -> Material conversion is not 100% accurate, needs a better solution
			return type != null && type.isOfType(entity.getCarriedBlock().getMaterial());
		}, false, false);
	}

	@Override
	public Class<Enderman> getType() {
		return Enderman.class;
	}

	private final static ArgsMessage format = new ArgsMessage("entities.enderman.format");

	@Override
	public String toString(final int flags) {
		final ItemType[] hand = this.hand;
		if (hand == null)
			return super.toString(flags);
		return format.toString(super.toString(flags), Classes.toString(hand, false));
	}

	@Override
	protected int hashCode_i() {
		return Arrays.hashCode(hand);
	}

	@Override
	protected boolean equals_i(final EntityData<?> obj) {
		if (!(obj instanceof EndermanData))
			return false;
		final EndermanData other = (EndermanData) obj;
		return Arrays.equals(hand, other.hand);
	}

//		if (hand == null)
//			return "";
//		final StringBuilder b = new StringBuilder();
//		for (final ItemType h : hand) {
//			final Pair<String, String> s = Classes.serialize(h);
//			if (s == null)
//				return null;
//			if (b.length() != 0)
//				b.append(",");
//			b.append(s.first);
//			b.append(":");
//			b.append(s.second.replace(",", ",,").replace(":", "::"));
//		}
//		return b.toString();
	@SuppressWarnings("null")
	@Override
	@Deprecated(since = "2.3.0", forRemoval = true)
	protected boolean deserialize(final String s) {
		if (s.isEmpty())
			return true;
		final String[] split = s.split("(?<!,),(?!,)");
		hand = new ItemType[split.length];
		for (int i = 0; i < hand.length; i++) {
			final String[] t = split[i].split("(?<!:):(?::)");
			if (t.length != 2)
				return false;
			final Object o = Classes.deserialize(t[0], t[1].replace(",,", ",").replace("::", ":"));
			if (o == null || !(o instanceof ItemType))
				return false;
			hand[i] = (ItemType) o;
		}
		return false;
	}

	private boolean isSubhand(final @Nullable ItemType[] sub) {
		if (hand != null)
			return sub != null && ItemType.isSubset(hand, sub);
		return true;
	}

	@Override
	public boolean isSupertypeOf(final EntityData<?> e) {
		if (e instanceof EndermanData)
			return isSubhand(((EndermanData) e).hand);
		return false;
	}

	@Override
	public @NotNull EntityData getSuperType() {
		return new EndermanData(hand);
	}

}
