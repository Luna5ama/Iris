package net.irisshaders.iris.pipeline.transform;

import io.github.douira.glsl_transformer.GLSLLexer;
import io.github.douira.glsl_transformer.ast.print.PrintType;
import io.github.douira.glsl_transformer.ast.transform.JobParameters;
import io.github.douira.glsl_transformer.ast.transform.SingleASTTransformer;
import io.github.douira.glsl_transformer.token_filter.ChannelFilter;
import io.github.douira.glsl_transformer.token_filter.TokenChannel;
import org.antlr.v4.runtime.Token;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LineAnnotationTest {
	@Test
	void annotatedPrinterRestoresLocationsAfterAstFormatting() {
		SingleASTTransformer<JobParameters> transformer = new SingleASTTransformer<>(
			SingleASTTransformer.IDENTITY_TRANSFORMATION);
		transformer.setParseLineDirectives(true);
		transformer.setPrintType(PrintType.SIMPLE_ANNOTATED);
		transformer.setTokenFilter(new ChannelFilter<>(TokenChannel.PREPROCESSOR) {
			@Override
			public boolean isTokenAllowed(Token token) {
				return super.isTokenAllowed(token) || token.getType() == GLSLLexer.NR_LINE;
			}
		});

		String output = transformer.transform("""
			#version 330
			#line 17 1
			vec3 helper() { return vec3(1.0); }
			#line 5 2
			void main() { helper(); }
			""");

		assertTrue(output.contains("#line 17 1"));
		assertTrue(output.contains("#line 5 2"));
	}
}
