package net.irisshaders.iris.pipeline;

import net.irisshaders.iris.pipeline.programs.ShaderKey;

public record ExternalDrawHostPipelineDescriptor(
	String descriptorId,
	String hostPipelineId,
	ShaderKey shaderKey,
	boolean shadow,
	String selectorPolicy,
	String runtimeDependencies,
	String passthroughReason
) {
}
