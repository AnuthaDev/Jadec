/*
 * Show Java - A java/apk decompiler for android
 * Copyright (c) 2018 Niranjan Rajendran
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.thesourceofcode.jadec.decompilers

import android.content.Context
import androidx.work.Data
import androidx.work.ListenableWorker
import com.strobel.assembler.InputTypeLoader
import com.strobel.assembler.metadata.CompositeTypeLoader
import com.strobel.assembler.metadata.DeobfuscationUtilities
import com.strobel.assembler.metadata.IMetadataResolver
import com.strobel.assembler.metadata.ITypeLoader
import com.strobel.assembler.metadata.JarTypeLoader
import com.strobel.assembler.metadata.MetadataParser
import com.strobel.assembler.metadata.MetadataSystem
import com.strobel.assembler.metadata.TypeDefinition
import com.strobel.core.StringUtilities
import com.strobel.core.VerifyArgument
import com.strobel.decompiler.DecompilationOptions
import com.strobel.decompiler.DecompilerSettings
import com.strobel.decompiler.PlainTextOutput
import com.strobel.decompiler.languages.java.JavaFormattingOptions
import com.thesourceofcode.jadec.R
import com.thesourceofcode.jadec.data.SourceInfo
import com.thesourceofcode.jadec.utils.ZipUtils
import com.thesourceofcode.jadec.utils.ktx.cleanMemory
import jadx.api.JadxArgs
import jadx.api.JadxDecompiler
import org.apache.commons.io.FileUtils
import org.benf.cfr.reader.api.CfrDriver
import org.jetbrains.java.decompiler.main.decompiler.ConsoleDecompiler
import timber.log.Timber
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.jar.JarFile


/**
 * The [JavaExtractionWorker] does the actual decompilation of extracting the `java` source from
 * the inputs. All of the three decompiler that we support, allow passing in multiple input files.
 * So, we pass in all of the chunks of either dex (in case of JaDX) or jar files (in case of CFR &
 * fernflower) as the input.
 */
class JavaExtractionWorker(context: Context, data: Data) : BaseDecompiler(context, data) {

    /**
     * Do the decompilation using the CFR decompiler.
     *
     * We set the `lowmem` flag as true to let CFR know that it has to be more aggressive in terms
     * of garbage collection and less-aggressive caching. This results in reduced performance. But
     * increase success rates for large inputs. Which is a good trade-off.
     */
    @Throws(Exception::class)
    private fun decompileWithCFR(jarInputFiles: File, javaOutputDir: File) {
        cleanMemory()
        val jarFiles = jarInputFiles.listFiles()
        val options = mapOf<String, String>(
            "outputdir" to javaOutputDir.canonicalPath,
            "lomem" to "true"
        )
        val cfrDriver = CfrDriver.Builder().withOptions(options).build()
        cfrDriver.analyse(jarFiles.map { it.canonicalPath })
    }
//
//    @Throws(Exception::class)
//    private fun decompileWithProcyon(inFile: File, outFile: File) {
//        JarFile(inFile).use { jfile ->
//            FileOutputStream(outFile).use { dest ->
//                BufferedOutputStream(dest).use { buffDest ->
//                    ZipOutputStream(buffDest).use { out ->
//                        //bar.setMinimum(0)
//                       // bar.setMaximum(jfile.size())
//                        val data = ByteArray(1024)
//                        val settings: DecompilerSettings = DecompilerSettings.javaDefaults()
//                        settings.typeLoader = InputTypeLoader()
////                        val typeLoader = LuytenTypeLoader()
////                        val jarLoader: ITypeLoader = JarTypeLoader(jfile)
//                        //typeLoader.getTypeLoaders().add(jarLoader)
//                        val decompilationOptions = DecompilationOptions()
//                        decompilationOptions.settings = settings
//                        decompilationOptions.isFullDecompilation = true
//
//                        settings.typeLoader = CompositeTypeLoader(JarTypeLoader(jfile), settings.typeLoader)
//                        val metadataSystem = MetadataSystem(settings.typeLoader)
//
//                        var mass: List<String?>? = null
//                        //val jarEntryFilter = JarEntryFilter(jfile)
////                        val luytenPrefs: LuytenPreferences =
////                            ConfigSaver.getLoadedInstance().getLuytenPreferences()
////                        mass = if (luytenPrefs.isFilterOutInnerClassEntries()) {
////                            jarEntryFilter.getEntriesWithoutInnerClasses()
////                        } else {
////                            jarEntryFilter.getAllEntriesFromJar()
////                        }
//                        val ent = jfile.entries()
//                        val history: MutableSet<String> =
//                            HashSet()
//                        var tick = 0
//                        while (ent.hasMoreElements()) {
//                            val entry = ent.nextElement()
//                            if (entry.name.endsWith(".class")) {
//                                val etn =
//                                    JarEntry(entry.name.replace(".class", ".java"))
//                                println("[SaveAll]: " + etn.name + " -> " + outFile.name)
//                                if (history.add(etn.name)) {
//                                    out.putNextEntry(etn)
//                                    try {
//                                        val isUnicodeEnabled =
//                                            decompilationOptions.settings.isUnicodeOutputEnabled
//                                        val internalName =
//                                            StringUtilities.removeRight(entry.name, ".class")
//                                        val type: TypeReference? =
//                                            metadataSystem.lookupType(internalName)
//                                        var resolvedType: TypeDefinition? = null
//                                        if (type == null || type.resolve()
//                                                .also { resolvedType = it } == null
//                                        ) {
//                                            throw java.lang.Exception("Unable to resolve type.")
//                                        }
//                                        val writer: Writer =
//                                            if (isUnicodeEnabled) OutputStreamWriter(
//                                                out,
//                                                "UTF-8"
//                                            ) else OutputStreamWriter(out)
//                                        val plainTextOutput = PlainTextOutput(writer)
//                                        plainTextOutput.isUnicodeOutputEnabled = isUnicodeEnabled
//                                        settings.language
//                                            .decompileType(
//                                                resolvedType,
//                                                plainTextOutput,
//                                                decompilationOptions
//                                            )
//                                        writer.flush()
//                                    } catch (e: java.lang.Exception) {
////                                        label.setText("Cannot decompile file: " + entry.name)
////                                        Luyten.showExceptionDialog(
////                                            "Unable to Decompile file!\nSkipping file...",
////                                            e
////                                        )
//                                    } finally {
//                                        out.closeEntry()
//                                    }
//                                }
//                            } else {
//                                try {
//                                    var etn =
//                                        JarEntry(entry.name)
//                                    if (entry.name.endsWith(".java")) etn =
//                                        JarEntry(
//                                            entry.name.replace(".java", ".src.java")
//                                        )
//                                    if (history.add(etn.name)) {
//                                        out.putNextEntry(etn)
//                                        try {
//                                            jfile.getInputStream(etn)?.use { inp ->
//                                                var count: Int
//                                                while (inp.read(data, 0, 1024)
//                                                        .also { count = it } != -1
//                                                ) {
//                                                    out.write(data, 0, count)
//                                                }
//                                            }
//                                        } finally {
//                                            out.closeEntry()
//                                        }
//                                    }
//                                } catch (ze: ZipException) {
//                                    if (!ze.message!!.contains("duplicate")) {
//                                        throw ze
//                                    }
//                                }
//                            }
//                        }
//                    }
//                }
//            }
//        }
//    }
//

    internal class NoRetryMetadataSystem : MetadataSystem {
        private val _failedTypes: MutableSet<String> = HashSet()

        constructor()
        constructor(typeLoader: ITypeLoader?) : super(typeLoader)

        override fun resolveType(descriptor: String, mightBePrimitive: Boolean): TypeDefinition? {
            if (_failedTypes.contains(descriptor)) {
                return null
            }
            val result = super.resolveType(descriptor, mightBePrimitive)
            if (result == null) {
                _failedTypes.add(descriptor)
            }
            return result
        }
    }
    internal class FileOutputWriter(
        /**
         * Returns the file to which 'this' is writing.
         *
         * @return the file to which 'this' is writing
         */
        val file: File, settings: DecompilerSettings
    ) :
        OutputStreamWriter(
            FileOutputStream(file),
            if (settings.isUnicodeOutputEnabled) StandardCharsets.UTF_8 else Charset.defaultCharset()
        )


    @Throws(Exception::class)
    private fun decompileWithProcyon(inFile: File, outFile: File) {

        inFile.listFiles()?.forEach {inJar->
            JarFile(inJar).use { jfile ->

                val data = ByteArray(1024)
                val settings: DecompilerSettings = DecompilerSettings.javaDefaults()
                settings.typeLoader = CompositeTypeLoader(JarTypeLoader(jfile), InputTypeLoader())
                settings.javaFormattingOptions =
                    JavaFormattingOptions.createDefault()
                settings.forceExplicitImports = true
                settings.showSyntheticMembers = false
                val entries = jfile.entries()

                var classesDecompiled = 0

                var metadataSystem = NoRetryMetadataSystem(settings.typeLoader)
                metadataSystem.isEagerMethodLoadingEnabled = true

                while (entries.hasMoreElements()) {
                    if (++classesDecompiled % 100 == 0) {
                        metadataSystem = NoRetryMetadataSystem(settings.typeLoader);
                    }
                    val entry = entries.nextElement()
                    val name = entry.name

                    if (!name.endsWith(".class")) {
                        continue
                    }

                    val internalName = StringUtilities.removeRight(name, ".class")

                    val outf = outFile.resolve("$internalName.java")
                    outf.parentFile?.mkdirs()
                    outf.createNewFile()



                    FileOutputStream(outf).use { stream ->
                        OutputStreamWriter(stream).use { writer ->

                            val output = PlainTextOutput(writer)

                            VerifyArgument.notNull(internalName, "internalName")
                            VerifyArgument.notNull(settings, "settings")
                            val type: Any?
                            type = if (internalName.length == 1) {
                                val parser = MetadataParser(IMetadataResolver.EMPTY)
                                val reference = parser.parseTypeDescriptor(internalName)
                                metadataSystem.resolve(reference)
                            } else {
                                metadataSystem.lookupType(internalName)
                            }

                            val resolvedType: TypeDefinition?
                            if (type != null) {
                                resolvedType = type.resolve()
                                DeobfuscationUtilities.processType(resolvedType)
                                val options = DecompilationOptions()
                                options.settings = settings
                                options.isFullDecompilation = true

                                if (!(resolvedType.isAnonymous || resolvedType.isSynthetic || resolvedType.isNested)) {

                                    println("Decompiling " + internalName.replace('/', '.'))
                                    classesDecompiled++

                                    settings.language.decompileType(resolvedType, output, options)
                                } else {
                                    outf.delete()
                                }
                            } else {
                                output.writeLine(
                                    "!!! ERROR: Failed to load class %s.",
                                    *arrayOf<Any>(internalName)
                                )
                            }
                        }
                    }


                    //                if (entry.name.endsWith(".class")) {
                    //                    val etn =
                    //                        JarEntry(entry.name.replace(".class", ".java"))
                    //                    Timber.tag("[SaveAll]: ").d(etn.name + " -> " + outFile.name)
                    //                }
                }
            }
        }
    }


//    @Throws(Exception::class)
//    private fun decompileWithProcyon(jarInputFiles: File, javaOutputDir: File) {
//        val settings = DecompilerSettings.javaDefaults()
//        val jf = JarFile(jarInputFiles)
//        settings.typeLoader = JarTypeLoader(jf)
//
//        try {
//            FileOutputStream(javaOutputDir).use { stream ->
//                OutputStreamWriter(stream).use { writer ->
//                    println("Decompiling " + "com/italankin/fifteen/export/RecordsExporter".replace('/', '.'))
//                    Decompiler.decompile(
//                        "com/italankin/fifteen/export/RecordsExporter",
//                        PlainTextOutput(writer),
//                        settings
//                    )
//                }
//            }
//        } catch (e: IOException) {
//            // handle error
//        }
//        //val loader = ClassLoader.getSystemClassLoader();
//
//    }
    /**
     * Do the decompilation using the JaDX decompiler.
     *
     * We set `threadsCount` as 1. This instructs JaDX to not spawn additional threads to prevent
     * issues on some devices.
     */
    @Throws(Exception::class)
    private fun decompileWithJaDX(dexInputFiles: File, javaOutputDir: File) {
        cleanMemory()

        val args = JadxArgs()
        args.outDirSrc = javaOutputDir
        args.inputFiles = dexInputFiles.listFiles().toMutableList()
        args.threadsCount = 1

        val jadx = JadxDecompiler(args)
        jadx.load()
        jadx.saveSources()
        if (dexInputFiles.exists() && dexInputFiles.isDirectory && !keepIntermediateFiles) {
            dexInputFiles.deleteRecursively()
        }
    }

    /**
     * Do the decompilation using FernFlower decompiler.
     *
     * The out of the decompiler is a jar archive containing the decompiled java files. So, we look
     * for and extract the archive after the decompilation.
     */
    @Throws(Exception::class)
    private fun decompileWithFernFlower(jarInputFiles: File, javaOutputDir: File) {
        cleanMemory()

        ConsoleDecompiler.main(
            arrayOf(
                jarInputFiles.canonicalPath, javaOutputDir.canonicalPath
            )
        )

        javaOutputDir.listFiles().forEach { decompiledJarFile ->
            if (decompiledJarFile.exists() && decompiledJarFile.isFile && decompiledJarFile.extension == "jar") {
                ZipUtils.unzip(decompiledJarFile, javaOutputDir, printStream!!)
                decompiledJarFile.delete()
            } else {
                throw FileNotFoundException("Decompiled jar does not exist")
            }
        }
    }

    override fun doWork(): ListenableWorker.Result {
        Timber.tag("JavaExtraction")
        context.getString(R.string.decompilingToJava).let {
            buildNotification(it)
            setStep(it)
        }

        super.doWork()

        val sourceInfo = SourceInfo.from(workingDirectory)
            .setPackageLabel(packageLabel)
            .setPackageName(packageName)
            .persist()

        try {
            when (decompiler) {
                "jadx" -> decompileWithJaDX(outputDexFiles, outputJavaSrcDirectory)
                "procyon" -> decompileWithProcyon(outputJarFiles, outputJavaSrcDirectory)
                "cfr" -> decompileWithCFR(outputJarFiles, outputJavaSrcDirectory)
                "fernflower" -> decompileWithFernFlower(outputJarFiles, outputJavaSrcDirectory)
            }
        } catch (e: Exception) {
            return exit(e)
        }

        if (outputDexFiles.exists() && outputDexFiles.isDirectory && !keepIntermediateFiles) {
            outputDexFiles.deleteRecursively()
        }

        if (outputJarFiles.exists() && outputJarFiles.isDirectory && !keepIntermediateFiles) {
            outputJarFiles.deleteRecursively()
        }

        sourceInfo
            .setJavaSourcePresence(true)
            .setSourceSize(FileUtils.sizeOfDirectory(workingDirectory))
            .persist()

        return successIf(!outputJavaSrcDirectory.list().isNullOrEmpty())
    }
}
