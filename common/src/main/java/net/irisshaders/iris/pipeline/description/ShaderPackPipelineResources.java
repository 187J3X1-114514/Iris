package net.irisshaders.iris.pipeline.description;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import net.irisshaders.iris.gl.buffer.ShaderStorageBufferHolder;
import net.irisshaders.iris.gl.framebuffer.GlFramebuffer;
import net.irisshaders.iris.gl.image.GlImage;
import net.irisshaders.iris.gl.image.ImageClearPass;
import net.irisshaders.iris.gl.image.ImageHolder;
import net.irisshaders.iris.gl.program.ProgramSamplers;
import net.irisshaders.iris.gl.sampler.SamplerHolder;
import net.irisshaders.iris.gl.texture.TextureAccess;
import net.irisshaders.iris.pipeline.WorldRenderingPipeline;
import net.irisshaders.iris.samplers.IrisImages;
import net.irisshaders.iris.samplers.IrisSamplers;
import net.irisshaders.iris.shaderpack.FilledIndirectPointer;
import net.irisshaders.iris.shaderpack.properties.IndirectPointer;
import net.irisshaders.iris.shaderpack.texture.TextureStage;
import net.irisshaders.iris.shadows.ShadowRenderTargets;
import net.irisshaders.iris.targets.RenderTarget;
import net.irisshaders.iris.targets.RenderTargets;
import net.minecraft.client.renderer.texture.AbstractTexture;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public record ShaderPackPipelineResources(
	List<String> logicalRenderTargets,
	List<String> logicalShadowTargets,
	List<String> logicalSamplers,
	List<String> logicalImages,
	List<String> logicalSsbo,
	List<String> irisCompatibilityResources,
	List<DerivedFramebufferDescriptor> derivedFramebuffers,
	List<String> mainTargetDependencies,
	List<String> resizeInvalidation,
	List<String> reloadInvalidation,
	List<String> destroyOwnership,
	List<String> debugRuntimeOwnerQueries,
	List<String> debugDerivedRuntimeObjects,
	List<String> debugProgramBindingDescriptors,
	List<ShaderPackBindingDescriptor> programBindingDescriptorDefinitions
) {
	public ShaderPackPipelineResources {
		logicalRenderTargets = List.copyOf(logicalRenderTargets);
		logicalShadowTargets = List.copyOf(logicalShadowTargets);
		logicalSamplers = List.copyOf(logicalSamplers);
		logicalImages = List.copyOf(logicalImages);
		logicalSsbo = List.copyOf(logicalSsbo);
		irisCompatibilityResources = List.copyOf(irisCompatibilityResources);
		derivedFramebuffers = List.copyOf(derivedFramebuffers);
		mainTargetDependencies = List.copyOf(mainTargetDependencies);
		resizeInvalidation = List.copyOf(resizeInvalidation);
		reloadInvalidation = List.copyOf(reloadInvalidation);
		destroyOwnership = List.copyOf(destroyOwnership);
		debugRuntimeOwnerQueries = List.copyOf(debugRuntimeOwnerQueries);
		debugDerivedRuntimeObjects = List.copyOf(debugDerivedRuntimeObjects);
		debugProgramBindingDescriptors = List.copyOf(debugProgramBindingDescriptors);
		programBindingDescriptorDefinitions = List.copyOf(programBindingDescriptorDefinitions);
	}

	public List<DerivedFramebufferKey> derivedFramebufferKeys() {
		return derivedFramebuffers.stream().map(DerivedFramebufferDescriptor::key).toList();
	}

	public List<String> debugDerivedFramebufferKeys() {
		return derivedFramebufferKeys().stream().map(DerivedFramebufferKey::debugName).toList();
	}

	public RuntimeBindings bindRuntime(RenderTargets renderTargets, Supplier<ShadowRenderTargets> shadowTargetsSupplier,
									   Set<GlImage> customImages, @Nullable ShaderStorageBufferHolder shaderStorageBufferHolder) {
		return new RuntimeBindings(this, renderTargets, shadowTargetsSupplier, customImages, shaderStorageBufferHolder);
	}

	public String ownerFor(String logicalId) {
		if (logicalId.startsWith("colortex") || logicalId.startsWith("depthtex")) {
			return "RenderTargets";
		}

		if (logicalId.startsWith("shadowcolor") || logicalId.startsWith("shadowtex")) {
			return "ShadowRenderTargets";
		}

		if (logicalId.startsWith("image:") || logicalId.startsWith("customImage:")) {
			return "custom image set";
		}

		if (logicalId.startsWith("ssbo")) {
			return "ShaderStorageBufferHolder";
		}

		if (logicalId.startsWith("main")) {
			return "Minecraft main target reference";
		}

		if (debugDerivedFramebufferKeys().contains(logicalId)) {
			return "derived framebuffer lookup";
		}

		if (irisCompatibilityResources.contains(logicalId)) {
			return "Iris runtime compatibility resource";
		}

		return "unknown";
	}

	public ProgramBindingInstallPlan installPlanFor(ShaderPackBindingDescriptor descriptor) {
		return new ProgramBindingInstallPlan(
			descriptor.descriptorId(),
			descriptor.samplerBindings().stream().map(this::samplerInstallStep).toList(),
			descriptor.imageBindings().stream().map(this::imageInstallStep).toList(),
			descriptor.ssboBindings().stream().map(this::ssboInstallStep).toList(),
			descriptor.compatibilityResources().stream().map(this::compatibilityInstallStep).toList()
		);
	}

	public ShaderPackBindingDescriptor bindingDescriptor(String descriptorId) {
		return programBindingDescriptorDefinitions.stream()
			.filter(descriptor -> descriptor.descriptorId().equals(descriptorId))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("Unknown shaderpack resource binding descriptor " + descriptorId));
	}

	public ShaderPackBindingDescriptor bindingDescriptorFor(ShaderPackPassStage stage, TextureStage textureStage) {
		return bindingDescriptor(bindingDescriptorId(stage, textureStage));
	}

	public static String bindingDescriptorId(ShaderPackPassStage stage, TextureStage textureStage) {
		return "bindings:" + stage.name().toLowerCase(Locale.ROOT) + ":" + textureStage.name().toLowerCase(Locale.ROOT);
	}

	public List<String> invalidationPlan(ResourceInvalidationReason reason) {
		return switch (reason) {
			case RESIZE -> resizeInvalidation;
			case MAIN_TARGET_VERSION_CHANGE -> List.of("mainTargetDependencies", "derivedFramebuffers", "finalHolder", "copyHolders", "depthAttachments");
			case SHADERPACK_RELOAD -> reloadInvalidation;
			case WORLD_SWITCH -> List.of("externalDrawRuntimeResolver", "activeProgramState", "sampler/image state");
			case DIMENSION_SWITCH -> List.of("rebuild pipeline/resources only when dimension-dependent directives change", "otherwise reset runtime resolver");
			case BEFORE_AFTER_TRANSLUCENT_BOUNDARY -> List.of("runtime view resolver", "pre-translucent depth copy", "external draw framebuffer policy");
			case HAND_BOUNDARY -> List.of("runtime hand resolver", "pre-hand depth copy", "hand framebuffer policy");
			case DESTROY -> destroyOwnership;
		};
	}

	public List<String> invalidationReasons() {
		return Arrays.stream(ResourceInvalidationReason.values()).map(Enum::name).toList();
	}

	private ProgramBindingInstallStep samplerInstallStep(SamplerBindingDirective directive) {
		BindingInstallerKind installer = switch (directive.kind()) {
			case RENDER_TARGETS -> BindingInstallerKind.IRIS_SAMPLERS_RENDER_TARGETS;
			case CUSTOM_TEXTURES, CUSTOM_TEXTURES_INTERCEPTED -> BindingInstallerKind.IRIS_SAMPLERS_CUSTOM_TEXTURES;
			case CUSTOM_IMAGES -> BindingInstallerKind.IRIS_SAMPLERS_CUSTOM_IMAGES;
			case NOISE -> BindingInstallerKind.IRIS_SAMPLERS_NOISE;
			case COMPOSITE_DEPTH -> BindingInstallerKind.IRIS_SAMPLERS_COMPOSITE_DEPTH;
			case WORLD_DEPTH -> BindingInstallerKind.IRIS_SAMPLERS_WORLD_DEPTH;
			case LEVEL -> BindingInstallerKind.IRIS_SAMPLERS_LEVEL;
			case PBR_DETECTION -> BindingInstallerKind.IRIS_SAMPLERS_PBR_DETECTION;
			case SHADOW, SHADOW_IF_PRESENT -> BindingInstallerKind.IRIS_SAMPLERS_SHADOW;
			case CENTER_DEPTH -> BindingInstallerKind.IRIS_SAMPLERS_CENTER_DEPTH;
		};
		return new ProgramBindingInstallStep(BindingTarget.SAMPLER, installer, directive.debugName(), "ProgramBindingContext", conditionalRule(directive.kind()));
	}

	private ProgramBindingInstallStep imageInstallStep(ImageBindingDirective directive) {
		BindingInstallerKind installer = switch (directive.kind()) {
			case RENDER_TARGETS -> BindingInstallerKind.IRIS_IMAGES_RENDER_TARGETS;
			case CUSTOM_IMAGES -> BindingInstallerKind.IRIS_IMAGES_CUSTOM_IMAGES;
			case SHADOW_COLOR, SHADOW_COLOR_IF_SAMPLER_PRESENT, SHADOW_COLOR_IF_IMAGE_PRESENT_OR_SHADOW_PASS -> BindingInstallerKind.IRIS_IMAGES_SHADOW_COLOR;
		};
		return new ProgramBindingInstallStep(BindingTarget.IMAGE, installer, directive.debugName(), "ProgramBindingContext.images", conditionalRule(directive.kind()));
	}

	private ProgramBindingInstallStep ssboInstallStep(SsboBindingDirective directive) {
		return new ProgramBindingInstallStep(BindingTarget.SSBO, BindingInstallerKind.SHADER_STORAGE_BUFFER_HOLDER, directive.debugName(), "ShaderStorageBufferHolder", "holder-present");
	}

	private ProgramBindingInstallStep compatibilityInstallStep(CompatibilityResourceDirective directive) {
		return new ProgramBindingInstallStep(BindingTarget.COMPATIBILITY, BindingInstallerKind.COMPATIBILITY_RESOURCE, directive.debugName(), ownerFor(directive.debugName()), conditionalRule(directive.kind()));
	}

	private static String conditionalRule(SamplerBindingKind kind) {
		return switch (kind) {
			case SHADOW_IF_PRESENT -> "install only when shadow samplers are active";
			case CENTER_DEPTH -> "install only when center depth sampler exists";
			default -> "always";
		};
	}

	private static String conditionalRule(ImageBindingKind kind) {
		return switch (kind) {
			case SHADOW_COLOR_IF_SAMPLER_PRESENT -> "install only after shadow sampler install";
			case SHADOW_COLOR_IF_IMAGE_PRESENT_OR_SHADOW_PASS -> "install for shadow pass or existing shadow image uniforms";
			default -> "always";
		};
	}

	private static String conditionalRule(CompatibilityResourceKind kind) {
		return kind == CompatibilityResourceKind.PBR_NORMAL_SPECULAR_SAMPLERS ? "record only when PBR samplers detected" : "always";
	}

	public enum ResourceInvalidationReason {
		RESIZE,
		MAIN_TARGET_VERSION_CHANGE,
		SHADERPACK_RELOAD,
		WORLD_SWITCH,
		DIMENSION_SWITCH,
		BEFORE_AFTER_TRANSLUCENT_BOUNDARY,
		HAND_BOUNDARY,
		DESTROY
	}

	public record ProgramBindingInstallPlan(
		String descriptorId,
		List<ProgramBindingInstallStep> samplerInstallers,
		List<ProgramBindingInstallStep> imageInstallers,
		List<ProgramBindingInstallStep> ssboInstallers,
		List<ProgramBindingInstallStep> compatibilityInstallers
	) {
	}

	public static final class RuntimeBindings {
		private final ShaderPackPipelineResources resources;
		private final RenderTargets renderTargets;
		private final Supplier<ShadowRenderTargets> shadowTargetsSupplier;
		private final Map<String, GlImage> customImagesByName;
		private final List<RuntimeBindingInstallRecord> bindingInstallRecords;
		private final List<DerivedResourceRecord> derivedResourceRecords;
		private final List<ResourceLifecycleEvent> lifecycleEvents;
		@Nullable
		private final ShaderStorageBufferHolder shaderStorageBufferHolder;

		private RuntimeBindings(ShaderPackPipelineResources resources, RenderTargets renderTargets, Supplier<ShadowRenderTargets> shadowTargetsSupplier,
								Set<GlImage> customImages, @Nullable ShaderStorageBufferHolder shaderStorageBufferHolder) {
			this.resources = resources;
			this.renderTargets = renderTargets;
			this.shadowTargetsSupplier = shadowTargetsSupplier;
			this.customImagesByName = new LinkedHashMap<>();
			for (GlImage image : customImages) {
				this.customImagesByName.put(image.getName(), image);
			}
			this.bindingInstallRecords = new ArrayList<>();
			this.derivedResourceRecords = new ArrayList<>();
			this.lifecycleEvents = new ArrayList<>();
			this.shaderStorageBufferHolder = shaderStorageBufferHolder;
			recordLifecycle(ResourceInvalidationReason.SHADERPACK_RELOAD, "runtime resources bound for shaderpack pipeline");
			recordLifecycle(ResourceInvalidationReason.WORLD_SWITCH, "runtime resources scoped to active world context");
			recordLifecycle(ResourceInvalidationReason.DIMENSION_SWITCH, "runtime resources scoped to active dimension context");
		}

		public GlFramebuffer createColorFramebuffer(ShaderPackPass pass) {
			DerivedFramebufferDescriptor descriptor = derivedFramebuffer(pass.layout().derivedFramebufferKey());
			recordDerived(descriptor.key(), descriptor.kind(), descriptor.owner(), pass.id());
			return renderTargets.createColorFramebuffer(stageWritesToMain(pass.layout(), "colortex"), pass.layout().drawBuffers().clone());
		}

		public GlFramebuffer createColorFramebuffer(DerivedFramebufferKey derivedFramebufferKey, Set<Integer> stageReadsFromAlt, int[] drawBuffers) {
			DerivedFramebufferDescriptor descriptor = derivedFramebuffer(derivedFramebufferKey);
			recordDerived(descriptor.key(), descriptor.kind(), descriptor.owner(), "runtime-rebuild");
			return renderTargets.createColorFramebuffer(ImmutableSet.copyOf(stageReadsFromAlt), drawBuffers.clone());
		}

		public GlFramebuffer createShadowColorFramebuffer(ShaderPackPass pass) {
			DerivedFramebufferDescriptor descriptor = derivedFramebuffer(pass.layout().derivedFramebufferKey());
			recordDerived(descriptor.key(), descriptor.kind(), descriptor.owner(), pass.id());
			return shadowTargets().createColorFramebuffer(stageWritesToMain(pass.layout(), "shadowcolor"), pass.layout().drawBuffers().clone());
		}

		public GlFramebuffer createShadowColorFramebuffer(DerivedFramebufferKey derivedFramebufferKey, Set<Integer> stageReadsFromAlt, int[] drawBuffers) {
			DerivedFramebufferDescriptor descriptor = derivedFramebuffer(derivedFramebufferKey);
			recordDerived(descriptor.key(), descriptor.kind(), descriptor.owner(), "runtime-rebuild");
			return shadowTargets().createColorFramebuffer(ImmutableSet.copyOf(stageReadsFromAlt), drawBuffers.clone());
		}

		public GlFramebuffer createGbufferFramebuffer(Set<Integer> stageReadsFromAlt, int[] drawBuffers) {
			DerivedFramebufferKey key = runtimeFramebufferKey(DerivedFramebufferKind.GBUFFERS_EXTERNAL_DRAW, stageReadsFromAlt, drawBuffers);
			recordDerived(key, DerivedFramebufferKind.GBUFFERS_EXTERNAL_DRAW, "RenderTargets", "external-draw-runtime");
			return renderTargets.createGbufferFramebuffer(ImmutableSet.copyOf(stageReadsFromAlt), drawBuffers.clone());
		}

		public GlFramebuffer createDHFramebuffer(Set<Integer> stageReadsFromAlt, int[] drawBuffers) {
			DerivedFramebufferKey key = runtimeFramebufferKey(DerivedFramebufferKind.DH_GBUFFERS, stageReadsFromAlt, drawBuffers);
			recordDerived(key, DerivedFramebufferKind.DH_GBUFFERS, "RenderTargets", "dh-runtime");
			return renderTargets.createDHFramebuffer(ImmutableSet.copyOf(stageReadsFromAlt), drawBuffers.clone());
		}

		public GlFramebuffer createDHShadowFramebuffer(Set<Integer> stageReadsFromAlt, int[] drawBuffers) {
			DerivedFramebufferKey key = runtimeFramebufferKey(DerivedFramebufferKind.DH_SHADOW, stageReadsFromAlt, drawBuffers);
			recordDerived(key, DerivedFramebufferKind.DH_SHADOW, "ShadowRenderTargets", "dh-runtime");
			return shadowTargets().createDHFramebuffer(ImmutableSet.copyOf(stageReadsFromAlt), drawBuffers.clone());
		}

		public void copyPreHandDepth() {
			renderTargets.copyPreHandDepth();
			recordDerived(DerivedFramebufferKey.of("runtime-depth-copy-depthtex2"), DerivedFramebufferKind.DEPTH_COPY, "RenderTargets", "beginHand");
			recordLifecycle(ResourceInvalidationReason.HAND_BOUNDARY, "copied pre-hand depth into depthtex2");
		}

		public void copyPreTranslucentDepth() {
			renderTargets.copyPreTranslucentDepth();
			recordDerived(DerivedFramebufferKey.of("runtime-depth-copy-depthtex1"), DerivedFramebufferKind.DEPTH_COPY, "RenderTargets", "beginTranslucents");
			recordLifecycle(ResourceInvalidationReason.BEFORE_AFTER_TRANSLUCENT_BOUNDARY, "copied pre-translucent depth into depthtex1");
		}

		public GlFramebuffer createFinalFallbackFramebuffer(ShaderPackPass pass) {
			DerivedFramebufferDescriptor descriptor = derivedFramebuffer(pass.layout().derivedFramebufferKey());
			recordDerived(descriptor.key(), descriptor.kind(), descriptor.owner(), pass.id());
			return renderTargets.createGbufferFramebuffer(readsFromAlt(pass.inputs(), "colortex"), pass.layout().drawBuffers().clone());
		}

		public GlFramebuffer createRestoreCopyFramebuffer(ShaderPackPass pass) {
			DerivedFramebufferDescriptor descriptor = derivedFramebuffer(pass.layout().derivedFramebufferKey());
			int[] drawBuffers = pass.layout().drawBuffers().clone();
			ImmutableSet<Integer> readsFromAlt = readsFromAlt(pass.inputs(), "colortex");
			ImmutableSet.Builder<Integer> readsFromMain = ImmutableSet.builder();
			for (int drawBuffer : drawBuffers) {
				if (!readsFromAlt.contains(drawBuffer)) {
					readsFromMain.add(drawBuffer);
				}
			}
			recordDerived(descriptor.key(), descriptor.kind(), descriptor.owner(), pass.id());
			return renderTargets.createColorFramebuffer(readsFromMain.build(), drawBuffers);
		}

		public GlFramebuffer createClearFramebuffer(boolean alt, int[] clearBuffers) {
			DerivedFramebufferKey key = runtimeViewKey(DerivedFramebufferKind.CLEAR, alt, clearBuffers);
			recordDerived(key, DerivedFramebufferKind.CLEAR, "RenderTargets", "clear-pass");
			return renderTargets.createClearFramebuffer(alt, clearBuffers.clone());
		}

		public GlFramebuffer createShadowClearFramebuffer(boolean alt, int[] clearBuffers) {
			ShadowRenderTargets shadowTargets = shadowTargets();
			DerivedFramebufferKey key = runtimeViewKey(DerivedFramebufferKind.SHADOW_CLEAR, alt, clearBuffers);
			recordDerived(key, DerivedFramebufferKind.SHADOW_CLEAR, "ShadowRenderTargets", "shadow-clear-pass");
			return alt ? shadowTargets.createFramebufferWritingToAlt(clearBuffers.clone()) : shadowTargets.createFramebufferWritingToMain(clearBuffers.clone());
		}

		public GlFramebuffer createMainFramebufferWritingToView(boolean alt, int[] drawBuffers) {
			DerivedFramebufferKey key = runtimeViewKey(DerivedFramebufferKind.MAIN_VIEW, alt, drawBuffers);
			recordDerived(key, DerivedFramebufferKind.MAIN_VIEW, "RenderTargets", "default-framebuffer");
			return alt ? renderTargets.createFramebufferWritingToAlt(drawBuffers.clone()) : renderTargets.createFramebufferWritingToMain(drawBuffers.clone());
		}

		public GlFramebuffer createShadowFramebufferWritingToView(boolean alt, int[] drawBuffers) {
			ShadowRenderTargets shadowTargets = shadowTargets();
			DerivedFramebufferKey key = runtimeViewKey(DerivedFramebufferKind.SHADOW_VIEW, alt, drawBuffers);
			recordDerived(key, DerivedFramebufferKind.SHADOW_VIEW, "ShadowRenderTargets", "default-shadow-framebuffer");
			return alt ? shadowTargets.createFramebufferWritingToAlt(drawBuffers.clone()) : shadowTargets.createFramebufferWritingToMain(drawBuffers.clone());
		}

		public GlFramebuffer createMainColorHolderFramebuffer(int colorTextureId) {
			GlFramebuffer framebuffer = new GlFramebuffer();
			framebuffer.addColorAttachment(0, colorTextureId);
			recordDerived(DerivedFramebufferKey.of("main-color-target"), DerivedFramebufferKind.MAIN_COLOR_TARGET, "Minecraft main target reference", "final-pass");
			return framebuffer;
		}

		public void updateMainColorHolderFramebuffer(GlFramebuffer framebuffer, int colorTextureId) {
			framebuffer.addColorAttachment(0, colorTextureId);
			recordLifecycle(ResourceInvalidationReason.MAIN_TARGET_VERSION_CHANGE, "main color holder attachment updated");
		}

		public ImmutableList<ImageClearPass> createImageClearPasses(List<ShaderPackPass> passDescriptions) {
			ImmutableList.Builder<ImageClearPass> clearPasses = ImmutableList.builder();
			for (ShaderPackPass passDescription : passDescriptions) {
				if (passDescription.type() != ShaderPackPassType.CLEAR) {
					continue;
				}

				String clearIntent = passDescription.behavior().clearIntent();
				if (!clearIntent.startsWith("image:")) {
					continue;
				}

				String imageName = clearIntent.substring("image:".length());
				GlImage image = customImagesByName.get(imageName);
				if (image == null) {
					throw new IllegalStateException("Missing custom image " + imageName + " for clear pass " + passDescription.id());
				}

				clearPasses.add(ImageClearPass.create(image));
				DerivedFramebufferDescriptor descriptor = derivedFramebuffer(passDescription.layout().derivedFramebufferKey());
				recordDerived(descriptor.key(), descriptor.kind(), descriptor.owner(), passDescription.id());
			}
			return clearPasses.build();
		}

		public RenderTarget renderTarget(int index) {
			return renderTargets.get(index);
		}

		public int renderTargetCount() {
			return renderTargets.getRenderTargetCount();
		}

		public ProgramSamplers.CustomTextureSamplerInterceptor customTextureSamplerInterceptor(SamplerHolder samplers, Object2ObjectMap<String, TextureAccess> customTextureIds) {
			return ProgramSamplers.customTextureSamplerInterceptor(samplers, customTextureIds);
		}

		public ProgramSamplers.CustomTextureSamplerInterceptor customTextureSamplerInterceptor(SamplerHolder samplers, Object2ObjectMap<String, TextureAccess> customTextureIds,
																							  ImmutableSet<Integer> flippedAtLeastOnceSnapshot) {
			return ProgramSamplers.customTextureSamplerInterceptor(samplers, customTextureIds, flippedAtLeastOnceSnapshot);
		}

		public void addRenderTargetSamplers(SamplerHolder samplers, Supplier<ImmutableSet<Integer>> flipped, boolean isFullscreenPass, WorldRenderingPipeline pipeline) {
			IrisSamplers.addRenderTargetSamplers(samplers, flipped, renderTargets, isFullscreenPass, pipeline);
		}

		public void addRenderTargetImages(ImageHolder images, Supplier<ImmutableSet<Integer>> flipped) {
			IrisImages.addRenderTargetImages(images, flipped, renderTargets);
		}

		public void addCustomTextureSamplers(SamplerHolder samplers, Object2ObjectMap<String, TextureAccess> irisCustomTextures) {
			IrisSamplers.addCustomTextures(samplers, irisCustomTextures);
		}

		public void addCustomImageSamplers(SamplerHolder samplers) {
			IrisSamplers.addCustomImages(samplers, Set.copyOf(customImagesByName.values()));
		}

		public void addCustomImageBindings(ImageHolder images) {
			IrisImages.addCustomImages(images, Set.copyOf(customImagesByName.values()));
		}

		public void addNoiseSampler(SamplerHolder samplers, TextureAccess noiseTexture) {
			IrisSamplers.addNoiseSampler(samplers, noiseTexture);
		}

		public void addCompositeSamplers(SamplerHolder samplers) {
			IrisSamplers.addCompositeSamplers(samplers, renderTargets);
		}

		public void addWorldDepthSamplers(SamplerHolder samplers) {
			IrisSamplers.addWorldDepthSamplers(samplers, renderTargets);
		}

		public boolean hasPbrSamplers(SamplerHolder samplers) {
			return IrisSamplers.hasPBRSamplers(samplers);
		}

		public void addLevelSamplers(SamplerHolder samplers, WorldRenderingPipeline pipeline, AbstractTexture whitePixel, boolean hasTexture, boolean hasLightmap, boolean hasOverlay) {
			IrisSamplers.addLevelSamplers(samplers, pipeline, whitePixel, hasTexture, hasLightmap, hasOverlay);
		}

		public boolean hasShadowSamplers(SamplerHolder samplers) {
			return IrisSamplers.hasShadowSamplers(samplers);
		}

		public boolean addShadowSamplers(SamplerHolder samplers, @Nullable ImmutableSet<Integer> flipped, boolean separateHardwareSamplers) {
			return IrisSamplers.addShadowSamplers(samplers, shadowTargets(), flipped, separateHardwareSamplers);
		}

		public boolean hasShadowImages(ImageHolder images) {
			return IrisImages.hasShadowImages(images);
		}

		public void addShadowColorImages(ImageHolder images, @Nullable ImmutableSet<Integer> flipped) {
			IrisImages.addShadowColorImages(images, shadowTargets(), flipped);
		}

		public void setupShaderStorageBuffers() {
			if (shaderStorageBufferHolder != null) {
				shaderStorageBufferHolder.setupBuffers();
				recordLifecycle(ResourceInvalidationReason.SHADERPACK_RELOAD, "shader storage buffers bound during pipeline construction");
			}
		}

		public void resizeDependentResources(int width, int height) {
			if (shaderStorageBufferHolder != null) {
				shaderStorageBufferHolder.hasResizedScreen(width, height);
			}
			for (GlImage image : customImagesByName.values()) {
				image.updateNewSize(width, height);
			}
			recordLifecycle(ResourceInvalidationReason.RESIZE, "relative custom images and SSBO resources resized to " + width + "x" + height);
		}

		public void recordLifecycleBoundary(ResourceInvalidationReason reason, String detail) {
			recordLifecycle(reason, detail);
		}

		public FilledIndirectPointer resolveIndirectPointer(IndirectPointer pointer) {
			if (pointer == null || shaderStorageBufferHolder == null) {
				return null;
			}
			recordLifecycle(ResourceInvalidationReason.SHADERPACK_RELOAD, "resolved compute indirect pointer buffer=" + pointer.buffer());
			return FilledIndirectPointer.basedOff(shaderStorageBufferHolder, pointer);
		}

		public RuntimeBindingInstallRecord installProgramBindings(ShaderPackPass pass, ProgramBindingContext context) {
			return installProgramBindings(pass.bindings(), pass.id(), context);
		}

		public RuntimeBindingInstallRecord installProgramBindings(ShaderPackPassStage stage, TextureStage textureStage, String runtimeProgramId, ProgramBindingContext context) {
			return installProgramBindings(resources.bindingDescriptorFor(stage, textureStage), runtimeProgramId, context);
		}

		public RuntimeBindingInstallRecord installProgramBindings(ShaderPackBindingDescriptor descriptor, String runtimeProgramId, ProgramBindingContext context) {
			ProgramBindingInstallPlan plan = resources.installPlanFor(descriptor);
			List<SamplerBindingDirective> installedSamplerBindings = new ArrayList<>();
			List<ImageBindingDirective> installedImageBindings = new ArrayList<>();
			List<SsboBindingDirective> installedSsboBindings = new ArrayList<>();
			List<CompatibilityResourceDirective> installedCompatibilityResources = new ArrayList<>();
			List<BindingSkip> skippedBindings = new ArrayList<>();

			for (SamplerBindingDirective directive : descriptor.samplerBindings()) {
				SamplerHolder samplerHolder = context.samplerHolderFor(directive);
				if (samplerHolder == null) {
					skippedBindings.add(BindingSkip.sampler(directive, BindingSkipReason.NO_SAMPLER_HOLDER));
					continue;
				}

				switch (directive.kind()) {
					case RENDER_TARGETS -> {
						addRenderTargetSamplers(samplerHolder, context.flipped(), context.fullscreenPass(), context.pipeline());
						installedSamplerBindings.add(directive);
					}
					case CUSTOM_TEXTURES, CUSTOM_TEXTURES_INTERCEPTED -> {
						addCustomTextureSamplers(samplerHolder, context.irisCustomTextures());
						installedSamplerBindings.add(directive);
					}
					case CUSTOM_IMAGES -> {
						addCustomImageSamplers(samplerHolder);
						installedSamplerBindings.add(directive);
					}
					case NOISE -> {
						addNoiseSampler(samplerHolder, context.noiseTexture());
						installedSamplerBindings.add(directive);
					}
					case COMPOSITE_DEPTH -> {
						addCompositeSamplers(samplerHolder);
						installedSamplerBindings.add(directive);
					}
					case WORLD_DEPTH -> {
						addWorldDepthSamplers(samplerHolder);
						installedSamplerBindings.add(directive);
					}
					case LEVEL -> {
						if (context.whitePixel() == null) {
							skippedBindings.add(BindingSkip.sampler(directive, BindingSkipReason.NO_WHITE_PIXEL));
						} else {
							addLevelSamplers(samplerHolder, context.pipeline(), context.whitePixel(), context.hasTexture(), context.hasLightmap(), context.hasOverlay());
							installedSamplerBindings.add(directive);
						}
					}
					case PBR_DETECTION -> {
						if (hasPbrSamplers(samplerHolder)) {
							context.markPbrSamplersPresent();
						}
						installedSamplerBindings.add(directive);
					}
					case SHADOW -> {
						addShadowSamplers(samplerHolder, context.shadowFlipped(), context.separateHardwareSamplers());
						context.markShadowSamplersInstalled();
						installedSamplerBindings.add(directive);
					}
					case SHADOW_IF_PRESENT -> {
						if (hasShadowSamplers(samplerHolder)) {
							addShadowSamplers(samplerHolder, context.shadowFlipped(), context.separateHardwareSamplers());
							context.markShadowSamplersInstalled();
							installedSamplerBindings.add(directive);
						} else {
							skippedBindings.add(BindingSkip.sampler(directive, BindingSkipReason.NOT_ACTIVE));
						}
					}
					case CENTER_DEPTH -> {
						if (context.centerDepthSampler() != null) {
							context.installCenterDepthSampler();
							installedSamplerBindings.add(directive);
						} else {
							skippedBindings.add(BindingSkip.sampler(directive, BindingSkipReason.NO_CENTER_DEPTH_SAMPLER));
						}
					}
				}
			}

			for (ImageBindingDirective directive : descriptor.imageBindings()) {
				if (context.images() == null) {
					skippedBindings.add(BindingSkip.image(directive, BindingSkipReason.NO_IMAGE_HOLDER));
					continue;
				}

				switch (directive.kind()) {
					case RENDER_TARGETS -> {
						addRenderTargetImages(context.images(), context.flipped());
						installedImageBindings.add(directive);
					}
					case CUSTOM_IMAGES -> {
						addCustomImageBindings(context.images());
						installedImageBindings.add(directive);
					}
					case SHADOW_COLOR -> {
						addShadowColorImages(context.images(), context.shadowFlipped());
						installedImageBindings.add(directive);
					}
					case SHADOW_COLOR_IF_SAMPLER_PRESENT -> {
						if (context.shadowSamplersInstalled()) {
							addShadowColorImages(context.images(), context.shadowFlipped());
							installedImageBindings.add(directive);
						} else {
							skippedBindings.add(BindingSkip.image(directive, BindingSkipReason.SHADOW_SAMPLER_NOT_INSTALLED));
						}
					}
					case SHADOW_COLOR_IF_IMAGE_PRESENT_OR_SHADOW_PASS -> {
						if (context.shadowPass() || hasShadowImages(context.images())) {
							addShadowColorImages(context.images(), context.shadowFlipped());
							installedImageBindings.add(directive);
						} else {
							skippedBindings.add(BindingSkip.image(directive, BindingSkipReason.NOT_ACTIVE));
						}
					}
				}
			}

			for (CompatibilityResourceDirective resource : descriptor.compatibilityResources()) {
				if (resource.kind() == CompatibilityResourceKind.PBR_NORMAL_SPECULAR_SAMPLERS && context.pbrSamplersPresent()) {
					installedCompatibilityResources.add(resource);
				} else if (resource.kind() != CompatibilityResourceKind.PBR_NORMAL_SPECULAR_SAMPLERS) {
					installedCompatibilityResources.add(resource);
				}
			}

			if (!descriptor.ssboBindings().isEmpty()) {
				if (shaderStorageBufferHolder == null) {
					for (SsboBindingDirective directive : descriptor.ssboBindings()) {
						skippedBindings.add(BindingSkip.ssbo(directive, BindingSkipReason.NO_SSBO_HOLDER));
					}
				} else {
					installedSsboBindings.addAll(descriptor.ssboBindings());
					recordLifecycle(ResourceInvalidationReason.SHADERPACK_RELOAD, "program binding has SSBO policy for " + runtimeProgramId);
				}
			}

			RuntimeBindingInstallRecord record = new RuntimeBindingInstallRecord(
				runtimeProgramId,
				descriptor.descriptorId(),
				plan,
				List.copyOf(installedSamplerBindings),
				List.copyOf(installedImageBindings),
				List.copyOf(installedSsboBindings),
				List.copyOf(installedCompatibilityResources),
				List.copyOf(skippedBindings)
			);
			bindingInstallRecords.add(record);
			return record;
		}

		public RenderTarget shadowRenderTarget(int index) {
			return shadowTargets().get(index);
		}

		public int shadowRenderTargetCount() {
			return shadowTargets().getRenderTargetCount();
		}

		public int shadowResolution() {
			return shadowTargets().getResolution();
		}

		@Nullable
		public ShadowRenderTargets shadowRenderTargetsOrNull() {
			return shadowTargetsSupplier.get();
		}

		@Nullable
		public ShaderStorageBufferHolder shaderStorageBufferHolder() {
			return shaderStorageBufferHolder;
		}

		public void destroyMainFramebuffer(GlFramebuffer framebuffer) {
			renderTargets.destroyFramebuffer(framebuffer);
			recordLifecycle(ResourceInvalidationReason.DESTROY, "main framebuffer destroyed");
		}

		public List<String> invalidationPlan(ResourceInvalidationReason reason) {
			return resources.invalidationPlan(reason);
		}

		public ResourceRuntimeSnapshot snapshotRuntimeResources() {
			return new ResourceRuntimeSnapshot(
				List.copyOf(bindingInstallRecords),
				List.copyOf(derivedResourceRecords),
				List.copyOf(lifecycleEvents),
				ownerSnapshot()
			);
		}

		private ShadowRenderTargets shadowTargets() {
			ShadowRenderTargets shadowTargets = shadowTargetsSupplier.get();
			if (shadowTargets == null) {
				throw new IllegalStateException("Shadow render targets are not available for resource lookup");
			}
			return shadowTargets;
		}

		private DerivedFramebufferDescriptor derivedFramebuffer(DerivedFramebufferKey key) {
			return resources.derivedFramebuffers().stream()
				.filter(descriptor -> descriptor.key().equals(key))
				.findFirst()
				.orElseThrow(() -> new IllegalStateException("Unknown derived framebuffer key " + key.debugName()));
		}

		private void recordDerived(DerivedFramebufferKey key, DerivedFramebufferKind kind, String owner, String source) {
			derivedResourceRecords.add(new DerivedResourceRecord(key, kind, owner, source));
		}

		private static DerivedFramebufferKey runtimeFramebufferKey(DerivedFramebufferKind kind, Set<Integer> writesToMain, int[] drawBuffers) {
			return DerivedFramebufferKey.of("runtime-" + kind.name().toLowerCase(Locale.ROOT) + "-writeMain" + sorted(writesToMain) + "-drawBuffers" + Arrays.toString(drawBuffers));
		}

		private static DerivedFramebufferKey runtimeViewKey(DerivedFramebufferKind kind, boolean alt, int[] drawBuffers) {
			return DerivedFramebufferKey.of("runtime-" + kind.name().toLowerCase(Locale.ROOT) + "-" + (alt ? "alt" : "main") + "-drawBuffers" + Arrays.toString(drawBuffers));
		}

		private void recordLifecycle(ResourceInvalidationReason reason, String detail) {
			lifecycleEvents.add(new ResourceLifecycleEvent(reason, detail, resources.invalidationPlan(reason)));
		}

		private Map<String, String> ownerSnapshot() {
			Map<String, String> owners = new LinkedHashMap<>();
			resources.logicalRenderTargets().forEach(resource -> owners.put(resource, "RenderTargets"));
			resources.logicalShadowTargets().forEach(resource -> owners.put(resource, "ShadowRenderTargets"));
			resources.logicalImages().forEach(resource -> owners.put(resource, "custom image set"));
			resources.logicalSsbo().forEach(resource -> owners.put(resource, "ShaderStorageBufferHolder"));
			resources.irisCompatibilityResources().forEach(resource -> owners.put(resource, resources.ownerFor(resource)));
			resources.derivedFramebufferKeys().forEach(resource -> owners.put(resource.debugName(), "derived framebuffer lookup"));
			return owners;
		}

		private static ImmutableSet<Integer> stageWritesToMain(ShaderPackPassLayout layout, String resourcePrefix) {
			ImmutableSet.Builder<Integer> writesToMain = ImmutableSet.builder();
			layout.bufferInputViews().forEach((resource, view) -> {
				if (view == ShaderPackResourceView.ALT && resource.startsWith(resourcePrefix)) {
					writesToMain.add(Integer.parseInt(resource.substring(resourcePrefix.length())));
				}
			});
			return writesToMain.build();
		}

		private static ImmutableSet<Integer> readsFromAlt(List<ShaderPackPassResource> inputs, String resourcePrefix) {
			ImmutableSet.Builder<Integer> readsFromAlt = ImmutableSet.builder();
			inputs.forEach(input -> {
				if (input.view() == ShaderPackResourceView.ALT && input.logicalId().startsWith(resourcePrefix)) {
					readsFromAlt.add(Integer.parseInt(input.logicalId().substring(resourcePrefix.length())));
				}
			});
			return readsFromAlt.build();
		}

		private static List<Integer> sorted(Set<Integer> values) {
			return values.stream().sorted().toList();
		}
	}

	public record RuntimeBindingInstallRecord(
		String runtimeProgramId,
		String descriptorId,
		ProgramBindingInstallPlan plan,
		List<SamplerBindingDirective> installedSamplerBindings,
		List<ImageBindingDirective> installedImageBindings,
		List<SsboBindingDirective> installedSsboBindings,
		List<CompatibilityResourceDirective> installedCompatibilityResources,
		List<BindingSkip> skippedBindings
	) {
	}

	public record DerivedResourceRecord(
		DerivedFramebufferKey key,
		DerivedFramebufferKind kind,
		String owner,
		String source
	) {
	}

	public record ResourceLifecycleEvent(
		ResourceInvalidationReason reason,
		String detail,
		List<String> invalidationPlan
	) {
	}

	public record ResourceRuntimeSnapshot(
		List<RuntimeBindingInstallRecord> bindingInstallRecords,
		List<DerivedResourceRecord> derivedResourceRecords,
		List<ResourceLifecycleEvent> lifecycleEvents,
		Map<String, String> ownerSnapshot
	) {
	}

	public static final class ProgramBindingContext {
		private final SamplerHolder samplers;
		private final SamplerHolder directSamplers;
		private final SamplerHolder levelSamplers;
		private final SamplerHolder compositeDepthSamplers;
		private final SamplerHolder customImageSamplers;
		private final ImageHolder images;
		private final Supplier<ImmutableSet<Integer>> flipped;
		@Nullable
		private final ImmutableSet<Integer> shadowFlipped;
		private final boolean fullscreenPass;
		private final boolean shadowPass;
		private final WorldRenderingPipeline pipeline;
		private final Object2ObjectMap<String, TextureAccess> irisCustomTextures;
		private final TextureAccess noiseTexture;
		private final AbstractTexture whitePixel;
		private final boolean hasTexture;
		private final boolean hasLightmap;
		private final boolean hasOverlay;
		private final boolean separateHardwareSamplers;
		@Nullable
		private final net.irisshaders.iris.pathways.CenterDepthSampler centerDepthSampler;
		private boolean shadowSamplersInstalled;
		private boolean pbrSamplersPresent;

		private ProgramBindingContext(Builder builder) {
			this.samplers = builder.samplers;
			this.directSamplers = builder.directSamplers;
			this.levelSamplers = builder.levelSamplers;
			this.compositeDepthSamplers = builder.compositeDepthSamplers;
			this.customImageSamplers = builder.customImageSamplers;
			this.images = builder.images;
			this.flipped = builder.flipped;
			this.shadowFlipped = builder.shadowFlipped;
			this.fullscreenPass = builder.fullscreenPass;
			this.shadowPass = builder.shadowPass;
			this.pipeline = builder.pipeline;
			this.irisCustomTextures = builder.irisCustomTextures;
			this.noiseTexture = builder.noiseTexture;
			this.whitePixel = builder.whitePixel;
			this.hasTexture = builder.hasTexture;
			this.hasLightmap = builder.hasLightmap;
			this.hasOverlay = builder.hasOverlay;
			this.separateHardwareSamplers = builder.separateHardwareSamplers;
			this.centerDepthSampler = builder.centerDepthSampler;
		}

		public static Builder builder(WorldRenderingPipeline pipeline, Object2ObjectMap<String, TextureAccess> irisCustomTextures, TextureAccess noiseTexture) {
			return new Builder(pipeline, irisCustomTextures, noiseTexture);
		}

		@Nullable
		private SamplerHolder samplers() {
			return samplers;
		}

		@Nullable
		private SamplerHolder samplerHolderFor(SamplerBindingDirective directive) {
			if (directive.kind() == SamplerBindingKind.CUSTOM_TEXTURES) {
				return directSamplers != null ? directSamplers : samplers;
			}

			if (directive.kind() == SamplerBindingKind.CUSTOM_IMAGES) {
				return customImageSamplers != null ? customImageSamplers : samplers;
			}

			if (directive.kind() == SamplerBindingKind.LEVEL) {
				return levelSamplers != null ? levelSamplers : samplers;
			}

			if (directive.kind() == SamplerBindingKind.COMPOSITE_DEPTH) {
				return compositeDepthSamplers != null ? compositeDepthSamplers : samplers;
			}

			return samplers;
		}

		@Nullable
		private ImageHolder images() {
			return images;
		}

		private Supplier<ImmutableSet<Integer>> flipped() {
			return flipped;
		}

		@Nullable
		private ImmutableSet<Integer> shadowFlipped() {
			return shadowFlipped;
		}

		private boolean fullscreenPass() {
			return fullscreenPass;
		}

		private boolean shadowPass() {
			return shadowPass;
		}

		private WorldRenderingPipeline pipeline() {
			return pipeline;
		}

		private Object2ObjectMap<String, TextureAccess> irisCustomTextures() {
			return irisCustomTextures;
		}

		private TextureAccess noiseTexture() {
			return noiseTexture;
		}

		private AbstractTexture whitePixel() {
			return whitePixel;
		}

		private boolean hasTexture() {
			return hasTexture;
		}

		private boolean hasLightmap() {
			return hasLightmap;
		}

		private boolean hasOverlay() {
			return hasOverlay;
		}

		private boolean separateHardwareSamplers() {
			return separateHardwareSamplers;
		}

		@Nullable
		private net.irisshaders.iris.pathways.CenterDepthSampler centerDepthSampler() {
			return centerDepthSampler;
		}

		private boolean shadowSamplersInstalled() {
			return shadowSamplersInstalled;
		}

		private void markShadowSamplersInstalled() {
			this.shadowSamplersInstalled = true;
		}

		private boolean pbrSamplersPresent() {
			return pbrSamplersPresent;
		}

		private void markPbrSamplersPresent() {
			this.pbrSamplersPresent = true;
		}

		private void installCenterDepthSampler() {
			if (centerDepthSampler == null || samplers == null) {
				return;
			}
			centerDepthSampler.setUsage(samplers.addDynamicSampler(centerDepthSampler::getCenterDepthTexture, net.irisshaders.iris.gl.sampler.GlSampler.NEAREST, "iris_centerDepthSmooth"));
		}

		public static final class Builder {
			private final WorldRenderingPipeline pipeline;
			private final Object2ObjectMap<String, TextureAccess> irisCustomTextures;
			private final TextureAccess noiseTexture;
			private SamplerHolder samplers;
			private SamplerHolder directSamplers;
			private SamplerHolder levelSamplers;
			private SamplerHolder compositeDepthSamplers;
			private SamplerHolder customImageSamplers;
			private ImageHolder images;
			private Supplier<ImmutableSet<Integer>> flipped = ImmutableSet::of;
			@Nullable
			private ImmutableSet<Integer> shadowFlipped;
			private boolean fullscreenPass;
			private boolean shadowPass;
			private AbstractTexture whitePixel;
			private boolean hasTexture = true;
			private boolean hasLightmap = true;
			private boolean hasOverlay;
			private boolean separateHardwareSamplers;
			@Nullable
			private net.irisshaders.iris.pathways.CenterDepthSampler centerDepthSampler;

			private Builder(WorldRenderingPipeline pipeline, Object2ObjectMap<String, TextureAccess> irisCustomTextures, TextureAccess noiseTexture) {
				this.pipeline = pipeline;
				this.irisCustomTextures = irisCustomTextures;
				this.noiseTexture = noiseTexture;
			}

			public Builder samplers(SamplerHolder samplers) {
				this.samplers = samplers;
				return this;
			}

			public Builder directSamplers(SamplerHolder directSamplers) {
				this.directSamplers = directSamplers;
				return this;
			}

			public Builder levelSamplers(SamplerHolder levelSamplers) {
				this.levelSamplers = levelSamplers;
				return this;
			}

			public Builder compositeDepthSamplers(SamplerHolder compositeDepthSamplers) {
				this.compositeDepthSamplers = compositeDepthSamplers;
				return this;
			}

			public Builder customImageSamplers(SamplerHolder customImageSamplers) {
				this.customImageSamplers = customImageSamplers;
				return this;
			}

			public Builder images(ImageHolder images) {
				this.images = images;
				return this;
			}

			public Builder flipped(Supplier<ImmutableSet<Integer>> flipped) {
				this.flipped = flipped;
				return this;
			}

			public Builder shadowFlipped(@Nullable ImmutableSet<Integer> shadowFlipped) {
				this.shadowFlipped = shadowFlipped;
				return this;
			}

			public Builder fullscreenPass(boolean fullscreenPass) {
				this.fullscreenPass = fullscreenPass;
				return this;
			}

			public Builder shadowPass(boolean shadowPass) {
				this.shadowPass = shadowPass;
				return this;
			}

			public Builder levelSamplers(AbstractTexture whitePixel, boolean hasTexture, boolean hasLightmap, boolean hasOverlay) {
				this.whitePixel = whitePixel;
				this.hasTexture = hasTexture;
				this.hasLightmap = hasLightmap;
				this.hasOverlay = hasOverlay;
				return this;
			}

			public Builder separateHardwareSamplers(boolean separateHardwareSamplers) {
				this.separateHardwareSamplers = separateHardwareSamplers;
				return this;
			}

			public Builder centerDepthSampler(@Nullable net.irisshaders.iris.pathways.CenterDepthSampler centerDepthSampler) {
				this.centerDepthSampler = centerDepthSampler;
				return this;
			}

			public ProgramBindingContext build() {
				return new ProgramBindingContext(this);
			}
		}
	}
}
