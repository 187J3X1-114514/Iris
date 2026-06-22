package net.irisshaders.iris.pipeline.description;

public record SamplerBindingDirective(SamplerBindingKind kind) {
	public static SamplerBindingDirective of(SamplerBindingKind kind) {
		return new SamplerBindingDirective(kind);
	}

	public String debugName() {
		return kind.debugName();
	}
}
