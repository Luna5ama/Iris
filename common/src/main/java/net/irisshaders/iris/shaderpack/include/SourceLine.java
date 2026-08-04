package net.irisshaders.iris.shaderpack.include;

public record SourceLine(String text, AbsolutePackPath path, int line) {
	public SourceLine {
		if (line < 1) {
			throw new IllegalArgumentException("Source line numbers must be positive");
		}
	}
}
