package net.irisshaders.iris.pipeline.description;

import java.util.List;

public record ShaderPackPipelineResources(
	List<String> logicalRenderTargets,
	List<String> logicalShadowTargets,
	List<String> logicalSamplers,
	List<String> logicalImages,
	List<String> logicalSsbo,
	List<String> irisCompatibilityResources,
	List<String> derivedFramebufferKeys,
	List<String> mainTargetDependencies,
	List<String> resizeInvalidation,
	List<String> reloadInvalidation,
	List<String> destroyOwnership
) {
}
