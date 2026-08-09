package net.irisshaders.iris.gl.program;

import dev.luna5ama.vibris.capture.GpuTimingProgram;
import net.irisshaders.iris.shaderpack.include.ShaderSourceMap;
import org.joml.Vector3i;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ComputeProgramTiming {
	private static final Pattern LINE_DIRECTIVE = Pattern.compile(
		"(?m)^\\h*#\\h*line\\h+\\d+(?:\\h+(\\d+))?\\h*$");
	private static final Pattern MAIN_FUNCTION = Pattern.compile("\\bvoid\\s+main\\s*\\(");

	private final String program;
	private final String sourceFile;
	private int directX = Integer.MIN_VALUE;
	private int directY = Integer.MIN_VALUE;
	private int directZ = Integer.MIN_VALUE;
	private GpuTimingProgram directTiming;
	private long indirectOffset = Long.MIN_VALUE;
	private GpuTimingProgram indirectTiming;

	ComputeProgramTiming(String program, String transformedSource) {
		this.program = program;
		this.sourceFile = resolveSourceFile(program, transformedSource);
	}

	GpuTimingProgram direct(Vector3i workGroups) {
		if (directTiming == null || directX != workGroups.x || directY != workGroups.y || directZ != workGroups.z) {
			directX = workGroups.x;
			directY = workGroups.y;
			directZ = workGroups.z;
			directTiming = timing("direct:" + directX + "x" + directY + "x" + directZ);
		}
		return directTiming;
	}

	GpuTimingProgram indirect(long offset) {
		if (indirectTiming == null || indirectOffset != offset) {
			indirectOffset = offset;
			indirectTiming = timing("indirect:offset=" + offset);
		}
		return indirectTiming;
	}

	private GpuTimingProgram timing(String dispatch) {
		return GpuTimingProgram.compute(program, sourceFile, Map.of(), dispatch);
	}

	private static String resolveSourceFile(String program, String transformedSource) {
		String fallback = program + ".csh";
		if (transformedSource == null) {
			return fallback;
		}

		// JCPP and TransformPatcher preserve physical include origins through #line directives plus this source map.
		// Attribute timing to the file containing the active main function, not merely the small wrapper .csh file.
		ShaderSourceMap sourceMap = ShaderSourceMap.parse(transformedSource);
		String source = withoutComments(sourceMap.sourceWithoutMetadata());
		Matcher main = MAIN_FUNCTION.matcher(source);
		if (!main.find()) {
			return fallback;
		}

		Integer sourceId = null;
		Matcher line = LINE_DIRECTIVE.matcher(source);
		while (line.find() && line.start() < main.start()) {
			if (line.group(1) != null) {
				sourceId = Integer.parseInt(line.group(1));
			}
		}
		if (sourceId == null) {
			return fallback;
		}

		String sourcePath = sourceMap.sourcePaths().get(sourceId);
		if (sourcePath == null || sourcePath.isBlank()) {
			return fallback;
		}
		int separator = Math.max(sourcePath.lastIndexOf('/'), sourcePath.lastIndexOf('\\'));
		return separator < 0 ? sourcePath : sourcePath.substring(separator + 1);
	}

	private static String withoutComments(String source) {
		StringBuilder result = new StringBuilder(source.length());
		boolean lineComment = false;
		boolean blockComment = false;
		for (int i = 0; i < source.length(); i++) {
			char current = source.charAt(i);
			char next = i + 1 < source.length() ? source.charAt(i + 1) : '\0';
			if (lineComment) {
				if (current == '\n' || current == '\r') {
					lineComment = false;
					result.append(current);
				} else {
					result.append(' ');
				}
			} else if (blockComment) {
				if (current == '*' && next == '/') {
					result.append("  ");
					i++;
					blockComment = false;
				} else {
					result.append(current == '\n' || current == '\r' ? current : ' ');
				}
			} else if (current == '/' && next == '/') {
				result.append("  ");
				i++;
				lineComment = true;
			} else if (current == '/' && next == '*') {
				result.append("  ");
				i++;
				blockComment = true;
			} else {
				result.append(current);
			}
		}
		return result.toString();
	}
}
