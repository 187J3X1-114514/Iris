package net.irisshaders.iris.pipeline.description;

public record BindingSkip(
	BindingTarget target,
	String directive,
	BindingSkipReason reason
) {
	public static BindingSkip sampler(SamplerBindingDirective directive, BindingSkipReason reason) {
		return new BindingSkip(BindingTarget.SAMPLER, directive.debugName(), reason);
	}

	public static BindingSkip image(ImageBindingDirective directive, BindingSkipReason reason) {
		return new BindingSkip(BindingTarget.IMAGE, directive.debugName(), reason);
	}

	public static BindingSkip ssbo(SsboBindingDirective directive, BindingSkipReason reason) {
		return new BindingSkip(BindingTarget.SSBO, directive.debugName(), reason);
	}

	public String debugName() {
		return target.name().toLowerCase(java.util.Locale.ROOT) + ":" + directive + ":" + reason.debugName();
	}
}
