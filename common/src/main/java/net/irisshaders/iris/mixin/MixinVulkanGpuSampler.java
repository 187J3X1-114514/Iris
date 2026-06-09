package net.irisshaders.iris.mixin;

import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.vulkan.VulkanGpuSampler;
import net.irisshaders.iris.mixinterface.IrisSamplerInterface;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(VulkanGpuSampler.class)
public abstract class MixinVulkanGpuSampler implements IrisSamplerInterface {
	@Override
	public void iris$setShadowCompare(CompareOp compareOp) {
		IrisSamplerInterface.super.iris$setShadowCompare(compareOp);
		// Vulkan sampler compare state is immutable. Shadow compare Vulkan samplers must be
		// created through an Iris-owned sampler cache so this marker can select the right handle.
	}
}
