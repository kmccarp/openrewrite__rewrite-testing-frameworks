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
package org.openrewrite.java.testing.mockito;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.search.FindMissingTypes;
import org.openrewrite.java.search.UsesType;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaSourceFile;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Removes unused "org.mockito" imports.
 */
public class CleanupMockitoImports extends Recipe {
    @Override
    public String getDisplayName() {
        return "Cleanup Mockito imports";
    }

    @Override
    public String getDescription() {
        return "Removes unused `org.mockito` import symbols, unless its possible they are associated with method invocations having null or unknown type information.";
    }

  @Override
  public Duration getEstimatedEffortPerOccurrence() {
    return Duration.ofMinutes(5);
  }

    @Override
    protected TreeVisitor<?, ExecutionContext> getVisitor() {
        return new CleanupMockitoImportsVisitor();
    }

    @Nullable
    @Override
    protected TreeVisitor<?, ExecutionContext> getSingleSourceApplicableTest() {
        return new UsesType<>("org.mockito.*");
    }

    public static class CleanupMockitoImportsVisitor extends JavaIsoVisitor<ExecutionContext> {
        private static final List<String> MOCKITO_METHOD_NAMES = Arrays.asList("mock", "mockingDetails", "spy",
                "stub", "when", "verify", "reset", "verifyNoMoreInteractions", "verifyZeroInteractions", "stubVoid",
                "doThrow", "doCallRealMethod", "doAnswer", "doNothing", "doReturn", "inOrder", "ignoreStubs",
                "times", "never", "atLeastOnce", "atLeast", "atMost", "calls", "only", "timeout", "after");
        @Override
        public JavaSourceFile visitJavaSourceFile(JavaSourceFile cu, ExecutionContext executionContext) {
            JavaSourceFile sf = super.visitJavaSourceFile(cu, executionContext);

            final List<String> unknownTypeMethodInvocationNames = FindMissingTypes.findMissingTypes(cu)
                    .stream().map(FindMissingTypes.MissingTypeResult::getJ)
                    .filter(J.MethodInvocation.class::isInstance)
                    .map(J.MethodInvocation.class::cast)
                    .map(J.MethodInvocation::getSimpleName).collect(Collectors.toList());

            for (J.Import _import : cu.getImports()) {
                if (_import.getPackageName().startsWith("org.mockito")) {
                    if (_import.isStatic()) {
                        String staticName = _import.getQualid().getSimpleName();
                        if ("*".equals(staticName) && !possibleMockitoMethod(unknownTypeMethodInvocationNames)) {
                            maybeRemoveImport(_import.getPackageName() + "." + _import.getClassName());
                        } else if (!unknownTypeMethodInvocationNames.contains(staticName)) {
                            maybeRemoveImport(_import.getPackageName() + "." + _import.getClassName() + "." + staticName);
                        }
                    } else {
                        maybeRemoveImport(_import.getPackageName() + "." + _import.getClassName());
                    }
                }
            }
            return sf;
        }

        private boolean possibleMockitoMethod(List<String> methodNamesHavingNullType) {
            for (String missingMethod : methodNamesHavingNullType) {
                if (MOCKITO_METHOD_NAMES.contains(missingMethod)) {
                    return true;
                }
            }

            return false;
        }
    }
}
