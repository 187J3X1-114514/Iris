package net.irisshaders.iris.pipeline.description;

import java.util.Objects;

public record DerivedFramebufferKey(String id) {
	public DerivedFramebufferKey {
		Objects.requireNonNull(id, "id");
	}

	public static DerivedFramebufferKey of(String id) {
		return new DerivedFramebufferKey(id);
	}

	public String debugName() {
		return id;
	}

	@Override
	public String toString() {
		return id;
	}
}
