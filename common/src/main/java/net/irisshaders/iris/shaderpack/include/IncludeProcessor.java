package net.irisshaders.iris.shaderpack.include;

import com.google.common.collect.ImmutableList;
import java.util.HashSet;
import java.util.Objects;

// TODO: Write tests for this code
public class IncludeProcessor {
	private final IncludeGraph graph;

	public IncludeProcessor(IncludeGraph graph) {
		this.graph = graph;
	}

	// TODO: Actual error handling

	public ImmutableList<String> getIncludedFile(AbsolutePackPath path) {
		return getIncludedSource(path).lines().stream()
			.map(SourceLine::text)
			.collect(ImmutableList.toImmutableList());
	}

	public IncludedSource getIncludedSource(AbsolutePackPath path) {
		return new IncludedSource(process(path, new HashSet<>()));
	}

	private ImmutableList<SourceLine> process(AbsolutePackPath path, HashSet<AbsolutePackPath> includedSet) {
		FileNode fileNode = graph.getNodes().get(path);

		if (fileNode == null) {
			return ImmutableList.of();
		}

		ImmutableList.Builder<SourceLine> linesBuilder = ImmutableList.builder();

		ImmutableList<String> lines = fileNode.getLines();
		var includes = fileNode.getIncludes();

		for (int i = 0; i < lines.size(); i++) {
			var includeEntry = includes.get(i);

			if (includeEntry != null) {
				var includePath = includeEntry.path();
				var subIncludedSet = includeEntry.conditional() ? new HashSet<>(includedSet) : includedSet;
				if (!subIncludedSet.add(includePath)) {
					FileNode includeFileNode = graph.getNodes().get(includePath);
					if (includeFileNode != null && includeFileNode.hasIncludeGuard()) {
						continue;
					}
				}
				linesBuilder.addAll(Objects.requireNonNull(process(includePath, subIncludedSet)));
			} else {
				linesBuilder.add(new SourceLine(lines.get(i), path, i + 1));
			}
		}

		return linesBuilder.build();
	}
}
