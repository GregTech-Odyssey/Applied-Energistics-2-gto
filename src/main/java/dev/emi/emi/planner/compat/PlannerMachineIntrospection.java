package dev.emi.emi.planner.compat;

import dev.emi.emi.api.stack.EmiStack;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.*;

import java.io.InputStream;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

public final class PlannerMachineIntrospection {
	private static final int MAX_DEPTH = 3;
	private static final int MAX_OBJECTS = 160;
	private static final int MAX_CONTAINER_VALUES = 32;
	private static final Set<String> INTERESTING_WORDS = Set.of(
		"recipe", "modifier", "ability", "hatch", "parallel", "thread", "overclock", "acceler",
		"auxiliary", "module", "coil", "laser", "tier", "machine", "definition", "pattern", "trait",
		"part", "multi", "temperature", "glass", "casing", "energy"
	);
	private static final Set<String> SAFE_METHODS = Set.of(
		"getDefinition", "getMachineDefinition", "getRecipeModifier", "getRecipeModifiers", "getRecipeTypes",
		"getAbilities", "getTraits", "getPattern", "getTier", "getPartAbility", "getMachineSupplier"
	);
	private static final Set<String> PROBE_WORDS = Set.of(
		"recipe", "modifier", "parallel", "duration", "time", "speed", "multiplier", "boost", "production",
		"output", "mode", "tier", "record", "resonance", "flower", "steam", "circuit", "machine", "definition",
		"supplier", "tooltip", "progress", "count", "efficiency", "lambda"
	);
	private static final Set<String> LAMBDA_HOST_WORDS = Set.of(
		"lambda$", "recipe", "modifier", "parallel", "duration", "time", "speed", "multiplier", "boost",
		"production", "output", "mode", "tier", "record", "resonance", "flower", "steam", "circuit", "efficiency"
	);
	private static final Set<String> IDENTITY_METHODS = Set.of(
		"getId", "getName", "getRegistryName", "getLocation", "location"
	);

	private PlannerMachineIntrospection() {
	}

	public static Snapshot inspect(EmiStack stack) {
		if (stack == null || stack.isEmpty()) {
			return Snapshot.EMPTY;
		}
		Set<String> tokens = new LinkedHashSet<>();
		List<String> strings = new ArrayList<>();
		Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
		ArrayDeque<Node> queue = new ArrayDeque<>();
		try {
			queue.add(new Node(stack.getKey(), 0));
		} catch (Throwable ignored) {
		}
		int count = 0;
		while (!queue.isEmpty() && count < MAX_OBJECTS) {
			Node node = queue.removeFirst();
			Object value = node.value;
			if (value == null || visited.contains(value)) {
				continue;
			}
			visited.add(value);
			count++;
			collectValue(value, node.depth, tokens, strings, queue);
		}
		return new Snapshot(Set.copyOf(tokens), List.copyOf(strings));
	}

	private static void collectValue(Object value, int depth, Set<String> tokens, List<String> strings, ArrayDeque<Node> queue) {
		Class<?> type = value.getClass();
		addToken(tokens, type.getName());
		addToken(tokens, type.getSimpleName());
		if (value instanceof CharSequence sequence) {
			addString(strings, sequence.toString());
			return;
		}
		if (value instanceof Enum<?> enumeration) {
			addToken(tokens, enumeration.name());
			addString(strings, enumeration.name());
			return;
		}
		if (isRecipeIdentityCarrier(type)) {
			collectRecipeIdentity(value, tokens, strings);
		}
		if (isLeaf(type) || depth >= MAX_DEPTH) {
			return;
		}
		if (type.isArray()) {
			int length = Math.min(Array.getLength(value), MAX_CONTAINER_VALUES);
			for (int i = 0; i < length; i++) {
				queue.addLast(new Node(Array.get(value, i), depth + 1));
			}
			return;
		}
		if (value instanceof Iterable<?> iterable) {
			int seen = 0;
			for (Object element : iterable) {
				if (seen++ >= MAX_CONTAINER_VALUES) {
					break;
				}
				queue.addLast(new Node(element, depth + 1));
			}
		}
		if (value instanceof Map<?, ?> map) {
			int seen = 0;
			try {
				for (Map.Entry<?, ?> entry : map.entrySet()) {
					if (seen++ >= MAX_CONTAINER_VALUES) {
						break;
					}
					queue.addLast(new Node(entry.getKey(), depth + 1));
					queue.addLast(new Node(entry.getValue(), depth + 1));
				}
			} catch (Throwable ignored) {
			}
		}
		for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
			for (Field field : current.getDeclaredFields()) {
				addToken(tokens, field.getName());
				if (Modifier.isStatic(field.getModifiers()) || !isInteresting(field.getName())) {
					continue;
				}
				try {
					field.setAccessible(true);
					queue.addLast(new Node(field.get(value), depth + 1));
				} catch (Throwable ignored) {
				}
			}
			for (Method method : current.getDeclaredMethods()) {
				addToken(tokens, method.getName());
				if (Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 0
						|| (!SAFE_METHODS.contains(method.getName()) && !isInteresting(method.getName()))) {
					continue;
				}
				if (!SAFE_METHODS.contains(method.getName())) {
					continue;
				}
				try {
					method.setAccessible(true);
					queue.addLast(new Node(method.invoke(value), depth + 1));
				} catch (Throwable ignored) {
				}
			}
		}
	}

	/**
	 * Diagnostic-only structural probe used by the GTO capability audit when a tooltip mechanic is still unresolved.
	 * It never invokes arbitrary machine methods: it records class/member names, reads leaf fields, and follows only
	 * shallow fields plus the same allow-listed getters used by {@link #inspect(EmiStack)}.
	 */
	public static List<String> probe(EmiStack stack, int maxLines) {
		if (stack == null || stack.isEmpty() || maxLines <= 0) {
			return List.of();
		}
		Set<String> lines = new LinkedHashSet<>();
		Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
		Set<Class<?>> probedLambdaHosts = new HashSet<>();
		ArrayDeque<ProbeNode> queue = new ArrayDeque<>();
		try {
			queue.add(new ProbeNode(stack.getKey(), 0, "stack.key"));
		} catch (Throwable ignored) {
		}
		int objects = 0;
		while (!queue.isEmpty() && objects < 420 && lines.size() < maxLines) {
			ProbeNode node = queue.removeFirst();
			Object value = node.value;
			if (value == null || visited.contains(value)) {
				continue;
			}
			visited.add(value);
			objects++;
			Class<?> type = value.getClass();
			boolean modifierSubtree = isRecipeModifierProbeNode(node.path, type);
			boolean supplierSubtree = isMachineSupplierProbeNode(node.path, type);
			boolean lambdaType = isLambdaClass(type);
			boolean prioritySubtree = modifierSubtree || supplierSubtree || lambdaType;
			addProbeLine(lines, maxLines, "class " + node.path + " = " + type.getName());
			if (lambdaType) {
				probeLambdaMetadata(type, lines, maxLines, probedLambdaHosts);
			}
			if (isProbeLeaf(type) || node.depth >= 8) {
				continue;
			}
			if (type.isArray()) {
				int length = Math.min(Array.getLength(value), 32);
				for (int i = 0; i < length; i++) {
					ProbeNode child = new ProbeNode(Array.get(value, i), node.depth + 1, node.path + "[" + i + "]");
					if (prioritySubtree) queue.addFirst(child);
					else queue.addLast(child);
				}
				continue;
			}
			if (value instanceof Iterable<?> iterable) {
				int i = 0;
				for (Object element : iterable) {
					if (i >= 32) break;
					ProbeNode child = new ProbeNode(element, node.depth + 1, node.path + "[" + i++ + "]");
					if (prioritySubtree) queue.addFirst(child);
					else queue.addLast(child);
				}
			}
			if (value instanceof Map<?, ?> map) {
				int i = 0;
				try {
					for (Map.Entry<?, ?> entry : map.entrySet()) {
						if (i >= 24) break;
						ProbeNode key = new ProbeNode(entry.getKey(), node.depth + 1, node.path + ".key" + i);
						ProbeNode val = new ProbeNode(entry.getValue(), node.depth + 1, node.path + ".value" + i);
						if (prioritySubtree) {
							queue.addFirst(val);
							queue.addFirst(key);
						} else {
							queue.addLast(key);
							queue.addLast(val);
						}
						i++;
					}
				} catch (Throwable ignored) {
				}
			}
			for (Class<?> current = type; current != null && current != Object.class && lines.size() < maxLines; current = current.getSuperclass()) {
				boolean projectClass = isProbeProjectClass(current);
				for (Field field : current.getDeclaredFields()) {
					boolean interesting = isProbeInteresting(field.getName()) || isProbeInteresting(field.getType().getName());
					boolean modifierField = modifierSubtree || normalize(field.getName()).contains("modifier");
					boolean supplierField = supplierSubtree || normalize(field.getName()).contains("supplier");
					boolean lambdaCapture = lambdaType && !Modifier.isStatic(field.getModifiers());
					boolean shallowProject = node.depth <= 1 && projectClass;
					if (!interesting && !modifierField && !supplierField && !lambdaCapture && !shallowProject) continue;
					try {
						field.setAccessible(true);
						Object fieldValue = Modifier.isStatic(field.getModifiers()) ? field.get(null) : field.get(value);
						String label = current.getSimpleName() + "#" + field.getName();
						if (fieldValue == null) {
							if (interesting || modifierField || supplierField || lambdaCapture) addProbeLine(lines, maxLines, label + " = null");
						} else if (isProbeLeaf(fieldValue.getClass())) {
							if (interesting || modifierField || supplierField || lambdaCapture || Modifier.isStatic(field.getModifiers())) {
								addProbeLine(lines, maxLines, label + " = " + compactProbeValue(fieldValue));
							}
						} else if (!Modifier.isStatic(field.getModifiers())) {
							if (interesting || modifierField || supplierField || lambdaCapture) {
								addProbeLine(lines, maxLines, label + " -> " + fieldValue.getClass().getName());
							}
							ProbeNode child = new ProbeNode(fieldValue, node.depth + 1, node.path + "." + field.getName());
							if (normalize(field.getName()).contains("modifier") || normalize(field.getName()).contains("supplier") || prioritySubtree) queue.addFirst(child);
							else queue.addLast(child);
						}
					} catch (Throwable ignored) {
					}
				}
				for (Method method : current.getDeclaredMethods()) {
					boolean interestingMethod = isProbeInteresting(method.getName()) || prioritySubtree;
					if (interestingMethod) {
						addProbeLine(lines, maxLines, "method " + current.getSimpleName() + "#" + method.getName()
							+ probeParameterTypes(method) + " -> " + method.getReturnType().getName());
					}
					if (Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 0 || !SAFE_METHODS.contains(method.getName())) {
						continue;
					}
					try {
						method.setAccessible(true);
						Object returned = method.invoke(value);
						if (returned != null) {
							ProbeNode child = new ProbeNode(returned, node.depth + 1, node.path + "." + method.getName() + "()");
							String methodName = normalize(method.getName());
							if (methodName.contains("modifier") || methodName.contains("supplier") || prioritySubtree) queue.addFirst(child);
							else queue.addLast(child);
						}
					} catch (Throwable ignored) {
					}
				}
			}
		}
		return List.copyOf(lines);
	}

	/**
	 * Diagnostic-only bytecode probe for the tiny set of machines whose public tooltip and runtime object graph still
	 * do not expose enough information to reproduce their mechanic. It locates the GTO data-registration class from
	 * the machine supplier, finds the exact static field that owns this definition, and disassembles only that field's
	 * initializer plus the lambda/static-field targets referenced by it. No game methods are invoked by this probe.
	 */
	public static List<String> bytecodeProbe(EmiStack stack, int maxLines) {
		if (stack == null || stack.isEmpty() || maxLines <= 0) {
			return List.of();
		}
		Set<String> lines = new LinkedHashSet<>();
		try {
			Object key = stack.getKey();
			Object definition = invokeProbeGetter(key, "getDefinition");
			if (definition == null) {
				addProbeLine(lines, maxLines, "bytecode: definition unavailable");
				return List.copyOf(lines);
			}
			Object machineSupplier = readProbeField(definition, "machineSupplier");
			if (machineSupplier == null) {
				machineSupplier = invokeProbeGetter(definition, "getMachineSupplier");
			}
			Class<?> registrationHost = findRegistrationHost(machineSupplier,
				Collections.newSetFromMap(new IdentityHashMap<>()), 0);
			if (registrationHost == null) {
				addProbeLine(lines, maxLines, "bytecode: GTO registration host unavailable");
				return List.copyOf(lines);
			}
			String definitionField = findDefinitionField(registrationHost, definition);
			addProbeLine(lines, maxLines, "bytecode: registration-host = " + registrationHost.getName());
			addProbeLine(lines, maxLines, "bytecode: definition-field = " + (definitionField == null ? "<unknown>" : definitionField));
			if (definitionField == null) {
				return List.copyOf(lines);
			}
			ClassLoader loader = registrationHost.getClassLoader();
			BytecodeProbeContext context = new BytecodeProbeContext(lines, maxLines, loader);
			probeStaticFieldInitializer(context, internalName(registrationHost), definitionField, 0);
			for (String candidate : List.copyOf(context.machineClasses)) {
				probeMachineClass(context, candidate);
				if (lines.size() >= maxLines) break;
			}
		} catch (Throwable throwable) {
			addProbeLine(lines, maxLines, "bytecode-error: " + throwable.getClass().getSimpleName()
				+ (throwable.getMessage() == null ? "" : ": " + throwable.getMessage()));
		}
		return List.copyOf(lines);
	}

	public static List<String> formulaProbe(EmiStack stack, int maxLines) {
		if (stack == null || stack.isEmpty() || maxLines <= 0) {
			return List.of();
		}
		Set<String> lines = new LinkedHashSet<>();
		try {
			Object key = stack.getKey();
			Object definition = invokeProbeGetter(key, "getDefinition");
			if (definition == null) {
				addProbeLine(lines, maxLines, "formula: definition unavailable");
				return List.copyOf(lines);
			}
			Object machineSupplier = readProbeField(definition, "machineSupplier");
			if (machineSupplier == null) {
				machineSupplier = invokeProbeGetter(definition, "getMachineSupplier");
			}
			Class<?> registrationHost = findRegistrationHost(machineSupplier,
				Collections.newSetFromMap(new IdentityHashMap<>()), 0);
			if (registrationHost == null) {
				addProbeLine(lines, maxLines, "formula: registration host unavailable");
				return List.copyOf(lines);
			}
			String definitionField = findDefinitionField(registrationHost, definition);
			addProbeLine(lines, maxLines, "formula: registration-host = " + registrationHost.getName());
			addProbeLine(lines, maxLines, "formula: definition-field = " + (definitionField == null ? "<unknown>" : definitionField));
			if (definitionField == null) {
				return List.copyOf(lines);
			}
			ClassLoader loader = registrationHost.getClassLoader();
			Set<String> discoveryLines = new LinkedHashSet<>();
			BytecodeProbeContext discovery = new BytecodeProbeContext(discoveryLines, 1200, loader);
			probeStaticFieldInitializer(discovery, internalName(registrationHost), definitionField, 0);
			if (discovery.machineClasses.isEmpty()) {
				addProbeLine(lines, maxLines, "formula: machine class unavailable");
				return List.copyOf(lines);
			}
			for (String owner : discovery.machineClasses) {
				if (lines.size() >= maxLines) break;
				addProbeLine(lines, maxLines, "formula: machine-class = " + owner.replace('/', '.'));
				probeFormulaFields(loader, owner, lines, maxLines, new HashSet<>());
				probeFormulaMethods(loader, owner, lines, maxLines, new HashSet<>());
			}
		} catch (Throwable throwable) {
			addProbeLine(lines, maxLines, "formula-error: " + throwable.getClass().getSimpleName()
				+ (throwable.getMessage() == null ? "" : ": " + throwable.getMessage()));
		}
		return List.copyOf(lines);
	}

	public static Number staticNumericField(EmiStack stack, String fieldName) {
		if (stack == null || stack.isEmpty() || fieldName == null || fieldName.isBlank()) {
			return null;
		}
		try {
			Object definition = invokeProbeGetter(stack.getKey(), "getDefinition");
			if (definition == null) return null;
			Object machineSupplier = readProbeField(definition, "machineSupplier");
			if (machineSupplier == null) machineSupplier = invokeProbeGetter(definition, "getMachineSupplier");
			Class<?> registrationHost = findRegistrationHost(machineSupplier,
				Collections.newSetFromMap(new IdentityHashMap<>()), 0);
			if (registrationHost == null) return null;
			String definitionField = findDefinitionField(registrationHost, definition);
			if (definitionField == null) return null;
			ClassLoader loader = registrationHost.getClassLoader();
			Set<String> discoveryLines = new LinkedHashSet<>();
			BytecodeProbeContext discovery = new BytecodeProbeContext(discoveryLines, 5000, loader);
			probeStaticFieldInitializer(discovery, internalName(registrationHost), definitionField, 0);
			for (String owner : discovery.machineClasses) {
				Class<?> type = Class.forName(owner.replace('/', '.'), false, loader);
				for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
					try {
						Field field = current.getDeclaredField(fieldName);
						if (!Modifier.isStatic(field.getModifiers())) continue;
						field.setAccessible(true);
						Object value = field.get(null);
						if (value instanceof Number number) return number;
					} catch (NoSuchFieldException ignored) {
					}
				}
			}
		} catch (Throwable ignored) {
		}
		return null;
	}


	public static List<Long> shortLongMethodTable(EmiStack stack, String methodName, int minInclusive, int maxInclusive) {
		if (stack == null || stack.isEmpty() || methodName == null || methodName.isBlank() || maxInclusive < minInclusive) {
			return List.of();
		}
		try {
			Object definition = invokeProbeGetter(stack.getKey(), "getDefinition");
			if (definition == null) return List.of();
			Object machineSupplier = readProbeField(definition, "machineSupplier");
			if (machineSupplier == null) machineSupplier = invokeProbeGetter(definition, "getMachineSupplier");
			Class<?> registrationHost = findRegistrationHost(machineSupplier,
				Collections.newSetFromMap(new IdentityHashMap<>()), 0);
			if (registrationHost == null) return List.of();
			String definitionField = findDefinitionField(registrationHost, definition);
			if (definitionField == null) return List.of();
			ClassLoader loader = registrationHost.getClassLoader();
			Set<String> discoveryLines = new LinkedHashSet<>();
			BytecodeProbeContext discovery = new BytecodeProbeContext(discoveryLines, 5000, loader);
			probeStaticFieldInitializer(discovery, internalName(registrationHost), definitionField, 0);
			for (String owner : discovery.machineClasses) {
				MethodNode method = findShortNumericMethod(loader, owner, methodName, "J");
				if (method == null) continue;
				List<Long> values = new ArrayList<>(maxInclusive - minInclusive + 1);
				for (int input = minInclusive; input <= maxInclusive; input++) {
					Number value = evaluatePureShortNumericMethod(method, (short) input);
					if (value == null) return List.of();
					values.add(value.longValue());
				}
				return List.copyOf(values);
			}
		} catch (Throwable ignored) {
		}
		return List.of();
	}

	private static MethodNode findShortNumericMethod(ClassLoader loader, String owner, String methodName, String returnDescriptor) {
		Set<String> visited = new HashSet<>();
		String current = owner;
		while (current != null && visited.add(current)) {
			ClassNode node = readClassNode(loader, current);
			if (node == null) return null;
			for (MethodNode method : node.methods) {
				if (methodName.equals(method.name) && ("(S)" + returnDescriptor).equals(method.desc)) return method;
			}
			current = node.superName;
		}
		return null;
	}

	private static Number evaluatePureShortNumericMethod(MethodNode method, short argument) {
		if (method == null || method.instructions == null) return null;
		AbstractInsnNode[] code = method.instructions.toArray();
		Map<AbstractInsnNode, Integer> indices = new IdentityHashMap<>();
		for (int i = 0; i < code.length; i++) indices.put(code[i], i);
		List<Number> stack = new ArrayList<>();
		int pc = 0;
		int steps = 0;
		while (pc >= 0 && pc < code.length && steps++ < 2000) {
			AbstractInsnNode insn = code[pc];
			int opcode = insn.getOpcode();
			if (opcode < 0) {
				pc++;
				continue;
			}
			switch (opcode) {
				case Opcodes.NOP -> pc++;
				case Opcodes.ICONST_M1 -> { stack.add(-1); pc++; }
				case Opcodes.ICONST_0 -> { stack.add(0); pc++; }
				case Opcodes.ICONST_1 -> { stack.add(1); pc++; }
				case Opcodes.ICONST_2 -> { stack.add(2); pc++; }
				case Opcodes.ICONST_3 -> { stack.add(3); pc++; }
				case Opcodes.ICONST_4 -> { stack.add(4); pc++; }
				case Opcodes.ICONST_5 -> { stack.add(5); pc++; }
				case Opcodes.LCONST_0 -> { stack.add(0L); pc++; }
				case Opcodes.LCONST_1 -> { stack.add(1L); pc++; }
				case Opcodes.FCONST_0 -> { stack.add(0.0F); pc++; }
				case Opcodes.FCONST_1 -> { stack.add(1.0F); pc++; }
				case Opcodes.FCONST_2 -> { stack.add(2.0F); pc++; }
				case Opcodes.DCONST_0 -> { stack.add(0.0D); pc++; }
				case Opcodes.DCONST_1 -> { stack.add(1.0D); pc++; }
				case Opcodes.BIPUSH, Opcodes.SIPUSH -> { stack.add(((IntInsnNode) insn).operand); pc++; }
				case Opcodes.LDC -> {
					Object value = ((LdcInsnNode) insn).cst;
					if (!(value instanceof Number number)) return null;
					stack.add(number);
					pc++;
				}
				case Opcodes.ILOAD -> {
					int var = ((VarInsnNode) insn).var;
					if (var != 1) return null;
					stack.add((int) argument);
					pc++;
				}
				case Opcodes.I2L -> { stack.add((long) popInt(stack)); pc++; }
				case Opcodes.I2F -> { stack.add((float) popInt(stack)); pc++; }
				case Opcodes.I2D -> { stack.add((double) popInt(stack)); pc++; }
				case Opcodes.L2I -> { stack.add((int) popLong(stack)); pc++; }
				case Opcodes.L2F -> { stack.add((float) popLong(stack)); pc++; }
				case Opcodes.L2D -> { stack.add((double) popLong(stack)); pc++; }
				case Opcodes.F2I -> { stack.add((int) popFloat(stack)); pc++; }
				case Opcodes.F2L -> { stack.add((long) popFloat(stack)); pc++; }
				case Opcodes.F2D -> { stack.add((double) popFloat(stack)); pc++; }
				case Opcodes.D2I -> { stack.add((int) popDouble(stack)); pc++; }
				case Opcodes.D2L -> { stack.add((long) popDouble(stack)); pc++; }
				case Opcodes.D2F -> { stack.add((float) popDouble(stack)); pc++; }
				case Opcodes.IADD -> { int b = popInt(stack), a = popInt(stack); stack.add(a + b); pc++; }
				case Opcodes.ISUB -> { int b = popInt(stack), a = popInt(stack); stack.add(a - b); pc++; }
				case Opcodes.IMUL -> { int b = popInt(stack), a = popInt(stack); stack.add(a * b); pc++; }
				case Opcodes.IDIV -> { int b = popInt(stack), a = popInt(stack); if (b == 0) return null; stack.add(a / b); pc++; }
				case Opcodes.LADD -> { long b = popLong(stack), a = popLong(stack); stack.add(a + b); pc++; }
				case Opcodes.LSUB -> { long b = popLong(stack), a = popLong(stack); stack.add(a - b); pc++; }
				case Opcodes.LMUL -> { long b = popLong(stack), a = popLong(stack); stack.add(a * b); pc++; }
				case Opcodes.LDIV -> { long b = popLong(stack), a = popLong(stack); if (b == 0L) return null; stack.add(a / b); pc++; }
				case Opcodes.FADD -> { float b = popFloat(stack), a = popFloat(stack); stack.add(a + b); pc++; }
				case Opcodes.FSUB -> { float b = popFloat(stack), a = popFloat(stack); stack.add(a - b); pc++; }
				case Opcodes.FMUL -> { float b = popFloat(stack), a = popFloat(stack); stack.add(a * b); pc++; }
				case Opcodes.FDIV -> { float b = popFloat(stack), a = popFloat(stack); stack.add(a / b); pc++; }
				case Opcodes.DADD -> { double b = popDouble(stack), a = popDouble(stack); stack.add(a + b); pc++; }
				case Opcodes.DSUB -> { double b = popDouble(stack), a = popDouble(stack); stack.add(a - b); pc++; }
				case Opcodes.DMUL -> { double b = popDouble(stack), a = popDouble(stack); stack.add(a * b); pc++; }
				case Opcodes.DDIV -> { double b = popDouble(stack), a = popDouble(stack); stack.add(a / b); pc++; }
				case Opcodes.IFEQ, Opcodes.IFNE, Opcodes.IFLT, Opcodes.IFGE, Opcodes.IFGT, Opcodes.IFLE -> {
					int value = popInt(stack);
					boolean take = switch (opcode) {
						case Opcodes.IFEQ -> value == 0;
						case Opcodes.IFNE -> value != 0;
						case Opcodes.IFLT -> value < 0;
						case Opcodes.IFGE -> value >= 0;
						case Opcodes.IFGT -> value > 0;
						default -> value <= 0;
					};
					pc = take ? jumpTarget(indices, (JumpInsnNode) insn) : pc + 1;
				}
				case Opcodes.IF_ICMPEQ, Opcodes.IF_ICMPNE, Opcodes.IF_ICMPLT, Opcodes.IF_ICMPGE, Opcodes.IF_ICMPGT, Opcodes.IF_ICMPLE -> {
					int b = popInt(stack), a = popInt(stack);
					boolean take = switch (opcode) {
						case Opcodes.IF_ICMPEQ -> a == b;
						case Opcodes.IF_ICMPNE -> a != b;
						case Opcodes.IF_ICMPLT -> a < b;
						case Opcodes.IF_ICMPGE -> a >= b;
						case Opcodes.IF_ICMPGT -> a > b;
						default -> a <= b;
					};
					pc = take ? jumpTarget(indices, (JumpInsnNode) insn) : pc + 1;
				}
				case Opcodes.GOTO -> pc = jumpTarget(indices, (JumpInsnNode) insn);
				case Opcodes.IRETURN -> { return popInt(stack); }
				case Opcodes.LRETURN -> { return popLong(stack); }
				case Opcodes.FRETURN -> { return popFloat(stack); }
				case Opcodes.DRETURN -> { return popDouble(stack); }
				default -> { return null; }
			}
		}
		return null;
	}

	private static int jumpTarget(Map<AbstractInsnNode, Integer> indices, JumpInsnNode jump) {
		Integer target = indices.get(jump.label);
		return target == null ? -1 : target;
	}

	private static Number popNumber(List<Number> stack) {
		if (stack.isEmpty()) throw new IllegalStateException("empty bytecode stack");
		return stack.remove(stack.size() - 1);
	}

	private static int popInt(List<Number> stack) { return popNumber(stack).intValue(); }
	private static long popLong(List<Number> stack) { return popNumber(stack).longValue(); }
	private static float popFloat(List<Number> stack) { return popNumber(stack).floatValue(); }
	private static double popDouble(List<Number> stack) { return popNumber(stack).doubleValue(); }

	private static void probeFormulaFields(ClassLoader loader, String owner, Set<String> lines, int maxLines, Set<String> visited) {
		if (owner == null || lines.size() >= maxLines || !visited.add(owner)) return;
		ClassNode node = readClassNode(loader, owner);
		if (node == null) return;
		try {
			Class<?> type = Class.forName(owner.replace('/', '.'), false, loader);
			for (Field field : type.getDeclaredFields()) {
				if (lines.size() >= maxLines) return;
				if (!Modifier.isStatic(field.getModifiers()) || !isFormulaFieldName(field.getName())) continue;
				try {
					field.setAccessible(true);
					Object value = field.get(null);
					if (value == null || isProbeLeaf(value.getClass())) {
						addProbeLine(lines, maxLines, "formula field " + type.getName() + "#" + field.getName() + " = " + compactProbeValue(value));
					} else {
						addProbeLine(lines, maxLines, "formula field " + type.getName() + "#" + field.getName() + " -> " + value.getClass().getName());
					}
				} catch (Throwable throwable) {
					addProbeLine(lines, maxLines, "formula field " + type.getName() + "#" + field.getName() + " = <" + throwable.getClass().getSimpleName() + ">");
				}
			}
		} catch (Throwable throwable) {
			addProbeLine(lines, maxLines, "formula fields unavailable for " + owner.replace('/', '.') + ": " + throwable.getClass().getSimpleName());
		}
		if (node.superName != null && node.superName.startsWith("com/gtocore/")) {
			probeFormulaFields(loader, node.superName, lines, maxLines, visited);
		}
		for (String iface : node.interfaces) {
			if (iface.startsWith("com/gtocore/") || iface.startsWith("com/gtolib/")) {
				probeFormulaFields(loader, iface, lines, maxLines, visited);
			}
		}
	}

	private static void probeFormulaMethods(ClassLoader loader, String owner, Set<String> lines, int maxLines, Set<String> visited) {
		if (owner == null || lines.size() >= maxLines || !visited.add(owner)) return;
		ClassNode node = readClassNode(loader, owner);
		if (node == null) return;
		List<MethodNode> methods = new ArrayList<>();
		for (MethodNode method : node.methods) {
			if (isFormulaMethodName(method.name)) methods.add(method);
		}
		methods.sort((a, b) -> Integer.compare(formulaMethodPriority(a.name), formulaMethodPriority(b.name)));
		for (MethodNode method : methods) {
			if (lines.size() >= maxLines) return;
			List<AbstractInsnNode> instructions = meaningfulInstructions(method);
			addProbeLine(lines, maxLines, "formula method " + owner.replace('/', '.') + "#" + method.name + method.desc
				+ " [" + instructions.size() + " instructions]");
			for (int i = 0; i < instructions.size() && lines.size() < maxLines; i++) {
				addProbeLine(lines, maxLines, "  fm " + i + ": " + describeInstruction(instructions.get(i)));
			}
		}
		if (node.superName != null && node.superName.startsWith("com/gtocore/")) {
			probeFormulaMethods(loader, node.superName, lines, maxLines, visited);
		}
	}

	private static int formulaMethodPriority(String name) {
		String lower = normalize(name);
		if (lower.equals("gettimemultiplier")) return 0;
		if (lower.equals("getmaxparallel")) return 1;
		if (lower.equals("gettiereffect")) return 2;
		if (lower.equals("calculateupgraderequirement")) return 3;
		if (lower.equals("upgradeentry")) return 4;
		if (lower.equals("getrealrecipe")) return 5;
		if (lower.equals("addentry")) return 6;
		return 7;
	}

	private static boolean isFormulaFieldName(String name) {
		String lower = normalize(name);
		return lower.contains("product") || lower.contains("output") || lower.contains("duration")
			|| lower.contains("steam") || lower.contains("parallel") || lower.contains("multiply")
			|| lower.contains("multiplier") || lower.contains("tier") || lower.contains("frequency")
			|| lower.contains("effect") || lower.contains("fluctuation");
	}

	private static boolean isFormulaMethodName(String name) {
		String lower = normalize(name);
		return lower.equals("gettimemultiplier") || lower.equals("getmaxparallel") || lower.equals("gettiereffect")
			|| lower.equals("calculateupgraderequirement") || lower.equals("getrealrecipe") || lower.equals("upgradeentry")
			|| lower.equals("addentry") || lower.contains("parallelmultiplier") || lower.contains("durationmultiplier");
	}

	private static Object invokeProbeGetter(Object owner, String name) {
		if (owner == null || name == null) return null;
		for (Class<?> current = owner.getClass(); current != null && current != Object.class; current = current.getSuperclass()) {
			try {
				Method method = current.getDeclaredMethod(name);
				if (method.getParameterCount() != 0 || Modifier.isStatic(method.getModifiers())) continue;
				method.setAccessible(true);
				return method.invoke(owner);
			} catch (NoSuchMethodException ignored) {
			} catch (Throwable ignored) {
				return null;
			}
		}
		return null;
	}

	private static Object readProbeField(Object owner, String name) {
		if (owner == null || name == null) return null;
		for (Class<?> current = owner.getClass(); current != null && current != Object.class; current = current.getSuperclass()) {
			try {
				Field field = current.getDeclaredField(name);
				if (Modifier.isStatic(field.getModifiers())) continue;
				field.setAccessible(true);
				return field.get(owner);
			} catch (NoSuchFieldException ignored) {
			} catch (Throwable ignored) {
				return null;
			}
		}
		return null;
	}

	private static Class<?> findRegistrationHost(Object value, Set<Object> visited, int depth) {
		if (value == null || depth > 5 || visited.contains(value)) return null;
		visited.add(value);
		Class<?> type = value.getClass();
		if (isLambdaClass(type)) {
			try {
				Class<?> host = type.getNestHost();
				if (host != null && host.getName().startsWith("com.gtocore.common.data.machines.")) {
					return host;
				}
			} catch (Throwable ignored) {
			}
		}
		for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
			for (Field field : current.getDeclaredFields()) {
				if (Modifier.isStatic(field.getModifiers())) continue;
				try {
					field.setAccessible(true);
					Object child = field.get(value);
					if (child == null || isProbeLeaf(child.getClass())) continue;
					Class<?> host = findRegistrationHost(child, visited, depth + 1);
					if (host != null) return host;
				} catch (Throwable ignored) {
				}
			}
		}
		return null;
	}

	private static String findDefinitionField(Class<?> host, Object definition) {
		if (host == null || definition == null) return null;
		for (Field field : host.getDeclaredFields()) {
			if (!Modifier.isStatic(field.getModifiers())) continue;
			try {
				field.setAccessible(true);
				if (field.get(null) == definition) return field.getName();
			} catch (Throwable ignored) {
			}
		}
		return null;
	}

	private static void probeStaticFieldInitializer(BytecodeProbeContext context, String owner, String fieldName, int depth) {
		if (context.full() || owner == null || fieldName == null || depth > 4) return;
		String key = owner + "#" + fieldName;
		if (!context.fields.add(key)) return;
		ClassNode node = readClassNode(context.loader, owner);
		if (node == null) {
			context.add("bytecode: class bytes unavailable for " + owner.replace('/', '.'));
			return;
		}
		MethodNode clinit = null;
		for (MethodNode method : node.methods) {
			if ("<clinit>".equals(method.name)) {
				clinit = method;
				break;
			}
		}
		if (clinit == null) {
			context.add("bytecode: no <clinit> for " + owner.replace('/', '.') + "#" + fieldName);
			return;
		}
		List<AbstractInsnNode> instructions = meaningfulInstructions(clinit);
		int target = -1;
		for (int i = 0; i < instructions.size(); i++) {
			AbstractInsnNode insn = instructions.get(i);
			if (insn instanceof FieldInsnNode field && field.getOpcode() == Opcodes.PUTSTATIC
					&& owner.equals(field.owner) && fieldName.equals(field.name)) {
				target = i;
				break;
			}
		}
		if (target < 0) {
			context.add("bytecode: PUTSTATIC not found for " + owner.replace('/', '.') + "#" + fieldName);
			return;
		}
		int start = Math.max(0, target - 180);
		for (int i = target - 1; i >= 0; i--) {
			AbstractInsnNode insn = instructions.get(i);
			if (insn instanceof FieldInsnNode field && field.getOpcode() == Opcodes.PUTSTATIC
					&& owner.equals(field.owner) && looksLikeMachineDefinitionDescriptor(field.desc)) {
				start = i + 1;
				break;
			}
		}
		context.add("bytecode initializer " + owner.replace('/', '.') + "#" + fieldName
			+ " [" + (target - start + 1) + " instructions]");
		List<BytecodeMethodRef> methodRefs = new ArrayList<>();
		List<BytecodeFieldRef> fieldRefs = new ArrayList<>();
		for (int i = start; i <= target && !context.full(); i++) {
			AbstractInsnNode insn = instructions.get(i);
			context.add("  bc " + (i - start) + ": " + describeInstruction(insn));
			collectBytecodeRefs(insn, methodRefs, fieldRefs, context.machineClasses);
		}
		for (BytecodeFieldRef ref : fieldRefs) {
			if (context.full()) break;
			if (isRelevantGtoOwner(ref.owner) && isRelevantStaticField(ref.name)) {
				probeStaticFieldInitializer(context, ref.owner, ref.name, depth + 1);
			}
		}
		for (BytecodeMethodRef ref : methodRefs) {
			if (context.full()) break;
			if (isRelevantGtoOwner(ref.owner)) {
				probeMethod(context, ref, depth + 1);
			}
		}
	}

	private static void probeMethod(BytecodeProbeContext context, BytecodeMethodRef ref, int depth) {
		if (context.full() || ref == null || depth > 5) return;
		String key = ref.owner + "#" + ref.name + ref.desc;
		if (!context.methods.add(key)) return;
		ClassNode node = readClassNode(context.loader, ref.owner);
		if (node == null) return;
		MethodNode target = null;
		for (MethodNode method : node.methods) {
			if (ref.name.equals(method.name) && ref.desc.equals(method.desc)) {
				target = method;
				break;
			}
		}
		if (target == null) return;
		List<AbstractInsnNode> instructions = meaningfulInstructions(target);
		context.add("bytecode method " + ref.owner.replace('/', '.') + "#" + ref.name + ref.desc
			+ " [" + instructions.size() + " instructions]");
		List<BytecodeMethodRef> nestedMethods = new ArrayList<>();
		List<BytecodeFieldRef> nestedFields = new ArrayList<>();
		int limit = Math.min(instructions.size(), 180);
		for (int i = 0; i < limit && !context.full(); i++) {
			AbstractInsnNode insn = instructions.get(i);
			context.add("  bc " + i + ": " + describeInstruction(insn));
			collectBytecodeRefs(insn, nestedMethods, nestedFields, context.machineClasses);
		}
		if (instructions.size() > limit) context.add("  bc ... " + (instructions.size() - limit) + " more instructions");
		for (BytecodeFieldRef field : nestedFields) {
			if (context.full()) break;
			if (isRelevantGtoOwner(field.owner) && isRelevantStaticField(field.name)) {
				probeStaticFieldInitializer(context, field.owner, field.name, depth + 1);
			}
		}
		for (BytecodeMethodRef method : nestedMethods) {
			if (context.full()) break;
			if (isRelevantGtoOwner(method.owner) && shouldFollowBytecodeMethod(method)) {
				probeMethod(context, method, depth + 1);
			}
		}
	}

	private static void probeMachineClass(BytecodeProbeContext context, String owner) {
		if (context.full() || owner == null || !context.classes.add(owner)) return;
		ClassNode node = readClassNode(context.loader, owner);
		if (node == null) return;
		context.add("bytecode machine-class = " + owner.replace('/', '.') + " extends "
			+ (node.superName == null ? "<none>" : node.superName.replace('/', '.')));
		for (org.objectweb.asm.tree.FieldNode field : node.fields) {
			if (context.full()) return;
			if (isProbeInteresting(field.name) || field.value instanceof Number || field.value instanceof Boolean) {
				context.add("  field " + field.name + " " + field.desc + (field.value == null ? "" : " = " + field.value));
			}
		}
		int dumped = 0;
		for (MethodNode method : node.methods) {
			if (context.full() || dumped >= 18) break;
			if ("<clinit>".equals(method.name)) continue;
			if (!isInterestingMachineMethod(method)) continue;
			probeMethod(context, new BytecodeMethodRef(owner, method.name, method.desc), 0);
			dumped++;
		}
	}

	private static boolean isInterestingMachineMethod(MethodNode method) {
		if (method == null) return false;
		if (isProbeInteresting(method.name)) return true;
		for (AbstractInsnNode insn : meaningfulInstructions(method)) {
			if (insn instanceof FieldInsnNode field && isProbeInteresting(field.name)) return true;
			if (insn instanceof MethodInsnNode call && (isProbeInteresting(call.name) || isProbeInteresting(call.owner))) return true;
			if (insn instanceof LdcInsnNode ldc && isMechanicConstant(ldc.cst)) return true;
			if (insn instanceof IntInsnNode value && (value.operand == 4 || value.operand == 8 || value.operand == 64 || value.operand == 256)) return true;
		}
		return false;
	}

	private static boolean isMechanicConstant(Object value) {
		if (!(value instanceof Number number)) return false;
		double d = number.doubleValue();
		return d == 4.0D || d == 8.0D || d == 64.0D || d == 256.0D || d == 0.025D || d == 0.0125D
			|| d == 0.1D || d == 0.9D || d == Long.MAX_VALUE;
	}

	private static void collectBytecodeRefs(AbstractInsnNode insn, List<BytecodeMethodRef> methods,
			List<BytecodeFieldRef> fields, Set<String> machineClasses) {
		if (insn instanceof MethodInsnNode call) {
			methods.add(new BytecodeMethodRef(call.owner, call.name, call.desc));
			if (isGtoMachineClass(call.owner)) machineClasses.add(call.owner);
		} else if (insn instanceof FieldInsnNode field) {
			if (field.getOpcode() == Opcodes.GETSTATIC) fields.add(new BytecodeFieldRef(field.owner, field.name));
			if (isGtoMachineClass(field.owner)) machineClasses.add(field.owner);
		} else if (insn instanceof TypeInsnNode type && type.getOpcode() == Opcodes.NEW) {
			if (isGtoMachineClass(type.desc)) machineClasses.add(type.desc);
		} else if (insn instanceof InvokeDynamicInsnNode indy) {
			for (Object argument : indy.bsmArgs) {
				if (argument instanceof Handle handle) {
					methods.add(new BytecodeMethodRef(handle.getOwner(), handle.getName(), handle.getDesc()));
					if (isGtoMachineClass(handle.getOwner())) machineClasses.add(handle.getOwner());
				}
			}
		}
	}

	private static boolean shouldFollowBytecodeMethod(BytecodeMethodRef ref) {
		String text = normalize(ref.owner + " " + ref.name);
		return ref.name.startsWith("lambda$") || isProbeInteresting(text) || ref.owner.startsWith("com/gtocore/common/machine/");
	}

	private static boolean isRelevantStaticField(String name) {
		String lower = normalize(name);
		return lower.contains("parallel") || lower.contains("modifier") || lower.contains("overclock")
			|| lower.contains("upgrade") || lower.contains("multiplier") || lower.contains("boost");
	}

	private static boolean isRelevantGtoOwner(String owner) {
		return owner != null && (owner.startsWith("com/gtocore/") || owner.startsWith("com/gtolib/")
			|| owner.startsWith("com/gregtechceu/gtceu/api/recipe/modifier/"));
	}

	private static boolean isGtoMachineClass(String owner) {
		return owner != null && owner.startsWith("com/gtocore/common/machine/");
	}

	private static boolean looksLikeMachineDefinitionDescriptor(String desc) {
		String lower = normalize(desc);
		return lower.contains("machinedefinition") || lower.contains("multiblockdefinition");
	}

	private static String internalName(Class<?> type) {
		return type.getName().replace('.', '/');
	}

	private static ClassNode readClassNode(ClassLoader loader, String internalName) {
		if (internalName == null || internalName.isBlank()) return null;
		String resource = internalName + ".class";
		try (InputStream stream = loader == null ? ClassLoader.getSystemResourceAsStream(resource) : loader.getResourceAsStream(resource)) {
			if (stream == null) return null;
			ClassNode node = new ClassNode();
			new ClassReader(stream).accept(node, ClassReader.SKIP_FRAMES);
			return node;
		} catch (Throwable ignored) {
			return null;
		}
	}

	private static List<AbstractInsnNode> meaningfulInstructions(MethodNode method) {
		if (method == null || method.instructions == null) return List.of();
		List<AbstractInsnNode> result = new ArrayList<>();
		for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()) {
			if (insn.getOpcode() >= 0) result.add(insn);
		}
		return result;
	}

	private static String describeInstruction(AbstractInsnNode insn) {
		if (insn == null) return "<null>";
		String opcode = opcodeName(insn.getOpcode());
		if (insn instanceof IntInsnNode value) return opcode + " " + value.operand;
		if (insn instanceof VarInsnNode value) return opcode + " var=" + value.var;
		if (insn instanceof TypeInsnNode value) return opcode + " " + value.desc.replace('/', '.');
		if (insn instanceof FieldInsnNode value) return opcode + " " + value.owner.replace('/', '.') + "#" + value.name + " " + value.desc;
		if (insn instanceof MethodInsnNode value) return opcode + " " + value.owner.replace('/', '.') + "#" + value.name + value.desc;
		if (insn instanceof InvokeDynamicInsnNode value) {
			StringBuilder builder = new StringBuilder(opcode).append(' ').append(value.name).append(value.desc)
				.append(" bsm=").append(describeHandle(value.bsm));
			for (Object argument : value.bsmArgs) {
				builder.append(" arg=");
				if (argument instanceof Handle handle) builder.append(describeHandle(handle));
				else if (argument instanceof Type type) builder.append(type.getDescriptor());
				else builder.append(compactProbeValue(argument));
			}
			return builder.toString();
		}
		if (insn instanceof LdcInsnNode value) return opcode + " " + compactProbeValue(value.cst);
		if (insn instanceof IincInsnNode value) return opcode + " var=" + value.var + " inc=" + value.incr;
		if (insn instanceof JumpInsnNode) return opcode + " <label>";
		if (insn instanceof TableSwitchInsnNode value) return opcode + " min=" + value.min + " max=" + value.max;
		if (insn instanceof LookupSwitchInsnNode value) return opcode + " keys=" + value.keys;
		if (insn instanceof MultiANewArrayInsnNode value) return opcode + " " + value.desc + " dims=" + value.dims;
		if (insn instanceof InsnNode) return opcode;
		return opcode + " " + insn.getClass().getSimpleName();
	}

	private static String describeHandle(Handle handle) {
		if (handle == null) return "<null>";
		return handle.getOwner().replace('/', '.') + "#" + handle.getName() + handle.getDesc() + "[tag=" + handle.getTag() + "]";
	}

	private static String opcodeName(int opcode) {
		return switch (opcode) {
			case Opcodes.NOP -> "NOP";
			case Opcodes.ACONST_NULL -> "ACONST_NULL";
			case Opcodes.ICONST_M1 -> "ICONST_M1";
			case Opcodes.ICONST_0 -> "ICONST_0";
			case Opcodes.ICONST_1 -> "ICONST_1";
			case Opcodes.ICONST_2 -> "ICONST_2";
			case Opcodes.ICONST_3 -> "ICONST_3";
			case Opcodes.ICONST_4 -> "ICONST_4";
			case Opcodes.ICONST_5 -> "ICONST_5";
			case Opcodes.LCONST_0 -> "LCONST_0";
			case Opcodes.LCONST_1 -> "LCONST_1";
			case Opcodes.FCONST_0 -> "FCONST_0";
			case Opcodes.FCONST_1 -> "FCONST_1";
			case Opcodes.FCONST_2 -> "FCONST_2";
			case Opcodes.DCONST_0 -> "DCONST_0";
			case Opcodes.DCONST_1 -> "DCONST_1";
			case Opcodes.BIPUSH -> "BIPUSH";
			case Opcodes.SIPUSH -> "SIPUSH";
			case Opcodes.LDC -> "LDC";
			case Opcodes.ILOAD -> "ILOAD";
			case Opcodes.LLOAD -> "LLOAD";
			case Opcodes.FLOAD -> "FLOAD";
			case Opcodes.DLOAD -> "DLOAD";
			case Opcodes.ALOAD -> "ALOAD";
			case Opcodes.ISTORE -> "ISTORE";
			case Opcodes.LSTORE -> "LSTORE";
			case Opcodes.FSTORE -> "FSTORE";
			case Opcodes.DSTORE -> "DSTORE";
			case Opcodes.ASTORE -> "ASTORE";
			case Opcodes.POP -> "POP";
			case Opcodes.POP2 -> "POP2";
			case Opcodes.DUP -> "DUP";
			case Opcodes.DUP_X1 -> "DUP_X1";
			case Opcodes.DUP_X2 -> "DUP_X2";
			case Opcodes.DUP2 -> "DUP2";
			case Opcodes.DUP2_X1 -> "DUP2_X1";
			case Opcodes.DUP2_X2 -> "DUP2_X2";
			case Opcodes.SWAP -> "SWAP";
			case Opcodes.IADD -> "IADD";
			case Opcodes.LADD -> "LADD";
			case Opcodes.FADD -> "FADD";
			case Opcodes.DADD -> "DADD";
			case Opcodes.ISUB -> "ISUB";
			case Opcodes.LSUB -> "LSUB";
			case Opcodes.FSUB -> "FSUB";
			case Opcodes.DSUB -> "DSUB";
			case Opcodes.IMUL -> "IMUL";
			case Opcodes.LMUL -> "LMUL";
			case Opcodes.FMUL -> "FMUL";
			case Opcodes.DMUL -> "DMUL";
			case Opcodes.IDIV -> "IDIV";
			case Opcodes.LDIV -> "LDIV";
			case Opcodes.FDIV -> "FDIV";
			case Opcodes.DDIV -> "DDIV";
			case Opcodes.IREM -> "IREM";
			case Opcodes.LREM -> "LREM";
			case Opcodes.FREM -> "FREM";
			case Opcodes.DREM -> "DREM";
			case Opcodes.INEG -> "INEG";
			case Opcodes.LNEG -> "LNEG";
			case Opcodes.FNEG -> "FNEG";
			case Opcodes.DNEG -> "DNEG";
			case Opcodes.ISHL -> "ISHL";
			case Opcodes.LSHL -> "LSHL";
			case Opcodes.ISHR -> "ISHR";
			case Opcodes.LSHR -> "LSHR";
			case Opcodes.IUSHR -> "IUSHR";
			case Opcodes.LUSHR -> "LUSHR";
			case Opcodes.IAND -> "IAND";
			case Opcodes.LAND -> "LAND";
			case Opcodes.IOR -> "IOR";
			case Opcodes.LOR -> "LOR";
			case Opcodes.IXOR -> "IXOR";
			case Opcodes.LXOR -> "LXOR";
			case Opcodes.IINC -> "IINC";
			case Opcodes.I2L -> "I2L";
			case Opcodes.I2F -> "I2F";
			case Opcodes.I2D -> "I2D";
			case Opcodes.L2I -> "L2I";
			case Opcodes.L2F -> "L2F";
			case Opcodes.L2D -> "L2D";
			case Opcodes.F2I -> "F2I";
			case Opcodes.F2L -> "F2L";
			case Opcodes.F2D -> "F2D";
			case Opcodes.D2I -> "D2I";
			case Opcodes.D2L -> "D2L";
			case Opcodes.D2F -> "D2F";
			case Opcodes.LCMP -> "LCMP";
			case Opcodes.FCMPL -> "FCMPL";
			case Opcodes.FCMPG -> "FCMPG";
			case Opcodes.DCMPL -> "DCMPL";
			case Opcodes.DCMPG -> "DCMPG";
			case Opcodes.IFEQ -> "IFEQ";
			case Opcodes.IFNE -> "IFNE";
			case Opcodes.IFLT -> "IFLT";
			case Opcodes.IFGE -> "IFGE";
			case Opcodes.IFGT -> "IFGT";
			case Opcodes.IFLE -> "IFLE";
			case Opcodes.IF_ICMPEQ -> "IF_ICMPEQ";
			case Opcodes.IF_ICMPNE -> "IF_ICMPNE";
			case Opcodes.IF_ICMPLT -> "IF_ICMPLT";
			case Opcodes.IF_ICMPGE -> "IF_ICMPGE";
			case Opcodes.IF_ICMPGT -> "IF_ICMPGT";
			case Opcodes.IF_ICMPLE -> "IF_ICMPLE";
			case Opcodes.IF_ACMPEQ -> "IF_ACMPEQ";
			case Opcodes.IF_ACMPNE -> "IF_ACMPNE";
			case Opcodes.GOTO -> "GOTO";
			case Opcodes.TABLESWITCH -> "TABLESWITCH";
			case Opcodes.LOOKUPSWITCH -> "LOOKUPSWITCH";
			case Opcodes.IRETURN -> "IRETURN";
			case Opcodes.LRETURN -> "LRETURN";
			case Opcodes.FRETURN -> "FRETURN";
			case Opcodes.DRETURN -> "DRETURN";
			case Opcodes.ARETURN -> "ARETURN";
			case Opcodes.RETURN -> "RETURN";
			case Opcodes.GETSTATIC -> "GETSTATIC";
			case Opcodes.PUTSTATIC -> "PUTSTATIC";
			case Opcodes.GETFIELD -> "GETFIELD";
			case Opcodes.PUTFIELD -> "PUTFIELD";
			case Opcodes.INVOKEVIRTUAL -> "INVOKEVIRTUAL";
			case Opcodes.INVOKESPECIAL -> "INVOKESPECIAL";
			case Opcodes.INVOKESTATIC -> "INVOKESTATIC";
			case Opcodes.INVOKEINTERFACE -> "INVOKEINTERFACE";
			case Opcodes.INVOKEDYNAMIC -> "INVOKEDYNAMIC";
			case Opcodes.NEW -> "NEW";
			case Opcodes.NEWARRAY -> "NEWARRAY";
			case Opcodes.ANEWARRAY -> "ANEWARRAY";
			case Opcodes.ARRAYLENGTH -> "ARRAYLENGTH";
			case Opcodes.ATHROW -> "ATHROW";
			case Opcodes.CHECKCAST -> "CHECKCAST";
			case Opcodes.INSTANCEOF -> "INSTANCEOF";
			case Opcodes.MONITORENTER -> "MONITORENTER";
			case Opcodes.MONITOREXIT -> "MONITOREXIT";
			case Opcodes.MULTIANEWARRAY -> "MULTIANEWARRAY";
			case Opcodes.IFNULL -> "IFNULL";
			case Opcodes.IFNONNULL -> "IFNONNULL";
			default -> "OP_" + opcode;
		};
	}

	private static final class BytecodeProbeContext {
		private final Set<String> lines;
		private final int maxLines;
		private final ClassLoader loader;
		private final Set<String> fields = new HashSet<>();
		private final Set<String> methods = new HashSet<>();
		private final Set<String> classes = new HashSet<>();
		private final Set<String> machineClasses = new LinkedHashSet<>();

		private BytecodeProbeContext(Set<String> lines, int maxLines, ClassLoader loader) {
			this.lines = lines;
			this.maxLines = maxLines;
			this.loader = loader;
		}

		private boolean full() {
			return lines.size() >= maxLines;
		}

		private void add(String line) {
			addProbeLine(lines, maxLines, line);
		}
	}

	private record BytecodeMethodRef(String owner, String name, String desc) {
	}

	private record BytecodeFieldRef(String owner, String name) {
	}

	private static boolean isLambdaClass(Class<?> type) {
		if (type == null) return false;
		try {
			if (type.isHidden()) return true;
		} catch (Throwable ignored) {
		}
		return type.getName().contains("$$Lambda$");
	}

	private static boolean isMachineSupplierProbeNode(String path, Class<?> type) {
		String p = normalize(path);
		String t = normalize(type == null ? "" : type.getName());
		return p.contains("machinesupplier") || p.endsWith("supplier") || t.contains("machinesupplier");
	}

	private static boolean isProbeProjectClass(Class<?> type) {
		if (type == null) return false;
		String name = type.getName();
		return name.startsWith("com.gto") || name.startsWith("com.gtolib") || name.startsWith("com.gregtechceu")
			|| name.startsWith("dev.emi.emi");
	}

	private static void probeLambdaMetadata(Class<?> lambdaType, Set<String> lines, int maxLines, Set<Class<?>> probedHosts) {
		try {
			Class<?> host = lambdaType.getNestHost();
			if (host != null) {
				addProbeLine(lines, maxLines, "lambda nest-host = " + host.getName());
				if (host != lambdaType && probedHosts.add(host)) {
					probeLambdaHost(host, lines, maxLines);
				}
			}
		} catch (Throwable ignored) {
		}
		try {
			Class<?> enclosing = lambdaType.getEnclosingClass();
			if (enclosing != null) addProbeLine(lines, maxLines, "lambda enclosing-class = " + enclosing.getName());
		} catch (Throwable ignored) {
		}
		try {
			Class<?> declaring = lambdaType.getDeclaringClass();
			if (declaring != null) addProbeLine(lines, maxLines, "lambda declaring-class = " + declaring.getName());
		} catch (Throwable ignored) {
		}
	}

	private static void probeLambdaHost(Class<?> host, Set<String> lines, int maxLines) {
		addProbeLine(lines, maxLines, "lambda host-class = " + host.getName());
		for (Field field : host.getDeclaredFields()) {
			if (lines.size() >= maxLines) return;
			String normalized = normalize(field.getName() + " " + field.getType().getName());
			if (!containsAnyWord(normalized, LAMBDA_HOST_WORDS)) continue;
			String label = "host field " + host.getSimpleName() + "#" + field.getName() + " : " + field.getType().getName();
			if (Modifier.isStatic(field.getModifiers())) {
				try {
					field.setAccessible(true);
					Object value = field.get(null);
					if (value != null && isProbeLeaf(value.getClass())) label += " = " + compactProbeValue(value);
				} catch (Throwable ignored) {
				}
			}
			addProbeLine(lines, maxLines, label);
		}
		for (Method method : host.getDeclaredMethods()) {
			if (lines.size() >= maxLines) return;
			String normalized = normalize(method.getName());
			if (!method.isSynthetic() && !containsAnyWord(normalized, LAMBDA_HOST_WORDS)) continue;
			addProbeLine(lines, maxLines, "host method " + host.getSimpleName() + "#" + method.getName()
				+ probeParameterTypes(method) + " -> " + method.getReturnType().getName()
				+ (method.isSynthetic() ? " [synthetic]" : ""));
		}
	}

	private static boolean containsAnyWord(String value, Set<String> words) {
		String normalized = normalize(value);
		for (String word : words) {
			if (normalized.contains(normalize(word))) return true;
		}
		return false;
	}

	private static boolean isRecipeModifierProbeNode(String path, Class<?> type) {
		String p = normalize(path);
		String t = normalize(type == null ? "" : type.getName());
		return p.contains("recipemodifier") || t.contains("recipemodifier") || t.contains("modifierlist");
	}

	private static String probeParameterTypes(Method method) {
		StringBuilder builder = new StringBuilder("(");
		Class<?>[] params = method.getParameterTypes();
		for (int i = 0; i < params.length; i++) {
			if (i > 0) builder.append(", ");
			builder.append(params[i].getName());
		}
		return builder.append(')').toString();
	}

	private static boolean isProbeInteresting(String value) {
		String lower = normalize(value);
		for (String word : PROBE_WORDS) {
			if (lower.contains(word)) return true;
		}
		return false;
	}

	private static boolean isProbeLeaf(Class<?> type) {
		return type.isPrimitive() || Number.class.isAssignableFrom(type) || Boolean.class == type || Character.class == type
			|| CharSequence.class.isAssignableFrom(type) || Enum.class.isAssignableFrom(type) || Class.class == type;
	}

	private static void addProbeLine(Set<String> lines, int maxLines, String line) {
		if (line == null || line.isBlank() || lines.size() >= maxLines) return;
		lines.add(line);
	}

	private static String compactProbeValue(Object value) {
		String text;
		try {
			text = String.valueOf(value).replaceAll("\\s+", " ").trim();
		} catch (Throwable ignored) {
			return "<unprintable>";
		}
		return text.length() > 140 ? text.substring(0, 137) + "..." : text;
	}

	private static boolean isRecipeIdentityCarrier(Class<?> type) {
		String name = normalize(type == null ? "" : type.getName());
		return name.contains("recipetype") || name.contains("recipemap") || name.contains("recipecategory");
	}

	private static void collectRecipeIdentity(Object value, Set<String> tokens, List<String> strings) {
		if (value == null) {
			return;
		}
		try {
			String text = String.valueOf(value);
			addToken(tokens, text);
			addString(strings, text);
		} catch (Throwable ignored) {
		}
		for (Class<?> current = value.getClass(); current != null && current != Object.class; current = current.getSuperclass()) {
			for (Field field : current.getDeclaredFields()) {
				if (Modifier.isStatic(field.getModifiers()) || !isIdentityName(field.getName())) {
					continue;
				}
				try {
					field.setAccessible(true);
					addIdentityValue(field.get(value), tokens, strings);
				} catch (Throwable ignored) {
				}
			}
			for (Method method : current.getDeclaredMethods()) {
				if (Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 0
						|| !IDENTITY_METHODS.contains(method.getName()) || method.getReturnType() == Void.TYPE) {
					continue;
				}
				try {
					method.setAccessible(true);
					addIdentityValue(method.invoke(value), tokens, strings);
				} catch (Throwable ignored) {
				}
			}
		}
	}

	private static boolean isIdentityName(String name) {
		String normalized = normalize(name);
		return normalized.equals("id") || normalized.equals("name") || normalized.contains("registry")
			|| normalized.contains("location") || normalized.contains("identifier");
	}

	private static void addIdentityValue(Object value, Set<String> tokens, List<String> strings) {
		if (value == null) {
			return;
		}
		try {
			String text = String.valueOf(value);
			addToken(tokens, text);
			addString(strings, text);
		} catch (Throwable ignored) {
		}
	}

	private static boolean isLeaf(Class<?> type) {
		if (type.isPrimitive() || Number.class.isAssignableFrom(type) || Boolean.class == type || Character.class == type
				|| Class.class == type) {
			return true;
		}
		String name = type.getName();
		return name.startsWith("java.time.") || name.startsWith("java.math.");
	}

	private static boolean isInteresting(String value) {
		String lower = normalize(value);
		for (String word : INTERESTING_WORDS) {
			if (lower.contains(word)) {
				return true;
			}
		}
		return false;
	}

	private static void addToken(Set<String> tokens, String value) {
		String normalized = normalize(value);
		if (!normalized.isBlank()) {
			tokens.add(normalized);
		}
	}

	private static void addString(List<String> strings, String value) {
		if (value == null || value.isBlank() || strings.size() >= 64) {
			return;
		}
		strings.add(value);
	}

	private static String normalize(String value) {
		return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "");
	}

	private record Node(Object value, int depth) {
	}

	private record ProbeNode(Object value, int depth, String path) {
	}

	public record Snapshot(Set<String> tokens, List<String> strings) {
		private static final Snapshot EMPTY = new Snapshot(Set.of(), List.of());

		public boolean containsAny(String... needles) {
			for (String needle : needles) {
				String normalized = normalize(needle);
				if (normalized.isBlank()) {
					continue;
				}
				for (String token : tokens) {
					if (token.contains(normalized)) {
						return true;
					}
				}
				for (String value : strings) {
					if (normalize(value).contains(normalized)) {
						return true;
					}
				}
			}
			return false;
		}
	}
}
