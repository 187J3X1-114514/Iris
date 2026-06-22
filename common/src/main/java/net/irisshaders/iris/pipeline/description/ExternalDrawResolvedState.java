package net.irisshaders.iris.pipeline.description;

import com.google.common.collect.ImmutableSet;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.pipeline.WorldRenderingPhase;
import net.irisshaders.iris.pipeline.programs.ShaderKey;

import java.util.Set;

public record ExternalDrawResolvedState(
	WorldRenderingPhase worldPhase,
	ExternalDrawPhasePass phasePass,
	ExternalDrawRuntimeDescriptor descriptor,
	GlFramebuffer framebuffer,
	ImmutableSet<Integer> flippedView,
	ShaderKey shaderKey
) {
	public Set<Integer> flippedViewSet() {
		return flippedView;
	}
}
