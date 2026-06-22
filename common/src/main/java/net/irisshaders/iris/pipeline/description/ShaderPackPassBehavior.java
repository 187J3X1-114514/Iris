package net.irisshaders.iris.pipeline.description;

import net.irisshaders.iris.gl.blending.BlendModeOverride;
import net.irisshaders.iris.gl.framebuffer.ViewportData;

import java.util.Set;

public record ShaderPackPassBehavior(
	String blend,
	BlendModeOverride blendModeOverride,
	ViewportData viewportScale,
	Set<Integer> mipmappedInputs,
	Set<String> barrierRequirements,
	String clearIntent,
	String copyIntent,
	String trigger
) {
}
