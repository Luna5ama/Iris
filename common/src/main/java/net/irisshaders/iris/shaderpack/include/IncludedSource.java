package net.irisshaders.iris.shaderpack.include;

import com.google.common.collect.ImmutableList;

public record IncludedSource(ImmutableList<SourceLine> lines) {
	public String text() {
		StringBuilder builder = new StringBuilder();
		for (SourceLine line : lines) {
			builder.append(line.text()).append('\n');
		}
		return builder.toString();
	}
}
