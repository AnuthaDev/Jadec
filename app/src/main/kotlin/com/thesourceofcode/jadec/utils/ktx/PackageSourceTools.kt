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

package com.thesourceofcode.jadec.utils.ktx

import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Environment
import java.io.File

/**
 * Path to the priamry storage directory on the user's internal memory
 */
val appStorage: File = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS), "jadec")

/**
 * Check if the given packageInfo points to a system application
 */
fun isSystemPackage(pkgInfo: PackageInfo): Boolean {
    return pkgInfo.applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0
}

/**
 * Get the source directory for a given package name
 */
fun sourceDir(packageName: String): File {
    return appStorage.resolve("sources/$packageName")
}

/**
 * Convert an arbitrary file name into a generated package name that we can use
 */
fun jarPackageName(jarFileName: String): String {
    val slug = toSlug(jarFileName)
    return "$slug-${hashString("SHA-1", slug).slice(0..7)}".toLowerCase()
}