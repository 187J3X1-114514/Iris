package net.irisshaders.iris.shadows;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.irisshaders.iris.features.FeatureFlags;
import net.irisshaders.iris.gl.IrisRenderSystem;
import net.irisshaders.iris.gl.blending.BlendModeOverride;
import net.irisshaders.iris.gl.blending.BlendModeStorage;
import net.irisshaders.iris.gl.buffer.ShaderStorageBufferHolder;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.gl.framebuffer.ViewportData;
import net.irisshaders.iris.gl.image.GlImage;
import net.irisshaders.iris.gl.program.ComputeProgram;
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.gl.program.ProgramBuilder;
import net.irisshaders.iris.gl.program.ProgramSamplers;
import net.irisshaders.iris.gl.program.ProgramUniforms;
import net.irisshaders.iris.gl.state.FogMode;
import net.irisshaders.iris.gl.texture.TextureAccess;
import net.irisshaders.iris.mixinterface.CustomPass;
import net.irisshaders.iris.pathways.FullScreenQuadRenderer;
import net.irisshaders.iris.pipeline.CompositeRenderer;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.pipeline.description.ShaderPackPass;
import net.irisshaders.iris.pipeline.description.ShaderPackPassLayout;
import net.irisshaders.iris.pipeline.description.ShaderPackPassStage;
import net.irisshaders.iris.pipeline.description.ShaderPackPassType;
import net.irisshaders.iris.pipeline.description.ShaderPackProgramDescriptor;
import net.irisshaders.iris.pipeline.description.ShaderPackResourceView;
import net.irisshaders.iris.pipeline.description.ShaderPackRuntimePassSnapshot;
import net.irisshaders.iris.pipeline.transform.PatchShaderType;
import net.irisshaders.iris.pipeline.transform.ShaderPrinter;
import net.irisshaders.iris.pipeline.transform.TransformPatcher;
import net.irisshaders.iris.samplers.IrisImages;
import net.irisshaders.iris.samplers.IrisSamplers;
import net.irisshaders.iris.shaderpack.FilledIndirectPointer;
import net.irisshaders.iris.shaderpack.programs.ComputeSource;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.shaderpack.texture.TextureStage;
import net.irisshaders.iris.targets.RenderTarget;
import net.irisshaders.iris.uniforms.CommonUniforms;
import net.irisshaders.iris.uniforms.FrameUpdateNotifier;
import net.irisshaders.iris.uniforms.custom.CustomUniforms;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL43C;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

public class ShadowCompositeRenderer {
	private final ShadowRenderTargets renderTargets;

	private final ImmutableList<Pass> passes;
	private final TextureAccess noiseTexture;
	private final Object2ObjectMap<String, TextureAccess> customTextureIds;
	private final ImmutableSet<Integer> flippedAtLeastOnceFinal;
	private final CustomUniforms customUniforms;
	private final Object2ObjectMap<String, TextureAccess> irisCustomTextures;
	private final WorldRenderingPipeline pipeline;
	private final Set<GlImage> irisCustomImages;

	public ShadowCompositeRenderer(WorldRenderingPipeline pipeline, List<ShaderPackPass> passDescriptions, Map<String, ProgramSource> sourcesByName, Map<String, ComputeSource> computesByName, ImmutableSet<Integer> finalFlippedBuffers, ShadowRenderTargets renderTargets, ShaderStorageBufferHolder holder,
	                               TextureAccess noiseTexture, FrameUpdateNotifier updateNotifier,
	                               Object2ObjectMap<String, TextureAccess> customTextureIds, Set<GlImage> customImages, Object2ObjectMap<String, TextureAccess> irisCustomTextures, CustomUniforms customUniforms) {
		this.pipeline = pipeline;
		this.noiseTexture = noiseTexture;
		this.renderTargets = renderTargets;
		this.customTextureIds = customTextureIds;
		this.irisCustomTextures = irisCustomTextures;
		this.irisCustomImages = customImages;
		this.customUniforms = customUniforms;

		final ImmutableList.Builder<Pass> passes = ImmutableList.builder();
		final ImmutableSet.Builder<Integer> flippedAtLeastOnce = new ImmutableSet.Builder<>();

		for (ShaderPackPass passDescription : passDescriptions) {
			ShaderPackPassLayout layout = passDescription.layout();
			ImmutableSet<Integer> stageReadsFromAlt = stageReadsFromAlt(layout);
			ImmutableSet<Integer> flippedAtLeastOnceSnapshot = ImmutableSet.copyOf(layout.flippedAtLeastOnceSnapshot());
			flippedAtLeastOnce.addAll(layout.flippedAtLeastOnceSnapshot());
			flippedAtLeastOnce.addAll(layout.resolvedFlips());

			if (passDescription.type() == ShaderPackPassType.COMPUTE) {
				ComputeOnlyPass pass = new ComputeOnlyPass();
				pass.id = passDescription.id();
				pass.name = passDescription.name();
				pass.stage = passDescription.stage();
				pass.type = passDescription.type();
				pass.computes = createComputes(computeSourcesFor(passDescription, computesByName), stageReadsFromAlt, flippedAtLeastOnceSnapshot, renderTargets, holder);
				pass.drawBuffers = layout.drawBuffers().clone();
				pass.attachmentMapping = Map.copyOf(layout.attachmentMapping());
				pass.explicitPreFlips = Map.copyOf(layout.explicitPreFlips());
				pass.explicitFlips = Map.copyOf(layout.explicitFlips());
				pass.resolvedFlips = ImmutableSet.copyOf(layout.resolvedFlips());
				pass.stageReadsFromAlt = stageReadsFromAlt;
				pass.viewportScale = passDescription.behavior().viewportScale();
				pass.mipmappedBuffers = ImmutableSet.copyOf(passDescription.behavior().mipmappedInputs());
				pass.flippedAtLeastOnce = flippedAtLeastOnceSnapshot;
				pass.derivedFramebufferKey = layout.derivedFramebufferKey();
				passes.add(pass);
				continue;
			}

			Pass pass = new Pass();
			ProgramSource source = sourceFor(passDescription, sourcesByName);
			int[] drawBuffers = layout.drawBuffers().clone();

			pass.id = passDescription.id();
			pass.name = passDescription.name();
			pass.stage = passDescription.stage();
			pass.type = passDescription.type();
			pass.program = createProgram(source, stageReadsFromAlt, flippedAtLeastOnceSnapshot, renderTargets);
			pass.blendModeOverride = passDescription.behavior().blendModeOverride();
			pass.computes = createComputes(computeSourcesFor(passDescription, computesByName), stageReadsFromAlt, flippedAtLeastOnceSnapshot, renderTargets, holder);
			GlFramebuffer framebuffer = renderTargets.createColorFramebuffer(stageReadsFromAlt, drawBuffers);

			pass.drawBuffers = drawBuffers;
			pass.attachmentMapping = Map.copyOf(layout.attachmentMapping());
			pass.explicitPreFlips = Map.copyOf(layout.explicitPreFlips());
			pass.explicitFlips = Map.copyOf(layout.explicitFlips());
			pass.resolvedFlips = ImmutableSet.copyOf(layout.resolvedFlips());
			pass.stageReadsFromAlt = stageReadsFromAlt;
			pass.framebuffer = framebuffer;
			pass.viewportScale = passDescription.behavior().viewportScale();
			pass.mipmappedBuffers = ImmutableSet.copyOf(passDescription.behavior().mipmappedInputs());
			pass.flippedAtLeastOnce = flippedAtLeastOnceSnapshot;
			pass.derivedFramebufferKey = layout.derivedFramebufferKey();

			passes.add(pass);
		}

		this.passes = passes.build();
		this.flippedAtLeastOnceFinal = flippedAtLeastOnce.build();

		syncFinalFlips(finalFlippedBuffers);

		GlStateManager._glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, 0);
	}

	private void syncFinalFlips(ImmutableSet<Integer> finalFlippedBuffers) {
		for (int buffer = 0; buffer < renderTargets.getRenderTargetCount(); buffer++) {
			if (renderTargets.isFlipped(buffer) != finalFlippedBuffers.contains(buffer)) {
				renderTargets.flip(buffer);
			}
		}
	}

	private static ProgramSource sourceFor(ShaderPackPass pass, Map<String, ProgramSource> sourcesByName) {
		for (ShaderPackProgramDescriptor descriptor : pass.programDescriptors()) {
			if (!descriptor.computeSources().isEmpty()) {
				continue;
			}

			ProgramSource source = sourcesByName.get(descriptor.sourceName());
			if (source != null) {
				return source;
			}
		}

		throw new IllegalStateException("Missing shadow composite program source for pass " + pass.id());
	}

	private static ComputeSource[] computeSourcesFor(ShaderPackPass pass, Map<String, ComputeSource> computesByName) {
		return pass.programDescriptors().stream()
			.filter(descriptor -> !descriptor.computeSources().isEmpty())
			.map(descriptor -> {
				ComputeSource source = computesByName.get(descriptor.sourceName());
				if (source == null) {
					throw new IllegalStateException("Missing shadow composite compute source " + descriptor.sourceName() + " for pass " + pass.id());
				}
				return source;
			})
			.toArray(ComputeSource[]::new);
	}

	private static ImmutableSet<Integer> stageReadsFromAlt(ShaderPackPassLayout layout) {
		ImmutableSet.Builder<Integer> flipped = ImmutableSet.builder();

		layout.bufferInputViews().forEach((resource, view) -> {
			if (view == ShaderPackResourceView.ALT && resource.startsWith("shadowcolor")) {
				flipped.add(Integer.parseInt(resource.substring("shadowcolor".length())));
			}
		});

		return flipped.build();
	}

	private static void setupMipmapping(net.irisshaders.iris.targets.RenderTarget target, boolean readFromAlt) {
		int texture = readFromAlt ? target.getAltTexture() : target.getMainTexture();

		// TODO: Only generate the mipmap if a valid mipmap hasn't been generated or if we've written to the buffer
		// (since the last mipmap was generated)
		//
		// NB: We leave mipmapping enabled even if the buffer is written to again, this appears to match the
		// behavior of ShadersMod/OptiFine, however I'm not sure if it's desired behavior. It's possible that a
		// program could use mipmapped sampling with a stale mipmap, which probably isn't great. However, the
		// sampling mode is always reset between frames, so this only persists after the first program to use
		// mipmapping on this buffer.
		//
		// Also note that this only applies to one of the two buffers in a render target buffer pair - making it
		// unlikely that this issue occurs in practice with most shader packs.
		IrisRenderSystem.generateMipmaps(texture, GL20C.GL_TEXTURE_2D);
		IrisRenderSystem.texParameteri(texture, GL20C.GL_TEXTURE_2D, GL20C.GL_TEXTURE_MIN_FILTER, target.getInternalFormat().getPixelFormat().isInteger() ? GL20C.GL_NEAREST_MIPMAP_NEAREST : GL20C.GL_LINEAR_MIPMAP_LINEAR);
	}

	private static void resetRenderTarget(RenderTarget target) {
		// Resets the sampling mode of the given render target and then unbinds it to prevent accidental sampling of it
		// elsewhere.

		int filter = GL20C.GL_LINEAR;
		if (target.getInternalFormat().getPixelFormat().isInteger()) {
			filter = GL20C.GL_NEAREST;
		}

		IrisRenderSystem.texParameteri(target.getMainTexture(), GL20C.GL_TEXTURE_2D, GL20C.GL_TEXTURE_MIN_FILTER, filter);
		IrisRenderSystem.texParameteri(target.getAltTexture(), GL20C.GL_TEXTURE_2D, GL20C.GL_TEXTURE_MIN_FILTER, filter);
	}

	public ImmutableSet<Integer> getFlippedAtLeastOnceFinal() {
		return this.flippedAtLeastOnceFinal;
	}

	public List<ShaderPackRuntimePassSnapshot> snapshotRuntimePasses() {
		ImmutableList.Builder<ShaderPackRuntimePassSnapshot> snapshots = ImmutableList.builder();

		for (Pass pass : passes) {
			snapshots.add(pass.snapshot());
		}

		return snapshots.build();
	}

	public void renderAll() {
		GpuBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS).getBuffer(6);
		com.mojang.blaze3d.IndexType type = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS).type();

		try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Shadow composites", Minecraft.getInstance().gameRenderer.mainRenderTarget().getColorTextureView(), Optional.empty())) {
			pass.setPipeline(CompositeRenderer.COMPOSITE_PIPELINE);
			pass.setVertexBuffer(0, FullScreenQuadRenderer.INSTANCE.getQuad().slice());
			pass.setIndexBuffer(indices, type);

			for (Pass renderPass : passes) {
				boolean ranCompute = false;
				for (ComputeProgram computeProgram : renderPass.computes) {
					if (computeProgram != null) {
						ranCompute = true;
						computeProgram.use();
						this.customUniforms.push(computeProgram);
						com.mojang.blaze3d.pipeline.RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
						computeProgram.dispatch(main.width, main.height);
					}
				}

				if (ranCompute) {
					IrisRenderSystem.memoryBarrier(GL43C.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT | GL43C.GL_TEXTURE_FETCH_BARRIER_BIT | GL43C.GL_SHADER_STORAGE_BARRIER_BIT);
				}

				Program.unbind();

				if (renderPass instanceof ComputeOnlyPass) {
					continue;
				}

				if (!renderPass.mipmappedBuffers.isEmpty()) {
					GlStateManager._activeTexture(GL15C.GL_TEXTURE0);

					for (int index : renderPass.mipmappedBuffers) {
						setupMipmapping(renderTargets.get(index), renderPass.stageReadsFromAlt.contains(index));
					}
				}

				pass.iris$setCustomPass(renderPass);

				float scaledWidth = renderTargets.getResolution() * renderPass.viewportScale.scale();
				float scaledHeight = renderTargets.getResolution() * renderPass.viewportScale.scale();
				int beginWidth = (int) (renderTargets.getResolution() * renderPass.viewportScale.viewportX());
				int beginHeight = (int) (renderTargets.getResolution() * renderPass.viewportScale.viewportY());
				GlStateManager._viewport(beginWidth, beginHeight, (int) scaledWidth, (int) scaledHeight);
				GlStateManager._scissorBox(beginWidth, beginHeight, (int) scaledWidth, (int) scaledHeight);
				GlStateManager._disableScissorTest();

				renderPass.framebuffer.bind();
				renderPass.program.use();

				this.customUniforms.push(renderPass.program);

				pass.drawIndexed(6, 1, 0, 0, 0);
			}
		}

		// Make sure to reset the viewport to how it was before... Otherwise weird issues could occur.
		ProgramUniforms.clearActiveUniforms();
		GlStateManager._glUseProgram(0);

		// TODO IMS: Apparantly we are not supposed to do this for shadowcomp...
		/*
		for (int i = 0; i < renderTargets.getRenderTargetCount(); i++) {
			// Reset mipmapping states at the end of the frame.
			if (renderTargets.get(i) != null) {
				resetRenderTarget(renderTargets.get(i));
			}
		}
		 */

		GlStateManager._activeTexture(GL15C.GL_TEXTURE0);
	}

	// TODO: Don't just copy this from DeferredWorldRenderingPipeline
	private Program createProgram(ProgramSource source, ImmutableSet<Integer> flipped, ImmutableSet<Integer> flippedAtLeastOnceSnapshot,
	                              ShadowRenderTargets targets) {
		// TODO: Properly handle empty shaders
		Map<PatchShaderType, String> transformed = TransformPatcher.patchComposite(
			source.getName(),
			source.getVertexSource().orElseThrow(NullPointerException::new),
			source.getGeometrySource().orElse(null),
			source.getFragmentSource().orElseThrow(NullPointerException::new), TextureStage.SHADOWCOMP, pipeline.getTextureMap());
		String vertex = transformed.get(PatchShaderType.VERTEX);
		String geometry = transformed.get(PatchShaderType.GEOMETRY);
		String fragment = transformed.get(PatchShaderType.FRAGMENT);
		ShaderPrinter.printProgram(source.getName()).addSources(transformed).print();

		Objects.requireNonNull(flipped);
		ProgramBuilder builder;

		try {
			builder = ProgramBuilder.begin(source.getName(), vertex, geometry, fragment,
				IrisSamplers.COMPOSITE_RESERVED_TEXTURE_UNITS);
		} catch (RuntimeException e) {
			// TODO: Better error handling
			throw new RuntimeException("Shader compilation failed for shadow composite " + source.getName() + "!", e);
		}

		ProgramSamplers.CustomTextureSamplerInterceptor customTextureSamplerInterceptor = ProgramSamplers.customTextureSamplerInterceptor(builder, customTextureIds, flippedAtLeastOnceSnapshot);

		CommonUniforms.addDynamicUniforms(builder, FogMode.OFF);
		this.customUniforms.assignTo(builder);

		IrisSamplers.addNoiseSampler(customTextureSamplerInterceptor, noiseTexture);
		IrisSamplers.addCustomTextures(customTextureSamplerInterceptor, irisCustomTextures);

		IrisSamplers.addShadowSamplers(customTextureSamplerInterceptor, targets, flipped, pipeline.hasFeature(FeatureFlags.SEPARATE_HARDWARE_SAMPLERS));
		IrisImages.addShadowColorImages(builder, targets, flipped);
		IrisImages.addCustomImages(builder, irisCustomImages);
		IrisSamplers.addCustomImages(builder, irisCustomImages);
		Program build = builder.build();
		this.customUniforms.mapholderToPass(builder, build);

		return build;
	}

	private ComputeProgram[] createComputes(ComputeSource[] sources, ImmutableSet<Integer> flipped, ImmutableSet<Integer> flippedAtLeastOnceSnapshot,
	                                        ShadowRenderTargets targets, ShaderStorageBufferHolder holder) {
		ComputeProgram[] programs = new ComputeProgram[sources.length];
		for (int i = 0; i < programs.length; i++) {
			ComputeSource source = sources[i];
			if (source == null || source.getSource().isEmpty()) {
			} else {
				Objects.requireNonNull(flipped);
				ProgramBuilder builder;

				try {
					String transformed = TransformPatcher.patchCompute(source.getName(), source.getSource().orElse(null), TextureStage.SHADOWCOMP, pipeline.getTextureMap());

					ShaderPrinter.printProgram(source.getName()).addSource(PatchShaderType.COMPUTE, transformed).print();

					builder = ProgramBuilder.beginCompute(source.getName(), transformed, IrisSamplers.COMPOSITE_RESERVED_TEXTURE_UNITS);
				} catch (RuntimeException e) {
					// TODO: Better error handling
					throw new RuntimeException("Shader compilation failed for shadowcomp compute " + source.getName() + "!", e);
				}

				ProgramSamplers.CustomTextureSamplerInterceptor customTextureSamplerInterceptor = ProgramSamplers.customTextureSamplerInterceptor(builder, customTextureIds, flippedAtLeastOnceSnapshot);

				CommonUniforms.addDynamicUniforms(builder, FogMode.OFF);
				this.customUniforms.assignTo(builder);
				IrisSamplers.addNoiseSampler(customTextureSamplerInterceptor, noiseTexture);
				IrisSamplers.addCustomTextures(customTextureSamplerInterceptor, irisCustomTextures);

				IrisSamplers.addShadowSamplers(customTextureSamplerInterceptor, targets, flipped, pipeline.hasFeature(FeatureFlags.SEPARATE_HARDWARE_SAMPLERS));
				IrisImages.addShadowColorImages(builder, targets, flipped);

				IrisImages.addCustomImages(builder, irisCustomImages);
				IrisSamplers.addCustomImages(builder, irisCustomImages);
				programs[i] = builder.buildCompute();

				this.customUniforms.mapholderToPass(builder, programs[i]);


				programs[i].setWorkGroupInfo(source.getWorkGroupRelative(), source.getWorkGroups(), FilledIndirectPointer.basedOff(holder, source.getIndirectPointer()));
			}
		}

		return programs;
	}

	public void destroy() {
		for (Pass renderPass : passes) {
			renderPass.destroy();
		}
	}

	private static class Pass implements CustomPass {
		String id;
		String name;
		ShaderPackPassStage stage;
		ShaderPackPassType type;
		Program program;
		BlendModeOverride blendModeOverride;
		GlFramebuffer framebuffer;
		int[] drawBuffers;
		Map<Integer, Integer> attachmentMapping;
		Map<Integer, Boolean> explicitPreFlips;
		Map<Integer, Boolean> explicitFlips;
		ImmutableSet<Integer> resolvedFlips;
		ImmutableSet<Integer> flippedAtLeastOnce;
		ImmutableSet<Integer> stageReadsFromAlt;
		ImmutableSet<Integer> mipmappedBuffers;
		ViewportData viewportScale;
		ComputeProgram[] computes;
		String derivedFramebufferKey;

		protected void destroy() {
			this.program.destroy();
			for (ComputeProgram compute : this.computes) {
				if (compute != null) {
					compute.destroy();
				}
			}
		}

		@Override
		public void setupState() {
			framebuffer.bind();
			if (blendModeOverride != null) {
				blendModeOverride.apply();
			} else {
				BlendModeStorage.restoreBlend();
				GlStateManager._disableBlend(0);
			}
		}

		ShaderPackRuntimePassSnapshot snapshot() {
			return new ShaderPackRuntimePassSnapshot(
				id,
				name,
				stage,
				type,
				drawBuffers,
				attachmentMapping,
				explicitPreFlips,
				explicitFlips,
				resolvedFlips,
				stageReadsFromAlt,
				flippedAtLeastOnce,
				mipmappedBuffers,
				viewportScale,
				blendModeOverride != null,
				countComputes(computes),
				program != null,
				derivedFramebufferKey
			);
		}
	}

	private static class ComputeOnlyPass extends Pass {
		@Override
		protected void destroy() {
			for (ComputeProgram compute : this.computes) {
				if (compute != null) {
					compute.destroy();
				}
			}
		}
	}

	private static int countComputes(ComputeProgram[] computes) {
		int count = 0;

		for (ComputeProgram compute : computes) {
			if (compute != null) {
				count++;
			}
		}

		return count;
	}
}
