package net.irisshaders.iris.mixinterface;

import com.mojang.blaze3d.platform.CompareOp;

public interface IrisSamplerInterface {
	default boolean iris$isShadowCompare() {
		return false;
	}

	default CompareOp iris$getCompareOp() {
		return CompareOp.LESS_THAN_OR_EQUAL;
	}

	default void iris$setShadowCompare(CompareOp compareOp) {
		throw new AssertionError("Not accessible.");
	}
}
