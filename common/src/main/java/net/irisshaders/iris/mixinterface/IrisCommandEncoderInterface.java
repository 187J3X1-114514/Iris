package net.irisshaders.iris.mixinterface;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;

public interface IrisCommandEncoderInterface {
	default void iris$generateMipmaps(GpuTexture texture, FilterMode requestedFilter) {
		throw new AssertionError("Not accessible.");
	}

	default void iris$blitTextureMip(GpuTexture texture, int srcLevel, int dstLevel, FilterMode requestedFilter) {
		throw new AssertionError("Not accessible.");
	}

	default boolean iris$supportsBlitMipmaps(GpuFormat format, boolean depth) {
		throw new AssertionError("Not accessible.");
	}
}
