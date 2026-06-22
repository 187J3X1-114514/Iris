package net.irisshaders.iris.pipeline.description;

public record ShaderPackDebugIdentity(
	String shaderPackName,
	String profile,
	String dimension,
	String featureFlags,
	String builderSchemaVersion,
	String resourceSchemaVersion,
	int renderWidth,
	int renderHeight,
	int shadowSize
) {
}
