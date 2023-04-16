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

package com.thesourceofcode.jadec.activities.explorer.navigator

import android.content.Context
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.thesourceofcode.jadec.data.FileItem
import com.thesourceofcode.jadec.utils.ZipUtils
import io.reactivex.Observable
import org.apache.commons.io.FileUtils
import java.io.File
import java.io.IOException
import java.text.DateFormat
import java.util.Date
import kotlin.collections.ArrayList
import kotlin.collections.forEach
import kotlin.collections.sortBy

class NavigatorHandler(private var context: Context) {

    /**
     * Load all files in the given directory
     */
    fun loadFiles(currentDirectory: File): Observable<ArrayList<FileItem>> {
        return Observable.fromCallable {
            val directories = ArrayList<FileItem>()
            val files = ArrayList<FileItem>()
            val items = currentDirectory.listFiles()
            if (items.isNullOrEmpty()) {
                return@fromCallable directories
            }
            items.forEach { file ->
                val lastModDate = DateFormat.getDateTimeInstance()
                    .format(
                        Date(
                            file.lastModified()
                        )
                    )
                if (file.isDirectory) {
                    val children = file.listFiles()
                    val noOfChildren = children?.size ?: 0
                    val fileSize = "$noOfChildren ${if (noOfChildren == 1) "item" else "items"}"
                    directories.add(FileItem(file, fileSize, lastModDate))
                } else {
                    val fileSize = FileUtils.byteCountToDisplaySize(file.length())
                    files.add(FileItem(file, fileSize, lastModDate))
                }
            }
            directories.sortBy { it.name?.toLowerCase() }
            files.sortBy { it.name?.toLowerCase() }
            directories.addAll(files)
            directories
        }
    }

    /**
     * Package an entire directory containing the source code into a .zip archive.
     */
    fun archiveDirectory(sourceDirectory: File, packageName: String): Observable<File> {
        return Observable.fromCallable {
            ZipUtils.zipDir(sourceDirectory, packageName)
        }
    }

    /**
     * Delete the source directory
     */
    fun deleteDirectory(sourceDirectory: File): Observable<Unit> {
        return Observable.fromCallable {
            try {
                if (sourceDirectory.exists()) {
                    FileUtils.deleteDirectory(sourceDirectory)
                }
            } catch (e: IOException) {
                FirebaseCrashlytics.getInstance().recordException(e)
            }
        }
    }
}
