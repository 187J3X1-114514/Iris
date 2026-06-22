package net.irisshaders.iris.pipeline.description;

import java.util.Arrays;
import java.util.Set;

public record DerivedFramebufferDescriptor(
	DerivedFramebufferKey key,
	DerivedFramebufferKind kind,
	String owner,
	Set<Integer> writesToMain,
	int[] drawBuffers,
	String source,
	String debugLabel
) {
	public DerivedFramebufferDescriptor {
		writesToMain = Set.copyOf(writesToMain);
		drawBuffers = drawBuffers.clone();
	}

	public int[] drawBuffers() {
		return drawBuffers.clone();
	}

	public String debugSummary() {
		return key.debugName() + "{kind=" + kind + ",owner=" + owner + ",writesToMain=" + writesToMain.stream().sorted().toList()
			+ ",drawBuffers=" + Arrays.toString(drawBuffers) + ",source=" + source + ",debugLabel=" + debugLabel + "}";
	}
}
