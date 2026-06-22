package net.irisshaders.iris.pipeline.description;

import com.google.common.collect.ImmutableMap;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.features.FeatureFlags;
import net.irisshaders.iris.gl.blending.BlendModeOverride;
import net.irisshaders.iris.gl.framebuffer.ViewportData;
import net.irisshaders.iris.pipeline.CompositePass;
import net.irisshaders.iris.pipeline.WorldRenderingPhase;
import net.irisshaders.iris.pipeline.programs.ShaderKey;
import net.irisshaders.iris.shaderpack.ImageInformation;
import net.irisshaders.iris.shaderpack.loading.ProgramArrayId;
import net.irisshaders.iris.shaderpack.loading.ProgramId;
import net.irisshaders.iris.shaderpack.programs.ComputeSource;
import net.irisshaders.iris.shaderpack.programs.ProgramSet;
import net.irisshaders.iris.shaderpack.programs.ProgramSource;
import net.irisshaders.iris.shaderpack.properties.PackDirectives;
import net.irisshaders.iris.shaderpack.properties.PackRenderTargetDirectives;
import net.irisshaders.iris.shaderpack.properties.PackShadowDirectives;
import net.irisshaders.iris.shaderpack.properties.ProgramDirectives;
import net.irisshaders.iris.shaderpack.texture.TextureStage;
import org.joml.Vector2i;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class ShaderPackPipelineBuilder {
	public static final String BUILDER_SCHEMA_VERSION = "phase-3-description-v1";
	public static final String RESOURCE_SCHEMA_VERSION = "phase-4-resources-v1";

	private final ProgramSet programSet;
	private final PackDirectives packDirectives;
	private final int renderWidth;
	private final int renderHeight;
	private final int shadowSize;
	private final Map<ShaderPackPassStage, List<ShaderPackPass>> stages = new EnumMap<>(ShaderPackPassStage.class);
	private final LinkedHashSet<Integer> mainFlipped = new LinkedHashSet<>();
	private final Map<DerivedFramebufferKey, DerivedFramebufferDescriptor> derivedFramebuffers = new LinkedHashMap<>();
	private Set<Integer> flippedBeforeShadow = Set.of();
	private Set<Integer> flippedAfterPrepare = Set.of();
	private Set<Integer> flippedAfterTranslucent = Set.of();
	private Set<Integer> flippedAfterComposite = Set.of();
	private Set<Integer> compositeFlippedAtLeastOnce = Set.of();
	private Set<Integer> shadowCompositeFlippedAfter = Set.of();

	private ShaderPackPipelineBuilder(ProgramSet programSet, int renderWidth, int renderHeight) {
		this.programSet = programSet;
		this.packDirectives = programSet.getPackDirectives();
		this.renderWidth = renderWidth;
		this.renderHeight = renderHeight;
		this.shadowSize = packDirectives.getShadowDirectives().getResolution();

		for (ShaderPackPassStage stage : ShaderPackPassStage.values()) {
			stages.put(stage, new ArrayList<>());
		}
	}

	public static ShaderPackPipeline build(ProgramSet programSet, int renderWidth, int renderHeight) {
		ShaderPackPipelineBuilder builder = new ShaderPackPipelineBuilder(programSet, renderWidth, renderHeight);
		return builder.build();
	}

	private ShaderPackPipeline build() {
		buildSetupPasses();
		buildMainCompositeStages();
		addDepthCopyDescriptors();
		buildShadowStage();
		buildShadowCompositeStage();
		buildFinalStage();

		List<ExternalDrawPhasePass> externalDrawPhases = buildExternalDrawPhases();
		stages.get(ShaderPackPassStage.GBUFFERS).addAll(externalDrawPhases.stream()
			.map(this::toExternalDrawPass)
			.collect(Collectors.toList()));

		ShaderPackPipelineResources resources = buildResources();
		ShaderPackDebugIdentity debugIdentity = new ShaderPackDebugIdentity(
			Iris.getCurrentPackName() == null ? "shaderpack" : Iris.getCurrentPackName(),
			programSet.getPack().getProfileInfo(),
			"runtime",
			activeFeatureSummary(),
			BUILDER_SCHEMA_VERSION,
			RESOURCE_SCHEMA_VERSION,
			renderWidth,
			renderHeight,
			shadowSize
		);

		return new ShaderPackPipeline(
			freezeStages(),
			List.copyOf(externalDrawPhases),
			resources,
			debugIdentity,
			Set.copyOf(flippedBeforeShadow),
			Set.copyOf(flippedAfterPrepare),
			Set.copyOf(flippedAfterTranslucent),
			Set.copyOf(flippedAfterComposite),
			Set.copyOf(compositeFlippedAtLeastOnce),
			Set.copyOf(shadowCompositeFlippedAfter)
		);
	}

	private Map<ShaderPackPassStage, List<ShaderPackPass>> freezeStages() {
		Map<ShaderPackPassStage, List<ShaderPackPass>> frozen = new EnumMap<>(ShaderPackPassStage.class);
		for (Map.Entry<ShaderPackPassStage, List<ShaderPackPass>> entry : stages.entrySet()) {
			frozen.put(entry.getKey(), List.copyOf(entry.getValue()));
		}
		return Collections.unmodifiableMap(frozen);
	}

	private String activeFeatureSummary() {
		List<String> active = new ArrayList<>();
		for (FeatureFlags flag : FeatureFlags.values()) {
			if (flag != FeatureFlags.UNKNOWN && programSet.getPack().hasFeature(flag)) {
				active.add(flag.name());
			}
		}
		Collections.sort(active);
		return active.toString();
	}

	private void buildSetupPasses() {
		addImageClearDescriptors();
		addClearDescriptors(ShaderPackPassStage.SETUP);
		addSetupComputes(programSet.getSetup(), ShaderPackPassStage.SETUP, TextureStage.SETUP, "setup", "shaderpack-load-or-resize");
	}

	private void buildMainCompositeStages() {
		StageBuildResult begin = buildCompositeLikeStage(ShaderPackPassStage.BEGIN, CompositePass.BEGIN, ProgramArrayId.Begin, TextureStage.BEGIN, "begin_pre", mainFlipped);
		flippedBeforeShadow = begin.finalFlipped();

		StageBuildResult prepare = buildCompositeLikeStage(ShaderPackPassStage.PREPARE, CompositePass.PREPARE, ProgramArrayId.Prepare, TextureStage.PREPARE, "prepare_pre", mainFlipped);
		flippedAfterPrepare = prepare.finalFlipped();

		buildCompositeLikeStage(ShaderPackPassStage.DEFERRED, CompositePass.DEFERRED, ProgramArrayId.Deferred, TextureStage.DEFERRED, "deferred_pre", mainFlipped);
		flippedAfterTranslucent = Set.copyOf(mainFlipped);

		StageBuildResult composite = buildCompositeLikeStage(ShaderPackPassStage.COMPOSITE, CompositePass.COMPOSITE, ProgramArrayId.Composite, TextureStage.COMPOSITE_AND_FINAL, "composite_pre", mainFlipped);
		flippedAfterComposite = composite.finalFlipped();
		compositeFlippedAtLeastOnce = composite.flippedAtLeastOnce();
	}

	private StageBuildResult buildCompositeLikeStage(ShaderPackPassStage stage, CompositePass compositePass, ProgramArrayId arrayId, TextureStage textureStage, String preFlipKey, LinkedHashSet<Integer> flipper) {
		ImmutableMap<Integer, Boolean> explicitPreFlips = packDirectives.getExplicitFlips(preFlipKey);
		applyPreFlips(flipper, explicitPreFlips);

		LinkedHashSet<Integer> flippedAtLeastOnce = new LinkedHashSet<>();
		ProgramSource[] sources = programSet.getComposite(arrayId);
		ComputeSource[][] computes = programSet.getCompute(arrayId);

		for (int i = 0; i < sources.length; i++) {
			ProgramSource source = sources[i];
			Set<Integer> flippedSnapshot = Set.copyOf(flipper);
			Set<Integer> flippedAtLeastOnceSnapshot = Set.copyOf(flippedAtLeastOnce);
			ComputeSource[] computeSources = computeSourcesAt(computes, i);

			if (source == null || !source.isValid()) {
				if (hasComputes(computeSources)) {
					addComputeOnlyPass(stage, textureStage, arrayId.name(), compositePass.name().toLowerCase() + "/" + i, computeSources, flippedSnapshot, flippedAtLeastOnceSnapshot, explicitPreFlips);
				}
				continue;
			}

			ProgramDirectives directives = source.getDirectives();
			int[] drawBuffers = directives.getDrawBuffers();
			LinkedHashSet<Integer> resolvedFlips = new LinkedHashSet<>();
			ImmutableMap<Integer, Boolean> explicitFlips = directives.getExplicitFlips();
			String passId = stablePassId(stage, i, source.getName());

			for (int buffer : drawBuffers) {
				if (explicitFlips.get(buffer) == Boolean.FALSE) {
					continue;
				}

				flip(flipper, buffer);
				flippedAtLeastOnce.add(buffer);
				resolvedFlips.add(buffer);
			}

			for (Map.Entry<Integer, Boolean> entry : explicitFlips.entrySet()) {
				if (entry.getValue()) {
					flip(flipper, entry.getKey());
					flippedAtLeastOnce.add(entry.getKey());
					resolvedFlips.add(entry.getKey());
				}
			}

			ShaderPackPassLayout layout = layoutFor(passId, stage, drawBuffers, flippedSnapshot, explicitPreFlips, explicitFlips, resolvedFlips,
				flippedAtLeastOnceSnapshot, derivedFramebufferKey(stage, "color", flippedSnapshot, drawBuffers));

			stages.get(stage).add(new ShaderPackPass(
				passId,
				source.getName(),
				stage,
				hasComputes(computeSources) ? ShaderPackPassType.GRAPHICS_WITH_COMPUTE : ShaderPackPassType.GRAPHICS,
				renderTargetInputs(flippedSnapshot, "sampler/image"),
				renderTargetOutputs(drawBuffers, "draw-buffer"),
				programDescriptors(source, computeSources, arrayId.name(), null, textureStage.name()),
				bindingDescriptor(stage, textureStage),
				behavior(directives.getBlendModeOverride().map(Object::toString).orElse("default"), directives.getBlendModeOverride().orElse(null), directives.getViewportScale(), directives.getMipmappedBuffers(),
					hasComputes(computeSources), "stage-order"),
				layout
			));
		}

		return new StageBuildResult(Set.copyOf(flipper), Set.copyOf(flippedAtLeastOnce));
	}

	private void buildShadowStage() {
		addSetupComputes(programSet.getShadowCompute(), ShaderPackPassStage.SHADOW, TextureStage.GBUFFERS_AND_SHADOW, "shadow", "beginLevelRendering:shadow");
		addShadowClearDescriptors();
	}

	private void buildShadowCompositeStage() {
		LinkedHashSet<Integer> shadowFlipped = new LinkedHashSet<>();
		ImmutableMap<Integer, Boolean> explicitPreFlips = packDirectives.getExplicitFlips("shadowcomp_pre");
		applyPreFlips(shadowFlipped, explicitPreFlips);
		LinkedHashSet<Integer> flippedAtLeastOnce = new LinkedHashSet<>();

		ProgramSource[] sources = programSet.getComposite(ProgramArrayId.ShadowComposite);
		ComputeSource[][] computes = programSet.getCompute(ProgramArrayId.ShadowComposite);

		for (int i = 0; i < sources.length; i++) {
			ProgramSource source = sources[i];
			Set<Integer> flippedSnapshot = Set.copyOf(shadowFlipped);
			Set<Integer> flippedAtLeastOnceSnapshot = Set.copyOf(flippedAtLeastOnce);
			ComputeSource[] computeSources = computeSourcesAt(computes, i);

			if (source == null || !source.isValid()) {
				if (hasComputes(computeSources)) {
					addComputeOnlyPass(ShaderPackPassStage.SHADOW_COMPOSITE, TextureStage.SHADOWCOMP, ProgramArrayId.ShadowComposite.name(), "shadowcomp/" + i,
						computeSources, flippedSnapshot, flippedAtLeastOnceSnapshot, explicitPreFlips);
				}
				continue;
			}

			ProgramDirectives directives = source.getDirectives();
			int[] drawBuffers = directives.hasUnknownDrawBuffers() ? new int[]{0, 1} : directives.getDrawBuffers();
			LinkedHashSet<Integer> resolvedFlips = new LinkedHashSet<>();
			ImmutableMap<Integer, Boolean> explicitFlips = directives.getExplicitFlips();
			String passId = stablePassId(ShaderPackPassStage.SHADOW_COMPOSITE, i, source.getName());

			for (int buffer : drawBuffers) {
				if (explicitFlips.get(buffer) == Boolean.FALSE) {
					continue;
				}

				flip(shadowFlipped, buffer);
				flippedAtLeastOnce.add(buffer);
				resolvedFlips.add(buffer);
			}

			for (Map.Entry<Integer, Boolean> entry : explicitFlips.entrySet()) {
				if (entry.getValue()) {
					flip(shadowFlipped, entry.getKey());
					flippedAtLeastOnce.add(entry.getKey());
					resolvedFlips.add(entry.getKey());
				}
			}

			ShaderPackPassLayout layout = layoutFor(passId, ShaderPackPassStage.SHADOW_COMPOSITE, drawBuffers, flippedSnapshot, explicitPreFlips, explicitFlips, resolvedFlips,
				flippedAtLeastOnceSnapshot, derivedFramebufferKey(ShaderPackPassStage.SHADOW_COMPOSITE, "shadow-color", flippedSnapshot, drawBuffers));

			stages.get(ShaderPackPassStage.SHADOW_COMPOSITE).add(new ShaderPackPass(
				passId,
				source.getName(),
				ShaderPackPassStage.SHADOW_COMPOSITE,
				hasComputes(computeSources) ? ShaderPackPassType.GRAPHICS_WITH_COMPUTE : ShaderPackPassType.GRAPHICS,
				shadowInputs(flippedSnapshot, "sampler/image"),
				shadowOutputs(drawBuffers, "draw-buffer"),
				programDescriptors(source, computeSources, ProgramArrayId.ShadowComposite.name(), null, TextureStage.SHADOWCOMP.name()),
				bindingDescriptor(ShaderPackPassStage.SHADOW_COMPOSITE, TextureStage.SHADOWCOMP),
				behavior(directives.getBlendModeOverride().map(Object::toString).orElse("default"), directives.getBlendModeOverride().orElse(null), directives.getViewportScale(), directives.getMipmappedBuffers(),
					hasComputes(computeSources), "shadow-composite-order"),
				layout
			));
		}

		shadowCompositeFlippedAfter = Set.copyOf(shadowFlipped);
	}

	private void buildFinalStage() {
		ComputeSource[] finalComputes = programSet.getFinalCompute();
		programSet.get(ProgramId.Final).ifPresentOrElse(source -> {
			String passId = "final/000/" + source.getName();
			ProgramDirectives directives = source.getDirectives();
			DerivedFramebufferKey key = registerDerivedFramebuffer("main-color-target", DerivedFramebufferKind.MAIN_COLOR_TARGET, "Minecraft main target reference", Set.of(), new int[0], passId, "final main color");
			ShaderPackPassLayout layout = new ShaderPackPassLayout(
				passId,
				bufferInputViews(flippedAfterComposite, ShaderPackResourceKind.COLORTEX),
				new int[0],
				Map.of(),
				Map.of(),
				Map.copyOf(directives.getExplicitFlips()),
				Set.of(),
				compositeFlippedAtLeastOnce,
				key
			);

			stages.get(ShaderPackPassStage.FINAL).add(new ShaderPackPass(
				passId,
				source.getName(),
				ShaderPackPassStage.FINAL,
				ShaderPackPassType.FINAL,
				renderTargetInputs(flippedAfterComposite, "final-sampler/image"),
				List.of(new ShaderPackPassResource("mainColor", ShaderPackResourceKind.MAIN_TARGET, ShaderPackResourceView.UNRESOLVED, "final-output")),
				programDescriptors(source, finalComputes, null, ProgramId.Final.name(), TextureStage.COMPOSITE_AND_FINAL.name()),
				bindingDescriptor(ShaderPackPassStage.FINAL, TextureStage.COMPOSITE_AND_FINAL),
				behavior(directives.getBlendModeOverride().map(Object::toString).orElse("default"), directives.getBlendModeOverride().orElse(null), ViewportData.defaultValue(), directives.getMipmappedBuffers(),
					hasComputes(finalComputes), "finalizeLevelRendering", "none", "final-main-color"),
				layout
			));
		}, () -> addFinalFallbackCopy());

		for (int buffer : sorted(flippedAfterComposite)) {
			if (programSet.getPackDirectives().getRenderTargetDirectives().getBuffersToBeCleared().contains(buffer)) {
				continue;
			}

			String passId = "final/restore/colortex" + buffer;
			ShaderPackPassLayout layout = layoutFor(passId, ShaderPackPassStage.FINAL, new int[]{buffer}, Set.of(buffer), Map.of(), Map.of(), Set.of(), compositeFlippedAtLeastOnce,
				derivedFramebufferKey(DerivedFramebufferKind.FINAL_RESTORE, ShaderPackPassStage.FINAL, "restore-copy", Set.of(buffer), new int[]{buffer}, passId));
			stages.get(ShaderPackPassStage.FINAL).add(new ShaderPackPass(
				passId,
				"restore colortex" + buffer,
				ShaderPackPassStage.FINAL,
				ShaderPackPassType.COPY,
				List.of(new ShaderPackPassResource("colortex" + buffer, ShaderPackResourceKind.COLORTEX, ShaderPackResourceView.ALT, "restore-copy-source")),
				List.of(new ShaderPackPassResource("colortex" + buffer, ShaderPackResourceKind.COLORTEX, ShaderPackResourceView.MAIN, "restore-copy-target")),
				List.of(),
				emptyBindingDescriptor(passId),
				behavior("none", null, ViewportData.defaultValue(), Set.of(), false, "finalizeLevelRendering:restore-flipped-buffer", "none", "restore:colortex" + buffer + ".alt-to-main"),
				layout
			));
		}
	}

	private void addFinalFallbackCopy() {
		String passId = "final/fallback-copy/colortex0-to-main";
		ShaderPackPassLayout layout = layoutFor(passId, ShaderPackPassStage.FINAL, new int[]{0}, flippedAfterComposite, Map.of(), Map.of(), Set.of(),
			compositeFlippedAtLeastOnce, derivedFramebufferKey(DerivedFramebufferKind.FINAL_FALLBACK, ShaderPackPassStage.FINAL, "final-baseline", flippedAfterComposite, new int[]{0}, passId));
		stages.get(ShaderPackPassStage.FINAL).add(new ShaderPackPass(
			passId,
			"fallback copy colortex0",
			ShaderPackPassStage.FINAL,
			ShaderPackPassType.COPY,
			List.of(new ShaderPackPassResource("colortex0", ShaderPackResourceKind.COLORTEX, viewFor(flippedAfterComposite, 0), "fallback-copy-source")),
			List.of(new ShaderPackPassResource("mainColor", ShaderPackResourceKind.MAIN_TARGET, ShaderPackResourceView.UNRESOLVED, "fallback-copy-target")),
			List.of(),
			emptyBindingDescriptor(passId),
			behavior("none", null, ViewportData.defaultValue(), Set.of(), false, "finalizeLevelRendering:if-final-missing", "none", "fallback:colortex0-to-main"),
			layout
		));
	}

	private void addDepthCopyDescriptors() {
		addDepthCopyPass("gbuffers/depth-copy/pre-translucent", "depthtex1", "beginTranslucents");
		addDepthCopyPass("gbuffers/depth-copy/pre-hand", "depthtex2", "beginHand");
	}

	private void addDepthCopyPass(String passId, String output, String trigger) {
		DerivedFramebufferKey key = registerDerivedFramebuffer("depth-copy-" + output, DerivedFramebufferKind.DEPTH_COPY, "RenderTargets", Set.of(), new int[0], passId, "depth-copy:" + output);
		ShaderPackPassLayout layout = new ShaderPackPassLayout(passId, Map.of(), new int[0], Map.of(), Map.of(), Map.of(), Set.of(), Set.of(), key);
		stages.get(ShaderPackPassStage.GBUFFERS).add(new ShaderPackPass(
			passId,
			output + " copy",
			ShaderPackPassStage.GBUFFERS,
			ShaderPackPassType.COPY,
			List.of(new ShaderPackPassResource("mainDepth", ShaderPackResourceKind.MAIN_TARGET, ShaderPackResourceView.UNRESOLVED, "depth-copy-source")),
			List.of(new ShaderPackPassResource(output, ShaderPackResourceKind.DEPTHTEX, ShaderPackResourceView.UNRESOLVED, "depth-copy-target")),
			List.of(),
			emptyBindingDescriptor(passId),
			behavior("none", null, ViewportData.defaultValue(), Set.of(), false, trigger, "none", "depth-copy:" + output),
			layout
		));
	}

	private void addImageClearDescriptors() {
		for (ImageInformation image : programSet.getPack().getIrisCustomImages()) {
			if (!image.clear()) {
				continue;
			}

			String passId = "setup/clear/image/" + image.name();
			DerivedFramebufferKey key = registerDerivedFramebuffer("image-clear-" + image.name(), DerivedFramebufferKind.IMAGE_CLEAR, "custom image set", Set.of(), new int[0], passId, "image-clear:" + image.name());
			ShaderPackPassLayout layout = new ShaderPackPassLayout(passId, Map.of(), new int[0], Map.of(), Map.of(), Map.of(), Set.of(), Set.of(), key);
			stages.get(ShaderPackPassStage.SETUP).add(new ShaderPackPass(
				passId,
				"clear image " + image.name(),
				ShaderPackPassStage.SETUP,
				ShaderPackPassType.CLEAR,
				List.of(),
				List.of(new ShaderPackPassResource(image.name(), ShaderPackResourceKind.CUSTOM_IMAGE, ShaderPackResourceView.UNRESOLVED, "image-clear " + image.internalTextureFormat().name())),
				List.of(),
				emptyBindingDescriptor(passId),
				behavior("none", null, ViewportData.defaultValue(), Set.of(), false, "beginLevelRendering:image-clear", "image:" + image.name(), "none"),
				layout
			));
		}
	}

	private void addClearDescriptors(ShaderPackPassStage stage) {
		for (Map.Entry<Integer, PackRenderTargetDirectives.RenderTargetSettings> entry : sortedRenderTargetSettings().entrySet()) {
			int buffer = entry.getKey();
			PackRenderTargetDirectives.RenderTargetSettings settings = entry.getValue();
			addClearDescriptor(stage, "full", buffer, ShaderPackResourceView.MAIN, settings.getInternalFormat().name(), "beginLevelRendering:fullClear");
			addClearDescriptor(stage, "full", buffer, ShaderPackResourceView.ALT, settings.getInternalFormat().name(), "beginLevelRendering:fullClear");

			if (settings.shouldClear()) {
				addClearDescriptor(stage, "incremental", buffer, ShaderPackResourceView.MAIN, settings.getInternalFormat().name(), "beginLevelRendering:clear");
				addClearDescriptor(stage, "incremental", buffer, ShaderPackResourceView.ALT, settings.getInternalFormat().name(), "beginLevelRendering:clear");
			}
		}
	}

	private void addClearDescriptor(ShaderPackPassStage stage, String clearClass, int buffer, ShaderPackResourceView view, String format, String trigger) {
		String passId = stage.name().toLowerCase() + "/clear/" + clearClass + "/colortex" + buffer + "." + view.name().toLowerCase();
		DerivedFramebufferKey key = derivedFramebufferKey(DerivedFramebufferKind.CLEAR, stage, "clear-" + clearClass + "-" + view.name().toLowerCase(), view == ShaderPackResourceView.MAIN ? Set.of(buffer) : Set.of(), new int[]{buffer}, passId);
		ShaderPackPassLayout layout = layoutFor(passId, stage, new int[]{buffer}, view == ShaderPackResourceView.MAIN ? Set.of(buffer) : Set.of(),
			Map.of(), Map.of(), Set.of(), Set.of(), key);
		stages.get(stage).add(new ShaderPackPass(
			passId,
			"clear colortex" + buffer + " " + view.name().toLowerCase(),
			stage,
			ShaderPackPassType.CLEAR,
			List.of(),
			List.of(new ShaderPackPassResource("colortex" + buffer, ShaderPackResourceKind.COLORTEX, view, "clear " + format)),
			List.of(),
			emptyBindingDescriptor(passId),
			behavior("none", null, ViewportData.defaultValue(), Set.of(), false, trigger, clearClass + ":colortex" + buffer + "." + view.name().toLowerCase() + ":" + format, "none"),
			layout
		));
	}

	private void addShadowClearDescriptors() {
		for (Map.Entry<Integer, PackShadowDirectives.SamplingSettings> entry : sortedShadowColorSettings().entrySet()) {
			int buffer = entry.getKey();
			PackShadowDirectives.SamplingSettings settings = entry.getValue();
			addShadowClearDescriptor("full", buffer, ShaderPackResourceView.MAIN, settings.getFormat().name(), "beginLevelRendering:shadow-fullClear");
			addShadowClearDescriptor("full", buffer, ShaderPackResourceView.ALT, settings.getFormat().name(), "beginLevelRendering:shadow-fullClear");
			if (settings.getClear()) {
				addShadowClearDescriptor("incremental", buffer, ShaderPackResourceView.MAIN, settings.getFormat().name(), "beginLevelRendering:shadow-clear");
				addShadowClearDescriptor("incremental", buffer, ShaderPackResourceView.ALT, settings.getFormat().name(), "beginLevelRendering:shadow-clear");
			}
		}
	}

	private void addShadowClearDescriptor(String clearClass, int buffer, ShaderPackResourceView view, String format, String trigger) {
		String passId = "shadow/clear/" + clearClass + "/shadowcolor" + buffer + "." + view.name().toLowerCase();
		DerivedFramebufferKey key = derivedFramebufferKey(DerivedFramebufferKind.SHADOW_CLEAR, ShaderPackPassStage.SHADOW, "shadow-clear-" + clearClass + "-" + view.name().toLowerCase(), view == ShaderPackResourceView.MAIN ? Set.of(buffer) : Set.of(), new int[]{buffer}, passId);
		ShaderPackPassLayout layout = layoutFor(passId, ShaderPackPassStage.SHADOW, new int[]{buffer}, view == ShaderPackResourceView.MAIN ? Set.of(buffer) : Set.of(),
			Map.of(), Map.of(), Set.of(), Set.of(), key);
		stages.get(ShaderPackPassStage.SHADOW).add(new ShaderPackPass(
			passId,
			"clear shadowcolor" + buffer + " " + view.name().toLowerCase(),
			ShaderPackPassStage.SHADOW,
			ShaderPackPassType.CLEAR,
			List.of(),
			List.of(new ShaderPackPassResource("shadowcolor" + buffer, ShaderPackResourceKind.SHADOWCOLOR, view, "clear " + format)),
			List.of(),
			emptyBindingDescriptor(passId),
			behavior("none", null, ViewportData.defaultValue(), Set.of(), false, trigger, clearClass + ":shadowcolor" + buffer + "." + view.name().toLowerCase() + ":" + format, "none"),
			layout
		));
	}

	private void addSetupComputes(ComputeSource[] computes, ShaderPackPassStage stage, TextureStage textureStage, String idPrefix, String trigger) {
		for (int i = 0; i < computes.length; i++) {
			ComputeSource source = computes[i];
			if (!isValidCompute(source)) {
				continue;
			}

			String passId = stage.name().toLowerCase() + "/" + idPrefix + "-compute/" + i + "/" + source.getName();
			DerivedFramebufferKey key = registerDerivedFramebuffer("compute-only-" + passId, DerivedFramebufferKind.COMPUTE_ONLY, "none", Set.of(), new int[0], passId, "compute-only");
			ShaderPackPassLayout layout = new ShaderPackPassLayout(passId, Map.of(), new int[0], Map.of(), Map.of(), Map.of(), Set.of(), Set.of(), key);
			stages.get(stage).add(new ShaderPackPass(
				passId,
				source.getName(),
				stage,
				stage == ShaderPackPassStage.SETUP ? ShaderPackPassType.SETUP : ShaderPackPassType.COMPUTE,
				List.of(),
				List.of(),
				List.of(ShaderPackProgramDescriptor.compute(passId, source.getName(), idPrefix, textureStage.name())),
				bindingDescriptor(stage, textureStage),
				behavior("none", null, ViewportData.defaultValue(), Set.of(), true, trigger),
				layout
			));
		}
	}

	private void addComputeOnlyPass(ShaderPackPassStage stage, TextureStage textureStage, String arrayId, String idPrefix, ComputeSource[] computeSources,
	                                Set<Integer> flippedSnapshot, Set<Integer> flippedAtLeastOnceSnapshot, Map<Integer, Boolean> explicitPreFlips) {
		String name = firstComputeName(computeSources);
		String passId = stage.name().toLowerCase() + "/compute-only/" + idPrefix + "/" + name;
		DerivedFramebufferKey key = registerDerivedFramebuffer("compute-only-" + passId, DerivedFramebufferKind.COMPUTE_ONLY, "none", Set.of(), new int[0], passId, "compute-only");
		ShaderPackPassLayout layout = new ShaderPackPassLayout(
			passId,
			bufferInputViews(flippedSnapshot, stage == ShaderPackPassStage.SHADOW_COMPOSITE ? ShaderPackResourceKind.SHADOWCOLOR : ShaderPackResourceKind.COLORTEX),
			new int[0],
			Map.of(),
			Map.copyOf(explicitPreFlips),
			Map.of(),
			Set.of(),
			flippedAtLeastOnceSnapshot,
			key
		);
		stages.get(stage).add(new ShaderPackPass(
			passId,
			name,
			stage,
			ShaderPackPassType.COMPUTE,
			stage == ShaderPackPassStage.SHADOW_COMPOSITE ? shadowInputs(flippedSnapshot, "compute-sampler/image") : renderTargetInputs(flippedSnapshot, "compute-sampler/image"),
			List.of(),
			computeDescriptors(computeSources, arrayId, textureStage.name()),
			bindingDescriptor(stage, textureStage),
			behavior("none", null, ViewportData.defaultValue(), Set.of(), true, "stage-order"),
			layout
		));
	}

	private ShaderPackPass toExternalDrawPass(ExternalDrawPhasePass external) {
		String passId = external.id();
		DerivedFramebufferKey key = registerDerivedFramebuffer("external-draw-" + external.worldPhase().name(), DerivedFramebufferKind.GBUFFERS_EXTERNAL_DRAW, "RenderTargets", Set.of(), new int[0], passId, "external-draw:" + external.worldPhase().name());
		ShaderPackPassLayout layout = new ShaderPackPassLayout(passId, Map.of(), new int[0], Map.of(), Map.of(), Map.of(), Set.of(), Set.of(), key);
		return new ShaderPackPass(
			passId,
			external.worldPhase().name().toLowerCase(),
			ShaderPackPassStage.GBUFFERS,
			ShaderPackPassType.EXTERNAL_DRAW,
			List.of(),
			List.of(),
			List.of(),
			bindingDescriptor(ShaderPackPassStage.GBUFFERS, TextureStage.GBUFFERS_AND_SHADOW),
			behavior("runtime", null, ViewportData.defaultValue(), Set.of(), false, "minecraft-or-sodium-draw"),
			layout
		);
	}

	private List<ExternalDrawPhasePass> buildExternalDrawPhases() {
		List<ExternalDrawPhasePass> phases = new ArrayList<>();
		for (WorldRenderingPhase phase : WorldRenderingPhase.values()) {
			phases.add(externalPhase(phase));
		}
		return List.copyOf(phases);
	}

	private ExternalDrawPhasePass externalPhase(WorldRenderingPhase phase) {
		String phaseClass = phaseClass(phase);
		boolean participates = phase != WorldRenderingPhase.NONE && phase != WorldRenderingPhase.DEBUG;
		String passthroughReason = participates ? "" : (phase == WorldRenderingPhase.NONE ? "no active world rendering phase" : "debug rendering is not shaderpack-overridden by this metadata pass");
		List<String> shaderKeys = shaderKeysFor(phase).stream().map(Enum::name).sorted().collect(Collectors.toList());
		String selector = phase == WorldRenderingPhase.NONE ? "none" : "IrisPipelines::getPipeline + ShaderOverrides phase helpers";
		String framebufferPolicy = switch (phaseClass) {
			case "shadow" -> "shadow framebuffer resolver";
			case "hand" -> "hand framebuffer resolver with beginHand depth copy";
			case "translucent", "particles", "clouds", "weather", "world-border" -> "before/after translucent framebuffer resolver";
			default -> participates ? "gbuffers framebuffer resolver" : "passthrough";
		};
		String runtimeViewPolicy = switch (phaseClass) {
			case "hand" -> "runtime hand state selects hand/hand_water and pre-hand depth view";
			case "translucent", "particles", "clouds", "weather", "world-border" -> "isBeforeTranslucent selects flippedAfterPrepare/flippedAfterTranslucent views";
			case "shadow" -> "ShadowRenderingState selects shadow targets";
			default -> participates ? "WorldRenderingPhase selects ShaderKey and current colortex views" : "none";
		};
		String sodiumPolicy = phaseClass.equals("terrain") ? "sodium namespace pipelines select SODIUM_TERRAIN_* ShaderKey and Iris terrain vertex format" :
			phaseClass.equals("shadow") ? "shadow render section manager and sodium shadow terrain keys participate" : "not sodium-specific";

		return new ExternalDrawPhasePass(
			"external/" + phase.name().toLowerCase(),
			phase,
			phaseClass,
			shaderKeys.isEmpty() ? "none" : "shader keys " + shaderKeys,
			selector,
			framebufferPolicy,
			runtimeViewPolicy,
			participates ? "ExtendedShader sampler/image/SSBO policy" : "none",
			vertexFormatPolicy(phaseClass),
			participates ? "Sampler0 albedo/PBR hook resolved at runtime when UV0 is present" : "none",
			sodiumPolicy,
			participates,
			passthroughReason,
			shaderKeys
		);
	}

	private String phaseClass(WorldRenderingPhase phase) {
		return switch (phase) {
			case SKY, SUNSET, CUSTOM_SKY, SUN, MOON, STARS, VOID -> "sky";
			case TERRAIN_SOLID, TERRAIN_CUTOUT_MIPPED, TERRAIN_CUTOUT, TERRAIN_TRANSLUCENT, TRIPWIRE -> "terrain";
			case ENTITIES, DESTROY, OUTLINE -> "entities";
			case BLOCK_ENTITIES -> "block-entities";
			case HAND_SOLID, HAND_TRANSLUCENT -> "hand";
			case PARTICLES -> "particles";
			case CLOUDS -> "clouds";
			case RAIN_SNOW -> "weather";
			case WORLD_BORDER -> "world-border";
			case DEBUG -> "debug";
			case NONE -> "none";
		};
	}

	private String vertexFormatPolicy(String phaseClass) {
		return switch (phaseClass) {
			case "terrain" -> "Iris/Sodium terrain vertex format policy";
			case "entities", "block-entities", "hand" -> "entity vertex format policy";
			case "particles", "weather" -> "particle vertex format policy";
			case "clouds" -> "cloud vertex format policy";
			case "sky", "world-border" -> "host pipeline vertex format policy";
			default -> "none";
		};
	}

	private Set<ShaderKey> shaderKeysFor(WorldRenderingPhase phase) {
		return switch (phase) {
			case SKY, VOID -> EnumSet.of(ShaderKey.SKY_BASIC, ShaderKey.SKY_TEXTURED, ShaderKey.BASIC);
			case SUNSET -> EnumSet.of(ShaderKey.SKY_BASIC_COLOR, ShaderKey.BASIC_COLOR);
			case CUSTOM_SKY, SUN, MOON, STARS -> EnumSet.of(ShaderKey.SKY_TEXTURED, ShaderKey.SKY_TEXTURED_COLOR, ShaderKey.SKY_BASIC);
			case TERRAIN_SOLID -> EnumSet.of(ShaderKey.TERRAIN_SOLID, ShaderKey.SODIUM_TERRAIN_SOLID, ShaderKey.SHADOW_SODIUM_TERRAIN_SOLID);
			case TERRAIN_CUTOUT, TERRAIN_CUTOUT_MIPPED, TRIPWIRE -> EnumSet.of(ShaderKey.TERRAIN_CUTOUT, ShaderKey.SODIUM_TERRAIN_CUTOUT, ShaderKey.SHADOW_TERRAIN_CUTOUT, ShaderKey.SHADOW_SODIUM_TERRAIN_CUTOUT);
			case TERRAIN_TRANSLUCENT -> EnumSet.of(ShaderKey.TERRAIN_TRANSLUCENT, ShaderKey.SODIUM_TERRAIN_TRANSLUCENT, ShaderKey.SHADOW_TRANSLUCENT, ShaderKey.SHADOW_SODIUM_TERRAIN_TRANSLUCENT);
			case ENTITIES -> EnumSet.of(ShaderKey.ENTITIES_SOLID, ShaderKey.ENTITIES_CUTOUT, ShaderKey.ENTITIES_TRANSLUCENT, ShaderKey.ENTITIES_EYES, ShaderKey.GLINT, ShaderKey.LIGHTNING, ShaderKey.BEACON, ShaderKey.LEASH);
			case BLOCK_ENTITIES -> EnumSet.of(ShaderKey.BLOCK_ENTITY, ShaderKey.BLOCK_ENTITY_DIFFUSE, ShaderKey.BE_TRANSLUCENT, ShaderKey.TEXT_BE, ShaderKey.TEXT_INTENSITY_BE);
			case DESTROY -> EnumSet.of(ShaderKey.CRUMBLING);
			case OUTLINE -> EnumSet.of(ShaderKey.LINES);
			case HAND_SOLID -> EnumSet.of(ShaderKey.HAND_CUTOUT, ShaderKey.HAND_CUTOUT_DIFFUSE, ShaderKey.HAND_TEXT);
			case HAND_TRANSLUCENT -> EnumSet.of(ShaderKey.HAND_TRANSLUCENT, ShaderKey.HAND_WATER_DIFFUSE, ShaderKey.HAND_TEXT_TRANSLUCENT);
			case PARTICLES -> EnumSet.of(ShaderKey.PARTICLES, ShaderKey.PARTICLES_TRANS, ShaderKey.SHADOW_PARTICLES);
			case CLOUDS -> EnumSet.of(ShaderKey.CLOUDS, ShaderKey.CLOUDS_SODIUM);
			case RAIN_SNOW -> EnumSet.of(ShaderKey.WEATHER, ShaderKey.SHADOW_PARTICLES);
			case WORLD_BORDER -> EnumSet.of(ShaderKey.TEXTURED);
			case DEBUG, NONE -> EnumSet.noneOf(ShaderKey.class);
		};
	}

	private ShaderPackPipelineResources buildResources() {
		List<String> logicalRenderTargets = new ArrayList<>();
		for (Map.Entry<Integer, PackRenderTargetDirectives.RenderTargetSettings> entry : sortedRenderTargetSettings().entrySet()) {
			Vector2i size = packDirectives.getTextureScaleOverride(entry.getKey(), renderWidth, renderHeight);
			logicalRenderTargets.add("colortex" + entry.getKey() + "{format=" + entry.getValue().getInternalFormat().name() + ",clear=" + entry.getValue().shouldClear() + ",size=" + size.x + "x" + size.y + "}");
		}

		List<String> logicalShadowTargets = new ArrayList<>();
		logicalShadowTargets.add("shadowtex0{size=" + shadowSize + "x" + shadowSize + "}");
		logicalShadowTargets.add("shadowtex1{size=" + shadowSize + "x" + shadowSize + "}");
		for (Map.Entry<Integer, PackShadowDirectives.SamplingSettings> entry : sortedShadowColorSettings().entrySet()) {
			logicalShadowTargets.add("shadowcolor" + entry.getKey() + "{format=" + entry.getValue().getFormat().name() + ",clear=" + entry.getValue().getClear() + "}");
		}

		List<String> logicalImages = programSet.getPack().getIrisCustomImages().stream()
			.map(this::imageSummary)
			.sorted()
			.collect(Collectors.toList());
		List<String> logicalSsbo = programSet.getPack().getBufferObjects().int2ObjectEntrySet().stream()
			.map(entry -> "ssbo" + entry.getIntKey() + "{size=" + entry.getValue().size() + ",relative=" + entry.getValue().relative() + "}")
			.sorted()
			.collect(Collectors.toList());

		List<String> customTextures = new ArrayList<>();
		programSet.getPack().getCustomTextureDataMap().forEach((stage, textures) -> textures.keySet().stream().sorted().forEach(name -> customTextures.add(stage.name() + ":" + name)));
		programSet.getPack().getIrisCustomTextureDataMap().keySet().stream().sorted().forEach(name -> customTextures.add("iris:" + name));

		List<String> logicalSamplers = new ArrayList<>();
		logicalSamplers.add("render-target-samplers");
		logicalSamplers.add("shadow-samplers");
		logicalSamplers.add("world-depth-samplers");
		logicalSamplers.add("noise-sampler");
		logicalSamplers.add("center-depth-sampler");
		logicalSamplers.addAll(customTextures.stream().map(name -> "custom-texture:" + name).toList());

		List<String> compatibility = List.of(
			"white-pixel",
			"bigger-white-pixel",
			"noise-texture",
			"center-depth-sampler",
			"pbr-normal-specular-samplers",
			"main-target-bridge"
		);

		List<String> runtimeOwnerQueries = List.of(
			"colortex*/depthtex* -> RenderTargets",
			"shadowcolor*/shadowtex* -> ShadowRenderTargets",
			"custom image name -> custom image set",
			"ssbo index -> ShaderStorageBufferHolder",
			"mainColor/mainDepth -> Minecraft main target reference",
			"derived framebuffer key -> resources RuntimeBindings"
		);

		List<String> derivedRuntimeObjects = List.of(
			"color framebuffer",
			"shadow color framebuffer",
			"gbuffers framebuffer",
			"clear framebuffer group",
			"shadow clear framebuffer group",
			"final baseline copy framebuffer",
			"final restore copy framebuffer",
			"main color holder framebuffer",
			"image clear runtime pass"
		);

		List<ShaderPackBindingDescriptor> bindingDescriptorDefinitions = stages.values().stream()
			.flatMap(List::stream)
			.map(ShaderPackPass::bindings)
			.distinct()
			.sorted((a, b) -> a.descriptorId().compareTo(b.descriptorId()))
			.collect(Collectors.toList());
		List<String> bindingDescriptors = bindingDescriptorDefinitions.stream()
			.map(this::resourcesBindingPlanId)
			.sorted()
			.collect(Collectors.toList());

		return new ShaderPackPipelineResources(
			List.copyOf(logicalRenderTargets),
			List.copyOf(logicalShadowTargets),
			List.copyOf(logicalSamplers),
			List.copyOf(logicalImages),
			List.copyOf(logicalSsbo),
			compatibility,
			List.copyOf(derivedFramebuffers.values()),
			List.of("main-color-texture-version", "main-depth-texture-version", "main-depth-format"),
			List.of("renderTargets", "shadowTargets", "relativeCustomImages", "relativeSSBO", "derivedFramebuffers", "finalHolder", "copyHolders", "setupCompute"),
			List.of("programDescriptors", "bindings", "renderTargets", "customImages", "ssbo", "derivedFramebuffers", "setupCompute"),
			List.of("RenderTargets destroy color/depth framebuffers", "ShadowRenderTargets destroy shadow framebuffers", "custom image owner destroys GlImage", "ShaderStorageBufferHolder destroys SSBO", "program owners destroy programs", "IrisRenderingPipeline orders destroy", "resources describe dependency graph"),
			runtimeOwnerQueries,
			derivedRuntimeObjects,
			bindingDescriptors,
			bindingDescriptorDefinitions
		);
	}

	private String resourcesBindingPlanId(ShaderPackBindingDescriptor descriptor) {
		return descriptor.debugSummary();
	}

	private String imageSummary(ImageInformation image) {
		return image.name() + "{sampler=" + image.samplerName() + ",format=" + image.internalTextureFormat().name() + ",relative=" + image.isRelative() + "}";
	}

	private Map<Integer, PackRenderTargetDirectives.RenderTargetSettings> sortedRenderTargetSettings() {
		return programSet.getPackDirectives().getRenderTargetDirectives().getRenderTargetSettings().entrySet().stream()
			.sorted(Map.Entry.comparingByKey())
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> a, LinkedHashMap::new));
	}

	private Map<Integer, PackShadowDirectives.SamplingSettings> sortedShadowColorSettings() {
		return programSet.getPackDirectives().getShadowDirectives().getColorSamplingSettings().int2ObjectEntrySet().stream()
			.sorted((a, b) -> Integer.compare(a.getIntKey(), b.getIntKey()))
			.collect(Collectors.toMap(entry -> entry.getIntKey(), entry -> entry.getValue(), (a, b) -> a, LinkedHashMap::new));
	}

	private ShaderPackPassLayout layoutFor(String passId, ShaderPackPassStage stage, int[] drawBuffers, Set<Integer> stageWritesToMain,
	                                       Map<Integer, Boolean> explicitPreFlips, Map<Integer, Boolean> explicitFlips, Set<Integer> resolvedFlips,
	                                       Set<Integer> flippedAtLeastOnceSnapshot, DerivedFramebufferKey derivedFramebufferKey) {
		ShaderPackResourceKind kind = stage == ShaderPackPassStage.SHADOW_COMPOSITE || stage == ShaderPackPassStage.SHADOW ? ShaderPackResourceKind.SHADOWCOLOR : ShaderPackResourceKind.COLORTEX;
		return new ShaderPackPassLayout(
			passId,
			bufferInputViews(stageWritesToMain, kind),
			drawBuffers.clone(),
			attachmentMapping(drawBuffers),
			Map.copyOf(explicitPreFlips),
			Map.copyOf(explicitFlips),
			Set.copyOf(resolvedFlips),
			Set.copyOf(flippedAtLeastOnceSnapshot),
			derivedFramebufferKey
		);
	}

	private Map<String, ShaderPackResourceView> bufferInputViews(Set<Integer> flipped, ShaderPackResourceKind kind) {
		Map<String, ShaderPackResourceView> views = new LinkedHashMap<>();
		if (kind == ShaderPackResourceKind.SHADOWCOLOR) {
			for (Integer buffer : sortedShadowBufferIds()) {
				views.put("shadowcolor" + buffer, viewFor(flipped, buffer));
			}
		} else {
			for (Integer buffer : sortedRenderTargetSettings().keySet()) {
				views.put("colortex" + buffer, viewFor(flipped, buffer));
			}
		}
		return Collections.unmodifiableMap(views);
	}

	private List<Integer> sortedShadowBufferIds() {
		LinkedHashSet<Integer> ids = new LinkedHashSet<>();
		ids.addAll(sortedShadowColorSettings().keySet());
		ids.add(0);
		ids.add(1);
		return ids.stream().sorted().collect(Collectors.toList());
	}

	private Map<Integer, Integer> attachmentMapping(int[] drawBuffers) {
		Map<Integer, Integer> mapping = new LinkedHashMap<>();
		for (int i = 0; i < drawBuffers.length; i++) {
			mapping.put(drawBuffers[i], i);
		}
		return Collections.unmodifiableMap(mapping);
	}

	private DerivedFramebufferKey derivedFramebufferKey(ShaderPackPassStage stage, String kind, Set<Integer> stageWritesToMain, int[] drawBuffers) {
		DerivedFramebufferKind framebufferKind = kind.equals("shadow-color") ? DerivedFramebufferKind.SHADOW_COLOR : DerivedFramebufferKind.COLOR;
		return derivedFramebufferKey(framebufferKind, stage, kind, stageWritesToMain, drawBuffers, stage.name().toLowerCase(Locale.ROOT));
	}

	private DerivedFramebufferKey derivedFramebufferKey(DerivedFramebufferKind kind, ShaderPackPassStage stage, String debugKind, Set<Integer> writesToMain, int[] drawBuffers, String source) {
		String key = debugKind + "-stage-" + stage.name() + "-writeMain-" + sorted(writesToMain) + "-drawBuffers-" + Arrays.toString(drawBuffers);
		String owner = kind == DerivedFramebufferKind.SHADOW_COLOR ? "ShadowRenderTargets" : "RenderTargets";
		return registerDerivedFramebuffer(key, kind, owner, writesToMain, drawBuffers, source, debugKind);
	}

	private DerivedFramebufferKey registerDerivedFramebuffer(String id, DerivedFramebufferKind kind, String owner, Set<Integer> writesToMain, int[] drawBuffers, String source, String debugLabel) {
		DerivedFramebufferKey key = DerivedFramebufferKey.of(id);
		derivedFramebuffers.putIfAbsent(key, new DerivedFramebufferDescriptor(key, kind, owner, writesToMain, drawBuffers, source, debugLabel));
		return key;
	}

	private List<ShaderPackPassResource> renderTargetInputs(Set<Integer> flipped, String usage) {
		List<ShaderPackPassResource> inputs = new ArrayList<>();
		for (Integer buffer : sortedRenderTargetSettings().keySet()) {
			inputs.add(new ShaderPackPassResource("colortex" + buffer, ShaderPackResourceKind.COLORTEX, viewFor(flipped, buffer), usage));
		}
		inputs.add(new ShaderPackPassResource("depthtex0", ShaderPackResourceKind.DEPTHTEX, ShaderPackResourceView.UNRESOLVED, usage));
		inputs.add(new ShaderPackPassResource("depthtex1", ShaderPackResourceKind.DEPTHTEX, ShaderPackResourceView.UNRESOLVED, usage));
		inputs.add(new ShaderPackPassResource("depthtex2", ShaderPackResourceKind.DEPTHTEX, ShaderPackResourceView.UNRESOLVED, usage));
		return List.copyOf(inputs);
	}

	private List<ShaderPackPassResource> renderTargetOutputs(int[] drawBuffers, String usage) {
		List<ShaderPackPassResource> outputs = new ArrayList<>();
		for (int buffer : drawBuffers) {
			outputs.add(new ShaderPackPassResource("colortex" + buffer, ShaderPackResourceKind.COLORTEX, ShaderPackResourceView.UNRESOLVED, usage));
		}
		return List.copyOf(outputs);
	}

	private List<ShaderPackPassResource> shadowInputs(Set<Integer> flipped, String usage) {
		List<ShaderPackPassResource> inputs = new ArrayList<>();
		for (Integer buffer : sortedShadowBufferIds()) {
			inputs.add(new ShaderPackPassResource("shadowcolor" + buffer, ShaderPackResourceKind.SHADOWCOLOR, viewFor(flipped, buffer), usage));
		}
		inputs.add(new ShaderPackPassResource("shadowtex0", ShaderPackResourceKind.SHADOWTEX, ShaderPackResourceView.UNRESOLVED, usage));
		inputs.add(new ShaderPackPassResource("shadowtex1", ShaderPackResourceKind.SHADOWTEX, ShaderPackResourceView.UNRESOLVED, usage));
		return List.copyOf(inputs);
	}

	private List<ShaderPackPassResource> shadowOutputs(int[] drawBuffers, String usage) {
		List<ShaderPackPassResource> outputs = new ArrayList<>();
		for (int buffer : drawBuffers) {
			outputs.add(new ShaderPackPassResource("shadowcolor" + buffer, ShaderPackResourceKind.SHADOWCOLOR, ShaderPackResourceView.UNRESOLVED, usage));
		}
		return List.copyOf(outputs);
	}

	private List<ShaderPackProgramDescriptor> programDescriptors(ProgramSource source, ComputeSource[] computes, String arrayId, String programId, String textureStage) {
		List<ShaderPackProgramDescriptor> descriptors = new ArrayList<>();
		descriptors.add(ShaderPackProgramDescriptor.graphics(source.getName(), source.getName(), arrayId, programId, textureStage, source.isValid()));
		descriptors.addAll(computeDescriptors(computes, arrayId, textureStage));
		return List.copyOf(descriptors);
	}

	private List<ShaderPackProgramDescriptor> computeDescriptors(ComputeSource[] computes, String arrayId, String textureStage) {
		List<ShaderPackProgramDescriptor> descriptors = new ArrayList<>();
		for (ComputeSource compute : computes) {
			if (isValidCompute(compute)) {
				descriptors.add(ShaderPackProgramDescriptor.compute(compute.getName(), compute.getName(), arrayId, textureStage));
			}
		}
		return List.copyOf(descriptors);
	}

	private ShaderPackBindingDescriptor bindingDescriptor(ShaderPackPassStage stage, TextureStage textureStage) {
		String id = ShaderPackPipelineResources.bindingDescriptorId(stage, textureStage);

		List<SamplerBindingDirective> samplers = new ArrayList<>();
		List<ImageBindingDirective> images = new ArrayList<>();
		List<CompatibilityResourceDirective> compatibility = new ArrayList<>(List.of(compatibility(CompatibilityResourceKind.NOISE_TEXTURE)));

		switch (stage) {
			case SETUP -> {
				samplers.addAll(samplers(SamplerBindingKind.RENDER_TARGETS, SamplerBindingKind.CUSTOM_TEXTURES, SamplerBindingKind.CUSTOM_IMAGES, SamplerBindingKind.NOISE, SamplerBindingKind.COMPOSITE_DEPTH, SamplerBindingKind.SHADOW_IF_PRESENT));
				images.addAll(images(ImageBindingKind.RENDER_TARGETS, ImageBindingKind.CUSTOM_IMAGES, ImageBindingKind.SHADOW_COLOR_IF_SAMPLER_PRESENT));
			}
			case SHADOW -> {
				samplers.addAll(samplers(SamplerBindingKind.RENDER_TARGETS, SamplerBindingKind.CUSTOM_TEXTURES, SamplerBindingKind.CUSTOM_IMAGES, SamplerBindingKind.LEVEL, SamplerBindingKind.NOISE, SamplerBindingKind.SHADOW_IF_PRESENT));
				images.addAll(images(ImageBindingKind.RENDER_TARGETS, ImageBindingKind.CUSTOM_IMAGES, ImageBindingKind.SHADOW_COLOR_IF_SAMPLER_PRESENT));
				compatibility.add(compatibility(CompatibilityResourceKind.WHITE_PIXEL));
			}
			case SHADOW_COMPOSITE -> {
				samplers.addAll(samplers(SamplerBindingKind.NOISE, SamplerBindingKind.CUSTOM_TEXTURES_INTERCEPTED, SamplerBindingKind.SHADOW, SamplerBindingKind.CUSTOM_IMAGES));
				images.addAll(images(ImageBindingKind.SHADOW_COLOR, ImageBindingKind.CUSTOM_IMAGES));
			}
			case GBUFFERS -> {
				samplers.addAll(samplers(SamplerBindingKind.RENDER_TARGETS, SamplerBindingKind.CUSTOM_TEXTURES_INTERCEPTED, SamplerBindingKind.CUSTOM_IMAGES, SamplerBindingKind.LEVEL, SamplerBindingKind.WORLD_DEPTH, SamplerBindingKind.NOISE, SamplerBindingKind.PBR_DETECTION, SamplerBindingKind.SHADOW_IF_PRESENT));
				images.addAll(images(ImageBindingKind.RENDER_TARGETS, ImageBindingKind.CUSTOM_IMAGES, ImageBindingKind.SHADOW_COLOR_IF_IMAGE_PRESENT_OR_SHADOW_PASS));
				compatibility.addAll(List.of(compatibility(CompatibilityResourceKind.WHITE_PIXEL), compatibility(CompatibilityResourceKind.PBR_NORMAL_SPECULAR_SAMPLERS)));
			}
			case BEGIN, PREPARE, DEFERRED, COMPOSITE, FINAL -> {
				samplers.addAll(samplers(SamplerBindingKind.RENDER_TARGETS, SamplerBindingKind.CUSTOM_TEXTURES, SamplerBindingKind.CUSTOM_IMAGES, SamplerBindingKind.NOISE, SamplerBindingKind.COMPOSITE_DEPTH, SamplerBindingKind.SHADOW_IF_PRESENT, SamplerBindingKind.CENTER_DEPTH));
				images.addAll(images(ImageBindingKind.RENDER_TARGETS, ImageBindingKind.CUSTOM_IMAGES, ImageBindingKind.SHADOW_COLOR_IF_SAMPLER_PRESENT));
				compatibility.add(compatibility(CompatibilityResourceKind.CENTER_DEPTH_SAMPLER));
			}
		}

		return new ShaderPackBindingDescriptor(
			id,
			textureStage.name(),
			List.copyOf(samplers),
			List.copyOf(images),
			programSet.getPack().getBufferObjects().isEmpty() ? List.of() : List.of(SsboBindingDirective.of(SsboBindingKind.SHADER_STORAGE_BUFFERS)),
			List.copyOf(compatibility)
		);
	}

	private ShaderPackBindingDescriptor emptyBindingDescriptor(String id) {
		return new ShaderPackBindingDescriptor("bindings:" + id, "none", List.of(), List.of(), List.of(), List.of());
	}

	private static List<SamplerBindingDirective> samplers(SamplerBindingKind... kinds) {
		return Arrays.stream(kinds).map(SamplerBindingDirective::of).toList();
	}

	private static List<ImageBindingDirective> images(ImageBindingKind... kinds) {
		return Arrays.stream(kinds).map(ImageBindingDirective::of).toList();
	}

	private static CompatibilityResourceDirective compatibility(CompatibilityResourceKind kind) {
		return CompatibilityResourceDirective.of(kind);
	}

	private ShaderPackPassBehavior behavior(String blend, BlendModeOverride blendModeOverride, ViewportData viewportData, Set<Integer> mipmappedInputs, boolean hasCompute, String trigger) {
		return behavior(blend, blendModeOverride, viewportData, mipmappedInputs, hasCompute, trigger, "none", "none");
	}

	private ShaderPackPassBehavior behavior(String blend, BlendModeOverride blendModeOverride, ViewportData viewportData, Set<Integer> mipmappedInputs, boolean hasCompute, String trigger, String clearIntent, String copyIntent) {
		return new ShaderPackPassBehavior(
			blend,
			blendModeOverride,
			viewportData,
			Set.copyOf(mipmappedInputs),
			hasCompute ? Set.of("GL_SHADER_IMAGE_ACCESS_BARRIER_BIT", "GL_TEXTURE_FETCH_BARRIER_BIT", "GL_SHADER_STORAGE_BARRIER_BIT") : Set.of(),
			clearIntent,
			copyIntent,
			trigger
		);
	}

	private void applyPreFlips(LinkedHashSet<Integer> flipper, Map<Integer, Boolean> explicitPreFlips) {
		explicitPreFlips.forEach((buffer, shouldFlip) -> {
			if (shouldFlip) {
				flip(flipper, buffer);
			}
		});
	}

	private void flip(LinkedHashSet<Integer> flipper, int target) {
		if (!flipper.remove(target)) {
			flipper.add(target);
		}
	}

	private ShaderPackResourceView viewFor(Set<Integer> flipped, int buffer) {
		return flipped.contains(buffer) ? ShaderPackResourceView.ALT : ShaderPackResourceView.MAIN;
	}

	private ComputeSource[] computeSourcesAt(ComputeSource[][] computes, int index) {
		if (computes.length == 0 || index >= computes.length || computes[index] == null) {
			return new ComputeSource[0];
		}
		return computes[index];
	}

	private boolean hasComputes(ComputeSource[] computes) {
		for (ComputeSource compute : computes) {
			if (isValidCompute(compute)) {
				return true;
			}
		}
		return false;
	}

	private boolean isValidCompute(ComputeSource compute) {
		return compute != null && compute.getSource().isPresent();
	}

	private String firstComputeName(ComputeSource[] computes) {
		for (ComputeSource compute : computes) {
			if (isValidCompute(compute)) {
				return compute.getName();
			}
		}
		return "unknown";
	}

	private String stablePassId(ShaderPackPassStage stage, int index, String name) {
		return stage.name().toLowerCase() + "/" + String.format("%03d", index) + "/" + name;
	}

	private List<Integer> sorted(Set<Integer> values) {
		return values.stream().sorted().collect(Collectors.toList());
	}

	private record StageBuildResult(Set<Integer> finalFlipped, Set<Integer> flippedAtLeastOnce) {
	}
}
