package net.irisshaders.iris.pipeline.description;

import net.irisshaders.iris.pipeline.ExternalDrawHostPipelineDescriptor;
import net.irisshaders.iris.pipeline.IrisPipelines;
import net.irisshaders.iris.pipeline.WorldRenderingPhase;
import net.irisshaders.iris.pipeline.programs.ShaderKey;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public record ShaderPackExternalDrawRuntimeDiff(
	List<String> differences,
	List<String> notes
) {
	public ShaderPackExternalDrawRuntimeDiff {
		differences = List.copyOf(differences);
		notes = List.copyOf(notes);
	}

	public static ShaderPackExternalDrawRuntimeDiff compare(ShaderPackPipeline pipeline, ProgramSet programSet) {
		List<String> differences = new ArrayList<>();
		List<String> notes = new ArrayList<>();

		Map<WorldRenderingPhase, ExternalDrawPhasePass> byPhase = new EnumMap<>(WorldRenderingPhase.class);
		for (ExternalDrawPhasePass phasePass : pipeline.externalDrawPhases()) {
			ExternalDrawPhasePass previous = byPhase.put(phasePass.worldPhase(), phasePass);
			if (previous != null) {
				differences.add("duplicate external draw phase row for " + phasePass.worldPhase());
			}
		}

		for (WorldRenderingPhase phase : WorldRenderingPhase.values()) {
			ExternalDrawPhasePass phasePass = byPhase.get(phase);
			if (phasePass == null) {
				differences.add("missing external draw phase row for " + phase);
				continue;
			}

			if (phasePass.participatesInOverride() && phasePass.runtimeDescriptors().isEmpty()) {
				differences.add("participating external draw phase has no runtime descriptors: " + phase);
			}

			checkBoundaryDescriptors(phasePass, differences);
			checkBoundaryTargetSets(phasePass, differences);
			checkDescriptorMatchesProgramSource(phasePass, programSet, differences);
		}

		Set<ShaderKey> selectorKeys = IrisPipelines.externalDrawHostPipelineDescriptors().stream()
			.map(ExternalDrawHostPipelineDescriptor::shaderKey)
			.collect(Collectors.toCollection(() -> EnumSet.noneOf(ShaderKey.class)));
		Set<ShaderKey> descriptorKeys = pipeline.externalDrawPhases().stream()
			.flatMap(phase -> phase.runtimeDescriptors().stream())
			.map(ExternalDrawRuntimeDescriptor::shaderKey)
			.collect(Collectors.toCollection(() -> EnumSet.noneOf(ShaderKey.class)));

		Set<ShaderKey> descriptorKeysWithoutSelector = EnumSet.copyOf(descriptorKeys);
		descriptorKeysWithoutSelector.removeAll(selectorKeys);
		descriptorKeysWithoutSelector.removeIf(ShaderPackExternalDrawRuntimeDiff::isFallbackOrPhaseOnlyKey);
		if (!descriptorKeysWithoutSelector.isEmpty()) {
			differences.add("external draw descriptors reference keys without host selector metadata: " + descriptorKeysWithoutSelector);
		}

		notes.add("scope=phase5-external-draw; verifies world phase matrix, runtime descriptors, and IrisPipelines selector registry");
		notes.add("externalPhaseRows=" + byPhase.size());
		notes.add("runtimeDescriptorCount=" + pipeline.externalDrawPhases().stream().mapToInt(phase -> phase.runtimeDescriptors().size()).sum());
		notes.add("hostPipelineSelectorRows=" + IrisPipelines.externalDrawHostPipelineDescriptors().size());
		notes.add("targetSetPolicy=DRAWBUFFERS/RENDERTARGETS define color attachment set; BEFORE/AFTER_TRANSLUCENT only selects main/alt view");
		notes.add("selectorKeys=" + selectorKeys.stream().map(Enum::name).sorted().toList());

		return new ShaderPackExternalDrawRuntimeDiff(differences, notes);
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

	public String matrixSummary() {
		return notes.stream()
			.filter(note -> note.startsWith("externalPhaseRows=")
				|| note.startsWith("runtimeDescriptorCount=")
				|| note.startsWith("hostPipelineSelectorRows=")
				|| note.startsWith("targetSetPolicy="))
			.toList()
			.toString();
	}

	public String dump() {
		StringBuilder out = new StringBuilder();
		out.append("shaderpackExternalDrawRuntimeDiff\n");
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

	private static void checkBoundaryDescriptors(ExternalDrawPhasePass phasePass, List<String> differences) {
		Map<ShaderKey, Set<ExternalDrawBoundary>> byKey = phasePass.runtimeDescriptors().stream()
			.collect(Collectors.groupingBy(ExternalDrawRuntimeDescriptor::shaderKey, () -> new EnumMap<>(ShaderKey.class),
				Collectors.mapping(ExternalDrawRuntimeDescriptor::boundary, Collectors.toSet())));

		for (Map.Entry<ShaderKey, Set<ExternalDrawBoundary>> entry : byKey.entrySet()) {
			Set<ExternalDrawBoundary> boundaries = entry.getValue();
			if (boundaries.contains(ExternalDrawBoundary.BEFORE_TRANSLUCENT) != boundaries.contains(ExternalDrawBoundary.AFTER_TRANSLUCENT)) {
				differences.add("external draw descriptor for " + phasePass.worldPhase() + "/" + entry.getKey() + " does not have paired before/after translucent descriptors: " + boundaries);
			}
		}
	}

	private static void checkBoundaryTargetSets(ExternalDrawPhasePass phasePass, List<String> differences) {
		Map<ShaderKey, Map<ExternalDrawBoundary, ExternalDrawRuntimeDescriptor>> byKey = phasePass.runtimeDescriptors().stream()
			.collect(Collectors.groupingBy(ExternalDrawRuntimeDescriptor::shaderKey, () -> new EnumMap<>(ShaderKey.class),
				Collectors.toMap(ExternalDrawRuntimeDescriptor::boundary, descriptor -> descriptor, (a, b) -> a, () -> new EnumMap<>(ExternalDrawBoundary.class))));

		for (Map.Entry<ShaderKey, Map<ExternalDrawBoundary, ExternalDrawRuntimeDescriptor>> entry : byKey.entrySet()) {
			ExternalDrawRuntimeDescriptor before = entry.getValue().get(ExternalDrawBoundary.BEFORE_TRANSLUCENT);
			ExternalDrawRuntimeDescriptor after = entry.getValue().get(ExternalDrawBoundary.AFTER_TRANSLUCENT);
			if (before == null || after == null) {
				continue;
			}
			if (!Arrays.equals(before.drawBuffers(), after.drawBuffers())) {
				differences.add("external draw descriptor for " + phasePass.worldPhase() + "/" + entry.getKey()
					+ " changes drawBuffers across translucent boundary; boundary may only select main/alt view. before="
					+ Arrays.toString(before.drawBuffers()) + " after=" + Arrays.toString(after.drawBuffers()));
			}
		}
	}

	private static void checkDescriptorMatchesProgramSource(ExternalDrawPhasePass phasePass, ProgramSet programSet, List<String> differences) {
		for (ExternalDrawRuntimeDescriptor descriptor : phasePass.runtimeDescriptors()) {
			ProgramSource source = programSet.get(descriptor.shaderKey().getProgram()).orElse(null);
			if (source == null) {
				if (!Arrays.equals(descriptor.drawBuffers(), new int[] {0})) {
					differences.add("external draw descriptor for " + phasePass.worldPhase() + "/" + descriptor.shaderKey()
						+ " has no ProgramSource fallback but drawBuffers are not [0]: " + Arrays.toString(descriptor.drawBuffers()));
				}
				continue;
			}

			int[] sourceDrawBuffers = source.getDirectives().getDrawBuffers();
			if (!Arrays.equals(descriptor.drawBuffers(), sourceDrawBuffers)) {
				differences.add("external draw descriptor for " + phasePass.worldPhase() + "/" + descriptor.shaderKey()
					+ " does not match ProgramSource " + descriptor.shaderKey().getProgram()
					+ " drawBuffers. descriptor=" + Arrays.toString(descriptor.drawBuffers())
					+ " source=" + Arrays.toString(sourceDrawBuffers));
			}
		}
	}

	private static boolean isFallbackOrPhaseOnlyKey(ShaderKey key) {
		return switch (key) {
			case BASIC, BASIC_COLOR, SKY_TEXTURED_COLOR, CLOUDS_SODIUM, MOVING_BLOCK -> true;
			default -> false;
		};
	}
}
