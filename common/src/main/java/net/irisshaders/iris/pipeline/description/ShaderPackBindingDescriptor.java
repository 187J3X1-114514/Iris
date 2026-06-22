package net.irisshaders.iris.pipeline.description;

import java.util.List;
import java.util.stream.Collectors;

public record ShaderPackBindingDescriptor(
	String descriptorId,
	String textureStage,
	List<SamplerBindingDirective> samplerBindings,
	List<ImageBindingDirective> imageBindings,
	List<SsboBindingDirective> ssboBindings,
	List<CompatibilityResourceDirective> compatibilityResources
) {
	public ShaderPackBindingDescriptor {
		samplerBindings = List.copyOf(samplerBindings);
		imageBindings = List.copyOf(imageBindings);
		ssboBindings = List.copyOf(ssboBindings);
		compatibilityResources = List.copyOf(compatibilityResources);
	}

	public boolean isEmpty() {
		return samplerBindings.isEmpty()
			&& imageBindings.isEmpty()
			&& ssboBindings.isEmpty()
			&& compatibilityResources.isEmpty();
	}

	public String debugSummary() {
		return descriptorId
			+ "{textureStage=" + textureStage
			+ ",samplers=" + debugNames(samplerBindings.stream().map(SamplerBindingDirective::debugName).toList())
			+ ",images=" + debugNames(imageBindings.stream().map(ImageBindingDirective::debugName).toList())
			+ ",ssbo=" + debugNames(ssboBindings.stream().map(SsboBindingDirective::debugName).toList())
			+ ",compatibility=" + debugNames(compatibilityResources.stream().map(CompatibilityResourceDirective::debugName).toList())
			+ "}";
	}

	public List<String> debugLines() {
		return List.of(
			"descriptorId=" + descriptorId,
			"textureStage=" + textureStage,
			"samplers=" + debugNames(samplerBindings.stream().map(SamplerBindingDirective::debugName).toList()),
			"images=" + debugNames(imageBindings.stream().map(ImageBindingDirective::debugName).toList()),
			"ssbo=" + debugNames(ssboBindings.stream().map(SsboBindingDirective::debugName).toList()),
			"compatibility=" + debugNames(compatibilityResources.stream().map(CompatibilityResourceDirective::debugName).toList())
		);
	}

	private static String debugNames(List<String> names) {
		return names.stream().sorted().collect(Collectors.toList()).toString();
	}
}
