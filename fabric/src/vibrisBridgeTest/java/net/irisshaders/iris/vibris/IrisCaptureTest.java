package net.irisshaders.iris.vibris;

import dev.vibris.api.ArtifactSink;
import dev.vibris.api.CancellationToken;
import dev.vibris.api.CapturePlan;
import dev.vibris.api.CaptureResourceNotFoundException;
import dev.vibris.api.CaptureResult;
import dev.vibris.api.ContextApplyResult;
import dev.vibris.api.ReloadResult;
import dev.vibris.api.ResourceCatalog;
import dev.vibris.api.RuntimeStatus;
import dev.vibris.api.SceneContext;
import dev.vibris.api.TemporalResetResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;

import static dev.vibris.api.CapturePlan.ArtifactFormat.BIN;
import static dev.vibris.api.CapturePlan.ArtifactFormat.PNG;
import static dev.vibris.api.CapturePlan.ArtifactFormat.RAW;
import static dev.vibris.api.ResourceCatalog.ResourceKind.BUFFER;
import static dev.vibris.api.ResourceCatalog.ResourceKind.FINAL_FRAMEBUFFER;
import static dev.vibris.api.ResourceCatalog.ResourceKind.TEXTURE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IrisCaptureTest {
	@TempDir
	Path output;

	@Test
	void screenshotTextureBufferReadable() throws Exception {
		IrisVibrisFrameClock frames = new IrisVibrisFrameClock();
		IrisVibrisRuntimeAdapter adapter = new IrisVibrisRuntimeAdapter(new CaptureHost(), frames);
		CapturePlan plan = new CapturePlan(List.of(
			target(FINAL_FRAMEBUFFER, "beauty", PNG, "beauty"),
			target(TEXTURE, "colortex0", RAW, "colortex0"),
			target(BUFFER, "radiance_cache", BIN, "radiance_cache")));

		var capture = adapter.capture(plan, new DirectorySink(output), CancellationToken.none())
			.toCompletableFuture();
		assertFalse(capture.isDone());
		frames.renderedFrame();
		CaptureResult result = capture.join();

		assertEquals(1, result.frameId());
		assertEquals(1, result.artifacts().values().stream().map(ResourceCatalog.ResourceDescriptor::frameId)
			.distinct().count());
		assertNotNull(ImageIO.read(output.resolve("beauty.png").toFile()));
		assertEquals(32, Files.size(output.resolve("colortex0.raw")));
		assertEquals(16, Files.size(output.resolve("radiance_cache.bin")));
		assertTrue(Files.isRegularFile(output.resolve("colortex0.json")));
		assertTrue(Files.isRegularFile(output.resolve("radiance_cache.json")));
	}

	@Test
	void sameFrameBundleAndUnknownResource() {
		IrisVibrisFrameClock frames = new IrisVibrisFrameClock();
		IrisVibrisRuntimeAdapter adapter = new IrisVibrisRuntimeAdapter(new CaptureHost(), frames);
		CapturePlan bundle = new CapturePlan(List.of(
			target(FINAL_FRAMEBUFFER, "beauty", PNG, "beauty"),
			target(TEXTURE, "colortex0", RAW, "colortex0"),
			target(TEXTURE, "depthtex0", RAW, "depthtex0"),
			target(BUFFER, "radiance_cache", BIN, "radiance_cache")));
		var capture = adapter.capture(bundle, new DirectorySink(output), CancellationToken.none())
			.toCompletableFuture();
		frames.renderedFrame();
		assertEquals(List.of(1L), capture.join().artifacts().values().stream()
			.map(ResourceCatalog.ResourceDescriptor::frameId).distinct().toList());
		assertTrue(Files.exists(output.resolve("depthtex0.raw")));

		CapturePlan missing = new CapturePlan(List.of(target(TEXTURE, "missing", RAW, "missing")));
		var failed = adapter.capture(missing, new DirectorySink(output), CancellationToken.none())
			.toCompletableFuture();
		frames.renderedFrame();
		CompletionException failure = assertThrows(CompletionException.class, failed::join);
		assertInstanceOf(CaptureResourceNotFoundException.class, failure.getCause());
		assertFalse(Files.exists(output.resolve("missing.raw")));
	}

	private static CapturePlan.Target target(
		ResourceCatalog.ResourceKind kind,
		String logicalName,
		CapturePlan.ArtifactFormat format,
		String artifactName
	) {
		return new CapturePlan.Target(kind, logicalName, format, artifactName, 0, 0);
	}

	private record DirectorySink(Path root) implements ArtifactSink {
		@Override
		public OutputStream open(String artifactName) throws IOException {
			return Files.newOutputStream(root.resolve(artifactName));
		}
	}

	private static final class CaptureHost implements IrisVibrisRuntimeHost {
		@Override
		public boolean isClientThread() {
			return true;
		}

		@Override
		public void executeOnClient(Runnable task) {
			task.run();
		}

		@Override
		public RuntimeStatus status() {
			return new RuntimeStatus(true, "save", "minecraft:overworld", "source");
		}

		@Override
		public CompletionStage<ContextApplyResult> applyContext(
			SceneContext context,
			CancellationToken cancellation
		) {
			return java.util.concurrent.CompletableFuture.completedFuture(ContextApplyResult.success(context));
		}

		@Override
		public ReloadResult reload(CancellationToken cancellation) {
			return ReloadResult.success(List.of());
		}

		@Override
		public TemporalResetResult resetTemporal(CancellationToken cancellation) {
			return new TemporalResetResult(true);
		}

		@Override
		public ResourceCatalog resourceCatalog(long frameId) {
			return ResourceCatalog.empty();
		}

		@Override
		public CaptureResult capture(
			CapturePlan plan,
			ArtifactSink sink,
			long frameId,
			CancellationToken cancellation
		) {
			if (plan.targets().stream().anyMatch(target -> target.logicalName().equals("missing"))) {
				throw new CaptureResourceNotFoundException("missing");
			}
			Map<String, ResourceCatalog.ResourceDescriptor> artifacts = new LinkedHashMap<>();
			for (CapturePlan.Target target : plan.targets()) {
				write(target, sink);
				long bytes = target.format() == PNG ? 16 : target.kind() == BUFFER ? 16 : 32;
				artifacts.put(target.artifactName(), new ResourceCatalog.ResourceDescriptor(
					target.logicalName(), target.kind(), 2, 2, 1, 1, 1, "test", 4,
					ResourceCatalog.ScalarType.FLOAT32, bytes, frameId, target.logicalName()));
			}
			return new CaptureResult(frameId, artifacts);
		}

		private static void write(CapturePlan.Target target, ArtifactSink sink) {
			int bytes = target.kind() == BUFFER ? 16 : 32;
			try (OutputStream output = sink.open(target.fileName())) {
				if (target.format() == PNG) {
					BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
					ImageIO.write(image, "png", output);
				} else {
					output.write(new byte[bytes]);
				}
				if (target.format() == RAW || target.format() == BIN) {
					try (OutputStream metadata = sink.open(target.metadataFileName())) {
						metadata.write(("{\"byte_size\":" + bytes + "}")
							.getBytes(java.nio.charset.StandardCharsets.UTF_8));
					}
				}
			} catch (IOException exception) {
				throw new java.io.UncheckedIOException(exception);
			}
		}

		@Override
		public void close() {
		}
	}
}
