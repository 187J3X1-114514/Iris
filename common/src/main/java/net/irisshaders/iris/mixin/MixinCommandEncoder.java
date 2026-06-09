package net.irisshaders.iris.mixin;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.CommandEncoderBackend;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import net.irisshaders.iris.mixinterface.IrisCommandEncoderBackend;
import net.irisshaders.iris.mixinterface.IrisCommandEncoderInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(CommandEncoder.class)
public abstract class MixinCommandEncoder implements IrisCommandEncoderInterface {
	@Shadow
	private CommandEncoderBackend backend;

	@Shadow
	protected abstract boolean isInRenderPass();

	@Override
	public void iris$generateMipmaps(GpuTexture texture, FilterMode requestedFilter) {
		if (this.isInRenderPass()) {
			throw new IllegalStateException("Close the existing render pass before performing additional commands");
		}

		if (texture.getMipLevels() <= 1) {
			return;
		}

		for (int level = 0; level < texture.getMipLevels() - 1; level++) {
			iris$blitTextureMip(texture, level, level + 1, requestedFilter);
		}
	}

	@Override
	public void iris$blitTextureMip(GpuTexture texture, int srcLevel, int dstLevel, FilterMode requestedFilter) {
		if (this.isInRenderPass()) {
			throw new IllegalStateException("Close the existing render pass before performing additional commands");
		}

		if (!(this.backend instanceof IrisCommandEncoderBackend irisBackend)) {
			throw new IllegalStateException("Current Blaze3D backend does not expose Iris mipmap blit support: " + this.backend.getClass().getName());
		}

		irisBackend.iris$blitTextureMip(texture, srcLevel, dstLevel, requestedFilter);
	}

	@Override
	public boolean iris$supportsBlitMipmaps(GpuFormat format, boolean depth) {
		return this.backend instanceof IrisCommandEncoderBackend irisBackend && irisBackend.iris$supportsBlitMipmaps(format, depth);
	}
}
