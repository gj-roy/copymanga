package top.fumiama.copymanga.ui.book

import android.annotation.SuppressLint
import android.content.Context.MODE_PRIVATE
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import kotlinx.android.synthetic.main.app_bar_main.*
import kotlinx.android.synthetic.main.card_book.*
import kotlinx.android.synthetic.main.fragment_book.*
import kotlinx.android.synthetic.main.line_booktandb.*
import kotlinx.coroutines.launch
import top.fumiama.copymanga.MainActivity
import top.fumiama.copymanga.manga.Book
import top.fumiama.copymanga.manga.Reader
import top.fumiama.copymanga.template.general.NoBackRefreshFragment
import top.fumiama.copymanga.tools.ui.Navigate
import top.fumiama.copymanga.ui.comicdl.ComicDlFragment
import top.fumiama.dmzj.copymanga.R
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReference

class BookFragment: NoBackRefreshFragment(R.layout.fragment_book) {
    var isOnPause = false
    var book: Book? = null
    private var mBookHandler: BookHandler? = null


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ComicDlFragment.exit = false
        fbvp?.setPadding(0, 0, 0, navBarHeight)

        if(isFirstInflate) {
            arguments?.apply {
                if (getBoolean("loadJson")) {
                    getString("name")?.let { name ->
                        try {
                            book = Book(name, {
                                return@Book getString(it)
                            }, activity?.getExternalFilesDir("")!!)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            Toast.makeText(context, R.string.null_book, Toast.LENGTH_SHORT).show()
                            findNavController().popBackStack()
                            return
                        }
                    }
                } else getString("path").let {
                    if (it != null) book = Book(it, { id ->
                        return@Book getString(id)
                    }, activity?.getExternalFilesDir("")!!, false)
                    else {
                        findNavController().popBackStack()
                        return
                    }
                }
            }
            mBookHandler = BookHandler(WeakReference(this))
            bookHandler.set(mBookHandler)

            lifecycleScope.launch {
                try {
                    book?.updateInfo()
                } catch (e: Exception) {
                    e.printStackTrace()
                    if(mBookHandler?.exit != false) return@launch
                    Toast.makeText(context, R.string.null_book, Toast.LENGTH_SHORT).show()
                    findNavController().popBackStack()
                    return@launch
                }
                Log.d("MyBF", "read path: ${book?.path}")
                for (i in 1..3) {
                    mBookHandler?.sendEmptyMessage(i)
                }
                try {
                    book?.updateVolumes {
                        mBookHandler?.sendEmptyMessage(10)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    if(mBookHandler?.exit != false) return@launch
                    Toast.makeText(context, R.string.null_volume, Toast.LENGTH_SHORT).show()
                    findNavController().popBackStack()
                    return@launch
                }
            }
        } else {
            bookHandler.set(mBookHandler)
        }
    }

    override fun onResume() {
        super.onResume()
        isOnPause = false
        bookHandler.set(mBookHandler)
        activity?.apply {
            toolbar.title = book?.name
        }
        setStartRead()
    }

    override fun onPause() {
        super.onPause()
        isOnPause = true
    }

    override fun onDestroy() {
        super.onDestroy()
        mBookHandler?.exit = true
        book?.exit = true
        bookHandler.set(null)
    }

    fun setStartRead() {
        if(mBookHandler?.chapterNames?.isNotEmpty() == true) activity?.apply {
            book?.name?.let { name ->
                getPreferences(MODE_PRIVATE).getInt(name, -1).let { p ->
                    this@BookFragment.lbbstart.apply {
                        var i = 0
                        if(p >= 0) {
                            text = mBookHandler!!.chapterNames[p]
                            i = p
                        }
                        setOnClickListener {
                            mBookHandler?.urlArray?.let {
                                Reader.start2viewManga(name, i, it)
                            }
                        }
                    }
                }
            }
        }
    }

    private suspend fun queryCollect() {
        MainActivity.shelf?.query(book?.path!!)?.let { b ->
            mBookHandler?.collect = b.results?.collect?:-2
            Log.d("MyBF", "get collect of ${book?.path} = ${mBookHandler?.collect}")
            tic.text = b.results?.browse?.chapter_name?.let { name ->
                getString(R.string.text_format_cloud_read_to).format(name)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    fun setAddToShelf() {
        if(mBookHandler?.chapterNames?.isNotEmpty() != true) return
        lifecycleScope.launch {
            queryCollect()
            mBookHandler?.collect?.let { collect ->
                if (collect > 0) {
                    this@BookFragment.lbbsub.setText(R.string.button_sub_subscribed)
                }
            }
            book?.uuid?.let { uuid ->
                this@BookFragment.lbbsub.setOnClickListener {
                    lifecycleScope.launch clickLaunch@ {
                        if (this@BookFragment.lbbsub.text != getString(R.string.button_sub)) {
                            mBookHandler?.collect?.let { collect ->
                                if (collect < 0) return@clickLaunch
                                val re = MainActivity.shelf?.del(collect)
                                Toast.makeText(context, re, Toast.LENGTH_SHORT).show()
                                if (re == "请求成功") {
                                    this@BookFragment.lbbsub.setText(R.string.button_sub)
                                }
                            }
                            return@clickLaunch
                        }
                        val re = MainActivity.shelf?.add(uuid)
                        Toast.makeText(context, re, Toast.LENGTH_SHORT).show()
                        if (re == "修改成功") {
                            queryCollect()
                            this@BookFragment.lbbsub.setText(R.string.button_sub_subscribed)
                        }
                    }
                }
            }
        }
    }

    fun navigate2dl() {
        val bundle = Bundle()
        Log.d("MyBF", "nav2: ${arguments?.getString("path")?:"null"}")
        bundle.putString("path", arguments?.getString("path")?:"null")
        bundle.putString("name", book!!.name!!)
        if(book?.volumes != null && book?.json != null) {
            bundle.putString("loadJson", book!!.json)
        }
        findNavController().let {
            Navigate.safeNavigateTo(it, R.id.action_nav_book_to_nav_group, bundle)
        }
    }

    companion object {
        var bookHandler: AtomicReference<BookHandler?> = AtomicReference(null)
    }
}
