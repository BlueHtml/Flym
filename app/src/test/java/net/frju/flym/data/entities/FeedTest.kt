package net.frju.flym.data.entities

import com.rometools.rome.feed.synd.SyndFeedImpl
import org.junit.Assert.assertEquals
import org.junit.Test

class FeedTest {

    @Test
    fun getLettersForName_empty_feed_title() {
        val letters = Feed.getLettersForName("")
        assertEquals("", letters)
    }

    @Test
    fun getLettersForName_single_letter_feed_title() {
        val letters = Feed.getLettersForName("a")
        assertEquals("A", letters)
    }

    @Test
    fun display_title_falls_back_to_link_without_changing_title() {
        val feed = Feed(link = "https://rsshub.app/one")
        assertEquals("https://rsshub.app/one", feed.getDisplayTitle())
        assertEquals(null, feed.title)
    }

    @Test
    fun update_uses_rss_title_for_new_feed() {
        val feed = Feed(link = "https://rsshub.app/one")
        val syndFeed = SyndFeedImpl().apply { title = "  RSSHub One  " }

        feed.update(syndFeed)

        assertEquals("RSSHub One", feed.title)
        assertEquals("RSSHub One", feed.getDisplayTitle())
    }

    @Test
    fun update_keeps_title_null_when_rss_has_no_title() {
        val feed = Feed(link = "https://rsshub.app/one", title = "   ")
        val syndFeed = SyndFeedImpl()

        feed.update(syndFeed)

        assertEquals(null, feed.title)
        assertEquals("https://rsshub.app/one", feed.getDisplayTitle())
    }

    @Test
    fun update_repairs_legacy_url_title() {
        val feed = Feed(link = "https://rsshub.app/one", title = "https://rsshub.app/one")
        val syndFeed = SyndFeedImpl().apply { title = "RSSHub One" }

        feed.update(syndFeed)

        assertEquals("RSSHub One", feed.title)
    }

    @Test
    fun update_does_not_overwrite_custom_title() {
        val feed = Feed(link = "https://rsshub.app/one", title = "My RSS")
        val syndFeed = SyndFeedImpl().apply { title = "RSSHub One" }

        feed.update(syndFeed)

        assertEquals("My RSS", feed.title)
    }

}
