package net.irisshaders.iris.pipeline.description;

import java.util.List;

public record ShaderPackProgramDescriptor(
	String descriptorId,
	String sourceName,
	String programArrayId,
	String programId,
	String textureStage,
	boolean valid,
	String compileKey,
	List<String> computeSources
) {
	public static ShaderPackProgramDescriptor graphics(String descriptorId, String sourceName, String programArrayId, String programId, String textureStage, boolean valid) {
		return new ShaderPackProgramDescriptor(descriptorId, sourceName, programArrayId, programId, textureStage, valid, descriptorId, List.of());
	}

	public static ShaderPackProgramDescriptor compute(String descriptorId, String sourceName, String programArrayId, String textureStage) {
		return new ShaderPackProgramDescriptor(descriptorId, sourceName, programArrayId, null, textureStage, true, descriptorId, List.of(sourceName));
	}
}
