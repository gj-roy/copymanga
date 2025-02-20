package top.fumiama.copymanga.manga

import android.util.Log
import com.google.gson.Gson
import top.fumiama.copymanga.MainActivity.Companion.mainWeakReference
import top.fumiama.copymanga.json.Chapter2Return
import top.fumiama.copymanga.template.http.PausableDownloader
import top.fumiama.copymanga.tools.http.DownloadPool
import java.io.File

class MangaDlTools {
    private var pool: DownloadPool? = null
    private var grp: CharSequence = ""
    private var indexMap = hashMapOf<String, Int>()
    var exit: Boolean
        get() = pool?.exit?:false
        set(value) { pool?.exit = value }
    var wait
        get() = pool?.wait
        set(value) { if (value != null) { pool?.wait = value } }

    suspend fun downloadChapterInVol(url: CharSequence, chapterName: CharSequence, group: CharSequence, index: Int) {
        Log.d("MyMDT", "下载：$url, index：$index")
        PausableDownloader(url.toString(), 1000) { data ->
            try {
                Gson().fromJson(data.decodeToString(), Chapter2Return::class.java)?.let {
                    getChapterInfo(it, index, chapterName, group)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                onDownloadedListener?.handleMessage(index, false, e.localizedMessage?:"Gson parsing error")
            }
        }.run()
    }

    @Synchronized private fun prepareDownloadListener() {
        pool?.setOnDownloadListener { fileName: String, isSuccess: Boolean, message: String ->
            indexMap[fileName]?.let { onDownloadedListener?.handleMessage(it, isSuccess, message) }
        }
        pool?.setOnPageDownloadListener { fileName: String, downloaded: Int, total: Int, isSuccess: Boolean, message: String ->
            indexMap[fileName]?.let { onDownloadedListener?.handleMessage(it, downloaded, total, isSuccess, message) }
        }
    }

    @Synchronized private fun setPool(comicName: String, group: CharSequence) {
        if(pool == null || grp != group) {
            pool = DownloadPool(File(
                mainWeakReference?.get()?.getExternalFilesDir(""),
                "$comicName/$group"
            ).absolutePath)
            grp = group
            prepareDownloadListener()
        }
    }

    @Synchronized private fun setIndexMap(f : String, index: Int) {
        indexMap[f] = index
    }

    private fun getChapterInfo(chapter2Return: Chapter2Return, index: Int, chapterName: CharSequence, group: CharSequence) {
        if(index >= 0){
            val f = "$chapterName.zip"
            setPool(chapter2Return.results.comic.name, group)
            setIndexMap(f, index)
            pool?.plusAssign(DownloadPool.Quest(f, getMangaUrls(chapter2Return)))
        }
    }

    private fun getMangaUrls(chapter2Return: Chapter2Return): Array<String>{
        var re: Array<String> = arrayOf()
        val hm: HashMap<Int, String> = hashMapOf()
        val chapter = chapter2Return.results.chapter
        if(chapter.words.size < chapter.contents.size) {
            chapter.words = chapter.words.toMutableList().apply {
                chapter.contents.indices.forEach {
                    if(!contains(it)) plusAssign(it)
                }
            }.toIntArray()
        }
        for(i in 0 until chapter.contents.size) {
            hm[chapter.words[i]] = chapter.contents[i].url
        }
        for(i in 0 until chapter.contents.size){
            re += hm[i]?:""
        }
        return re
    }

    var onDownloadedListener: OnDownloadedListener? = null
    interface OnDownloadedListener{
        fun handleMessage(index: Int, isSuccess: Boolean, message: String)
        fun handleMessage(index: Int, downloaded: Int, total: Int, isSuccess: Boolean, message: String)
    }
}
