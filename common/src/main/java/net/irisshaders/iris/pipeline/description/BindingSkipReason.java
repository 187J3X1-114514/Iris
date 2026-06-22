package net.irisshaders.iris.pipeline.description;

public enum BindingSkipReason {
	NO_SAMPLER_HOLDER("no-sampler-holder"),
	NO_IMAGE_HOLDER("no-image-holder"),
	NO_WHITE_PIXEL("no-white-pixel"),
	NO_CENTER_DEPTH_SAMPLER("no-center-depth-sampler"),
	NO_SSBO_HOLDER("no-holder"),
	NOT_ACTIVE("not-active"),
	SHADOW_SAMPLER_NOT_INSTALLED("shadow-sampler-not-installed");

	private final String debugName;

	BindingSkipReason(String debugName) {
		this.debugName = debugName;
	}

	public String debugName() {
		return debugName;
	}
}
