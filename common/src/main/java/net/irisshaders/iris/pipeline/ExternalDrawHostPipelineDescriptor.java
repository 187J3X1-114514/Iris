package net.irisshaders.iris.pipeline;

import net.irisshaders.iris.pipeline.programs.ShaderKey;

import java.util.Set;

public record ExternalDrawHostPipelineDescriptor(
	String descriptorId,
	String hostPipelineId,
	ShaderKey shaderKey,
	boolean shadow,
	ExternalDrawSelectorPolicy selectorPolicy,
	Set<ExternalDrawRuntimeDependency> runtimeDependencies
) {
	public ExternalDrawHostPipelineDescriptor {
		runtimeDependencies = Set.copyOf(runtimeDependencies);
	}
}
