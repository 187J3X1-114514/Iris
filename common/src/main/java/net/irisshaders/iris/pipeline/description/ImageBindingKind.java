package net.irisshaders.iris.pipeline.description;

public enum ImageBindingKind {
	RENDER_TARGETS("render-target-images"),
	CUSTOM_IMAGES("custom-images"),
	SHADOW_COLOR("shadow-color-images"),
	SHADOW_COLOR_IF_SAMPLER_PRESENT("shadow-color-images-if-sampler-present"),
	SHADOW_COLOR_IF_IMAGE_PRESENT_OR_SHADOW_PASS("shadow-color-images-if-image-present-or-shadow-pass");

	private final String debugName;

	ImageBindingKind(String debugName) {
		this.debugName = debugName;
	}

	public String debugName() {
		return debugName;
	}
}
