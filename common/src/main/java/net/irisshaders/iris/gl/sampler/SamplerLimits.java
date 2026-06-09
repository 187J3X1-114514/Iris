package net.irisshaders.iris.gl.sampler;

import com.mojang.blaze3d.opengl.GlStateManager;
import net.irisshaders.iris.gl.IrisRenderSystem;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL45C;

public class SamplerLimits {
	private static SamplerLimits instance;

	private SamplerLimits() {
	}

	public static SamplerLimits get() {
		if (instance == null) {
			instance = new SamplerLimits();
		}

		return instance;
	}

	public int getMaxTextureUnits() {
		return 32;
	}

	public int getMaxDrawBuffers() {
		return 8;
	}

	public int getMaxShaderStorageUnits() {
		return 64;
	}
}
