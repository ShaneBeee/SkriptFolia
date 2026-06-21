package ch.njol.skript.variables;

import ch.njol.skript.lang.util.ContextlessEvent;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.event.Event;
import org.easymock.EasyMock;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;

public class VariablesExecutionOwnerTest {

	@Test
	public void findLocalExecutionOwnerReturnsScalarEntity() {
		Event event = ContextlessEvent.get();
		Entity entity = EasyMock.niceMock(Entity.class);
		Variables.setVariable("player", entity, event, true);

		Object locals = Variables.removeLocals(event);

		assertSame(entity, Variables.findLocalExecutionOwner(locals).orElse(null));
	}

	@Test
	public void findLocalExecutionOwnerReturnsListEntity() {
		Event event = ContextlessEvent.get();
		Entity entity = EasyMock.niceMock(Entity.class);
		Variables.setVariable("targets::1", entity, event, true);

		Object locals = Variables.removeLocals(event);

		assertSame(entity, Variables.findLocalExecutionOwner(locals).orElse(null));
	}

	@Test
	public void findLocalExecutionOwnerPrefersEntityOverBlock() {
		Event event = ContextlessEvent.get();
		Block block = EasyMock.niceMock(Block.class);
		Entity entity = EasyMock.niceMock(Entity.class);
		Variables.setVariable("block", block, event, true);
		Variables.setVariable("player", entity, event, true);

		Object locals = Variables.removeLocals(event);

		assertSame(entity, Variables.findLocalExecutionOwner(locals).orElse(null));
	}

	@Test
	public void findLocalExecutionOwnerPrefersBlockOverLocation() {
		Event event = ContextlessEvent.get();
		Block block = EasyMock.niceMock(Block.class);
		Location location = new Location(null, 1, 2, 3);
		Variables.setVariable("block", block, event, true);
		Variables.setVariable("location", location, event, true);

		Object locals = Variables.removeLocals(event);

		assertSame(block, Variables.findLocalExecutionOwner(locals).orElse(null));
	}

	@Test
	public void findLocalExecutionOwnerReturnsEmptyForNullMap() {
		assertFalse(Variables.findLocalExecutionOwner(null).isPresent());
	}

}
