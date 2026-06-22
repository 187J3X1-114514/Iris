package net.irisshaders.iris.pipeline.description;

import java.util.List;
import java.util.Map;

public record ShaderPackPipeline(
	Map<ShaderPackPassStage, List<ShaderPackPass>> stages,
	List<ExternalDrawPhasePass> externalDrawPhases,
	ShaderPackPipelineResources resources,
	ShaderPackDebugIdentity debugIdentity
) {
}
