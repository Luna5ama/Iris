package net.irisshaders.iris.shaderpack.include;

import com.google.common.collect.ImmutableList;

public record IncludedSource(ImmutableList<SourceLine> lines) {
	public String text() {
		int capacity = 0;
		for (SourceLine line : lines) {
			capacity += line.text().length() + 1;
		}

		StringBuilder builder = new StringBuilder(capacity);
		for (SourceLine line : lines) {
			builder.append(line.text()).append('\n');
		}
		return builder.toString();
	}
}
