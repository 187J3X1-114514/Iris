package net.irisshaders.iris.pipeline.description;

import net.irisshaders.iris.gl.framebuffer.ViewportData;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record ShaderPackRuntimePassSnapshot(
	String id,
	String name,
	ShaderPackPassStage stage,
	ShaderPackPassType type,
	int[] drawBuffers,
	Map<Integer, Integer> attachmentMapping,
	Map<Integer, Boolean> explicitPreFlips,
	Map<Integer, Boolean> explicitFlips,
	Set<Integer> resolvedFlips,
	Set<Integer> bufferReadsFromAlt,
	Set<Integer> flippedAtLeastOnceSnapshot,
	Set<Integer> mipmappedInputs,
	ViewportData viewportScale,
	boolean hasBlendOverride,
	int computeProgramCount,
	boolean hasGraphicsProgram,
	DerivedFramebufferKey derivedFramebufferKey
) {
	public ShaderPackRuntimePassSnapshot {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(stage, "stage");
		Objects.requireNonNull(type, "type");
		Objects.requireNonNull(drawBuffers, "drawBuffers");
		Objects.requireNonNull(attachmentMapping, "attachmentMapping");
		Objects.requireNonNull(explicitPreFlips, "explicitPreFlips");
		Objects.requireNonNull(explicitFlips, "explicitFlips");
		Objects.requireNonNull(resolvedFlips, "resolvedFlips");
		Objects.requireNonNull(bufferReadsFromAlt, "bufferReadsFromAlt");
		Objects.requireNonNull(flippedAtLeastOnceSnapshot, "flippedAtLeastOnceSnapshot");
		Objects.requireNonNull(mipmappedInputs, "mipmappedInputs");
		Objects.requireNonNull(viewportScale, "viewportScale");
		Objects.requireNonNull(derivedFramebufferKey, "derivedFramebufferKey");

		drawBuffers = drawBuffers.clone();
		attachmentMapping = Map.copyOf(attachmentMapping);
		explicitPreFlips = Map.copyOf(explicitPreFlips);
		explicitFlips = Map.copyOf(explicitFlips);
		resolvedFlips = Set.copyOf(resolvedFlips);
		bufferReadsFromAlt = Set.copyOf(bufferReadsFromAlt);
		flippedAtLeastOnceSnapshot = Set.copyOf(flippedAtLeastOnceSnapshot);
		mipmappedInputs = Set.copyOf(mipmappedInputs);
	}

	@Override
	public int[] drawBuffers() {
		return drawBuffers.clone();
	}
}
