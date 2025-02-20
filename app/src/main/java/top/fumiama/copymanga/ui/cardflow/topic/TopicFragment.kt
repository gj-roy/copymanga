package top.fumiama.copymanga.ui.cardflow.topic

import android.os.Bundle
import androidx.lifecycle.lifecycleScope
import com.google.gson.Gson
import kotlinx.android.synthetic.main.app_bar_main.*
import kotlinx.android.synthetic.main.fragment_topic.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.fumiama.copymanga.json.TopicStructure
import top.fumiama.copymanga.template.http.PausableDownloader
import top.fumiama.copymanga.template.ui.InfoCardLoader
import top.fumiama.copymanga.tools.api.CMApi
import top.fumiama.dmzj.copymanga.R

@ExperimentalStdlibApi
class TopicFragment : InfoCardLoader(R.layout.fragment_topic, R.id.action_nav_topic_to_nav_book) {
    private var type = 1
    override fun getApiUrl() =
        getString(R.string.topicContentApiUrl).format(CMApi.myHostApiUrl, arguments?.getString("path"), type, offset)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch {
            PausableDownloader(getString(R.string.topicApiUrl).format(CMApi.myHostApiUrl, arguments?.getString("path"))) { data ->
                withContext(Dispatchers.IO) {
                    if(ad?.exit == true) return@withContext
                    data.inputStream().use { i ->
                        val r = i.reader()
                        Gson().fromJson(r, TopicStructure::class.java)?.apply {
                            if(ad?.exit == true) return@withContext
                            withContext(Dispatchers.Main) withMain@ {
                                if(ad?.exit == true) return@withMain
                                activity?.toolbar?.title = results.title
                                ftttime.text = results.datetime_created
                                fttintro.text = results.intro
                                type = results.type
                            }
                        }
                    }
                }
            }.run()
        }
    }
}
