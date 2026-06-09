package net.irisshaders.iris.targets;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import net.irisshaders.iris.mixinterface.IrisCommandEncoderInterface;

public final class GpuMipmapGenerator {
	private GpuMipmapGenerator() {
	}

	public static void generate(GpuTexture texture, FilterMode requestedFilter) {
		if (texture == null || texture.getMipLevels() <= 1) {
			return;
		}

		((IrisCommandEncoderInterface) RenderSystem.getDevice().createCommandEncoder()).iris$generateMipmaps(texture, requestedFilter);
	}
}
