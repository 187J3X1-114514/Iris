package net.irisshaders.iris.pipeline.description;

import java.util.List;

public record ShaderPackBindingDescriptor(
	String descriptorId,
	List<String> samplerPolicies,
	List<String> imagePolicies,
	List<String> ssboPolicies,
	List<String> customTexturePolicies,
	List<String> compatibilityResources
) {
}
