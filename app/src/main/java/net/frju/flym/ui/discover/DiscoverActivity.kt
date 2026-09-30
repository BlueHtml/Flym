package net.frju.flym.ui.discover

import net.frju.flym.utils.*
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.URLUtil
import android.widget.AutoCompleteTextView
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.FragmentTransaction
import net.fred.feedex.R
import net.frju.flym.App
import net.frju.flym.data.entities.Feed
import net.frju.flym.data.entities.SearchFeedResult
import net.frju.flym.service.FetcherService
import net.frju.flym.utils.setupTheme
import java.util.Timer
import java.util.TimerTask


class DiscoverActivity : AppCompatActivity(), FeedManagementInterface {

    companion object {

        @JvmStatic
        fun newInstance(context: Context) = context.startActivity<DiscoverActivity>()

        @JvmStatic
        fun newInstance(context: Context, query: String) =
                context.startActivity<DiscoverActivity>(FeedSearchFragment.ARG_QUERY to query)
    }

    private var searchInput: AutoCompleteTextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        setupTheme()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_feed_search)
        this.initSearchInputs()

        val query = savedInstanceState?.getString(FeedSearchFragment.ARG_QUERY) ?: intent.getStringExtra(FeedSearchFragment.ARG_QUERY)
        if (!query.isNullOrEmpty()) {
            this.searchForFeed(query)
        } else {
            this.goDiscover()
        }
    }

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putString(FeedSearchFragment.ARG_QUERY, searchInput?.text?.toString())
    }

    private fun initSearchInputs() {
        var timer = Timer()
        searchInput = this.findViewById(R.id.et_search_input)
        searchInput?.let { searchInput ->

            // Handle IME Options Search Action
            searchInput.onEditorAction { _, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    val term = searchInput.text.toString().trim()
                    if (term.isNotEmpty()) {
                        goSearch(term)
                    }
                    true
                } else
                    false
            }

            // Handle search after N ms pause after input
            searchInput.textChangedListener {
                afterTextChanged {
                    val term = searchInput.text.toString().trim()
                    if (term.isNotEmpty()) {
                        timer.cancel()
                        timer = Timer()
                        timer.schedule(object : TimerTask() {
                            override fun run() {
                                goSearch(term)
                            }
                        }, 400)
                    }
                }
            }

            // Handle manually adding URL
            this.findViewById<Button>(R.id.btn_add_feed).onClick {
                val text = searchInput.text.toString().trim()
                if (URLUtil.isNetworkUrl(text)) {
                    // An URL entered manually must not become the feed title.
                    addFeed(searchInput, "", text)
                    searchInput.setText("")
                }
            }
        }
    }

    private fun goSearch(query: String) {
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fcv_discover_fragments)
        if (currentFragment is FeedSearchFragment) {
            currentFragment.search(query)
        } else {
            val fragment = FeedSearchFragment.newInstance(query)
            supportFragmentManager
                    .beginTransaction()
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                    .replace(R.id.fcv_discover_fragments, fragment, FeedSearchFragment.TAG)
                    .addToBackStack(DiscoverFragment.TAG)
                    .commitAllowingStateLoss()
        }
    }

    private fun goDiscover() {
        val currentFragment = supportFragmentManager.findFragmentById(R.id.fcv_discover_fragments)
        if (currentFragment !is DiscoverFragment) {
            val fragment = DiscoverFragment.newInstance()
            supportFragmentManager
                    .beginTransaction()
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                    .replace(R.id.fcv_discover_fragments, fragment, DiscoverFragment.TAG)
                    .commitAllowingStateLoss()
        }
    }

    override fun searchForFeed(query: String) {
        searchInput?.setText(query)
    }

    override fun addFeed(view: View, title: String, link: String) {
        doAsync {
            val normalizedLink = link.trim()
            if (normalizedLink.isEmpty()) {
                return@doAsync
            }

            if (App.db.feedDao().findByLink(normalizedLink) != null) {
                uiThread {
                    view.snackbar(R.string.feed_already_added)
                }
                return@doAsync
            }

            val normalizedTitle = title.trim().takeIf { it.isNotEmpty() }
            val feedToAdd = Feed(link = normalizedLink, title = normalizedTitle)
            App.db.feedDao().insert(feedToAdd)
            val insertedFeed = App.db.feedDao().findByLink(normalizedLink)

            // Refresh the new feed immediately so a manually-entered URL gets its RSS/Atom title.
            insertedFeed?.id?.let { feedId ->
                try {
                    android.util.Log.i("DiscoverActivity", "Starting refresh for newly added feed: id=$feedId, url=$normalizedLink")
                    App.context.startService(
                            android.content.Intent(App.context, FetcherService::class.java)
                                    .setAction(FetcherService.ACTION_REFRESH_FEEDS)
                                    .putExtra(FetcherService.EXTRA_FEED_ID, feedId)
                    )
                } catch (t: Throwable) {
                    android.util.Log.e("DiscoverActivity", "Unable to start FetcherService for newly added feed", t)
                }
            }

            uiThread {
                view.snackbar(R.string.feed_added)
            }
        }
    }

    override fun deleteFeed(view: View, feed: SearchFeedResult) {
        doAsync {
            App.db.feedDao().deleteByLink(feed.link)
        }
    }

    override fun previewFeed(view: View, feed: SearchFeedResult) {
        TODO("Not yet implemented")
    }
}
