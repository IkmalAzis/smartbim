package com.fyp.smartsigntranslator

import android.content.Context
import com.google.android.exoplayer2.database.StandaloneDatabaseProvider
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor
import com.google.android.exoplayer2.upstream.cache.SimpleCache
import java.io.File

object VideoCache {
    private const val CACHE_SIZE = 250L * 1024 * 1024 // 250MB
    private const val CACHE_DIR = "video_cache"
    private const val PREF_LAST_CLEAR = "last_cache_clear"
    private const val ONE_DAY_MS = 24 * 60 * 60 * 1000L

    private var simpleCache: SimpleCache? = null

    fun getCache(context: Context): SimpleCache {
        if (simpleCache == null) {
            val cacheDir = File(context.cacheDir, CACHE_DIR)
            val evictor = LeastRecentlyUsedCacheEvictor(CACHE_SIZE)
            val databaseProvider = StandaloneDatabaseProvider(context)
            simpleCache = SimpleCache(cacheDir, evictor, databaseProvider)
        }
        return simpleCache!!
    }

    fun clearCache(context: Context) {
        try {
            simpleCache?.release()
            simpleCache = null
            val cacheDir = File(context.cacheDir, CACHE_DIR)
            cacheDir.deleteRecursively()
            // Save last clear time
            context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
                .edit().putLong(PREF_LAST_CLEAR, System.currentTimeMillis()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun autoDeleteIfNeeded(context: Context) {
        val sharedPref = context.getSharedPreferences("AppSettings", Context.MODE_PRIVATE)
        val lastClear = sharedPref.getLong(PREF_LAST_CLEAR, 0L)
        val now = System.currentTimeMillis()
        if (now - lastClear > ONE_DAY_MS) {
            clearCache(context)
        }
    }

    fun getCacheSize(context: Context): String {
        val cacheDir = File(context.cacheDir, CACHE_DIR)
        if (!cacheDir.exists()) return "0 MB"
        val bytes = cacheDir.walkTopDown().sumOf { it.length() }
        return when {
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${"%.1f".format(bytes / (1024.0 * 1024.0))} MB"
        }
    }
}
