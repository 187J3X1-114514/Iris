package net.irisshaders.iris.pipeline.description;

public record ImageBindingDirective(ImageBindingKind kind) {
	public static ImageBindingDirective of(ImageBindingKind kind) {
		return new ImageBindingDirective(kind);
	}

	public String debugName() {
		return kind.debugName();
	}
}
