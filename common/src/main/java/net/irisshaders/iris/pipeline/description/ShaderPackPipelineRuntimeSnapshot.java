package net.irisshaders.iris.pipeline.description;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record ShaderPackPipelineRuntimeSnapshot(
	Map<ShaderPackPassStage, List<ShaderPackRuntimePassSnapshot>> passesByStage
) {
	public ShaderPackPipelineRuntimeSnapshot {
		passesByStage = passesByStage.entrySet().stream()
			.collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
	}
}
