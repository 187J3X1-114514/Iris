package net.irisshaders.iris.pipeline.description;

public record ShaderPackPassResource(
	String logicalId,
	ShaderPackResourceKind kind,
	ShaderPackResourceView view,
	String usage
) {
}
