/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.FramesComputationMode
import com.android.build.api.instrumentation.InstrumentationParameters
import com.android.build.api.instrumentation.InstrumentationScope
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes


abstract class JADXLogPlugin : Plugin<Project> {

    override fun apply(project: Project) {

        val androidComponents = project.extensions.getByType(AndroidComponentsExtension::class.java)

        androidComponents.onVariants { variant ->
            variant.instrumentation.transformClassesWith(JADXVisitorFactory::class.java,
                                 InstrumentationScope.ALL) {
            }
            variant.instrumentation.setAsmFramesComputationMode(FramesComputationMode.COPY_FRAMES)
        }
    }

    abstract class JADXVisitorFactory :
        AsmClassVisitorFactory<InstrumentationParameters.None> {

        override fun createClassVisitor(
            classContext: ClassContext,
            nextClassVisitor: ClassVisitor
        ): ClassVisitor {
            return JADXClassVisitor(instrumentationContext.apiVersion.get(), nextClassVisitor)
        }

        override fun isInstrumentable(classData: ClassData): Boolean {
            return classData.className.startsWith("jadx.core.dex.visitors.SaveCode")
        }
    }
}

class JADXClassVisitor(private val apiversion: Int, cv: ClassVisitor?) : ClassVisitor(apiversion, cv) {
    override fun visitMethod(
        access: Int,
        name: String,
        desc: String?,
        signature: String?,
        exceptions: Array<String?>?
    ): MethodVisitor {
        return if (name == "save" && desc== "(Ljava/io/File;Ljadx/core/dex/nodes/ClassNode;Ljadx/api/ICodeInfo;)V") {
            JADXMethodVisitor(apiversion, super.visitMethod(access, name, desc, signature, exceptions))
        } else super.visitMethod(access, name, desc, signature, exceptions)
    }

    private class JADXMethodVisitor(apiversion: Int, mv: MethodVisitor?) :
        MethodVisitor(apiversion, mv) {
        // This method will be called before almost all instructions
        override fun visitCode() {
            visitVarInsn(Opcodes.ALOAD, 0)
            visitVarInsn(Opcodes.ALOAD, 1)
            visitVarInsn(Opcodes.ALOAD, 2)
            visitMethodInsn(
                Opcodes.INVOKESTATIC,
                "com/thesourceofcode/jadec/utils/streams/Logger",
                "logJadxClassWrite",
                "(Ljava/io/File;Ljadx/core/dex/nodes/ClassNode;Ljadx/api/ICodeInfo;)V",
                false
            )
        }
    }
}
