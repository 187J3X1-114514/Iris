package net.irisshaders.iris.pipeline.description;

import net.irisshaders.iris.pipeline.WorldRenderingPhase;

import java.util.List;

public record ExternalDrawPhasePass(
	String id,
	WorldRenderingPhase worldPhase,
	String phaseClass,
	String shaderKeyPolicy,
	String hostPipelineSelectorDescriptor,
	String framebufferPolicy,
	String runtimeViewPolicy,
	String bindingPolicy,
	String vertexFormatPolicy,
	String pbrHookPolicy,
	String sodiumPolicy,
	boolean participatesInOverride,
	String passthroughReason,
	List<String> shaderKeys
) {
}
