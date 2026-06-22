package net.irisshaders.iris.pipeline.description;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class ShaderPackPipelineDebugDump {
	private ShaderPackPipelineDebugDump() {
	}

	public static String dump(ShaderPackPipeline pipeline) {
		StringBuilder out = new StringBuilder();
		ShaderPackDebugIdentity identity = pipeline.debugIdentity();

		out.append("shaderpackPipelineDump\n");
		out.append("schema=").append(identity.builderSchemaVersion()).append('\n');
		out.append("resourceSchema=").append(identity.resourceSchemaVersion()).append('\n');
		out.append("shaderpack=").append(identity.shaderPackName()).append('\n');
		out.append("profile=").append(identity.profile()).append('\n');
		out.append("dimension=").append(identity.dimension()).append('\n');
		out.append("featureFlags=").append(identity.featureFlags()).append('\n');
		out.append("renderSize=").append(identity.renderWidth()).append('x').append(identity.renderHeight()).append('\n');
		out.append("shadowSize=").append(identity.shadowSize()).append('\n');
		out.append("flippedBeforeShadow=").append(sorted(pipeline.flippedBeforeShadow())).append('\n');
		out.append("flippedAfterPrepare=").append(sorted(pipeline.flippedAfterPrepare())).append('\n');
		out.append("flippedAfterTranslucent=").append(sorted(pipeline.flippedAfterTranslucent())).append('\n');
		out.append("flippedAfterComposite=").append(sorted(pipeline.flippedAfterComposite())).append('\n');
		out.append("compositeFlippedAtLeastOnce=").append(sorted(pipeline.compositeFlippedAtLeastOnce())).append('\n');
		out.append("shadowCompositeFlippedAfter=").append(sorted(pipeline.shadowCompositeFlippedAfter())).append('\n');
		out.append('\n');

		appendStages(out, pipeline);
		appendPasses(out, pipeline);
		appendResources(out, pipeline.resources());
		appendExternalDraw(out, pipeline.externalDrawPhases());

		return out.toString();
	}

	private static void appendStages(StringBuilder out, ShaderPackPipeline pipeline) {
		out.append("stages\n");
		for (ShaderPackPassStage stage : ShaderPackPassStage.values()) {
			List<ShaderPackPass> passes = pipeline.stages().getOrDefault(stage, List.of());
			out.append("- ").append(stage.name())
				.append(" count=").append(passes.size())
				.append(" ids=").append(passes.stream().map(ShaderPackPass::id).collect(Collectors.toList()))
				.append('\n');
		}
		out.append('\n');
	}

	private static void appendPasses(StringBuilder out, ShaderPackPipeline pipeline) {
		out.append("passes\n");
		for (ShaderPackPassStage stage : ShaderPackPassStage.values()) {
			for (ShaderPackPass pass : pipeline.stages().getOrDefault(stage, List.of())) {
				out.append("- id=").append(pass.id()).append('\n');
				out.append("  name=").append(pass.name()).append('\n');
				out.append("  stage=").append(pass.stage()).append('\n');
				out.append("  type=").append(pass.type()).append('\n');
				out.append("  programs=").append(pass.programDescriptors()).append('\n');
				out.append("  inputs=").append(pass.inputs()).append('\n');
				out.append("  outputs=").append(pass.outputs()).append('\n');
				out.append("  bindings=").append(pass.bindings()).append('\n');
				out.append("  blend=").append(pass.behavior().blend()).append('\n');
				out.append("  viewportScale=").append(pass.behavior().viewportScale()).append('\n');
				out.append("  mipmappedInputs=").append(sorted(pass.behavior().mipmappedInputs())).append('\n');
				out.append("  barriers=").append(sortedStrings(pass.behavior().barrierRequirements())).append('\n');
				out.append("  clearIntent=").append(pass.behavior().clearIntent()).append('\n');
				out.append("  copyIntent=").append(pass.behavior().copyIntent()).append('\n');
				out.append("  trigger=").append(pass.behavior().trigger()).append('\n');
				appendLayout(out, pass.layout());
			}
		}
		out.append('\n');
	}

	private static void appendLayout(StringBuilder out, ShaderPackPassLayout layout) {
		out.append("  layout.passId=").append(layout.passId()).append('\n');
		out.append("  layout.bufferInputViews=").append(sortedMap(layout.bufferInputViews())).append('\n');
		out.append("  layout.drawBuffers=").append(Arrays.toString(layout.drawBuffers())).append('\n');
		out.append("  layout.attachmentMapping=").append(sortedMap(layout.attachmentMapping())).append('\n');
		out.append("  layout.explicitPreFlips=").append(sortedMap(layout.explicitPreFlips())).append('\n');
		out.append("  layout.explicitFlips=").append(sortedMap(layout.explicitFlips())).append('\n');
		out.append("  layout.resolvedFlips=").append(sorted(layout.resolvedFlips())).append('\n');
		out.append("  layout.flippedAtLeastOnceSnapshot=").append(sorted(layout.flippedAtLeastOnceSnapshot())).append('\n');
		out.append("  layout.derivedFramebufferKey=").append(layout.derivedFramebufferKey().debugName()).append('\n');
	}

	private static void appendResources(StringBuilder out, ShaderPackPipelineResources resources) {
		out.append("resources\n");
		out.append("- logicalRenderTargets=").append(resources.logicalRenderTargets()).append('\n');
		out.append("- logicalShadowTargets=").append(resources.logicalShadowTargets()).append('\n');
		out.append("- logicalSamplers=").append(resources.logicalSamplers()).append('\n');
		out.append("- logicalImages=").append(resources.logicalImages()).append('\n');
		out.append("- logicalSsbo=").append(resources.logicalSsbo()).append('\n');
		out.append("- irisCompatibilityResources=").append(resources.irisCompatibilityResources()).append('\n');
		out.append("- debugDerivedFramebufferKeys=").append(resources.debugDerivedFramebufferKeys()).append('\n');
		out.append("- derivedFramebufferDescriptors=").append(resources.derivedFramebuffers().stream().map(DerivedFramebufferDescriptor::debugSummary).collect(Collectors.toList())).append('\n');
		out.append("- mainTargetDependencies=").append(resources.mainTargetDependencies()).append('\n');
		out.append("- resizeInvalidation=").append(resources.resizeInvalidation()).append('\n');
		out.append("- reloadInvalidation=").append(resources.reloadInvalidation()).append('\n');
		out.append("- destroyOwnership=").append(resources.destroyOwnership()).append('\n');
		out.append("- debugRuntimeOwnerQueries=").append(resources.debugRuntimeOwnerQueries()).append('\n');
		out.append("- debugDerivedRuntimeObjects=").append(resources.debugDerivedRuntimeObjects()).append('\n');
		out.append("- debugProgramBindingDescriptors=").append(resources.debugProgramBindingDescriptors()).append('\n');
		out.append("- programBindingDescriptorDefinitions=").append(resources.programBindingDescriptorDefinitions().stream().map(ShaderPackBindingDescriptor::debugSummary).collect(Collectors.toList())).append('\n');
		out.append("- invalidationReasons=").append(resources.invalidationReasons()).append('\n');
		out.append('\n');
	}

	private static void appendExternalDraw(StringBuilder out, List<ExternalDrawPhasePass> externalDrawPhases) {
		out.append("externalDraw\n");
		for (ExternalDrawPhasePass phase : externalDrawPhases) {
			out.append("- worldPhase=").append(phase.worldPhase()).append('\n');
			out.append("  phaseClass=").append(phase.phaseClass()).append('\n');
			out.append("  participatesInOverride=").append(phase.participatesInOverride()).append('\n');
			out.append("  shaderKeyPolicy=").append(phase.shaderKeyPolicy()).append('\n');
			out.append("  hostPipelineSelectorDescriptor=").append(phase.hostPipelineSelectorDescriptor()).append('\n');
			out.append("  framebufferPolicy=").append(phase.framebufferPolicy()).append('\n');
			out.append("  runtimeViewPolicy=").append(phase.runtimeViewPolicy()).append('\n');
			out.append("  bindingPolicy=").append(phase.bindingPolicy()).append('\n');
			out.append("  vertexFormatPolicy=").append(phase.vertexFormatPolicy()).append('\n');
			out.append("  pbrHookPolicy=").append(phase.pbrHookPolicy()).append('\n');
			out.append("  sodiumPolicy=").append(phase.sodiumPolicy()).append('\n');
			out.append("  passthroughReason=").append(phase.passthroughReason()).append('\n');
			out.append("  shaderKeys=").append(phase.shaderKeys()).append('\n');
		}
	}

	private static List<Integer> sorted(java.util.Set<Integer> values) {
		return values.stream().sorted().collect(Collectors.toList());
	}

	private static List<String> sortedStrings(java.util.Set<String> values) {
		return values.stream().sorted().collect(Collectors.toList());
	}

	private static <K, V> Map<K, V> sortedMap(Map<K, V> values) {
		return values.entrySet().stream()
			.sorted(Comparator.comparing(entry -> String.valueOf(entry.getKey())))
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, java.util.LinkedHashMap::new));
	}
}
