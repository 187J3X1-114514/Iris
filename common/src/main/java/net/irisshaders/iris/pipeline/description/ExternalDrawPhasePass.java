package net.irisshaders.iris.pipeline.description;

import net.irisshaders.iris.pipeline.WorldRenderingPhase;
import net.irisshaders.iris.pipeline.programs.ShaderKey;

import java.util.List;
import java.util.Set;

public record ExternalDrawPhasePass(
	String id,
	WorldRenderingPhase worldPhase,
	ExternalDrawPhaseClass phaseClass,
	Set<ShaderKey> shaderKeys,
	List<String> hostPipelineSelectorDescriptorIds,
	ExternalDrawFramebufferPolicy framebufferPolicy,
	ExternalDrawRuntimeViewPolicy runtimeViewPolicy,
	ExternalDrawBindingPolicy bindingPolicy,
	ExternalDrawVertexFormatPolicy vertexFormatPolicy,
	ExternalDrawPbrHookPolicy pbrHookPolicy,
	ExternalDrawSodiumPolicy sodiumPolicy,
	boolean participatesInOverride,
	ExternalDrawPassthroughReason passthroughReason,
	List<ExternalDrawRuntimeDescriptor> runtimeDescriptors
) {
	public ExternalDrawPhasePass {
		shaderKeys = Set.copyOf(shaderKeys);
		hostPipelineSelectorDescriptorIds = List.copyOf(hostPipelineSelectorDescriptorIds);
		runtimeDescriptors = List.copyOf(runtimeDescriptors);
	}
}
