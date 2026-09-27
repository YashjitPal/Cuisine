package app.cuisine.data

import androidx.compose.runtime.Immutable
import java.time.LocalDate

const val ME = "me"

@Immutable
data class Author(
    val id: String,
    val name: String,
    val handle: String,
    val avatar: Media?,
    val isMe: Boolean = false,
)

@Immutable
data class Post(
    val id: String,
    /** One author, or everyone in the photos for a collab. */
    val authors: List<Author>,
    val media: List<Media>,
    val caption: String,
    val place: String,
    val takenAt: Long,
    val isUserPost: Boolean,
) {
    val author: Author get() = authors.first()
    val isCollab: Boolean get() = authors.size > 1
    fun features(authorId: String): Boolean = authors.any { it.id == authorId }
}

@Immutable
data class Reel(val id: String, val media: Media, val authors: List<Author>, val caption: String) {
    val author: Author get() = authors.first()
    fun features(authorId: String): Boolean = authors.any { it.id == authorId }
}

enum class StoryKind { Mine, Person, Highlight }

@Immutable
data class Story(
    val id: String,
    val owner: Author,
    val title: String,
    val subtitle: String,
    val items: List<Media>,
    val kind: StoryKind,
    /** Everything in it has been watched; it stays rewatchable for a day with a grey ring. */
    val seen: Boolean,
    /** Where to start watching: the first item not seen yet. */
    val startIndex: Int = 0,
)

@Immutable
data class Feed(
    val me: Author,
    val authors: Map<String, Author>,
    val posts: List<Post>,
    /** You first, then everyone you've added, with or without anything new. */
    val stories: List<Story>,
    val highlights: List<Story>,
    val reels: List<Reel>,
    /** Media id to everyone in it (besides you, unless you're recognised in it). */
    val peopleIn: Map<Long, Set<String>>,
) {
    fun author(id: String): Author = authors[id] ?: me
    fun storyOf(authorId: String): Story? = stories.firstOrNull { it.owner.id == authorId && it.items.isNotEmpty() }

    /** What shows on someone's profile: photos of them, or for you, yours and those you're in. */
    fun mediaOf(authorId: String, library: Library): List<Media> = library.all.filter { m ->
        val people = peopleIn[m.id]
        if (authorId == ME) people == null || ME in people else people != null && authorId in people
    }
}

/** Turns the library plus the user's social data into posts, stories and reels. */
object FeedBuilder {
    private const val MAX_CAROUSEL = 10
    private const val MOMENT_WINDOW = 3 * HOUR
    private const val STORY_WINDOW = 7 * DAY
    private const val REWATCH_WINDOW = DAY
    private const val MAX_STORY = 30

    fun build(library: Library, social: SocialState, peopleIn: Map<Long, Set<String>>): Feed {
        val byId = library.byId
        val now = System.currentTimeMillis()
        val me = Author(
            id = ME,
            name = social.profile.name,
            handle = social.profile.handle,
            avatar = social.profile.avatarId?.let(byId::get),
            isMe = true,
        )
        val authors = LinkedHashMap<String, Author>().apply { put(ME, me) }
        social.people.forEach { p -> authors[p.id] = Author(p.id, p.name, p.handle, p.avatarId?.let(byId::get)) }
        val order = authors.keys.withIndex().associate { it.value to it.index }
        fun authorsOf(ids: Collection<String>): List<Author> =
            ids.filter { it in authors }.sortedBy { order[it] }.map(authors::getValue).ifEmpty { listOf(me) }

        val userPosts = social.posts.mapNotNull { up ->
            val media = up.mediaIds.mapNotNull(byId::get)
            if (media.isEmpty()) {
                null
            } else {
                Post(
                    id = "u:${up.id}",
                    authors = authorsOf(up.authorIds),
                    media = media,
                    caption = up.caption,
                    place = media.first().bucketName.ifBlank { "Library" },
                    takenAt = up.createdAt,
                    isUserPost = true,
                )
            }
        }
        val claimed = social.posts.flatMapTo(HashSet()) { it.mediaIds }
        val visible = library.all.filter { it.id !in social.hidden }
        val candidates = visible.filter { it.id !in claimed }
        val eligible = candidates
            .filter { social.settings.feedIncludesScreenshots || !it.isScreenshot }
            .ifEmpty { candidates }
        val posts = (userPosts + moments(eligible, peopleIn, ::authorsOf, social.captions))
            .sortedByDescending { it.takenAt }

        fun storyFor(id: String, owner: Author, title: String, kind: StoryKind): Story {
            val items = visible
                .filter { m -> now - m.addedAt <= STORY_WINDOW && peopleIn[m.id]?.contains(id) == true }
                .sortedWith(compareBy<Media> { it.addedAt }.thenBy { it.takenAt })
                .takeLast(MAX_STORY)
            val watch = social.watched[id]
            val firstNew = items.indexOfFirst { watch == null || it.addedAt > watch.upTo }
            return when {
                firstNew >= 0 -> Story(id, owner, title, "", items, kind, seen = false, startIndex = firstNew)
                watch != null && now - watch.at <= REWATCH_WINDOW -> Story(id, owner, title, "", items, kind, seen = true)
                else -> Story(id, owner, title, "", emptyList(), kind, seen = true)
            }
        }
        val people = social.people
            .map { p -> storyFor(p.id, authors.getValue(p.id), p.name, StoryKind.Person) to p }
            .sortedWith(
                compareByDescending<Pair<Story, Person>> { (s, _) -> s.items.isNotEmpty() && !s.seen }
                    .thenByDescending { (s, _) -> if (!s.seen) s.items.lastOrNull()?.addedAt ?: 0L else 0L }
                    .thenByDescending { (_, p) -> p.createdAt },
            )
            .map { it.first }
        val stories = listOf(storyFor(ME, me, "You", StoryKind.Mine)) + people

        val reels = visible
            .filter { it.isVideo && (social.settings.feedIncludesScreenshots || !it.isScreenshot) }
            .map { m ->
                val id = "r:${m.id}"
                Reel(id, m, authorsOf(peopleIn[m.id].orEmpty()), social.captions[id].orEmpty())
            }

        return Feed(
            me = me,
            authors = authors,
            posts = posts,
            stories = stories,
            highlights = highlights(library, visible, memories(visible), social),
            reels = reels,
            peopleIn = peopleIn,
        )
    }

    /** A profile's highlights: each album of theirs, played like a story. */
    fun albumHighlights(authorId: String, feed: Feed, library: Library): List<Story> =
        feed.mediaOf(authorId, library)
            .groupBy { it.bucketId }
            .values
            .sortedByDescending { it.size }
            .map { list ->
                val head = list.first()
                val name = head.bucketName.ifBlank { "Library" }
                val id = "album:$authorId:${head.bucketId}"
                Story(
                    id = id,
                    owner = Author(id, name, name.lowercase(), head),
                    title = name,
                    subtitle = plural(list.size, "item"),
                    items = list.take(30),
                    kind = StoryKind.Highlight,
                    seen = false,
                )
            }

    /**
     * Groups consecutive shots from the same album and ~3 hour window, with the same people in
     * them, into carousels. Photos with several people become one collab post.
     */
    private fun moments(
        media: List<Media>,
        peopleIn: Map<Long, Set<String>>,
        authorsOf: (Collection<String>) -> List<Author>,
        captions: Map<String, String>,
    ): List<Post> {
        val posts = ArrayList<Post>()
        var group = ArrayList<Media>()
        var people: Set<String> = emptySet()
        fun flush() {
            if (group.isEmpty()) return
            val head = group.first()
            // Anchored on the oldest shot so the id survives new photos joining the moment.
            val id = "a:${group.last().id}"
            posts += Post(
                id = id,
                authors = authorsOf(people),
                media = group,
                caption = captions[id].orEmpty(),
                place = head.bucketName.ifBlank { "Library" },
                takenAt = head.takenAt,
                isUserPost = false,
            )
            group = ArrayList()
        }
        for (m in media) {
            // Just you counts the same as nobody else: both are your posts.
            val who = peopleIn[m.id].orEmpty().let { if (it.size == 1 && ME in it) emptySet() else it }
            val head = group.firstOrNull()
            val joins = head != null &&
                who == people &&
                m.bucketId == head.bucketId &&
                head.takenAt - m.takenAt <= MOMENT_WINDOW &&
                group.size < MAX_CAROUSEL
            if (!joins) {
                flush()
                people = who
            }
            group += m
        }
        flush()
        return posts
    }

    private fun memories(media: List<Media>): List<Media> {
        val today = LocalDate.now()
        return media.filter {
            val d = it.localDate
            d.year < today.year && d.monthValue == today.monthValue && d.dayOfMonth == today.dayOfMonth
        }.take(24)
    }

    private fun highlights(
        library: Library,
        visible: List<Media>,
        memories: List<Media>,
        social: SocialState,
    ): List<Story> = buildList {
        fun add(id: String, title: String, subtitle: String, items: List<Media>) {
            if (items.isEmpty()) return
            add(Story(id, Author(id, title, "", items.first()), title, subtitle, items, StoryKind.Highlight, seen = false))
        }
        val cutoff = (visible.firstOrNull()?.takenAt ?: 0L) - 14 * DAY
        val recent = visible.filter { !it.isScreenshot && it.takenAt >= cutoff }.take(16)
        if (recent.size >= 3) add("h:recent", "Recent highlights", plural(recent.size, "moment"), recent)
        if (memories.isNotEmpty()) {
            val years = yearsAgo(memories.first().takenAt)
            add("h:memories", "On this day", if (years == 1) "1 year ago" else "$years years ago", memories)
        }
        val favorites = visible.filter { it.id in social.favorites }
        if (favorites.isNotEmpty()) add("h:favorites", "Favourites", plural(favorites.size, "item"), favorites.take(30))
        val videos = visible.filter { it.isVideo }
        if (videos.size >= 2) add("h:videos", "Motion", plural(videos.size, "video"), videos.take(20))
        library.albums
            .filter { it.count >= 3 && !it.cover.isScreenshot }
            .take(4)
            .forEach { album ->
                val items = visible.filter { it.bucketId == album.id }.take(24)
                add("h:album:${album.id}", album.name, plural(album.count, "item"), items)
            }
    }
}
