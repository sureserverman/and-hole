package org.pihole.android.data.lists.storage

import android.content.Context
import java.io.File

/**
 * Large compiled manifests do not fit in [android.database.CursorWindow]; keep the trie source on disk.
 */
object CompiledSnapshotManifestStore {
    private const val DIR = "compiled-snapshot"
    private const val FILE = "manifest.json"

    fun manifestFile(context: Context): File = File(File(context.filesDir, DIR), FILE)

    fun write(context: Context, manifestJson: String) {
        val dir = File(context.filesDir, DIR)
        dir.mkdirs()
        val tmp = File(dir, "$FILE.tmp")
        val out = File(dir, FILE)
        tmp.writeText(manifestJson)
        if (out.exists()) out.delete()
        check(tmp.renameTo(out)) { "failed to finalize manifest" }
    }

    fun readTextOrNull(context: Context): String? {
        val f = manifestFile(context)
        if (!f.isFile || f.length() == 0L) return null
        return f.readText()
    }
}
