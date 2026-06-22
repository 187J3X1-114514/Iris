package net.irisshaders.iris.pipeline.description;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public record ShaderPackPipelineResourceRuntimeDiff(
	List<String> differences,
	List<String> notes
) {
	private static final Set<ShaderPackPipelineResources.ResourceInvalidationReason> REQUIRED_LIFECYCLE_PLANS = EnumSet.of(
		ShaderPackPipelineResources.ResourceInvalidationReason.RESIZE,
		ShaderPackPipelineResources.ResourceInvalidationReason.MAIN_TARGET_VERSION_CHANGE,
		ShaderPackPipelineResources.ResourceInvalidationReason.SHADERPACK_RELOAD,
		ShaderPackPipelineResources.ResourceInvalidationReason.WORLD_SWITCH,
		ShaderPackPipelineResources.ResourceInvalidationReason.DIMENSION_SWITCH,
		ShaderPackPipelineResources.ResourceInvalidationReason.BEFORE_AFTER_TRANSLUCENT_BOUNDARY,
		ShaderPackPipelineResources.ResourceInvalidationReason.HAND_BOUNDARY
	);

	public ShaderPackPipelineResourceRuntimeDiff {
		differences = List.copyOf(differences);
		notes = List.copyOf(notes);
	}

	public static ShaderPackPipelineResourceRuntimeDiff compare(ShaderPackPipeline description, ShaderPackPipelineResources.ResourceRuntimeSnapshot runtime) {
		List<String> differences = new ArrayList<>();
		List<String> notes = new ArrayList<>();
		ShaderPackPipelineResources resources = description.resources();

		compareOwners(resources, runtime, differences, notes);
		compareBindingDescriptors(description, runtime, differences, notes);
		compareDerivedResources(description, runtime, differences, notes);
		compareLifecycle(resources, runtime, differences, notes);

		notes.add("scope=phase4-resources; verifies resource owners, descriptor-driven installs, derived runtime objects, and lifecycle plans");
		notes.add("conditional policy skips are allowed for inactive shader uniforms, missing shadow targets before shadow enablement, and sampler-only/image-only split installs");
		notes.add("dhFramebufferLookups=" + runtime.derivedResourceRecords().stream().filter(record -> record.kind() == DerivedFramebufferKind.DH_GBUFFERS || record.kind() == DerivedFramebufferKind.DH_SHADOW).count());
		notes.add("externalDrawFramebufferLookups=" + runtime.derivedResourceRecords().stream().filter(record -> record.kind() == DerivedFramebufferKind.GBUFFERS_EXTERNAL_DRAW).count());

		return new ShaderPackPipelineResourceRuntimeDiff(differences, notes);
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
		out.append("shaderpackPipelineResourceRuntimeDiff\n");
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

	private static void compareOwners(ShaderPackPipelineResources resources, ShaderPackPipelineResources.ResourceRuntimeSnapshot runtime, List<String> differences, List<String> notes) {
		Map<String, String> owners = runtime.ownerSnapshot();
		for (String logical : allLogicalResources(resources)) {
			String owner = owners.get(logical);
			if (owner == null) {
				differences.add("missing runtime owner for logical resource " + logical);
			} else if (owner.equals("unknown")) {
				differences.add("unknown runtime owner for logical resource " + logical);
			}
		}

		notes.add("runtimeOwnerCount=" + owners.size());
	}

	private static void compareBindingDescriptors(ShaderPackPipeline description, ShaderPackPipelineResources.ResourceRuntimeSnapshot runtime, List<String> differences, List<String> notes) {
		Map<String, ShaderPackBindingDescriptor> expectedDescriptors = expectedDescriptors(description);
		Map<String, List<ShaderPackPipelineResources.RuntimeBindingInstallRecord>> installsByDescriptor = runtime.bindingInstallRecords().stream()
			.collect(Collectors.groupingBy(ShaderPackPipelineResources.RuntimeBindingInstallRecord::descriptorId, LinkedHashMap::new, Collectors.toList()));

		for (Map.Entry<String, ShaderPackBindingDescriptor> entry : expectedDescriptors.entrySet()) {
			String descriptorId = entry.getKey();
			ShaderPackBindingDescriptor descriptor = entry.getValue();
			if (isEmptyDescriptor(descriptor)) {
				continue;
			}

			List<ShaderPackPipelineResources.RuntimeBindingInstallRecord> installs = installsByDescriptor.getOrDefault(descriptorId, List.of());
			if (installs.isEmpty()) {
				differences.add("descriptor " + descriptorId + " was declared but never installed at runtime");
				continue;
			}

			checkBindings(descriptorId, BindingTarget.SAMPLER, descriptor.samplerBindings(), installs.stream().flatMap(record -> record.installedSamplerBindings().stream()).collect(Collectors.toCollection(LinkedHashSet::new)), installs, differences);
			checkBindings(descriptorId, BindingTarget.IMAGE, descriptor.imageBindings(), installs.stream().flatMap(record -> record.installedImageBindings().stream()).collect(Collectors.toCollection(LinkedHashSet::new)), installs, differences);
			checkBindings(descriptorId, BindingTarget.SSBO, descriptor.ssboBindings(), installs.stream().flatMap(record -> record.installedSsboBindings().stream()).collect(Collectors.toCollection(LinkedHashSet::new)), installs, differences);
		}

		for (ShaderPackPipelineResources.RuntimeBindingInstallRecord record : runtime.bindingInstallRecords()) {
			if (!expectedDescriptors.containsKey(record.descriptorId())) {
				differences.add("runtime installed descriptor not declared by description: " + record.descriptorId() + " for " + record.runtimeProgramId());
			}
		}

		notes.add("declaredBindingDescriptors=" + expectedDescriptors.keySet());
		notes.add("runtimeBindingInstallCount=" + runtime.bindingInstallRecords().size());
		notes.add("runtimeBindingInstallsByDescriptor=" + installsByDescriptor.entrySet().stream()
			.collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().size(), (a, b) -> a, LinkedHashMap::new)));
	}

	private static void compareDerivedResources(ShaderPackPipeline description, ShaderPackPipelineResources.ResourceRuntimeSnapshot runtime, List<String> differences, List<String> notes) {
		Set<DerivedFramebufferKey> declaredKeys = new LinkedHashSet<>(description.resources().derivedFramebufferKeys());
		Set<DerivedFramebufferKey> recordedKeys = runtime.derivedResourceRecords().stream()
			.map(ShaderPackPipelineResources.DerivedResourceRecord::key)
			.collect(Collectors.toCollection(LinkedHashSet::new));

		for (ShaderPackPass pass : description.stages().values().stream().flatMap(List::stream).toList()) {
			DerivedFramebufferKey key = pass.layout().derivedFramebufferKey();
			DerivedFramebufferKind kind = derivedKind(description.resources(), key);
			if (kind == DerivedFramebufferKind.COMPUTE_ONLY) {
				continue;
			}

			if (!declaredKeys.contains(key)) {
				differences.add("pass " + pass.id() + " uses derived key not declared in resources: " + key);
			}
		}

		for (DerivedFramebufferDescriptor descriptor : description.resources().derivedFramebuffers()) {
			if (isGroupedOrLifecycleDerivedKind(descriptor.kind())) {
				continue;
			}

			if (!recordedKeys.contains(descriptor.key())) {
				differences.add("declared derived runtime key was not materialized through resources: " + descriptor.key());
			}
		}

		checkGroupedDerivedKind(DerivedFramebufferKind.CLEAR, description.resources().derivedFramebuffers(), runtime.derivedResourceRecords(), notes);
		checkGroupedDerivedKind(DerivedFramebufferKind.SHADOW_CLEAR, description.resources().derivedFramebuffers(), runtime.derivedResourceRecords(), notes);
		checkGroupedDerivedKind(DerivedFramebufferKind.GBUFFERS_EXTERNAL_DRAW, description.resources().derivedFramebuffers(), runtime.derivedResourceRecords(), notes);
		checkGroupedDerivedKind(DerivedFramebufferKind.DEPTH_COPY, description.resources().derivedFramebuffers(), runtime.derivedResourceRecords(), notes);
		notes.add("derivedRuntimeRecordCount=" + runtime.derivedResourceRecords().size());
		notes.add("declaredDerivedKeys=" + declaredKeys.size());
	}

	private static void compareLifecycle(ShaderPackPipelineResources resources, ShaderPackPipelineResources.ResourceRuntimeSnapshot runtime, List<String> differences, List<String> notes) {
		for (ShaderPackPipelineResources.ResourceInvalidationReason reason : REQUIRED_LIFECYCLE_PLANS) {
			if (resources.invalidationPlan(reason).isEmpty()) {
				differences.add("missing resource invalidation plan for " + reason);
			}
		}

		Set<ShaderPackPipelineResources.ResourceInvalidationReason> recorded = runtime.lifecycleEvents().stream()
			.map(ShaderPackPipelineResources.ResourceLifecycleEvent::reason)
			.collect(Collectors.toCollection(() -> EnumSet.noneOf(ShaderPackPipelineResources.ResourceInvalidationReason.class)));

		if (!recorded.contains(ShaderPackPipelineResources.ResourceInvalidationReason.SHADERPACK_RELOAD)) {
			differences.add("runtime resources did not record shaderpack reload construction lifecycle");
		}

		if (!recorded.contains(ShaderPackPipelineResources.ResourceInvalidationReason.WORLD_SWITCH)) {
			differences.add("runtime resources did not record world-scope lifecycle boundary");
		}

		if (!recorded.contains(ShaderPackPipelineResources.ResourceInvalidationReason.DIMENSION_SWITCH)) {
			differences.add("runtime resources did not record dimension-scope lifecycle boundary");
		}

		notes.add("recordedLifecycleEvents=" + recorded);
		notes.add("lifecycleEventCount=" + runtime.lifecycleEvents().size());
	}

	private static List<String> allLogicalResources(ShaderPackPipelineResources resources) {
		List<String> logical = new ArrayList<>();
		logical.addAll(resources.logicalRenderTargets());
		logical.addAll(resources.logicalShadowTargets());
		logical.addAll(resources.logicalImages());
		logical.addAll(resources.logicalSsbo());
		logical.addAll(resources.irisCompatibilityResources());
		logical.addAll(resources.debugDerivedFramebufferKeys());
		return logical;
	}

	private static Map<String, ShaderPackBindingDescriptor> expectedDescriptors(ShaderPackPipeline description) {
		Map<String, ShaderPackBindingDescriptor> descriptors = new LinkedHashMap<>();
		description.resources().programBindingDescriptorDefinitions().stream()
			.sorted(Comparator.comparing(ShaderPackBindingDescriptor::descriptorId))
			.forEach(descriptor -> descriptors.put(descriptor.descriptorId(), descriptor));

		description.stages().values().stream()
			.flatMap(List::stream)
			.map(ShaderPackPass::bindings)
			.sorted(Comparator.comparing(ShaderPackBindingDescriptor::descriptorId))
			.forEach(descriptor -> descriptors.putIfAbsent(descriptor.descriptorId(), descriptor));

		return descriptors;
	}

	private static <T> void checkBindings(String descriptorId, BindingTarget target, List<T> expected, Set<T> installed,
	                                      List<ShaderPackPipelineResources.RuntimeBindingInstallRecord> installs, List<String> differences) {
		for (T directive : expected) {
			if (installed.contains(directive)) {
				continue;
			}

			boolean conditionallySkipped = installs.stream()
				.flatMap(record -> record.skippedBindings().stream())
				.anyMatch(skip -> skip.target() == target && skip.directive().equals(debugName(directive)) && isAllowedSkip(skip.reason()));
			if (!conditionallySkipped) {
				differences.add("descriptor " + descriptorId + " missing installed " + target.name().toLowerCase(java.util.Locale.ROOT) + " binding " + debugName(directive));
			}
		}
	}

	private static String debugName(Object directive) {
		if (directive instanceof SamplerBindingDirective sampler) {
			return sampler.debugName();
		}
		if (directive instanceof ImageBindingDirective image) {
			return image.debugName();
		}
		if (directive instanceof SsboBindingDirective ssbo) {
			return ssbo.debugName();
		}
		return String.valueOf(directive);
	}

	private static boolean isAllowedSkip(BindingSkipReason reason) {
		return EnumSet.of(
			BindingSkipReason.NOT_ACTIVE,
			BindingSkipReason.SHADOW_SAMPLER_NOT_INSTALLED,
			BindingSkipReason.NO_CENTER_DEPTH_SAMPLER,
			BindingSkipReason.NO_SAMPLER_HOLDER,
			BindingSkipReason.NO_IMAGE_HOLDER,
			BindingSkipReason.NO_WHITE_PIXEL,
			BindingSkipReason.NO_SSBO_HOLDER
		).contains(reason);
	}

	private static boolean isGroupedOrLifecycleDerivedKind(DerivedFramebufferKind kind) {
		return kind == DerivedFramebufferKind.CLEAR
			|| kind == DerivedFramebufferKind.SHADOW_CLEAR
			|| kind == DerivedFramebufferKind.GBUFFERS_EXTERNAL_DRAW
			|| kind == DerivedFramebufferKind.DEPTH_COPY
			|| kind == DerivedFramebufferKind.IMAGE_CLEAR
			|| kind == DerivedFramebufferKind.COMPUTE_ONLY;
	}

	private static void checkGroupedDerivedKind(DerivedFramebufferKind kind, List<DerivedFramebufferDescriptor> declared, List<ShaderPackPipelineResources.DerivedResourceRecord> recorded, List<String> notes) {
		long declaredCount = declared.stream().filter(descriptor -> descriptor.kind() == kind).count();
		if (declaredCount == 0) {
			return;
		}

		long recordedCount = recorded.stream().filter(record -> record.kind() == kind).count();
		notes.add("groupedDerivedKind=" + kind + " declared=" + declaredCount + " recorded=" + recordedCount);
	}

	private static DerivedFramebufferKind derivedKind(ShaderPackPipelineResources resources, DerivedFramebufferKey key) {
		return resources.derivedFramebuffers().stream()
			.filter(descriptor -> descriptor.key().equals(key))
			.map(DerivedFramebufferDescriptor::kind)
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("Unknown derived framebuffer key " + key.debugName()));
	}

	private static boolean isEmptyDescriptor(ShaderPackBindingDescriptor descriptor) {
		return descriptor.isEmpty();
	}
}
