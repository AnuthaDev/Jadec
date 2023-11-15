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

//import net.dongliu.apk.parser.AbstractApkFile
//import net.dongliu.apk.parser.ApkFile
//import net.dongliu.apk.parser.exception.ParserException
//import net.dongliu.apk.parser.struct.resource.ResourcePackage
//import net.dongliu.apk.parser.struct.resource.ResourceTable

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.work.Data
import androidx.work.ListenableWorker
import com.reandroid.apk.APKLogger
import com.reandroid.apk.ApkModule
import com.reandroid.apk.ApkModuleXmlDecoder
import com.reandroid.arsc.value.ResConfig
import com.thesourceofcode.jadec.R
import com.thesourceofcode.jadec.data.PackageInfo
import com.thesourceofcode.jadec.data.SourceInfo
import com.thesourceofcode.jadec.utils.ktx.cleanMemory
import com.thesourceofcode.jadec.utils.ktx.toFile
import jadx.api.JadxArgs
import jadx.api.JadxDecompiler
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import org.apache.commons.io.IOUtils
import timber.log.Timber
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter


class ResourcesExtractionWorker(context: Context, data: Data) : BaseDecompiler(context, data) {

    private lateinit var apkModule: ApkModule
    private val images = listOf("jpg", "png", "gif", "jpeg", "webp", "tiff", "bmp")

    /**
     * Extract xml & image resources using JaDX. JaDX has a better resources decompiler compared
     * to others. But sadly, JaDX also uses javax.imageio classes that are not available in android
     * at present. So, we don't use it.
     *
     * TODO Figure out how to use it only for XML though.
     *
     * @experimental
     */
    @Throws(Exception::class)
    private fun extractResourcesWithJadx() {
        cleanMemory()
        val resDir = outputSrcDirectory
        val args = JadxArgs()
        args.outDirRes = resDir
        args.inputFiles = mutableListOf(inputPackageFile)
        val jadx = JadxDecompiler(args)
        jadx.load()
        jadx.saveResources()
    }
    fun normalizeFile(f: File) {
        var temp: File? = null
        var bufferIn: BufferedReader? = null
        var bufferOut: BufferedWriter? = null
        try {
            if (f.exists()) {
                // Create a new temp file to write to
                temp = File(f.absolutePath + ".normalized")
                temp.createNewFile()

                // Get a stream to read from the file un-normalized file
                val fileIn = FileInputStream(f)
                val dataIn = DataInputStream(fileIn)
                bufferIn = BufferedReader(InputStreamReader(dataIn))

                // Get a stream to write to the normalized file
                val fileOut = FileOutputStream(temp)
                val dataOut = DataOutputStream(fileOut)
                bufferOut = BufferedWriter(OutputStreamWriter(dataOut))

                // For each line in the un-normalized file
                var line: String?
                while (bufferIn.readLine().also { line = it } != null) {
                    // Write the original line plus the operating-system dependent newline
                    bufferOut.write(line)
                    bufferOut.newLine()
                }
                bufferIn.close()
                bufferOut.close()

                // Remove the original file
                f.delete()

                // And rename the original file to the new one
                temp.renameTo(f)
            } else {
                // If the file doesn't exist...
                Timber.w("Could not find file to open: %s", f.absolutePath)
            }
        } catch (e: java.lang.Exception) {
            Timber.w(e)
        } finally {
            // Clean up, temp should never exist
            FileUtils.deleteQuietly(temp)
            IOUtils.closeQuietly(bufferIn)
            IOUtils.closeQuietly(bufferOut)
        }
    }
    /**
     * Read the APK as zip, and extract XML resources using the apk-parser and image/other resources
     * by just extracting it from the zip.
     */
    @Throws(Exception::class)
    private fun extractResourcesWithParser() {
        cleanMemory()
//        writeManifest()
//        val zipFile = ZipFile(inputPackageFile)
//        val entries = zipFile.entries()
//        while (entries.hasMoreElements()) {
//            val zipEntry = entries.nextElement()
//
//            try {
//                if (!zipEntry.isDirectory && zipEntry.name != "AndroidManifest.xml") {
//                    sendStatus(zipEntry.name)
//                    if (FilenameUtils.isExtension(zipEntry.name, "xml") && !zipEntry.name.startsWith("assets")) {
//                        writeXML(zipEntry.name)
//                    } else if (FilenameUtils.isExtension(zipEntry.name, images) || zipEntry.name.startsWith("assets")) {
//                        writeFile(zipFile.getInputStream(zipEntry), zipEntry.name)
//                    }
//                }
//            } catch (e: java.lang.Exception) {
//                sendStatus("Skipped ${zipEntry.name}")
//            }
//        }
//        zipFile.close()

        val xmlDecoder = ApkModuleXmlDecoder(apkModule)
        xmlDecoder.setApkLogger(object:APKLogger{
            override fun logMessage(p0: String?) {
                if (p0 != null) {
                    sendStatus(p0)
                }
            }

            override fun logError(p0: String?, p1: Throwable?) {
                if (p0 != null) {
                    sendStatus(p0)
                }
            }

            override fun logVerbose(p0: String?) {
                if (p0 != null) {
                    sendStatus(p0)
                }
            }

        })
        xmlDecoder.setKeepResPath(false)
        xmlDecoder.decodeAndroidManifest(outputSrcDirectory.parentFile)
        xmlDecoder.decodeResourceTable(outputSrcDirectory)
        outputSrcDirectory.parentFile?.let { normalizeFile(it.resolve("AndroidManifest.xml")) }
    }

    /**
     * Currently the extracted XML resources, refer to the resource used within them via their
     * numeric ID. This is an experiment to parse the resource table in the APK and get the human
     * readable names from the numeric ID.
     *
     * @experimental
     */
//    @Suppress("UNCHECKED_CAST")
//    @RequiresApi(Build.VERSION_CODES.N)
//    @Throws(Exception::class)
//    private fun loadResourcesTable() {
//        cleanMemory()
//        val resourceTableField = AbstractApkFile::class.java.getDeclaredField("resourceTable")
//        resourceTableField.isAccessible = true
//        val resourceTable = resourceTableField.get(parsedInputApkFile) as ResourceTable
//        val packageMapField = resourceTable.javaClass.getDeclaredField("packageMap")
//        packageMapField.isAccessible = true
//        val packageMap = packageMapField.get(resourceTable) as Map<Short, ResourcePackage>
//        packageMap.forEach { _, u ->
//            Timber.d("[res] ID: ${u.id} Name: ${u.name}")
//            u.typesMap.forEach { _, iu ->
//                iu.forEach {
//                    Timber.d("[res] Inner ID: ${it.id} Inner Name: ${it.name}")
//                }
//            }
//        }
//    }

    /**
     * Write a file at the appropriate output path within the source directory
     */
    @Throws(Exception::class)
    private fun writeFile(fileStream: InputStream, path: String) {
        val fileFolderPath =
            outputSrcDirectory.canonicalPath + "/" + path.replace(
                FilenameUtils.getName(
                    path
                ), ""
            )
        val fileFolder = File(fileFolderPath)
        if (!fileFolder.exists() || !fileFolder.isDirectory) {
            fileFolder.mkdirs()
        }
        fileStream.toFile(File(fileFolderPath, FilenameUtils.getName(path)))
    }

    /**
     * Read and decompile an XML resource from the APK and write it to the source directory.
     */
//    @Throws(ParserException::class)
//    private fun writeXML(path: String) {
//        val xml = parsedInputApkFile.transBinaryXml(path)
//        val fileFolderPath =
//            outputSrcDirectory.canonicalPath + "/" + path.replace(
//                FilenameUtils.getName(
//                    path
//                ), ""
//            )
//        val fileFolder = File(fileFolderPath)
//        if (!fileFolder.exists() || !fileFolder.isDirectory) {
//            fileFolder.mkdirs()
//        }
//        FileUtils.writeStringToFile(
//            File(fileFolderPath + FilenameUtils.getName(path)),
//            xml,
//            Charset.defaultCharset()
//        )
//    }

    /**
     * Write the AndroidManifest file to the source directory
     */
//    @Throws(Exception::class)
//    private fun writeManifest() {
//        val manifestXml = parsedInputApkFile.manifestXml
//        FileUtils.writeStringToFile(
//            workingDirectory.resolve("AndroidManifest.xml"),
//            manifestXml,
//            Charset.defaultCharset()
//        )
//    }

    /**
     * Get bitmap from drawable. This is used to read the app icon and save to the source directory.
     */
    // Borrowed from from https://stackoverflow.com/a/52453231/1562480
    private fun getBitmapFromDrawable(drawable: Drawable): Bitmap {
        val bmp = Bitmap.createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bmp)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bmp
    }

    /**
     * Save the app icon to the source directory
     */
    @Throws(Exception::class)
    private fun saveIcon() {
        val packageInfo =
            context.packageManager.getPackageArchiveInfo(inputPackageFile.canonicalPath, 0)
        val drawable = packageInfo?.applicationInfo?.loadIcon(context.packageManager)

        val bitmap = if (drawable != null) {
            getBitmapFromDrawable(drawable)
        } else {
            val inp = apkModule.listResFiles(
                apkModule.androidManifestBlock.iconResourceId,
                ResConfig.parse(
                    ResConfig.Density.HDPI.toString()
                )
            )[0].inputSource.openStream()

            BitmapFactory.decodeStream(inp)

        }
        val iconOutput = FileOutputStream(workingDirectory.resolve("icon.png"))
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, iconOutput)
        iconOutput.close()
    }

    override fun doWork(): ListenableWorker.Result {
        Timber.tag("ResourcesExtraction")

        if (type == PackageInfo.Type.APK) {
            context.getString(R.string.extractingResources).let {
                buildNotification(it)
                setStep(it)
            }
        }

        super.doWork()

        /* when (decompiler) {
            "jadx" -> extractResourcesWithJadx()
            else -> extractResourcesWithParser()
        } */

        // Not using JaDX for resource extraction.
        // Due to its dependency on the javax.imageio.ImageIO class which is unavailable on android

        val sourceInfo = SourceInfo.from(workingDirectory)
            .setPackageLabel(packageLabel)
            .setPackageName(packageName)

        if (type == PackageInfo.Type.APK) {
            apkModule = ApkModule.loadApkFile(inputPackageFile)

            try {
                extractResourcesWithParser()
                saveIcon()
            } catch (e: Exception) {
                return exit(e)
            }

            sourceInfo
                .setXmlSourcePresence(true)
                .persist()
        }

        sourceInfo
            .setSourceSize(FileUtils.sizeOfDirectory(workingDirectory))
            .persist()

        onCompleted()

        return ListenableWorker.Result.success()
    }
}
