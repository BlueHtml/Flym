/*
 * Copyright (c) 2012-2018 Frederic Julian
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
 * along with this program.  If not, see <http:></http:>//www.gnu.org/licenses/>.
 */

package net.frju.flym.data.entities

import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.rometools.rome.feed.synd.SyndFeed
import org.jsoup.Jsoup
import kotlinx.android.parcel.Parcelize


val DELIMITERS = arrayOf(" ", "-", "&", ":", "|")

@Parcelize
@Entity(tableName = "feeds",
        indices = [(Index(value = ["groupId"])), (Index(value = ["feedId", "feedLink"], unique = true))],
        foreignKeys = [(ForeignKey(entity = Feed::class,
                parentColumns = ["feedId"],
                childColumns = ["groupId"],
                onDelete = ForeignKey.CASCADE))])
data class Feed(
        @PrimaryKey(autoGenerate = true)
        @ColumnInfo(name = "feedId")
        var id: Long = 0L,
        @ColumnInfo(name = "feedLink")
        var link: String = "",
        @ColumnInfo(name = "feedTitle")
        var title: String? = null,
        @ColumnInfo(name = "feedImageLink")
        var imageLink: String? = null,
        var fetchError: Boolean = false,
        var retrieveFullText: Boolean = false,
        var isGroup: Boolean = false,
        var groupId: Long? = null,
        var displayPriority: Int = 0,
        @Deprecated("Not used anymore")
        var lastManualActionUid: String = "") : Parcelable {

    companion object {

        const val ALL_ENTRIES_ID = -1L

        private val MATERIAL_COLORS = intArrayOf(
                Color.rgb(244, 67, 54),
                Color.rgb(233, 30, 99),
                Color.rgb(156, 39, 176),
                Color.rgb(103, 58, 183),
                Color.rgb(63, 81, 181),
                Color.rgb(33, 150, 243),
                Color.rgb(3, 169, 244),
                Color.rgb(0, 188, 212),
                Color.rgb(0, 150, 136),
                Color.rgb(76, 175, 80),
                Color.rgb(139, 195, 74),
                Color.rgb(205, 220, 57),
                Color.rgb(255, 235, 59),
                Color.rgb(255, 193, 7),
                Color.rgb(255, 152, 0),
                Color.rgb(255, 87, 34),
                Color.rgb(121, 85, 72),
                Color.rgb(158, 158, 158),
                Color.rgb(96, 125, 139)
        )

        fun getLetterDrawable(feedId: Long, feedTitle: String?, rounded: Boolean = false): Drawable {
            val feedName = feedTitle.orEmpty()
            val colorIndex = Math.floorMod(feedId, MATERIAL_COLORS.size.toLong()).toInt()
            return LetterDrawable(getLettersForName(feedName), MATERIAL_COLORS[colorIndex], rounded)
        }

        internal fun getLettersForName(feedName: String): String {
            val split = feedName.split(*DELIMITERS).filter { it != "" }     // filtering empty strings that occur when multiple delimiters are matched, e. g. colon-whitespace: ": "

            val letters = when {
                split.size >= 2 -> String(charArrayOf(split[0][0], split[1][0]))    // first letter of first and second word
                split.isEmpty() -> ""
                else -> split[0][0].toString()
            }

            return letters.toUpperCase()
        }
    }

    fun update(feed: SyndFeed) {
        val currentTitle = title?.trim()
        val incomingTitle = normalizeTitle(feed.title)

        // A null/blank title means it is waiting for the feed's own title.
        // title == link is kept for backward compatibility with feeds added by older Flym versions.
        when {
            currentTitle.isNullOrEmpty() -> title = incomingTitle
            currentTitle == link -> {
                if (incomingTitle != null) {
                    title = incomingTitle
                } else {
                    title = currentTitle
                }
            }
            currentTitle != title -> title = currentTitle
        }

        if (feed.image?.url != null) {
            imageLink = feed.image?.url
        }

        // no error anymore since we just got a feedWithCount
        fetchError = false
    }

    fun getDisplayTitle(): String {
        return title?.takeIf { it.isNotBlank() } ?: link
    }

    fun getLetterDrawable(rounded: Boolean = false): Drawable {
        return getLetterDrawable(id, getDisplayTitle(), rounded)
    }

    private fun normalizeTitle(value: String?): String? {
        if (value.isNullOrBlank()) {
            return null
        }

        val text = Jsoup.parseBodyFragment(value).text().trim()
        return text.takeIf { it.isNotBlank() }
    }
}
