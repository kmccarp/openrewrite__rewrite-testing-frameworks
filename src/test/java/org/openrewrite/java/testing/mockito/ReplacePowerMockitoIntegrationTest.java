/*
 * Copyright 2023 the original author or authors.
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

import static org.openrewrite.java.Assertions.java;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openrewrite.InMemoryExecutionContext;
import org.openrewrite.config.Environment;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

@Disabled("TODO - this test is failing")
class ReplacePowerMockitoIntegrationTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec
          .parser(JavaParser.fromJavaVersion()
            .classpathFromResources(new InMemoryExecutionContext(),
              "mockito-all-1.10.19", "junit-jupiter-api-5.9.2", "junit-4.13.2", "powermock-core-1.6.5", "powermock-api-mockito-1.6.5"))
          .recipe(Environment.builder()
            .scanRuntimeClasspath("org.openrewrite.java.testing.mockito")
            .build()
            .activateRecipes("org.openrewrite.java.testing.mockito.ReplacePowerMockito"));
    }

    @Test
    void testThatMockStaticGetsReplaced() {
        //language=java
        rewriteRun(java("""
          package org.powermock.modules.junit4;

          public class PowerMockRunner {}
          """
        ), java(
          """
            package mockito.example;

            import static org.mockito.Mockito.mock;
            import static org.mockito.Mockito.when;
            import static org.powermock.api.mockito.PowerMockito.mockStatic;
            import static org.junit.jupiter.api.Assertions.assertEquals;
            
            import java.util.Calendar;
            
            import org.junit.runner.RunWith;
            import org.powermock.core.classloader.annotations.PowerMockIgnore;
            import org.powermock.core.classloader.annotations.PrepareForTest;
            import org.powermock.modules.junit4.PowerMockRunner;
            import org.junit.jupiter.api.Test;
            
            @RunWith(PowerMockRunner.class)
            @PowerMockIgnore({"org.apache.*", "com.sun.*", "javax.*"})
            @PrepareForTest(value = {Calendar.class})
            public class StaticMethodTest {
            
                private Calendar calendarMock = mock(Calendar.class);
            
                @Test
                void testWithCalendar() {
                    mockStatic(Calendar.class);
                    when(Calendar.getInstance()).thenReturn(calendarMock);
                    assertEquals(Calendar.getInstance(), calendarMock);
                }
            
            }
          """,
          """
            package mockito.example;

            import static org.mockito.Mockito.mock;
            import static org.mockito.Mockito.mockStatic;
            import static org.junit.jupiter.api.Assertions.assertEquals;
            
            import java.util.Calendar;
            
            import org.mockito.MockedStatic;
            import org.junit.jupiter.api.AfterEach;
            import org.junit.jupiter.api.Test;
            
            public class StaticMethodTest {
            
                private MockedStatic<Calendar> mockedCalendar;
                
                private Calendar calendarMock = mock(Calendar.class);
            
                @AfterEach
                void tearDown() {
                    mockedCalendar.close();
                }
                
                @Test
                void testWithCalendar() {
                    mockedCalendar = mockStatic(Calendar.class);
                    mockedStatic.when(Calendar::getInstance).thenReturn(calendarMock);
                    assertEquals(Calendar.getInstance(), calendarMock);
                }
            
            }
          """
        ));
    }
}