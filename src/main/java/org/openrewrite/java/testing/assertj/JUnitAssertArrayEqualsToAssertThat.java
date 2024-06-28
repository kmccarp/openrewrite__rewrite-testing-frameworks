/*
 * Copyright 2021 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.testing.assertj;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Preconditions;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.Expression;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.TypeUtils;

import java.util.List;

public class JUnitAssertArrayEqualsToAssertThat extends Recipe {
    private static final String JUNIT_QUALIFIED_ASSERTIONS_CLASS_NAME = "org.junit.jupiter.api.Assertions";

    @Override
    public String getDisplayName() {
        return "JUnit `assertArrayEquals` To AssertJ";
    }

    @Override
    public String getDescription() {
        return "Convert JUnit-style `assertArrayEquals()` to AssertJ's `assertThat().contains()` equivalents.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new UsesType<>(JUNIT_QUALIFIED_ASSERTIONS_CLASS_NAME, false), new AssertArrayEqualsToAssertThatVisitor());
    }

    public static class AssertArrayEqualsToAssertThatVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final MethodMatcher JUNIT_ASSERT_EQUALS = new MethodMatcher(JUNIT_QUALIFIED_ASSERTIONS_CLASS_NAME + " assertArrayEquals(..)");

        private JavaParser.Builder<?, ?> assertionsParser;

        private JavaParser.Builder<?, ?> assertionsParser(ExecutionContext ctx) {
            if (assertionsParser == null) {
                assertionsParser = JavaParser.fromJavaVersion()
                        .classpathFromResources(ctx, "assertj-core-3.24");
            }
            return assertionsParser;
        }


        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
            if (!JUNIT_ASSERT_EQUALS.matches(method)) {
                return method;
            }

            List<Expression> args = method.getArguments();
            Expression expected = args.getFirst();
            Expression actual = args.get(1);

            // Make sure there is a static import for "org.assertj.core.api.Assertions.assertThat" (even if not referenced)
            maybeAddImport("org.assertj.core.api.Assertions", "assertThat", false);
            maybeRemoveImport(JUNIT_QUALIFIED_ASSERTIONS_CLASS_NAME);

            if (args.size() == 2) {
                return JavaTemplate.builder("assertThat(#{anyArray()}).containsExactly(#{anyArray()});")
                        .staticImports("org.assertj.core.api.Assertions.assertThat")
                        .javaParser(assertionsParser(ctx))
                        .build()
                        .apply(getCursor(), method.getCoordinates().replace(), actual, expected);
            } else if (args.size() == 3 && !isFloatingPointType(args.get(2))) {
                Expression message = args.get(2);
                JavaTemplate.Builder template = TypeUtils.isString(message.getType()) ?
                        JavaTemplate.builder("assertThat(#{anyArray()}).as(#{any(String)}).containsExactly(#{anyArray()});") :
                        JavaTemplate.builder("assertThat(#{anyArray()}).as(#{any(java.util.function.Supplier)}).containsExactly(#{anyArray()});");
                return template
                        .staticImports("org.assertj.core.api.Assertions.assertThat")
                        .javaParser(assertionsParser(ctx))
                        .build()
                        .apply(getCursor(), method.getCoordinates().replace(), actual, message, expected);
            } else if (args.size() == 3) {
                maybeAddImport("org.assertj.core.api.Assertions", "within", false);
                // assert is using floating points with a delta and no message.
                return JavaTemplate.builder("assertThat(#{anyArray()}).containsExactly(#{anyArray()}, within(#{any()}));")
                        .staticImports("org.assertj.core.api.Assertions.assertThat", "org.assertj.core.api.Assertions.within")
                        .javaParser(assertionsParser(ctx))
                        .build()
                        .apply(getCursor(), method.getCoordinates().replace(), actual, expected, args.get(2));
            }

            // The assertEquals is using a floating point with a delta argument and a message.
            Expression message = args.get(3);
            maybeAddImport("org.assertj.core.api.Assertions", "within", false);

            JavaTemplate.Builder template = TypeUtils.isString(message.getType()) ?
                    JavaTemplate.builder("assertThat(#{anyArray()}).as(#{any(String)}).containsExactly(#{anyArray()}, within(#{any()}));") :
                    JavaTemplate.builder("assertThat(#{anyArray()}).as(#{any(java.util.function.Supplier)}).containsExactly(#{anyArray()}, within(#{}));");
            return template
                    .staticImports("org.assertj.core.api.Assertions.assertThat", "org.assertj.core.api.Assertions.within")
                    .javaParser(assertionsParser(ctx))
                    .build()
                    .apply(getCursor(), method.getCoordinates().replace(), actual, message, expected, args.get(2));
        }

        /**
         * Returns true if the expression's type is either a primitive float/double or their object forms Float/Double
         *
         * @param expression The expression parsed from the original AST.
         * @return true if the type is a floating point number.
         */
        private static boolean isFloatingPointType(Expression expression) {

            JavaType.FullyQualified fullyQualified = TypeUtils.asFullyQualified(expression.getType());
            if (fullyQualified != null) {
                String typeName = fullyQualified.getFullyQualifiedName();
                return "java.lang.Double".equals(typeName) || "java.lang.Float".equals(typeName);
            }

            JavaType.Primitive parameterType = TypeUtils.asPrimitive(expression.getType());
            return parameterType == JavaType.Primitive.Double || parameterType == JavaType.Primitive.Float;
        }
    }
}
