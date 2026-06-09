package net.irisshaders.iris.mixin;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.vulkan.VulkanCommandEncoder;
import com.mojang.blaze3d.vulkan.VulkanConst;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanGpuTexture;
import net.irisshaders.iris.mixinterface.IrisCommandEncoderBackend;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRSynchronization2;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDependencyInfo;
import org.lwjgl.vulkan.VkFormatProperties;
import org.lwjgl.vulkan.VkImageBlit;
import org.lwjgl.vulkan.VkImageMemoryBarrier2;
import org.lwjgl.vulkan.VkImageSubresourceLayers;
import org.lwjgl.vulkan.VkImageSubresourceRange;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(VulkanCommandEncoder.class)
public abstract class MixinVulkanCommandEncoder implements IrisCommandEncoderBackend {
	@Shadow
	private VulkanDevice device;

	@Invoker("commandBuffer")
	protected abstract VkCommandBuffer iris$commandBuffer();

	@Override
	public void iris$blitTextureMip(GpuTexture texture, int srcLevel, int dstLevel, FilterMode requestedFilter) {
		int filter = iris$mipmapFilter(texture.getFormat(), requestedFilter);
		if (!iris$supportsBlitMipmaps(texture.getFormat(), texture.getFormat().hasDepthAspect(), filter == VK12.VK_FILTER_LINEAR)) {
			throw new IllegalStateException("Vulkan backend cannot blit mipmaps for " + texture.getFormat() + " on texture " + texture.getLabel());
		}

		VulkanGpuTexture vulkanTexture = (VulkanGpuTexture) texture;
		int aspectMask = iris$blitAspectMask(texture.getFormat());
		VkCommandBuffer commandBuffer = this.iris$commandBuffer();
		MemoryStack stack = MemoryStack.stackPush();

		try {
			iris$imageBarrier(commandBuffer, stack, vulkanTexture, aspectMask, Math.min(srcLevel, dstLevel), 2);

			VkImageSubresourceLayers srcSubresource = VkImageSubresourceLayers.calloc(stack);
			srcSubresource.aspectMask(aspectMask);
			srcSubresource.mipLevel(srcLevel);
			srcSubresource.baseArrayLayer(0);
			srcSubresource.layerCount(1);

			VkImageSubresourceLayers dstSubresource = VkImageSubresourceLayers.calloc(stack);
			dstSubresource.aspectMask(aspectMask);
			dstSubresource.mipLevel(dstLevel);
			dstSubresource.baseArrayLayer(0);
			dstSubresource.layerCount(1);

			VkImageBlit.Buffer region = VkImageBlit.calloc(1, stack);
			region.srcSubresource(srcSubresource);
			region.srcOffsets(0).set(0, 0, 0);
			region.srcOffsets(1).set(texture.getWidth(srcLevel), texture.getHeight(srcLevel), 1);
			region.dstSubresource(dstSubresource);
			region.dstOffsets(0).set(0, 0, 0);
			region.dstOffsets(1).set(texture.getWidth(dstLevel), texture.getHeight(dstLevel), 1);

			VK12.vkCmdBlitImage(commandBuffer, vulkanTexture.vkImage(), VK12.VK_IMAGE_LAYOUT_GENERAL, vulkanTexture.vkImage(), VK12.VK_IMAGE_LAYOUT_GENERAL, region, filter);
			iris$imageBarrier(commandBuffer, stack, vulkanTexture, aspectMask, Math.min(srcLevel, dstLevel), 2);
		} finally {
			stack.close();
		}
	}

	@Override
	public boolean iris$supportsBlitMipmaps(GpuFormat format, boolean depth) {
		return iris$supportsBlitMipmaps(format, depth, false);
	}

	@Unique
	private boolean iris$supportsBlitMipmaps(GpuFormat format, boolean depth, boolean linear) {
		int features = iris$optimalTilingFeatures(format);
		int required = VK12.VK_FORMAT_FEATURE_BLIT_SRC_BIT | VK12.VK_FORMAT_FEATURE_BLIT_DST_BIT;
		if (linear && !depth && !iris$isInteger(format)) {
			required |= VK12.VK_FORMAT_FEATURE_SAMPLED_IMAGE_FILTER_LINEAR_BIT;
		}
		return (features & required) == required;
	}

	@Unique
	private int iris$optimalTilingFeatures(GpuFormat format) {
		MemoryStack stack = MemoryStack.stackPush();

		try {
			VkFormatProperties properties = VkFormatProperties.calloc(stack);
			VK12.vkGetPhysicalDeviceFormatProperties(this.device.vkDevice().getPhysicalDevice(), VulkanConst.toVk(format), properties);
			return properties.optimalTilingFeatures();
		} finally {
			stack.close();
		}
	}

	@Unique
	private static void iris$imageBarrier(VkCommandBuffer commandBuffer, MemoryStack stack, VulkanGpuTexture texture, int aspectMask, int baseMipLevel, int levelCount) {
		VkImageMemoryBarrier2.Buffer barrier = VkImageMemoryBarrier2.calloc(1, stack).sType$Default();
		barrier.srcStageMask(65536L);
		barrier.srcAccessMask(98304L);
		barrier.dstStageMask(65536L);
		barrier.dstAccessMask(98304L);
		barrier.oldLayout(VK12.VK_IMAGE_LAYOUT_GENERAL);
		barrier.newLayout(VK12.VK_IMAGE_LAYOUT_GENERAL);
		barrier.srcQueueFamilyIndex(VK12.VK_QUEUE_FAMILY_IGNORED);
		barrier.dstQueueFamilyIndex(VK12.VK_QUEUE_FAMILY_IGNORED);
		barrier.image(texture.vkImage());
		VkImageSubresourceRange range = barrier.subresourceRange();
		range.aspectMask(aspectMask);
		range.baseMipLevel(baseMipLevel);
		range.levelCount(levelCount);
		range.baseArrayLayer(0);
		range.layerCount(1);

		VkDependencyInfo dependencyInfo = VkDependencyInfo.calloc(stack).sType$Default();
		dependencyInfo.pImageMemoryBarriers(barrier);
		KHRSynchronization2.vkCmdPipelineBarrier2KHR(commandBuffer, dependencyInfo);
	}

	@Unique
	private static int iris$blitAspectMask(GpuFormat format) {
		if (format.hasDepthAspect()) {
			return VK12.VK_IMAGE_ASPECT_DEPTH_BIT;
		}

		return VulkanConst.formatAspectMask(format);
	}

	@Unique
	private static int iris$mipmapFilter(GpuFormat format, FilterMode requestedFilter) {
		if (format.hasDepthAspect() || iris$isInteger(format)) {
			return VK12.VK_FILTER_NEAREST;
		}

		return requestedFilter == FilterMode.LINEAR ? VK12.VK_FILTER_LINEAR : VK12.VK_FILTER_NEAREST;
	}

	@Unique
	private static boolean iris$isInteger(GpuFormat format) {
		return switch (format.componentType()) {
			case UINT_8, SINT_8, UINT_16, SINT_16, UINT_32, SINT_32 -> true;
			default -> false;
		};
	}
}
