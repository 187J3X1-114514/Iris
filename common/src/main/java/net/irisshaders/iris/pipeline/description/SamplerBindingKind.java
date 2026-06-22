package net.irisshaders.iris.pipeline.description;

public enum SamplerBindingKind {
	RENDER_TARGETS("render-target-samplers"),
	CUSTOM_TEXTURES("custom-textures"),
	CUSTOM_TEXTURES_INTERCEPTED("custom-textures-intercepted"),
	CUSTOM_IMAGES("custom-images"),
	NOISE("noise"),
	COMPOSITE_DEPTH("composite-depth-samplers"),
	WORLD_DEPTH("world-depth-samplers"),
	LEVEL("level-samplers"),
	PBR_DETECTION("pbr-detection"),
	SHADOW("shadow-samplers"),
	SHADOW_IF_PRESENT("shadow-samplers-if-present"),
	CENTER_DEPTH("center-depth-sampler");

	private final String debugName;

	SamplerBindingKind(String debugName) {
		this.debugName = debugName;
	}

	public String debugName() {
		return debugName;
	}
}
