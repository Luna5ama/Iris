package net.irisshaders.iris.shaderpack.include;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.irisshaders.iris.shaderpack.transform.line.LineTransform;

import java.util.Objects;
import java.util.regex.Pattern;

public class FileNode {
	private static final Pattern MARCO_NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
	private static final Pattern DEFINE_MACRO = Pattern.compile("#define\\s+([A-Za-z_][A-Za-z0-9_]*).*");
	private static final Pattern IFNDEF_MARCO = Pattern.compile("(?:#ifndef|#if !defined)[\\s(]+([A-Za-z_][A-Za-z0-9_]*).*");

	private final AbsolutePackPath path;
	private final ImmutableList<String> lines;
	private final ImmutableList<SourceLine> sourceLines;
	private final ImmutableMap<Integer, IncludeEntry> includes;
	private final boolean includeGuard;

	// NB: The caller is responsible for ensuring that the includes map
	//     is in sync with the lines list.
	private FileNode(
		AbsolutePackPath path, ImmutableList<String> lines,
		ImmutableMap<Integer, IncludeEntry> includes,
		boolean includeGuard
	) {
		this.path = path;
		this.lines = lines;
		this.sourceLines = createSourceLines(path, lines);
		this.includes = includes;
		this.includeGuard = includeGuard;
	}

	public FileNode(AbsolutePackPath path, ImmutableList<String> lines) {
		this.path = path;
		this.lines = lines;
		this.sourceLines = createSourceLines(path, lines);

		boolean foundIncludeGuard = false;
		AbsolutePackPath currentDirectory = path.parent().orElseThrow(
			() -> new IllegalArgumentException("Not a valid shader file name: " + path));

		ImmutableMap.Builder<Integer, IncludeEntry> foundIncludes = ImmutableMap.builder();

		{
			boolean foundCode = false;
			boolean blockComment = false;
			int macroConditionDepth = 0;
			int lastEndIfIndex = Integer.MAX_VALUE;
			int firstNotNestedIndex = -1;

			outer:
			for (int i = 0; i < lines.size(); i++) {
				String line = lines.get(i).trim();

				while (true) {
					if (line.isEmpty()) continue outer;
					if (line.startsWith("//")) continue outer;

					if (line.contains("/*")) {
						blockComment = true;
					}
					int commentEnd = line.indexOf("*/");
					if (commentEnd >= 0) {
						blockComment = false;
						line = line.substring(commentEnd + 2).trim();
						continue;
					}
					if (blockComment) continue outer;

					break;
				}

				if (!foundCode) {
					if (!line.startsWith("#")) {
						foundCode = true;
						continue;
					}
					var defineMatch = DEFINE_MACRO.matcher(line);
					if (defineMatch.matches()) {
						String previousLine = "";
						for (int j = i - 1; j >= 0; j--) {
							previousLine = lines.get(j).trim();
							if (!previousLine.isEmpty()) break;
						}
						if (!previousLine.isEmpty()) {
							var ifdefMatch = IFNDEF_MARCO.matcher(previousLine);
							if (ifdefMatch.matches()) {
								foundIncludeGuard |= ifdefMatch.group(1).equals(defineMatch.group(1));
							}
						}
					}
				}

				if (line.startsWith("#if")) {
					macroConditionDepth++;
				} else if (line.startsWith("#endif")) {
					macroConditionDepth--;
					lastEndIfIndex = i;
				}

				if (macroConditionDepth <= 0 && firstNotNestedIndex == -1) {
					firstNotNestedIndex = i;
				}
			}

			foundIncludeGuard &= firstNotNestedIndex >= lastEndIfIndex;
		}

		{
			int macroConditionDepth = 0;
			for (int i = 0; i < lines.size(); i++) {
				String line = lines.get(i).trim();

				if (line.startsWith("#if")) {
					macroConditionDepth++;
				} else if (line.startsWith("#endif")) {
					macroConditionDepth--;
				}

				if (!line.startsWith("#include")) {
					continue;
				}

				// Remove the "#include " part so that we just have the file path
				String target = line.substring("#include ".length()).trim();

				// Remove quotes if they're present
				// All include directives should have quotes, but I'm not sure whether they're required to.
				// TODO: Check if quotes are required, and don't permit mismatched quotes
				// TODO: This shouldn't be accepted:
				//       #include "test.glsl
				//       #include test.glsl"
				if (target.startsWith("\"")) {
					target = target.substring(1);
				}

				if (target.endsWith("\"")) {
					target = target.substring(0, target.length() - 1);
				}

				var conditional = macroConditionDepth > (foundIncludeGuard ? 1 : 0);
				var includePath = currentDirectory.resolve(target);
				foundIncludes.put(i, new IncludeEntry(includePath, conditional));
			}
		}

		this.includes = foundIncludes.build();
		this.includeGuard = foundIncludeGuard;
	}

	public AbsolutePackPath getPath() {
		return path;
	}

	public ImmutableList<String> getLines() {
		return lines;
	}

	public ImmutableList<SourceLine> getSourceLines(int fromIndex, int toIndex) {
		return sourceLines.subList(fromIndex, toIndex);
	}

	public ImmutableMap<Integer, IncludeEntry> getIncludes() {
		return includes;
	}

	public boolean hasIncludeGuard() {
		return includeGuard;
	}

	public FileNode map(LineTransform transform) {
		ImmutableList.Builder<String> newLines = ImmutableList.builder();
		int index = 0;

		for (String line : lines) {
			String transformedLine = transform.transform(index, line);

			if (includes.containsKey(index)) {
				if (!Objects.equals(line, transformedLine)) {
					throw new IllegalStateException("Attempted to modify an #include line in LineTransform.");
				}
			}

			newLines.add(transformedLine);
			index += 1;
		}

		return new FileNode(path, newLines.build(), includes, includeGuard);
	}

	private static ImmutableList<SourceLine> createSourceLines(AbsolutePackPath path, ImmutableList<String> lines) {
		ImmutableList.Builder<SourceLine> sourceLines = ImmutableList.builderWithExpectedSize(lines.size());
		for (int i = 0; i < lines.size(); i++) {
			sourceLines.add(new SourceLine(lines.get(i), path, i + 1));
		}
		return sourceLines.build();
	}

	public record IncludeEntry(AbsolutePackPath path, boolean conditional) {}
}
