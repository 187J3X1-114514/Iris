package net.irisshaders.iris.pipeline.description;

public enum SsboBindingKind {
	SHADER_STORAGE_BUFFERS("shader-storage-buffers");

	private final String debugName;

	SsboBindingKind(String debugName) {
		this.debugName = debugName;
	}

	public String debugName() {
		return debugName;
	}
}
