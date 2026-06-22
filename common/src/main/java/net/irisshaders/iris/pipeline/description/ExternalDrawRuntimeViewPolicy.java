package net.irisshaders.iris.pipeline.description;

public enum ExternalDrawRuntimeViewPolicy {
	NONE,
	WORLD_PHASE_CURRENT_VIEW,
	BEFORE_AFTER_TRANSLUCENT,
	HAND_STATE_WITH_PRE_HAND_DEPTH,
	SHADOW_RENDER_TARGETS
}
