package net.irisshaders.iris.shaderpack.parsing;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ConstDirectiveParser {
	public static List<ConstDirective> findDirectives(String source) {
		List<ConstDirective> directives = new ArrayList<>();
		int lineStart = 0;
		for (int index = 0; index <= source.length(); index++) {
			if (index < source.length() && !isLineBreak(source.charAt(index))) {
				continue;
			}
			findDirectiveInRange(source, lineStart, index).ifPresent(directives::add);
			if (index < source.length() && source.charAt(index) == '\r'
				&& index + 1 < source.length() && source.charAt(index + 1) == '\n') {
				index++;
			}
			lineStart = index + 1;
		}

		return directives;
	}

	public static Optional<ConstDirective> findDirectiveInLine(String line) {
		return findDirectiveInRange(line, 0, line.length());
	}

	private static Optional<ConstDirective> findDirectiveInRange(String source, int start, int end) {
		// Valid const directives contain the following elements:
		// * Zero or more whitespace characters
		// * A "const" literal
		// * At least one whitespace character
		// * A type literal (int, float, vec4, or bool)
		// * At least one whitespace character
		// * The name / key of the const directive (alphanumeric & underscore characters)
		// * Zero or more whitespace characters
		// * An equals sign
		// * Zero or more whitespace characters
		// * The value of the const directive (alphanumeric & underscore characters)
		// * A semicolon
		// * (any content)

		// Bail-out early without doing any processing if required components are not found
		// A const directive must contain at the very least a const keyword, then an equals
		// sign, then a semicolon.
		// Trim any surrounding whitespace (such as indentation) from the line before processing it.
		start = trimStart(source, start, end);
		end = trimEnd(source, start, end);

		// A valid declaration must have a trimmed line starting with const
		if (!source.regionMatches(start, "const", 0, "const".length())) {
			return Optional.empty();
		}

		// Remove the const part from the string
		start += "const".length();

		// There must be at least one whitespace character between the "const" keyword and the type keyword
		if (!startsWithWhitespace(source, start, end)) {
			return Optional.empty();
		}

		// Trim all whitespace between the const keyword and the type keyword
		start = trimStart(source, start, end);
		end = trimEnd(source, start, end);

		// Valid const declarations have a type that is either an int, a float, a vec4, or a bool.
		Type type;

		if (source.regionMatches(start, "int", 0, "int".length())) {
			type = Type.INT;
			start += "int".length();
		} else if (source.regionMatches(start, "float", 0, "float".length())) {
			type = Type.FLOAT;
			start += "float".length();
		} else if (source.regionMatches(start, "vec2", 0, "vec2".length())) {
			type = Type.VEC2;
			start += "vec2".length();
		} else if (source.regionMatches(start, "ivec3", 0, "ivec3".length())) {
			type = Type.IVEC3;
			start += "ivec3".length();
		} else if (source.regionMatches(start, "vec4", 0, "vec4".length())) {
			type = Type.VEC4;
			start += "vec4".length();
		} else if (source.regionMatches(start, "bool", 0, "bool".length())) {
			type = Type.BOOL;
			start += "bool".length();
		} else {
			return Optional.empty();
		}

		// There must be at least one whitespace character between the type keyword and the key of the const declaration
		if (!startsWithWhitespace(source, start, end)) {
			return Optional.empty();
		}

		// Split the declaration at the equals sign
		int equalsIndex = source.indexOf('=', start);

		if (equalsIndex == -1 || equalsIndex >= end) {
			// No equals sign found, not a valid const declaration
			return Optional.empty();
		}

		// The key comes before the equals sign
		int keyStart = trimStart(source, start, equalsIndex);
		int keyEnd = trimEnd(source, keyStart, equalsIndex);

		// The key must be a "word" (alphanumeric & underscore characters)
		if (!isWord(source, keyStart, keyEnd)) {
			return Optional.empty();
		}

		// Everything after the equals sign but before the semicolon is the value
		int semicolonIndex = source.indexOf(';', equalsIndex + 1);

		if (semicolonIndex == -1 || semicolonIndex >= end) {
			// No semicolon found, not a valid const declaration
			return Optional.empty();
		}

		int valueStart = trimStart(source, equalsIndex + 1, semicolonIndex);
		int valueEnd = trimEnd(source, valueStart, semicolonIndex);

		// We make no attempt to properly parse / verify the value here, that responsibility lies with whatever code
		// is working with the directives.
		return Optional.of(new ConstDirective(type,
			source.substring(keyStart, keyEnd), source.substring(valueStart, valueEnd)));
	}

	private static boolean startsWithWhitespace(String source, int start, int end) {
		return start < end && Character.isWhitespace(source.charAt(start));
	}

	private static int trimStart(String source, int start, int end) {
		while (start < end && source.charAt(start) <= ' ') {
			start++;
		}
		return start;
	}

	private static int trimEnd(String source, int start, int end) {
		while (end > start && source.charAt(end - 1) <= ' ') {
			end--;
		}
		return end;
	}

	private static boolean isWord(String source, int start, int end) {
		if (start == end) {
			return false;
		}

		for (int index = start; index < end; index++) {
			char character = source.charAt(index);
			if (!Character.isDigit(character) && !Character.isAlphabetic(character) && character != '_') {
				return false;
			}
		}

		return true;
	}

	private static boolean isLineBreak(char character) {
		return character == '\n' || character == '\u000B' || character == '\f' || character == '\r'
			|| character == '\u0085' || character == '\u2028' || character == '\u2029';
	}

	public enum Type {
		INT,
		FLOAT,
		VEC2,
		IVEC3,
		VEC4,
		BOOL
	}

	public static class ConstDirective {
		private final Type type;
		private final String key;
		private final String value;

		ConstDirective(Type type, String key, String value) {
			this.type = type;
			this.key = key;
			this.value = value;
		}

		public Type getType() {
			return type;
		}

		public String getKey() {
			return key;
		}

		public String getValue() {
			return value;
		}

		public String toString() {
			return "ConstDirective { " + type + " " + key + " = " + value + "; }";
		}
	}
}
