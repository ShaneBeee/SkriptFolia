package ch.njol.skript.variables;

import ch.njol.skript.lang.util.ContextlessEvent;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.event.Event;
import org.easymock.EasyMock;
import org.junit.After;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;

public class VariablesExecutionOwnerTest {

	private final Field localVariablesField;
	private final Field variablesField;

	public VariablesExecutionOwnerTest() throws ReflectiveOperationException {
		localVariablesField = Variables.class.getDeclaredField("localVariables");
		localVariablesField.setAccessible(true);
		variablesField = Variables.class.getDeclaredField("variables");
		variablesField.setAccessible(true);
	}

	@After
	@SuppressWarnings("unchecked")
	public void clearLocals() throws ReflectiveOperationException {
		Map<Event, VariablesMap> locals = (Map<Event, VariablesMap>) localVariablesField.get(null);
		locals.clear();
		VariablesMap global = (VariablesMap) variablesField.get(null);
		global.hashMap.clear();
		global.treeMap.clear();
	}

	@SuppressWarnings("unchecked")
	private VariablesMap inject(Event event, String name, Object value) throws ReflectiveOperationException {
		Map<Event, VariablesMap> locals = (Map<Event, VariablesMap>) localVariablesField.get(null);
		VariablesMap map = locals.computeIfAbsent(event, e -> new VariablesMap());
		map.setVariable(name, value);
		return map;
	}

	@Test
	public void findLocalExecutionOwnerReturnsScalarEntity() throws ReflectiveOperationException {
		Event event = ContextlessEvent.get();
		Entity entity = EasyMock.niceMock(Entity.class);
		VariablesMap map = inject(event, "player", entity);

		assertSame(entity, Variables.findLocalExecutionOwner(map).orElse(null));
	}

	@Test
	public void findLocalExecutionOwnerReturnsListEntity() throws ReflectiveOperationException {
		Event event = ContextlessEvent.get();
		Entity entity = EasyMock.niceMock(Entity.class);
		VariablesMap map = inject(event, "targets::1", entity);

		assertSame(entity, Variables.findLocalExecutionOwner(map).orElse(null));
	}

	@Test
	public void findLocalExecutionOwnerPrefersEntityOverBlock() throws ReflectiveOperationException {
		Event event = ContextlessEvent.get();
		Block block = EasyMock.niceMock(Block.class);
		Entity entity = EasyMock.niceMock(Entity.class);
		VariablesMap map = inject(event, "block", block);
		map.setVariable("player", entity);

		assertSame(entity, Variables.findLocalExecutionOwner(map).orElse(null));
	}

	@Test
	public void findLocalExecutionOwnerPrefersBlockOverLocation() throws ReflectiveOperationException {
		Event event = ContextlessEvent.get();
		Block block = EasyMock.niceMock(Block.class);
		Location location = new Location(null, 1, 2, 3);
		VariablesMap map = inject(event, "block", block);
		map.setVariable("location", location);

		assertSame(block, Variables.findLocalExecutionOwner(map).orElse(null));
	}

	@Test
	public void findLocalExecutionOwnerReturnsEmptyForNullMap() {
		assertFalse(Variables.findLocalExecutionOwner(null).isPresent());
	}

	@Test
	public void findExecutionOwnerReturnsScalarEntity() {
		Entity entity = EasyMock.niceMock(Entity.class);
		Optional<Object> owner = Variables.findExecutionOwner(entity);
		assertSame(entity, owner.orElse(null));
	}

	@Test
	public void findExecutionOwnerReturnsScalarBlock() {
		Block block = EasyMock.niceMock(Block.class);
		Optional<Object> owner = Variables.findExecutionOwner(block);
		assertSame(block, owner.orElse(null));
	}

	@Test
	public void findExecutionOwnerReturnsScalarLocation() {
		Location location = new Location(null, 1, 2, 3);
		Optional<Object> owner = Variables.findExecutionOwner(location);
		assertSame(location, owner.orElse(null));
	}

	@Test
	public void findExecutionOwnerPriorityEntityOverBlockOverLocation() {
		Block block = EasyMock.niceMock(Block.class);
		Location location = new Location(null, 1, 2, 3);
		Entity entity = EasyMock.niceMock(Entity.class);

		Object[] values = new Object[] {block, entity, location};
		Optional<Object> owner = Variables.findExecutionOwner(values);
		assertSame(entity, owner.orElse(null));
	}

	@Test
	public void findExecutionOwnerScansArray() {
		Block block = EasyMock.niceMock(Block.class);
		Object[] array = new Object[] {"scalar", 42, block};
		Optional<Object> owner = Variables.findExecutionOwner(array);
		assertSame(block, owner.orElse(null));
	}

	@Test
	public void findExecutionOwnerScansIterable() {
		Block block = EasyMock.niceMock(Block.class);
		List<Object> list = Arrays.asList("scalar", 1, block);
		Optional<Object> owner = Variables.findExecutionOwner(list);
		assertSame(block, owner.orElse(null));
	}

	@Test
	public void findExecutionOwnerScansNestedMap() {
		Entity entity = EasyMock.niceMock(Entity.class);
		Map<String, Object> nested = new HashMap<>();
		nested.put("inner", entity);
		Optional<Object> owner = Variables.findExecutionOwner(nested);
		assertSame(entity, owner.orElse(null));
	}

	@Test
	public void findExecutionOwnerScansKeyedValuesMap() {
		Block block = EasyMock.niceMock(Block.class);
		Map<String, Object> keyed = new LinkedHashMap<>();
		keyed.put("first", "scalar");
		keyed.put("second", block);
		Optional<Object> owner = Variables.findExecutionOwner(keyed);
		assertSame(block, owner.orElse(null));
	}

	@Test
	public void findExecutionOwnerReturnsEmptyForNull() {
		assertFalse(Variables.findExecutionOwner(null).isPresent());
	}

	@Test
	public void findExecutionOwnerReturnsEmptyForEmptyArray() {
		Optional<Object> owner = Variables.findExecutionOwner(new Object[0]);
		assertFalse(owner.isPresent());
	}

}
