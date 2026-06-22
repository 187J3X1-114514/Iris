package net.irisshaders.iris.pipeline.description;

public record SsboBindingDirective(SsboBindingKind kind) {
	public static SsboBindingDirective of(SsboBindingKind kind) {
		return new SsboBindingDirective(kind);
	}

	public String debugName() {
		return kind.debugName();
	}
}
