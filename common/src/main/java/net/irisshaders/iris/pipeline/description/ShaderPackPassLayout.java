package net.irisshaders.iris.pipeline.description;

import java.util.Map;
import java.util.Set;

public record ShaderPackPassLayout(
	String passId,
	Map<String, ShaderPackResourceView> bufferInputViews,
	int[] drawBuffers,
	Map<Integer, Integer> attachmentMapping,
	Map<Integer, Boolean> explicitPreFlips,
	Map<Integer, Boolean> explicitFlips,
	Set<Integer> resolvedFlips,
	Set<Integer> flippedAtLeastOnceSnapshot,
	DerivedFramebufferKey derivedFramebufferKey
) {
}
