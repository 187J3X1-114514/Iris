package net.irisshaders.iris.pipeline.description;

public enum CompatibilityResourceKind {
	WHITE_PIXEL("white-pixel"),
	BIGGER_WHITE_PIXEL("bigger-white-pixel"),
	NOISE_TEXTURE("noise-texture"),
	CENTER_DEPTH_SAMPLER("center-depth-sampler"),
	PBR_NORMAL_SPECULAR_SAMPLERS("pbr-normal-specular-samplers"),
	MAIN_TARGET_BRIDGE("main-target-bridge");

	private final String debugName;

	CompatibilityResourceKind(String debugName) {
		this.debugName = debugName;
	}

	public String debugName() {
		return debugName;
	}
}
