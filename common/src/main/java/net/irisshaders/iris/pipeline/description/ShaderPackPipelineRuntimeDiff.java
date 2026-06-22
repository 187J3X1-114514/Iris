package net.irisshaders.iris.pipeline.description;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public record ShaderPackPipelineRuntimeDiff(
	List<String> differences,
	List<String> notes
) {
	private static final EnumSet<ShaderPackPassStage> STRICT_RUNTIME_STAGES = EnumSet.of(
		ShaderPackPassStage.SETUP,
		ShaderPackPassStage.BEGIN,
		ShaderPackPassStage.PREPARE,
		ShaderPackPassStage.SHADOW,
		ShaderPackPassStage.DEFERRED,
		ShaderPackPassStage.COMPOSITE,
		ShaderPackPassStage.SHADOW_COMPOSITE,
		ShaderPackPassStage.GBUFFERS,
		ShaderPackPassStage.FINAL
	);

	public ShaderPackPipelineRuntimeDiff {
		differences = List.copyOf(differences);
		notes = List.copyOf(notes);
	}

	public static ShaderPackPipelineRuntimeDiff compare(ShaderPackPipeline description, ShaderPackPipelineRuntimeSnapshot runtime) {
		List<String> differences = new ArrayList<>();
		List<String> notes = new ArrayList<>();

		notes.add("strictRuntimeStages=" + STRICT_RUNTIME_STAGES);
		for (ShaderPackPassStage stage : STRICT_RUNTIME_STAGES) {
			if (!runtime.passesByStage().containsKey(stage)) {
				int expectedCount = description.stages().getOrDefault(stage, List.of()).size();
				if (expectedCount == 0) {
					notes.add(stage + " runtime renderer not constructed and description has no passes; skipped");
				} else {
					differences.add(stage + " runtime renderer not constructed but description has " + expectedCount + " passes");
				}
				continue;
			}

			compareStage(description, runtime, stage, differences);
		}

		notes.add("scope=phase1-to-phase3; composite stages are runtime renderer snapshots, setup/shadow/gbuffers/final include descriptor-backed phase 3 snapshots");
		notes.add("gbuffers copy descriptors are verified only; depth copy execution remains lifecycle-triggered by beginHand/beginTranslucents");
		notes.add("descriptionStageCounts=" + STRICT_RUNTIME_STAGES.stream()
			.collect(Collectors.toMap(stage -> stage.name(), stage -> expectedPasses(description, stage).size(), (a, b) -> a, java.util.LinkedHashMap::new)));

		return new ShaderPackPipelineRuntimeDiff(differences, notes);
	}

	public boolean hasDifferences() {
		return !differences.isEmpty();
	}

	public int differenceCount() {
		return differences.size();
	}

	public String summary() {
		return differenceCount() + " differences";
	}

	public String dump() {
		StringBuilder out = new StringBuilder();
		out.append("shaderpackPipelineRuntimeDiff\n");
		out.append("summary=").append(summary()).append('\n');
		out.append("differences\n");
		if (differences.isEmpty()) {
			out.append("- none\n");
		} else {
			for (String difference : differences) {
				out.append("- ").append(difference).append('\n');
			}
		}

		out.append("notes\n");
		if (notes.isEmpty()) {
			out.append("- none\n");
		} else {
			for (String note : notes) {
				out.append("- ").append(note).append('\n');
			}
		}

		return out.toString();
	}

	private static void compareStage(ShaderPackPipeline description, ShaderPackPipelineRuntimeSnapshot runtime, ShaderPackPassStage stage, List<String> differences) {
		List<ShaderPackPass> expected = expectedPasses(description, stage);
		List<ShaderPackRuntimePassSnapshot> actual = runtime.passesByStage().getOrDefault(stage, List.of());

		if (expected.size() != actual.size()) {
			differences.add(stage + " pass count expected=" + expected.size() + " actual=" + actual.size());
		}

		int shared = Math.min(expected.size(), actual.size());
		for (int i = 0; i < shared; i++) {
			comparePass(stage, i, expected.get(i), actual.get(i), differences);
		}
	}

	private static List<ShaderPackPass> expectedPasses(ShaderPackPipeline description, ShaderPackPassStage stage) {
		List<ShaderPackPass> passes = description.stages().getOrDefault(stage, List.of());

		return switch (stage) {
			case SETUP -> passes.stream()
				.filter(pass -> pass.type() == ShaderPackPassType.SETUP || pass.type() == ShaderPackPassType.CLEAR)
				.toList();
			case SHADOW -> passes.stream()
				.filter(pass -> pass.type() == ShaderPackPassType.COMPUTE || pass.type() == ShaderPackPassType.CLEAR)
				.toList();
			case GBUFFERS -> passes.stream()
				.filter(pass -> pass.type() == ShaderPackPassType.COPY)
				.toList();
			default -> passes;
		};
	}

	private static void comparePass(ShaderPackPassStage stage, int index, ShaderPackPass expected, ShaderPackRuntimePassSnapshot actual, List<String> differences) {
		String prefix = stage + "[" + index + "] " + expected.id() + ": ";
		ShaderPackPassLayout layout = expected.layout();

		compareValue(prefix + "id", expected.id(), actual.id(), differences);
		compareValue(prefix + "name", expected.name(), actual.name(), differences);
		compareValue(prefix + "stage", expected.stage(), actual.stage(), differences);
		compareValue(prefix + "type", expected.type(), actual.type(), differences);
		compareArray(prefix + "drawBuffers", layout.drawBuffers(), actual.drawBuffers(), differences);
		compareValue(prefix + "attachmentMapping", layout.attachmentMapping(), actual.attachmentMapping(), differences);
		compareValue(prefix + "explicitPreFlips", layout.explicitPreFlips(), actual.explicitPreFlips(), differences);
		compareValue(prefix + "explicitFlips", layout.explicitFlips(), actual.explicitFlips(), differences);
		compareValue(prefix + "resolvedFlips", layout.resolvedFlips(), actual.resolvedFlips(), differences);
		compareValue(prefix + "bufferReadsFromAlt", expectedBufferReadsFromAlt(expected), actual.bufferReadsFromAlt(), differences);
		compareValue(prefix + "flippedAtLeastOnceSnapshot", layout.flippedAtLeastOnceSnapshot(), actual.flippedAtLeastOnceSnapshot(), differences);
		compareValue(prefix + "mipmappedInputs", expected.behavior().mipmappedInputs(), actual.mipmappedInputs(), differences);
		compareValue(prefix + "viewportScale", expected.behavior().viewportScale(), actual.viewportScale(), differences);
		compareValue(prefix + "hasBlendOverride", expected.behavior().blendModeOverride() != null, actual.hasBlendOverride(), differences);
		compareValue(prefix + "computeProgramCount", computeDescriptorCount(expected), actual.computeProgramCount(), differences);
		compareValue(prefix + "hasGraphicsProgram", expectsGraphicsProgram(expected.type()), actual.hasGraphicsProgram(), differences);
		compareValue(prefix + "derivedFramebufferKey", layout.derivedFramebufferKey(), actual.derivedFramebufferKey(), differences);
	}

	private static boolean expectsGraphicsProgram(ShaderPackPassType type) {
		return type == ShaderPackPassType.GRAPHICS || type == ShaderPackPassType.GRAPHICS_WITH_COMPUTE || type == ShaderPackPassType.FINAL;
	}

	private static int computeDescriptorCount(ShaderPackPass pass) {
		return (int) pass.programDescriptors().stream()
			.filter(descriptor -> !descriptor.computeSources().isEmpty())
			.count();
	}

	private static Set<Integer> expectedBufferReadsFromAlt(ShaderPackPass pass) {
		if (pass.type() == ShaderPackPassType.CLEAR) {
			return Set.of();
		}

		if (pass.type() == ShaderPackPassType.COPY) {
			return bufferReadsFromAlt(pass.inputs(), pass.stage());
		}

		return bufferReadsFromAlt(pass.layout(), pass.stage());
	}

	private static Set<Integer> bufferReadsFromAlt(ShaderPackPassLayout layout, ShaderPackPassStage stage) {
		String prefix = bufferPrefix(stage);

		return layout.bufferInputViews().entrySet().stream()
			.filter(entry -> entry.getValue() == ShaderPackResourceView.ALT)
			.filter(entry -> entry.getKey().startsWith(prefix))
			.map(entry -> Integer.parseInt(entry.getKey().substring(prefix.length())))
			.sorted()
			.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	private static Set<Integer> bufferReadsFromAlt(List<ShaderPackPassResource> resources, ShaderPackPassStage stage) {
		String prefix = bufferPrefix(stage);

		return resources.stream()
			.filter(resource -> resource.view() == ShaderPackResourceView.ALT)
			.filter(resource -> resource.logicalId().startsWith(prefix))
			.map(resource -> Integer.parseInt(resource.logicalId().substring(prefix.length())))
			.sorted()
			.collect(Collectors.toCollection(LinkedHashSet::new));
	}

	private static String bufferPrefix(ShaderPackPassStage stage) {
		return stage == ShaderPackPassStage.SHADOW || stage == ShaderPackPassStage.SHADOW_COMPOSITE ? "shadowcolor" : "colortex";
	}

	private static void compareArray(String label, int[] expected, int[] actual, List<String> differences) {
		if (!Arrays.equals(expected, actual)) {
			differences.add(label + " expected=" + Arrays.toString(expected) + " actual=" + Arrays.toString(actual));
		}
	}

	private static void compareValue(String label, Object expected, Object actual, List<String> differences) {
		if (!expected.equals(actual)) {
			differences.add(label + " expected=" + stable(expected) + " actual=" + stable(actual));
		}
	}

	private static Object stable(Object value) {
		if (value instanceof Set<?> set) {
			return set.stream().sorted(Comparator.comparing(String::valueOf)).collect(Collectors.toList());
		}

		if (value instanceof Map<?, ?> map) {
			return map.entrySet().stream()
				.sorted(Comparator.comparing(entry -> String.valueOf(entry.getKey())))
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, java.util.LinkedHashMap::new));
		}

		return value;
	}
}
