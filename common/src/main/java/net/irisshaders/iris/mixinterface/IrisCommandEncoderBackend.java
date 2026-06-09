package net.irisshaders.iris.mixinterface;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;

public interface IrisCommandEncoderBackend {
	void iris$blitTextureMip(GpuTexture texture, int srcLevel, int dstLevel, FilterMode requestedFilter);

	boolean iris$supportsBlitMipmaps(GpuFormat format, boolean depth);
}
