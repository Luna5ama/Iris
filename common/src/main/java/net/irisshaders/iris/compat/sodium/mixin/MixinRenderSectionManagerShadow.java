package net.irisshaders.iris.compat.sodium.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import it.unimi.dsi.fastutil.objects.ReferenceSet;
import net.caffeinemc.mods.sodium.client.SodiumClientMod;
import net.caffeinemc.mods.sodium.client.gl.device.CommandList;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSection;
import net.caffeinemc.mods.sodium.client.render.chunk.RenderSectionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.TaskQueueType;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.BuilderTaskOutput;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkBuilder;
import net.caffeinemc.mods.sodium.client.render.chunk.compile.executor.ChunkJobResult;
import net.caffeinemc.mods.sodium.client.render.chunk.data.BuiltSectionInfo;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.SortedRenderLists;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.TreeSectionCollector;
import net.caffeinemc.mods.sodium.client.render.chunk.region.RenderRegionManager;
import net.caffeinemc.mods.sodium.client.render.chunk.translucent_sorting.SortBehavior;
import net.caffeinemc.mods.sodium.client.render.viewport.Viewport;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.caffeinemc.mods.sodium.client.util.iterator.ByteIterator;
import net.irisshaders.iris.mixinterface.ShadowRenderRegion;
import net.irisshaders.iris.mixinterface.VibrisShadowTerrainInvalidation;
import net.irisshaders.iris.mixinterface.VibrisTerrainQuiescence;
import net.irisshaders.iris.shadows.ShadowRenderingState;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedDeque;

@Mixin(RenderSectionManager.class)
public abstract class MixinRenderSectionManagerShadow implements VibrisTerrainQuiescence, VibrisShadowTerrainInvalidation {
	@Shadow(remap = false)
	@Final
	private ChunkBuilder builder;

	@Shadow(remap = false)
	@Final
	private ConcurrentLinkedDeque<ChunkJobResult<? extends BuilderTaskOutput>> buildResults;

	@Shadow(remap = false)
	@Final
	private Long2ReferenceMap<RenderSection> sectionByPosition;

	@Shadow(remap = false)
	private boolean needsGraphUpdate;

	@Shadow(remap = false)
	@Final
	private ReferenceSet<RenderSection> sectionsWithGlobalEntities;

	@Shadow(remap = false)
	private int thisFrameBlockingTasks;

	@Shadow(remap = false)
	private int nextFrameBlockingTasks;

	@Shadow(remap = false)
	private int deferredTasks;

	@Shadow(remap = false)
	private @NotNull SortedRenderLists renderLists;
	@Shadow(remap = false)
	private @NotNull Map<TaskQueueType, ArrayDeque<RenderSection>> taskLists;
	@Shadow
	private int lastUpdatedFrame;

	@Shadow
	protected abstract boolean isOutOfGraph(SectionPos pos);

	@Shadow
	@Final
	private RenderRegionManager regions;
	@Unique
	private @NotNull SortedRenderLists shadowRenderLists = SortedRenderLists.empty();
	@Unique
	private @NotNull Map<TaskQueueType, ArrayDeque<RenderSection>> shadowTaskLists = new EnumMap<>(TaskQueueType.class);
	private int lastUpdatedFrameShadow;

	@Unique
	private boolean shadowNeedsRenderListUpdate = true;
	@Unique
	private long shadowRequestedRenderListGeneration = 1;
	@Unique
	private long shadowTraversalGeneration;
	@Unique
	private long shadowFinalizedRenderListGeneration;
	@Unique
	private boolean shadowTraversalPendingFinalization;

	@Unique
	private boolean renderListStateIsShadow = false;

	@Unique
	@Override
	public TerrainSnapshot iris$captureTerrainSnapshot() {
		List<String> mismatches = new ArrayList<>();
		String terrainWorkMismatch = iris$terrainWorkMismatch();
		boolean quiescent = terrainWorkMismatch.isEmpty();
		if (!quiescent) mismatches.add(terrainWorkMismatch);
		SortedRenderLists capturedRegularRenderLists = renderLists;
		SortedRenderLists capturedShadowRenderLists = shadowRenderLists;
		Map<TaskQueueType, ArrayDeque<RenderSection>> capturedRegularTaskLists = taskLists;
		Map<TaskQueueType, ArrayDeque<RenderSection>> capturedShadowTaskLists = shadowTaskLists;
		boolean capturedShadowNeedsRenderListUpdate = shadowNeedsRenderListUpdate;
		long capturedShadowRequestedGeneration = shadowRequestedRenderListGeneration;
		long capturedShadowFinalizedGeneration = shadowFinalizedRenderListGeneration;
		boolean capturedShadowTraversalPendingFinalization = shadowTraversalPendingFinalization;
		boolean shadowGenerationReady = VibrisShadowTerrainInvalidation.isGenerationReady(
			capturedShadowNeedsRenderListUpdate,
			capturedShadowRequestedGeneration,
			capturedShadowFinalizedGeneration,
			capturedShadowTraversalPendingFinalization
		);
		quiescent &= shadowGenerationReady;
		if (!shadowGenerationReady) {
			mismatches.add("shadow render list generation is pending: needs_update=" +
				capturedShadowNeedsRenderListUpdate + ", traversal_pending=" +
				capturedShadowTraversalPendingFinalization + ", requested=" +
				capturedShadowRequestedGeneration + ", finalized=" + capturedShadowFinalizedGeneration);
		}
		if (!quiescent) {
			return new TerrainSnapshot(
				false,
				List.of(),
				List.of(),
				List.of(),
				capturedShadowNeedsRenderListUpdate,
				List.of(),
				String.join("; ", mismatches)
			);
		}

		List<RenderListState> regularRenderListStates = new ArrayList<>();
		List<RenderListState> shadowRenderListStates = new ArrayList<>();
		boolean regularRenderListsValid =
			iris$captureRenderLists(capturedRegularRenderLists, regularRenderListStates, true);
		boolean shadowRenderListsValid =
			iris$captureRenderLists(capturedShadowRenderLists, shadowRenderListStates, false);
		quiescent &= regularRenderListsValid && shadowRenderListsValid;
		if (!regularRenderListsValid) mismatches.add("regular render lists contain unfinished or fading sections");
		if (!shadowRenderListsValid) mismatches.add("shadow render lists contain invalid sections");

		List<ShadowTaskState> shadowPendingTasks = new ArrayList<>();
		boolean shadowTasksValid = iris$captureShadowPendingTasks(capturedShadowTaskLists, shadowPendingTasks);
		quiescent &= shadowTasksValid;
		if (!shadowTasksValid) mismatches.add("shadow task queues contain invalid section identities");

		List<GlobalEntitySectionState> globalEntitySections = new ArrayList<>();
		boolean globalEntitySectionsValid = iris$captureGlobalEntitySections(globalEntitySections);
		quiescent &= globalEntitySectionsValid;
		if (!globalEntitySectionsValid) mismatches.add("global block entity sections are invalid");

		List<ShadowTaskState> recheckedShadowPendingTasks = new ArrayList<>();
		boolean shadowTasksStillValid = iris$captureShadowPendingTasks(shadowTaskLists, recheckedShadowPendingTasks);
		List<RenderListState> recheckedRegularRenderListStates = new ArrayList<>();
		boolean regularVisibleStillValid =
			iris$captureRenderLists(renderLists, recheckedRegularRenderListStates, true);
		List<RenderListState> recheckedShadowRenderListStates = new ArrayList<>();
		boolean shadowVisibleStillValid =
			iris$captureRenderLists(shadowRenderLists, recheckedShadowRenderListStates, false);
		List<GlobalEntitySectionState> recheckedGlobalEntitySections = new ArrayList<>();
		boolean globalEntitiesStillValid = iris$captureGlobalEntitySections(recheckedGlobalEntitySections);
		boolean capturedStateUnchanged = capturedRegularRenderLists == renderLists &&
			capturedShadowRenderLists == shadowRenderLists && capturedRegularTaskLists == taskLists &&
			capturedShadowTaskLists == shadowTaskLists &&
			capturedShadowNeedsRenderListUpdate == shadowNeedsRenderListUpdate &&
			capturedShadowRequestedGeneration == shadowRequestedRenderListGeneration &&
			capturedShadowFinalizedGeneration == shadowFinalizedRenderListGeneration &&
			capturedShadowTraversalPendingFinalization == shadowTraversalPendingFinalization &&
			shadowPendingTasks.equals(recheckedShadowPendingTasks) &&
			regularRenderListStates.equals(recheckedRegularRenderListStates) &&
			shadowRenderListStates.equals(recheckedShadowRenderListStates) &&
			globalEntitySections.equals(recheckedGlobalEntitySections);
		String recheckedTerrainWorkMismatch = iris$terrainWorkMismatch();
		boolean recheckedTerrainWorkReady = recheckedTerrainWorkMismatch.isEmpty();
		quiescent &= shadowTasksStillValid && regularVisibleStillValid && shadowVisibleStillValid &&
			globalEntitiesStillValid && capturedStateUnchanged && recheckedTerrainWorkReady;
		if (!shadowTasksStillValid || !regularVisibleStillValid || !shadowVisibleStillValid ||
			!globalEntitiesStillValid || !capturedStateUnchanged) {
			mismatches.add("terrain state changed while the atomic snapshot was captured");
		}
		if (!recheckedTerrainWorkReady) mismatches.add(recheckedTerrainWorkMismatch);

		return new TerrainSnapshot(
			quiescent,
			regularRenderListStates,
			shadowRenderListStates,
			shadowPendingTasks,
			capturedShadowNeedsRenderListUpdate,
			globalEntitySections,
			String.join("; ", mismatches)
		);
	}

	@Unique
	private String iris$terrainWorkMismatch() {
		int scheduledJobs = builder.getScheduledJobCount();
		int busyThreads = builder.getBusyThreadCount();
		if (needsGraphUpdate || scheduledJobs != 0 || busyThreads != 0 || !buildResults.isEmpty() ||
			thisFrameBlockingTasks != 0 || nextFrameBlockingTasks != 0 || deferredTasks != 0) {
			return "terrain work is pending: graph_update=" + needsGraphUpdate + ", scheduled=" + scheduledJobs +
				", busy=" + busyThreads + ", results=" + buildResults.size() + ", blocking_now=" +
				thisFrameBlockingTasks + ", blocking_next=" + nextFrameBlockingTasks + ", deferred=" + deferredTasks;
		}
		for (Map.Entry<TaskQueueType, ArrayDeque<RenderSection>> entry : taskLists.entrySet()) {
			if (!entry.getValue().isEmpty()) {
				return "regular terrain task queue is pending: type=" + entry.getKey() + ", count=" +
					entry.getValue().size();
			}
		}
		int runningJobs = 0;
		for (RenderSection section : sectionByPosition.values()) {
			if (section == null) return "terrain section map contains a null section";
			if (section.getRunningJob() != null) runningJobs++;
		}
		return runningJobs == 0 ? "" : "terrain sections still have running jobs: count=" + runningJobs;
	}

	@Unique
	private boolean iris$captureRenderLists(
		SortedRenderLists capturedRenderLists,
		List<RenderListState> renderListStates,
		boolean requireNoPendingUpdate
	) {
		if (capturedRenderLists == null) {
			return false;
		}

		boolean valid = true;
		valid &= iris$captureRenderListTraversal(
			capturedRenderLists, false, renderListStates, requireNoPendingUpdate);
		valid &= iris$captureRenderListTraversal(
			capturedRenderLists, true, renderListStates, requireNoPendingUpdate);
		return valid;
	}

	@Unique
	private boolean iris$captureRenderListTraversal(
		SortedRenderLists capturedRenderLists,
		boolean reverse,
		List<RenderListState> renderListStates,
		boolean requireNoPendingUpdate
	) {
		boolean valid = true;
		var renderListIterator = capturedRenderLists.iterator(reverse);
		while (renderListIterator.hasNext()) {
			ChunkRenderList renderList = renderListIterator.next();
			if (renderList == null || renderList.getRegion() == null) {
				valid = false;
				continue;
			}

			List<SectionState> geometrySections = new ArrayList<>();
			List<SectionState> spriteSections = new ArrayList<>();
			List<SectionState> entitySections = new ArrayList<>();
			valid &= iris$captureSectionSequence(
				renderList, renderList.sectionsWithGeometryIterator(reverse), geometrySections, requireNoPendingUpdate);
			if (!reverse) {
				valid &= iris$captureSectionSequence(
					renderList, renderList.sectionsWithSpritesIterator(), spriteSections, requireNoPendingUpdate);
				valid &= iris$captureSectionSequence(
					renderList, renderList.sectionsWithEntitiesIterator(), entitySections, requireNoPendingUpdate);
			}
			renderListStates.add(new RenderListState(
				reverse,
				renderList,
				renderList.getRegion(),
				geometrySections,
				spriteSections,
				entitySections
			));
		}
		return valid;
	}

	@Unique
	private boolean iris$captureSectionSequence(
		ChunkRenderList renderList,
		ByteIterator iterator,
		List<SectionState> sectionStates,
		boolean requireNoPendingUpdate
	) {
		if (iterator == null) {
			return true;
		}

		boolean valid = true;
		while (iterator.hasNext()) {
			RenderSection section = renderList.getRegion().getSection(iterator.nextByteAsInt());
			if (section == null) {
				valid = false;
				continue;
			}

			long position = SectionPos.asLong(section.getChunkX(), section.getChunkY(), section.getChunkZ());
			if (sectionByPosition.get(position) != section) {
				valid = false;
			}
			int pendingUpdateType = section.getPendingUpdate();
			long pendingUpdateSince = section.getPendingUpdateSince();
			if ((requireNoPendingUpdate && pendingUpdateType != 0) || section.getRunningJob() != null ||
				section.getCurrentVisibility() < 1.0F) {
				valid = false;
			}
			sectionStates.add(new SectionState(
				position,
				section.getFlags(),
				section.getVisibilityData(),
				section.getLastUploadFrame(),
				pendingUpdateType,
				pendingUpdateSince,
				section
			));
		}

		return valid;
	}

	@Unique
	private boolean iris$captureShadowPendingTasks(
		Map<TaskQueueType, ArrayDeque<RenderSection>> capturedShadowTaskLists,
		List<ShadowTaskState> shadowPendingTasks
	) {
		if (capturedShadowTaskLists == null) {
			return false;
		}

		boolean valid = true;
		for (TaskQueueType queueType : TaskQueueType.values()) {
			ArrayDeque<RenderSection> tasks = capturedShadowTaskLists.get(queueType);
			if (tasks == null) {
				valid = false;
				continue;
			}
			for (RenderSection section : tasks) {
				if (section == null) {
					valid = false;
					continue;
				}
				long position = SectionPos.asLong(section.getChunkX(), section.getChunkY(), section.getChunkZ());
				if (sectionByPosition.get(position) != section) {
					valid = false;
				}
				shadowPendingTasks.add(new ShadowTaskState(
					queueType,
					position,
					section.getPendingUpdate(),
					section.getPendingUpdateSince(),
					section
				));
			}
		}
		return valid;
	}

	@Unique
	private boolean iris$captureGlobalEntitySections(List<GlobalEntitySectionState> capturedSections) {
		boolean valid = true;
		TreeMap<Long, GlobalEntitySectionState> sortedSections = new TreeMap<>();
		for (RenderSection section : sectionsWithGlobalEntities) {
			if (section == null) {
				valid = false;
				continue;
			}

			long sectionPosition = SectionPos.asLong(section.getChunkX(), section.getChunkY(), section.getChunkZ());
			BlockEntity[] globalBlockEntities = section.getGlobalBlockEntities();
			if (globalBlockEntities == null) {
				valid = false;
				continue;
			}

			List<GlobalBlockEntityState> entityStates = new ArrayList<>(globalBlockEntities.length);
			for (BlockEntity blockEntity : globalBlockEntities) {
				if (blockEntity == null) {
					valid = false;
					continue;
				}
				entityStates.add(new GlobalBlockEntityState(blockEntity.getBlockPos().asLong(), blockEntity));
			}
			entityStates.sort(Comparator.comparingLong(GlobalBlockEntityState::position));

			GlobalEntitySectionState state = new GlobalEntitySectionState(
				new SectionState(
					sectionPosition,
					section.getFlags(),
					section.getVisibilityData(),
					section.getLastUploadFrame(),
					section.getPendingUpdate(),
					section.getPendingUpdateSince(),
					section
				),
				entityStates
			);
			if (sortedSections.putIfAbsent(sectionPosition, state) != null) {
				valid = false;
			}
		}

		capturedSections.addAll(sortedSections.values());
		return valid;
	}

	@Inject(method = "needsUpdate", at = @At("RETURN"), cancellable = true)
	private void notifyChangedCamera(CallbackInfoReturnable<Boolean> cir) {
		if (ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
			if (this.shadowNeedsRenderListUpdate) {
				cir.setReturnValue(true);
			}
		} else if (cir.getReturnValue()) {
			iris$markShadowRenderListDirty();
		}
	}

	@Inject(method = "<init>", at = @At("TAIL"))
	private void create(ClientLevel level, int renderDistance, SortBehavior sortBehavior, CommandList commandList, CallbackInfo ci) {
		for (int var6 = 0; var6 < TaskQueueType.values().length; ++var6) {
			TaskQueueType type = TaskQueueType.values()[var6];
			shadowTaskLists.put(type, new ArrayDeque<>());
		}
	}

	@Redirect(remap = false, method = "finalizeRenderLists", at = @At(value = "FIELD", target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSectionManager;renderLists:Lnet/caffeinemc/mods/sodium/client/render/chunk/lists/SortedRenderLists;"))
	private void useShadowRenderList(RenderSectionManager instance, SortedRenderLists value) {
		if (ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
			shadowRenderLists = value;
		} else {
			renderLists = value;
		}
	}

	@WrapMethod(method = "createTerrainRenderList")
	private boolean updateShadowRenderLists(Camera camera, Viewport viewport, FogParameters fogParameters, int frame, boolean spectator, Operation<Boolean> original) {
		boolean shadow = ShadowRenderingState.areShadowsCurrentlyBeingRendered();
		if (!shadow) {
			if (this.renderListStateIsShadow) {
				for (var region : this.regions.getLoadedRegions()) {
					((net.irisshaders.iris.mixinterface.ShadowRenderRegion) region).swapToRegularRenderList();
				}
				this.renderListStateIsShadow = false;
			}
		} else {
			if (!this.renderListStateIsShadow) {
				for (var region : this.regions.getLoadedRegions()) {
					((ShadowRenderRegion) region).swapToShadowRenderList();
				}
				this.renderListStateIsShadow = true;
			}
		}

		long generation = this.shadowRequestedRenderListGeneration;
		boolean needsRevisit = original.call(camera, viewport, fogParameters, frame, spectator);
		if (shadow) {
			this.shadowTraversalGeneration = generation;
			this.shadowTraversalPendingFinalization = true;
		}
		return needsRevisit;
	}

	@Inject(method = "finalizeRenderLists", at = @At("TAIL"), remap = false)
	private void iris$finalizeShadowRenderListGeneration(Viewport viewport, CallbackInfo ci) {
		if (!ShadowRenderingState.areShadowsCurrentlyBeingRendered() ||
			!this.shadowTraversalPendingFinalization) {
			return;
		}

		this.shadowTraversalPendingFinalization = false;
		if (this.shadowTraversalGeneration == this.shadowRequestedRenderListGeneration) {
			this.shadowFinalizedRenderListGeneration = this.shadowTraversalGeneration;
			this.shadowNeedsRenderListUpdate = false;
		}
	}

	@Unique
	@Override
	public void iris$markShadowRenderListDirty() {
		this.shadowNeedsRenderListUpdate = true;
		this.shadowRequestedRenderListGeneration++;
	}

	@Inject(method = "updateSectionInfo", at = @At("RETURN"))
	private void updateSectionInfo(RenderSection render, BuiltSectionInfo info, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ()) iris$markShadowRenderListDirty();
	}

	@Inject(method = "onSectionRemoved", at = @At("HEAD"))
	private void onSectionRemoved(int x, int y, int z, CallbackInfo ci) {
		iris$markShadowRenderListDirty();
	}

	@Redirect(remap = false, method = "createTerrainRenderList", at = @At(value = "FIELD", target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSectionManager;taskLists:Ljava/util/Map;"))
	private void useShadowTaskrList(RenderSectionManager instance, @NotNull Map<TaskQueueType, ArrayDeque<RenderSection>> value) {
		if (ShadowRenderingState.areShadowsCurrentlyBeingRendered()) {
			shadowTaskLists = value;
		} else {
			taskLists = value;
		}
	}

	/**
	 * Adding a note for myself: This is how the occlusion culling skip for the shadow map is done. Remember this.
	 */
	@Redirect(method = "createTerrainRenderList", at = @At(value = "INVOKE", target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSectionManager;isOutOfGraph(Lnet/minecraft/core/SectionPos;)Z"))
	private boolean iris$setOutOfGraph(RenderSectionManager instance, SectionPos pos) {
		return ShadowRenderingState.areShadowsCurrentlyBeingRendered() || this.isOutOfGraph(pos);
	}

	@Redirect(method = {
		"getRenderLists",
		"getVisibleChunkCount",
		"renderLayer"
	}, at = @At(value = "FIELD", target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSectionManager;renderLists:Lnet/caffeinemc/mods/sodium/client/render/chunk/lists/SortedRenderLists;"), remap = false)
	private SortedRenderLists useShadowRenderList2(RenderSectionManager instance) {
		return ShadowRenderingState.areShadowsCurrentlyBeingRendered() ? shadowRenderLists : renderLists;
	}

	@Inject(method = "updateChunks", at = @At("HEAD"), cancellable = true, remap = false)
	private void doNotUpdateDuringShadow(boolean updateImmediately, CallbackInfo ci) {
		if (ShadowRenderingState.areShadowsCurrentlyBeingRendered()) ci.cancel();
	}

	@Inject(method = "uploadChunks", at = @At("HEAD"), cancellable = true, remap = false)
	private void doNotUploadDuringShadow(CallbackInfo ci) {
		if (ShadowRenderingState.areShadowsCurrentlyBeingRendered()) ci.cancel();
	}

	@Redirect(method = {
		"resetRenderLists",
		"submitSectionTasks(Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/executor/ChunkJobCollector;Lnet/caffeinemc/mods/sodium/client/render/chunk/compile/estimation/UploadResourceBudget;Lnet/caffeinemc/mods/sodium/client/render/chunk/TaskQueueType;)V"
	}, at = @At(value = "FIELD", target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSectionManager;taskLists:Ljava/util/Map;"), remap = false)
	private @NotNull Map<TaskQueueType, ArrayDeque<RenderSection>> useShadowTaskList3(RenderSectionManager instance) {
		return ShadowRenderingState.areShadowsCurrentlyBeingRendered() ? shadowTaskLists : taskLists;
	}

	@Redirect(method = {
		"resetRenderLists"
	}, at = @At(value = "FIELD", target = "Lnet/caffeinemc/mods/sodium/client/render/chunk/RenderSectionManager;renderLists:Lnet/caffeinemc/mods/sodium/client/render/chunk/lists/SortedRenderLists;"), remap = false)
	private void useShadowRenderList3(RenderSectionManager instance, SortedRenderLists value) {
		if (ShadowRenderingState.areShadowsCurrentlyBeingRendered()) shadowRenderLists = value;
		else renderLists = value;
	}
}
