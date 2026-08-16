package net.irisshaders.iris.shaderpack.include;

import com.google.common.collect.ImmutableList;
import java.util.HashSet;

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
		ImmutableList.Builder<SourceLine> linesBuilder = ImmutableList.builder();
		process(path, new HashSet<>(), linesBuilder);
		return new IncludedSource(linesBuilder.build());
	}

	private void process(AbsolutePackPath path, HashSet<AbsolutePackPath> includedSet,
						 ImmutableList.Builder<SourceLine> linesBuilder) {
		FileNode fileNode = graph.getNodes().get(path);

		if (fileNode == null) {
			return;
		}

		ImmutableList<String> lines = fileNode.getLines();
		var includes = fileNode.getIncludes();
		int rangeStart = 0;

		for (int i = 0; i < lines.size(); i++) {
			var includeEntry = includes.get(i);

			if (includeEntry != null) {
				linesBuilder.addAll(fileNode.getSourceLines(rangeStart, i));
				var includePath = includeEntry.path();
				var subIncludedSet = includeEntry.conditional() ? new HashSet<>(includedSet) : includedSet;
				if (!subIncludedSet.add(includePath)) {
					FileNode includeFileNode = graph.getNodes().get(includePath);
					if (includeFileNode != null && includeFileNode.hasIncludeGuard()) {
						continue;
					}
				}
				process(includePath, subIncludedSet, linesBuilder);
				rangeStart = i + 1;
			}
		}

		linesBuilder.addAll(fileNode.getSourceLines(rangeStart, lines.size()));
	}
}
