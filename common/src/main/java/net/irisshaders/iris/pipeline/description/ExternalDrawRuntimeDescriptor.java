package net.irisshaders.iris.pipeline.description;

import net.irisshaders.iris.pipeline.programs.ShaderKey;

import java.util.Arrays;

public record ExternalDrawRuntimeDescriptor(
	ExternalDrawBoundary boundary,
	ShaderKey shaderKey,
	ShaderPackBindingDescriptor bindingDescriptor,
	DerivedFramebufferKey derivedFramebufferKey,
	int[] drawBuffers,
	int[] writesToMain,
	int[] writesToAlt,
	ExternalDrawDrawBufferSource drawBufferSource,
	ExternalDrawTargetSetPolicy targetSetPolicy,
	ExternalDrawRuntimeViewPolicy runtimeViewPolicy
) {
	public ExternalDrawRuntimeDescriptor {
		drawBuffers = drawBuffers.clone();
		writesToMain = writesToMain.clone();
		writesToAlt = writesToAlt.clone();
	}

	public int[] drawBuffers() {
		return drawBuffers.clone();
	}

	public int[] writesToMain() {
		return writesToMain.clone();
	}

	public int[] writesToAlt() {
		return writesToAlt.clone();
	}

	public String debugSummary() {
		return "ExternalDrawRuntimeDescriptor{boundary=" + boundary
			+ ",shaderKey=" + shaderKey
			+ ",bindingDescriptor=" + bindingDescriptor.descriptorId()
			+ ",derivedFramebufferKey=" + derivedFramebufferKey.debugName()
			+ ",drawBuffers=" + Arrays.toString(drawBuffers)
			+ ",writesToMain=" + Arrays.toString(writesToMain)
			+ ",writesToAlt=" + Arrays.toString(writesToAlt)
			+ ",drawBufferSource=" + drawBufferSource
			+ ",targetSetPolicy=" + targetSetPolicy
			+ ",runtimeViewPolicy=" + runtimeViewPolicy + "}";
	}
}
