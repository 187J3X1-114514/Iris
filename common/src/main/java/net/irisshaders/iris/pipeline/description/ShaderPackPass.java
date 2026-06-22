package net.irisshaders.iris.pipeline.description;

import java.util.List;

public record ShaderPackPass(
	String id,
	String name,
	ShaderPackPassStage stage,
	ShaderPackPassType type,
	List<ShaderPackPassResource> inputs,
	List<ShaderPackPassResource> outputs,
	List<ShaderPackProgramDescriptor> programDescriptors,
	ShaderPackBindingDescriptor bindings,
	ShaderPackPassBehavior behavior,
	ShaderPackPassLayout layout
) {
}
