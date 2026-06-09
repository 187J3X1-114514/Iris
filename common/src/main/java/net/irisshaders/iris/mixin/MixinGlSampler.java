package net.irisshaders.iris.mixin;

import com.mojang.blaze3d.opengl.GlSampler;
import com.mojang.blaze3d.platform.CompareOp;
import net.irisshaders.iris.mixinterface.IrisSamplerInterface;
import org.lwjgl.opengl.GL33C;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(GlSampler.class)
public abstract class MixinGlSampler implements IrisSamplerInterface {
	@Shadow
	public abstract int getId();

	@Override
	public void iris$setShadowCompare(CompareOp compareOp) {
		IrisSamplerInterface.super.iris$setShadowCompare(compareOp);
		GL33C.glSamplerParameteri(this.getId(), GL33C.GL_TEXTURE_COMPARE_MODE, GL33C.GL_COMPARE_REF_TO_TEXTURE);
		GL33C.glSamplerParameteri(this.getId(), GL33C.GL_TEXTURE_COMPARE_FUNC, GL33C.GL_LEQUAL);
	}
}
