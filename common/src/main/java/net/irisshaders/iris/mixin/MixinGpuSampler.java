package net.irisshaders.iris.mixin;

import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.textures.GpuSampler;
import net.irisshaders.iris.mixinterface.IrisSamplerInterface;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(GpuSampler.class)
public abstract class MixinGpuSampler implements IrisSamplerInterface {
	@Unique
	private boolean iris$shadowCompare;

	@Unique
	private CompareOp iris$compareOp = CompareOp.LESS_THAN_OR_EQUAL;

	@Override
	public boolean iris$isShadowCompare() {
		return iris$shadowCompare;
	}

	@Override
	public CompareOp iris$getCompareOp() {
		return iris$compareOp;
	}

	@Override
	public void iris$setShadowCompare(CompareOp compareOp) {
		this.iris$shadowCompare = true;
		this.iris$compareOp = compareOp;
	}
}
