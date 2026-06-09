package net.irisshaders.iris.gl.framebuffer;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.opengl.GlTextureView;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import net.irisshaders.iris.gl.GlResource;
import net.irisshaders.iris.gl.IrisRenderSystem;
import org.lwjgl.opengl.GL30C;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class GlFramebuffer extends GlResource {
	private final Int2IntMap attachments;
	private final Map<Integer, Supplier<GpuTextureView>> colorAttachmentSuppliers;
	private final int maxDrawBuffers;
	private final int maxColorAttachments;
	private boolean hasDepthAttachment;

	public GlFramebuffer() {
		super(IrisRenderSystem.createFramebuffer());

		this.attachments = new Int2IntArrayMap();
		this.colorAttachmentSuppliers = new HashMap<>();
		this.maxDrawBuffers = GlStateManager._getInteger(GL30C.GL_MAX_DRAW_BUFFERS);
		this.maxColorAttachments = GlStateManager._getInteger(GL30C.GL_MAX_COLOR_ATTACHMENTS);
		this.hasDepthAttachment = false;
	}

	public void addDepthAttachment(GpuTexture texture) {
		int fb = getGlId();
		GlTexture glTexture = (GlTexture) texture;

		IrisRenderSystem.framebufferTexture2D(fb, GL30C.GL_FRAMEBUFFER, GL30C.GL_DEPTH_ATTACHMENT, GL30C.GL_TEXTURE_2D, glTexture.glId(), glTexture.fboMipLevel());

		this.hasDepthAttachment = true;
	}

	public void addDepthAttachment(GpuTextureView textureView) {
		int fb = getGlId();
		GlTextureView glTextureView = (GlTextureView) textureView;

		// TODO: NeoForge 1.21.5
		//if (texture.getFormat().hasStencilAspect()) {
		//	IrisRenderSystem.framebufferTexture2D(fb, GL30C.GL_FRAMEBUFFER, GL30C.GL_DEPTH_STENCIL_ATTACHMENT, GL30C.GL_TEXTURE_2D, texture, 0);
		//} else {
			IrisRenderSystem.framebufferTexture2D(fb, GL30C.GL_FRAMEBUFFER, GL30C.GL_DEPTH_ATTACHMENT, GL30C.GL_TEXTURE_2D, glTextureView.glId(), glTextureView.fboMipLevel());
		//}

		this.hasDepthAttachment = true;
	}

	public void addDepthAttachmentBypass(int texture) {
		int fb = getGlId();

		IrisRenderSystem.framebufferTexture2D(fb, GL30C.GL_FRAMEBUFFER, GL30C.GL_DEPTH_ATTACHMENT, GL30C.GL_TEXTURE_2D, texture, 0);

		this.hasDepthAttachment = true;
	}

	public void addColorAttachment(int index, int texture) {
		int fb = getGlId();

		IrisRenderSystem.framebufferTexture2D(fb, GL30C.GL_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0 + index, GL30C.GL_TEXTURE_2D, texture, 0);
		attachments.put(index, texture);
		colorAttachmentSuppliers.remove(index);
	}

	public void addColorAttachment(int index, GpuTextureView textureView) {
		colorAttachmentSuppliers.remove(index);
		attachColorAttachment(index, textureView);
	}

	public void addColorAttachment(int index, Supplier<GpuTextureView> textureViewSupplier) {
		colorAttachmentSuppliers.put(index, textureViewSupplier);
		attachColorAttachment(index, textureViewSupplier.get());
	}

	private void attachColorAttachment(int index, GpuTextureView textureView) {
		int fb = getGlId();
		GlTextureView glTextureView = (GlTextureView) textureView;

		IrisRenderSystem.framebufferTexture2D(fb, GL30C.GL_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0 + index, GL30C.GL_TEXTURE_2D, glTextureView.glId(), glTextureView.fboMipLevel());
		attachments.put(index, glTextureView.glId());
	}

	public void refreshColorAttachments() {
		for (Map.Entry<Integer, Supplier<GpuTextureView>> entry : colorAttachmentSuppliers.entrySet()) {
			attachColorAttachment(entry.getKey(), entry.getValue().get());
		}
	}

	public void noDrawBuffers() {
		IrisRenderSystem.drawBuffers(getGlId(), new int[]{GL30C.GL_NONE});
	}

	public void drawBuffers(int[] buffers) {
		int[] glBuffers = new int[buffers.length];
		int index = 0;

		if (buffers.length > maxDrawBuffers) {
			throw new IllegalArgumentException("Cannot write to more than " + maxDrawBuffers + " draw buffers on this GPU");
		}

		for (int buffer : buffers) {
			if (buffer >= maxColorAttachments) {
				throw new IllegalArgumentException("Only " + maxColorAttachments + " color attachments are supported on this GPU, but an attempt was made to write to a color attachment with index " + buffer);
			}

			glBuffers[index++] = GL30C.GL_COLOR_ATTACHMENT0 + buffer;
		}

		IrisRenderSystem.drawBuffers(getGlId(), glBuffers);
	}

	public void readBuffer(int buffer) {
		IrisRenderSystem.readBuffer(getGlId(), GL30C.GL_COLOR_ATTACHMENT0 + buffer);
	}

	public int getColorAttachment(int index) {
		return attachments.get(index);
	}

	public boolean hasDepthAttachment() {
		return hasDepthAttachment;
	}

	public void bind() {
		GlStateManager._glBindFramebuffer(GL30C.GL_FRAMEBUFFER, getGlId());
	}

	public void bindAsReadBuffer() {
		GlStateManager._glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, getGlId());
	}

	public void bindAsDrawBuffer() {
		GlStateManager._glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, getGlId());
	}

	protected void destroyInternal() {
		GlStateManager._glDeleteFramebuffers(getGlId());
	}

	public int getStatus() {
		bind();

		return IrisRenderSystem.checkFramebufferStatus(GL30C.GL_FRAMEBUFFER);
	}

	public int getId() {
		return getGlId();
	}
}
