package net.irisshaders.iris.pipeline.description;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record ShaderPackPipeline(
	Map<ShaderPackPassStage, List<ShaderPackPass>> stages,
	List<ExternalDrawPhasePass> externalDrawPhases,
	ShaderPackPipelineResources resources,
	ShaderPackDebugIdentity debugIdentity,
	Set<Integer> flippedBeforeShadow,
	Set<Integer> flippedAfterPrepare,
	Set<Integer> flippedAfterTranslucent,
	Set<Integer> flippedAfterComposite,
	Set<Integer> compositeFlippedAtLeastOnce,
	Set<Integer> shadowCompositeFlippedAfter
) {
}
