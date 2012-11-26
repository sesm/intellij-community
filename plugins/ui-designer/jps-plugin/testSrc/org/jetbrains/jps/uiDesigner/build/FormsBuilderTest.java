/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.uiDesigner.build;

import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.asm4.ClassReader;
import org.jetbrains.asm4.ClassVisitor;
import org.jetbrains.asm4.MethodVisitor;
import org.jetbrains.asm4.Opcodes;
import org.jetbrains.jps.builders.JpsBuildTestCase;
import org.jetbrains.jps.incremental.java.JavaBuilder;
import org.jetbrains.jps.model.java.JpsJavaExtensionService;
import org.jetbrains.jps.model.module.JpsModule;
import org.jetbrains.jps.uiDesigner.compiler.FormsInstrumenter;
import org.jetbrains.jps.uiDesigner.model.JpsUiDesignerExtensionService;

import java.io.File;
import java.io.IOException;

/**
 * @author nik
 */
public class FormsBuilderTest extends JpsBuildTestCase {
  private static final String SIMPLE_FORM_PATH = "plugins/ui-designer/jps-plugin/testData/build/simple";

  public void testSimple() {
    JpsModule m = addModule("m", copyToProject(SIMPLE_FORM_PATH, "src"));
    makeAll().assertSuccessful();
    assertInstrumented(m, "xxx/MyForm.class");
    makeAll().assertUpToDate();
  }

  public void testEnableInstrumenting() {
    JpsModule m = addModule("m", copyToProject(SIMPLE_FORM_PATH, "src"));
    JpsUiDesignerExtensionService.getInstance().getOrCreateUiDesignerConfiguration(myProject).setInstrumentClasses(false);
    makeAll().assertSuccessful();
    assertNotInstrumented(m, "xxx/MyForm.class");
    makeAll().assertUpToDate();

    JpsUiDesignerExtensionService.getInstance().getOrCreateUiDesignerConfiguration(myProject).setInstrumentClasses(true);
    makeAll().assertSuccessful();
    assertInstrumented(m, "xxx/MyForm.class");
    makeAll().assertUpToDate();
  }

  public void testDisableInstrumenting() {
    JpsModule m = addModule("m", copyToProject(SIMPLE_FORM_PATH, "src"));
    makeAll().assertSuccessful();
    assertInstrumented(m, "xxx/MyForm.class");

    JpsUiDesignerExtensionService.getInstance().getOrCreateUiDesignerConfiguration(myProject).setInstrumentClasses(false);
    rebuildAll();//todo[nik,jeka] perhaps we shouldn't require rebuild to remove instrumented code
    assertNotInstrumented(m, "xxx/MyForm.class");
  }

  public void testRecompileFormForChangedClass() {
    JpsModule m = addModule("m", copyToProject(SIMPLE_FORM_PATH, "src"));
    makeAll().assertSuccessful();
    assertInstrumented(m, "xxx/MyForm.class");

    change(getAbsolutePath("src/xxx/MyForm.java"));
    makeAll().assertSuccessful();
    assertCompiled(JavaBuilder.BUILDER_NAME, "src/xxx/MyForm.java");
    assertCompiled(FormsInstrumenter.BUILDER_NAME, "src/xxx/MyForm.form");
    assertInstrumented(m, "xxx/MyForm.class");
    makeAll().assertUpToDate();
  }

  private static void assertNotInstrumented(JpsModule m, final String classPath) {
    assertFalse(isInstrumented(m, classPath));
  }

  private static void assertInstrumented(JpsModule m, final String classPath) {
    assertTrue(isInstrumented(m, classPath));
  }

  private static boolean isInstrumented(JpsModule m, final String classPath) {
    File file = new File(JpsJavaExtensionService.getInstance().getOutputDirectory(m, false), classPath);
    assertTrue(file.getAbsolutePath() + " not found", file.exists());

    final Ref<Boolean> instrumented = Ref.create(false);
    ClassVisitor visitor = new ClassVisitor(Opcodes.ASM4) {
      @Override
      public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
        if (name.equals("$$$setupUI$$$")) {
          instrumented.set(true);
        }
        return null;
      }
    };
    try {
      ClassReader reader = new ClassReader(FileUtil.loadFileBytes(file));
      reader.accept(visitor, 0);
      return instrumented.get();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

}
