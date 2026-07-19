package com.callbackdev.saldo.feature.recap

import android.content.Context
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.core.content.FileProvider
import com.callbackdev.saldo.core.common.di.IoDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.time.YearMonth
import javax.inject.Inject

/**
 * Writes the recap summary image into the app cache and exposes it through
 * the manifest FileProvider, mirroring the CSV export pipeline: no storage
 * permission, no network, the PNG only leaves the device through the share
 * sheet the user picks (ADR 28).
 */
class RecapImageSharer @Inject constructor(
    @ApplicationContext private val context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {

    /** Writes `cache/exports/saldo-recap-YYYY-MM.png`, returning its shareable [Uri]. */
    suspend fun share(image: ImageBitmap, month: YearMonth): Uri = withContext(ioDispatcher) {
        val directory = File(context.cacheDir, EXPORTS_DIRECTORY).apply { mkdirs() }
        val file = File(directory, "saldo-recap-$month.png")
        file.outputStream().use { stream ->
            image.asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG, PNG_QUALITY, stream)
        }
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private companion object {
        /** Matches the `cache-path` exposed in `res/xml/file_paths.xml`. */
        const val EXPORTS_DIRECTORY = "exports"

        /** Ignored for PNG (lossless), required by the API. */
        const val PNG_QUALITY = 100
    }
}
