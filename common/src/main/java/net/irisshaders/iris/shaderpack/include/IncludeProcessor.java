package net.irisshaders.iris.shaderpack.include;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// TODO: Write tests for this code
public class IncludeProcessor {
	private final IncludeGraph graph;

	public IncludeProcessor(IncludeGraph graph) {
		this.graph = graph;
	}

	// TODO: Actual error handling

	public ImmutableList<String> getIncludedFile(AbsolutePackPath path) {
		return process(path, new HashSet<>());
	}

	private ImmutableList<String> process(AbsolutePackPath path, HashSet<AbsolutePackPath> includedSet) {
		FileNode fileNode = graph.getNodes().get(path);

		if (fileNode == null) {
			return ImmutableList.of();
		}

		ImmutableList.Builder<String> linesBuilder = ImmutableList.builder();

		ImmutableList<String> lines = fileNode.getLines();
		var includes = fileNode.getIncludes();

		for (int i = 0; i < lines.size(); i++) {
			var includeEntry = includes.get(i);

			if (includeEntry != null) {
				var includePath = includeEntry.path();
				if (!includedSet.add(includePath)) {
					FileNode includeFileNode = graph.getNodes().get(includePath);
					if (includeFileNode != null && includeFileNode.hasIncludeGuard()) {
						continue;
					}
				}
				var subIncludedSet = includeEntry.conditional() ? new HashSet<>(includedSet) : includedSet;
				linesBuilder.addAll(Objects.requireNonNull(process(includePath, subIncludedSet)));
			} else {
				linesBuilder.add(lines.get(i));
			}
		}

		return linesBuilder.build();
	}
}
