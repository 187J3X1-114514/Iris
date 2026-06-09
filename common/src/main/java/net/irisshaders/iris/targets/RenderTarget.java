package net.irisshaders.iris.targets;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.irisshaders.iris.gl.GLDebug;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.sampler.GlSampler;
import net.irisshaders.iris.gl.texture.InternalTextureFormat;
import net.irisshaders.iris.gl.texture.PixelFormat;
import net.irisshaders.iris.gl.texture.PixelType;
import net.irisshaders.iris.mixinterface.GpuTextureInterface;
import org.joml.Vector2i;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL13C;
import org.lwjgl.opengl.GL43C;

public class RenderTarget {
	private final InternalTextureFormat internalFormat;
	private GpuTexture mainTexture;
	private GpuTexture altTexture;
	private GpuTextureView mainTextureView;
	private GpuTextureView altTextureView;
	private int width;
	private int height;
	private boolean isValid;
	private String name;
	private boolean allowsLinear;
	private boolean mipmapsOnAlt;
	private boolean mipmapsOnMain;

	public RenderTarget(Builder builder) {
		this.isValid = true;

		this.name = builder.name;
		this.internalFormat = builder.internalFormat;

		this.width = builder.width;
		this.height = builder.height;


		boolean isPixelFormatInteger = builder.internalFormat.getPixelFormat().isInteger();
		this.allowsLinear = !isPixelFormatInteger;
		this.mainTexture = createTexture(builder.width, builder.height, false);
		this.altTexture = createTexture(builder.width, builder.height, true);
		this.mainTextureView = RenderSystem.getDevice().createTextureView(this.mainTexture);
		this.altTextureView = RenderSystem.getDevice().createTextureView(this.altTexture);
		if (this.mainTexture instanceof GlTexture) {
			setupTexture(getMainTexture(), !isPixelFormatInteger);
			setupTexture(getAltTexture(), !isPixelFormatInteger);
		}

		// Clean up after ourselves
		// This is strictly defensive to ensure that other buggy code doesn't tamper with our textures
		IrisRenderSystem.bindTextureToUnit(GL11C.GL_TEXTURE_2D, 0, 0);
	}

	public static Builder builder() {
		return new Builder();
	}

	private GpuTexture createTexture(int width, int height, boolean alt) {
		GpuTexture texture = RenderSystem.getDevice().createTexture(name == null ? null : name + " " + (alt ? "alt" : "main"),
			GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT,
			this.internalFormat.toGpuFormat(), width, height, 1, maxSafeMipLevels(width, height));

		if (name != null && texture instanceof GlTexture) {
			GLDebug.nameObject(GL43C.GL_TEXTURE, ((GpuTextureInterface) texture).iris$getGlId(), name + " " + (alt ? "alt" : "main"));
		}

		return texture;
	}

	private static int maxSafeMipLevels(int width, int height) {
		int levels = 1;
		while ((width >> levels) > 0 && (height >> levels) > 0) {
			levels++;
		}
		return levels;
	}

	private void setupTexture(int texture, boolean allowsLinear) {
		IrisRenderSystem.texParameteri(texture, GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MIN_FILTER, allowsLinear ? GL11C.GL_LINEAR : GL11C.GL_NEAREST);
		IrisRenderSystem.texParameteri(texture, GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_MAG_FILTER, allowsLinear ? GL11C.GL_LINEAR : GL11C.GL_NEAREST);
		IrisRenderSystem.texParameteri(texture, GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_S, GL13C.GL_CLAMP_TO_EDGE);
		IrisRenderSystem.texParameteri(texture, GL11C.GL_TEXTURE_2D, GL11C.GL_TEXTURE_WRAP_T, GL13C.GL_CLAMP_TO_EDGE);
	}

	void resize(Vector2i textureScaleOverride) {
		this.resize(textureScaleOverride.x, textureScaleOverride.y);
	}

	// Package private, call CompositeRenderTargets#resizeIfNeeded instead.
	void resize(int width, int height) {
		requireValid();

		this.width = width;
		this.height = height;

		this.mainTextureView.close();
		this.altTextureView.close();
		this.mainTexture.close();
		this.altTexture.close();

		this.mainTexture = createTexture(width, height, false);
		this.altTexture = createTexture(width, height, true);
		this.mainTextureView = RenderSystem.getDevice().createTextureView(this.mainTexture);
		this.altTextureView = RenderSystem.getDevice().createTextureView(this.altTexture);
		if (this.mainTexture instanceof GlTexture) {
			setupTexture(getMainTexture(), this.allowsLinear);
			setupTexture(getAltTexture(), this.allowsLinear);
		}
	}

	public InternalTextureFormat getInternalFormat() {
		return internalFormat;
	}

	public int getMainTexture() {
		requireValid();

		return ((GpuTextureInterface) mainTexture).iris$getGlId();
	}

	public int getAltTexture() {
		requireValid();

		return ((GpuTextureInterface) altTexture).iris$getGlId();
	}

	public GpuTexture getMainGpuTexture() {
		requireValid();

		return mainTexture;
	}

	public GpuTexture getAltGpuTexture() {
		requireValid();

		return altTexture;
	}

	public GpuTextureView getMainTextureView() {
		requireValid();

		return mainTextureView;
	}

	public GpuTextureView getAltTextureView() {
		requireValid();

		return altTextureView;
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	public void destroy() {
		requireValid();
		isValid = false;

		mainTextureView.close();
		altTextureView.close();
		mainTexture.close();
		altTexture.close();
	}

	private void requireValid() {
		if (!isValid) {
			throw new IllegalStateException("Attempted to use a deleted composite render target");
		}
	}

	public GlSampler getAltSampler() {
		if (mipmapsOnAlt) {
			return allowsLinear ? GlSampler.MIPPED_LINEAR : GlSampler.MIPPED_NEAREST;
		}
		return allowsLinear ? GlSampler.LINEAR : GlSampler.NEAREST;
	}

	public GlSampler getMainSampler() {
		if (mipmapsOnMain) {
			return allowsLinear ? GlSampler.MIPPED_LINEAR : GlSampler.MIPPED_NEAREST;
		}
		return allowsLinear ? GlSampler.LINEAR : GlSampler.NEAREST;
	}

	public void turnOnMips(boolean alt) {
		if (alt) {
			this.mipmapsOnAlt = true;
		} else {
			this.mipmapsOnMain = true;
		}
	}

	public void turnOffMips(boolean alt) {
		if (alt) {
			this.mipmapsOnAlt = false;
		} else {
			this.mipmapsOnMain = false;
		}
	}

	public static class Builder {
		private InternalTextureFormat internalFormat = InternalTextureFormat.RGBA8;
		private int width = 0;
		private int height = 0;
		private PixelFormat format = PixelFormat.RGBA;
		private PixelType type = PixelType.UNSIGNED_BYTE;
		private String name = null;

		private Builder() {
			// No-op
		}

		public Builder setName(String name) {
			this.name = name;

			return this;
		}

		public Builder setInternalFormat(InternalTextureFormat format) {
			this.internalFormat = format;

			return this;
		}

		public Builder setDimensions(int width, int height) {
			if (width <= 0) {
				throw new IllegalArgumentException("Width must be greater than zero");
			}

			if (height <= 0) {
				throw new IllegalArgumentException("Height must be greater than zero");
			}

			this.width = width;
			this.height = height;

			return this;
		}

		public Builder setPixelFormat(PixelFormat pixelFormat) {
			this.format = pixelFormat;

			return this;
		}

		public Builder setPixelType(PixelType pixelType) {
			this.type = pixelType;

			return this;
		}

		public RenderTarget build() {
			return new RenderTarget(this);
		}
	}
}
