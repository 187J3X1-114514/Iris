package net.irisshaders.iris.pipeline.description;

public record CompatibilityResourceDirective(CompatibilityResourceKind kind) {
	public static CompatibilityResourceDirective of(CompatibilityResourceKind kind) {
		return new CompatibilityResourceDirective(kind);
	}

	public String debugName() {
		return kind.debugName();
	}
}
