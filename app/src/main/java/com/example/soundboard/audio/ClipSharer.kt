package com.example.soundboard.audio

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.soundboard.R
import java.io.File

/**
 * Turns a clip into an MP3 in `cacheDir/shared/` and builds the share-sheet intent for it
 * (SPEC §2, "Sharing"). The folder only ever holds the latest shared clip.
 */
class ClipSharer(context: Context) {
    private val appContext = context.applicationContext
    private val dir = File(appContext.cacheDir, "shared")

    /** Encodes [clip] (blocking: call off the main thread). Null when encoding or writing failed. */
    fun prepare(clip: ShortArray, name: String): Intent? {
        val mp3 = Mp3Encoder.encode(ShareFormat.withHeadroom(clip)) ?: return null
        dir.deleteRecursively()
        if (!dir.mkdirs()) return null
        val file = File(dir, ShareFormat.fileName(name, appContext.getString(R.string.share_default_name)))
        file.writeBytes(mp3)
        val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.share", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "audio/mpeg"
            putExtra(Intent.EXTRA_STREAM, uri)
            // ClipData carries the read grant through the chooser to the picked app.
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, null)
    }
}
