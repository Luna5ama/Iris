package net.irisshaders.iris.gl;

import dev.luna5ama.vibris.capture.CaptureKt;
import dev.luna5ama.vibris.capture.ShaderInfo;
import dev.luna5ama.vibris.capture.ShaderSourceContext;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.gl.shader.ShaderType;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3i;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.Locale;
import java.util.regex.Pattern;

public final class IrisCaptureManager {
	private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private static CaptureRequest pendingCapture;
	private static CaptureSession activeCapture;
	private static Thread savingThread;
	private static Path lastOutputPath;
	private static String lastError;

	private IrisCaptureManager() {
	}

	public static synchronized Path defaultOutputPath(String name) {
		return Path.of("vibris", name + "-" + TIMESTAMP_FORMAT.format(LocalDateTime.now()));
	}

	public static synchronized void prepareSingleCapture(@NotNull Path path, @NotNull String passName) {
		pendingCapture = new CaptureRequest(path, CaptureMode.SINGLE, passName, null);
		lastError = null;
	}

	public static synchronized void prepareMultiCapture(@NotNull Path path, @NotNull String programType) {
		String normalized = normalizeProgramType(programType);
		pendingCapture = new CaptureRequest(path, CaptureMode.MULTI, null, normalized);
		lastError = null;
	}

	public static synchronized void startFrame() {
		if (pendingCapture == null || activeCapture != null) {
			return;
		}

		activeCapture = new CaptureSession(pendingCapture);
		pendingCapture = null;
		try {
			CaptureKt.beginGlCapture(activeCapture.request.path);
			Iris.logger.info("Started vibris capture: {}", activeCapture.request.path);
		} catch (RuntimeException e) {
			lastError = e.getMessage();
			activeCapture = null;
			throw e;
		}
	}

	public static synchronized void endFrame() {
		if (activeCapture != null && activeCapture.request.mode == CaptureMode.MULTI) {
			finishActiveCapture("Finished multi-pass vibris capture");
		} else if (activeCapture != null && activeCapture.request.mode == CaptureMode.SINGLE) {
			finishActiveCapture("Finished unmatched single-pass vibris capture");
		}
	}

	public static synchronized boolean hasActiveCapture() {
		return activeCapture != null;
	}

	public static void pushDebugLabel(String name) {
		if (hasActiveCapture()) {
			CaptureKt.captureDebugLabelPush(name);
		}
	}

	public static void popDebugLabel() {
		if (hasActiveCapture()) {
			CaptureKt.captureDebugLabelPop();
		}
	}

	public static synchronized CaptureStatus getStatus() {
		boolean saving = savingThread != null && savingThread.isAlive();
		return new CaptureStatus(
			pendingCapture != null,
			activeCapture != null,
			saving,
			lastOutputPath,
			lastError
		);
	}

	public static boolean dispatchCompute(EnumMap<ShaderType, String> sources, String passName, Vector3i workGroups) {
		CaptureSession session = activeCapture;
		if (session == null || !session.matches(passName)) {
			return false;
		}

		ShaderInfo shaderInfo = createShaderInfo(sources, passName, session.request.programType);
		CaptureKt.captureGlDispatchCompute(shaderInfo, workGroups.x, workGroups.y, workGroups.z);
		session.capturedDispatches++;
		if (session.request.mode == CaptureMode.SINGLE) {
			finishActiveCapture("Finished single-pass vibris capture");
		}
		return true;
	}

	public static boolean dispatchComputeIndirect(EnumMap<ShaderType, String> sources, String passName, long offset) {
		CaptureSession session = activeCapture;
		if (session == null || !session.matches(passName)) {
			return false;
		}

		ShaderInfo shaderInfo = createShaderInfo(sources, passName, session.request.programType);
		CaptureKt.captureGlDispatchComputeIndirect(shaderInfo, offset);
		session.capturedDispatches++;
		if (session.request.mode == CaptureMode.SINGLE) {
			finishActiveCapture("Finished single-pass vibris capture");
		}
		return true;
	}

	private static ShaderInfo createShaderInfo(EnumMap<ShaderType, String> sources, String passName, String programType) {
		String computeSource = sources.get(ShaderType.COMPUTE);
		ShaderSourceContext sourceContext = new ShaderSourceContext(computeSource);
		sourceContext.setIdentity(passName, programType, passName + ".csh");
		sourceContext.patchShaderForVulkan();
		return sourceContext.toShaderInfo();
	}

	private static synchronized void finishActiveCapture(String message) {
		CaptureSession session = activeCapture;
		if (session == null) {
			return;
		}

		activeCapture = null;
		try {
			savingThread = CaptureKt.endGlCapture();
			lastOutputPath = session.request.path;
			Iris.logger.info("{}: {} ({} dispatches)", message, session.request.path, session.capturedDispatches);
		} catch (RuntimeException e) {
			lastError = e.getMessage();
			throw e;
		}
	}

	private static String normalizeProgramType(String programType) {
		String normalized = programType.toLowerCase(Locale.ROOT);
		return switch (normalized) {
			case "prepare", "begin", "deferred", "composite" -> normalized;
			default -> throw new IllegalArgumentException("Unsupported capturemulti type: " + programType);
		};
	}

	private enum CaptureMode {
		SINGLE,
		MULTI
	}

	private record CaptureRequest(Path path, CaptureMode mode, String passName, String programType) {
	}

	private static final class CaptureSession {
		private final CaptureRequest request;
		private int capturedDispatches;

		private CaptureSession(CaptureRequest request) {
			this.request = request;
		}

		private boolean matches(String passName) {
			return switch (request.mode) {
				case SINGLE -> request.passName.equals(passName);
				case MULTI -> Pattern.matches(Pattern.quote(request.programType) + "([1-9][0-9]?)?(_[a-z])?", passName);
			};
		}
	}

	public record CaptureStatus(
		boolean pending,
		boolean active,
		boolean saving,
		Path lastOutputPath,
		String lastError
	) {
	}
}
