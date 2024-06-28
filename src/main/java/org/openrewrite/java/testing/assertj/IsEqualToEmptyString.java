/*
 * Copyright 2024 the original author or authors.
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
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.search.UsesMethod;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;

import java.util.Collections;

/**
 * AssertJ has a more idiomatic way of asserting that a String is empty.
 * This recipe will find instances of `assertThat(String).isEqualTo("")` and replace them with `isEmpty()`.
 */
public class IsEqualToEmptyString extends Recipe {

    private static final MethodMatcher IS_EQUAL_TO = new MethodMatcher("org.assertj.core.api.AbstractStringAssert isEqualTo(java.lang.String)");

    @Override
    public String getDisplayName() {
        return "Convert `assertThat(String).isEqualTo(\"\")` to `isEmpty()`";
    }

    @Override
    public String getDescription() {
        return "Adopt idiomatic AssertJ assertion for empty Strings.";
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(
                new UsesMethod<>(IS_EQUAL_TO),
                new JavaIsoVisitor<ExecutionContext>() {
                    @Override
                    public J.MethodInvocation visitMethodInvocation(J.MethodInvocation method, ExecutionContext ctx) {
                        J.MethodInvocation mi = super.visitMethodInvocation(method, ctx);
                        if (IS_EQUAL_TO.matches(mi) && J.Literal.isLiteralValue(mi.getArguments().getFirst(), "")) {
                            JavaType.Method isEmptyMethodType = mi.getMethodType().withName("isEmpty");
                            return mi
                                    .withName(mi.getName().withSimpleName("isEmpty").withType(isEmptyMethodType))
                                    .withMethodType(isEmptyMethodType)
                                    .withArguments(Collections.emptyList());
                        }
                        return mi;
                    }
                }
        );
    }
}
