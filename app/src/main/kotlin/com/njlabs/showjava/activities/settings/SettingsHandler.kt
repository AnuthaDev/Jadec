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

package com.njlabs.showjava.activities.settings

import android.content.Context
//import com.njlabs.showjava.utils.ktx.appStorage
import io.reactivex.Observable

class SettingsHandler(private var context: Context) {

    /**
     * Delete all decompiled sources recursively
     */
    fun deleteHistory(): Observable<Any> {
        return Observable.fromCallable {
            context.getExternalFilesDir(null)!!.resolve("show-java").resolve("sources")
                .deleteRecursively()
        }
    }
}