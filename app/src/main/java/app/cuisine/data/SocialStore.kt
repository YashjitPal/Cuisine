package app.cuisine.data

import android.content.Context
import androidx.compose.runtime.Immutable
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

enum class ThemeMode { System, Light, Dark }

enum class Flavor(val label: String) {
    Wallpaper("Wallpaper"),
    Paprika("Paprika"),
    Saffron("Saffron"),
    Basil("Basil"),
    Blueberry("Blueberry"),
    Plum("Plum"),
}

@Immutable
data class Settings(
    val themeMode: ThemeMode = ThemeMode.System,
    val flavor: Flavor = Flavor.Wallpaper,
    val feedIncludesScreenshots: Boolean = false,
    val setupDismissed: Boolean = false,
)

/** Where a face sits in a photo, as fractions of the photo's width and height. */
@Immutable
data class FaceBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/** A face the user confirmed as someone; every other face is compared against these. */
@Immutable
class FaceSample(val mediaId: Long, val box: FaceBox, val embedding: FloatArray)

/** Everything Cuisine knows about which photos someone is in. */
@Immutable
data class Likeness(
    /** Photos the user picked as being of them. They always count, faces or not. */
    val picked: List<Long> = emptyList(),
    /** Faces taken from [picked] that face recognition looks for. */
    val samples: List<FaceSample> = emptyList(),
    /** Photos the user said they aren't in, whatever recognition thinks. */
    val excluded: Set<Long> = emptySet(),
)

@Immutable
data class Profile(
    val name: String = "You",
    val handle: String = "you",
    val bio: String = "",
    val avatarId: Long? = null,
    val likeness: Likeness = Likeness(),
) {
    val isDefault: Boolean get() = name == "You" && handle == "you" && avatarId == null && bio.isEmpty()
}

/** Someone the user added. Their photos come from the user's own library. */
@Immutable
data class Person(
    val id: String,
    val name: String,
    val handle: String,
    val avatarId: Long?,
    val likeness: Likeness,
    val createdAt: Long,
)

@Immutable
data class UserPost(
    val id: String,
    /** One author, or several for a collab. */
    val authorIds: List<String>,
    val mediaIds: List<Long>,
    val caption: String,
    val createdAt: Long,
)

@Immutable
data class Note(val id: String, val text: String, val createdAt: Long)

@Immutable
data class Tag(val id: String, val name: String, val createdAt: Long)

/** The user watched a story up to the item added at [upTo] (epoch ms), at time [at]. */
@Immutable
data class Watch(val at: Long, val upTo: Long)

@Immutable
data class SocialState(
    val loaded: Boolean = false,
    val profile: Profile = Profile(),
    val people: List<Person> = emptyList(),
    val posts: List<UserPost> = emptyList(),
    /** Post and reel ids. */
    val likes: Set<String> = emptySet(),
    val saves: Set<String> = emptySet(),
    /** Media ids hearted from the library, viewer and pinboard. */
    val favorites: Set<Long> = emptySet(),
    val captions: Map<String, String> = emptyMap(),
    val notes: Map<String, List<Note>> = emptyMap(),
    val hidden: Set<Long> = emptySet(),
    val tags: List<Tag> = emptyList(),
    /** Media id to the ids of its tags. */
    val mediaTags: Map<Long, Set<String>> = emptyMap(),
    /** Story id to how far the user got. */
    val watched: Map<String, Watch> = emptyMap(),
    /** Last known fingerprint of every media id referenced above; see [Media.fingerprint]. */
    val mediaKeys: Map<Long, String> = emptyMap(),
    val settings: Settings = Settings(),
)

fun newId(): String = UUID.randomUUID().toString().substring(0, 12)

/** Persists everything social about the library as a small JSON document in app storage. */
class SocialStore(context: Context) {
    private val file = File(context.filesDir, FILE_NAME)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(SocialState())
    val state: StateFlow<SocialState> = _state.asStateFlow()

    /** Supplies fingerprints for media currently in the library; set once the library loads. */
    @Volatile
    var fingerprintOf: (Long) -> String? = { null }

    private val saveRequests = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    init {
        scope.launch {
            val stored = runCatching { if (file.exists()) decodeState(JSONObject(file.readText())) else null }.getOrNull()
            _state.update { (stored ?: it).copy(loaded = true) }
        }
        @OptIn(FlowPreview::class)
        scope.launch {
            saveRequests.debounce(300).collect { runCatching { write(_state.value) } }
        }
    }

    fun update(transform: (SocialState) -> SocialState) {
        _state.update(transform)
        saveRequests.tryEmit(Unit)
    }

    /** Everything, including photo fingerprints, as a document another install can restore. */
    fun export(): String = encodeState(withKeys(_state.value)).toString(2)

    /** Parses a document from [export]; throws if it isn't one. */
    fun parse(json: String): SocialState {
        val o = JSONObject(json)
        require(o.optString("app") == "cuisine") { "Not a Cuisine backup" }
        return decodeState(o).copy(loaded = true)
    }

    private fun withKeys(state: SocialState): SocialState {
        val keys = HashMap<Long, String>()
        for (id in state.referencedMedia()) {
            (fingerprintOf(id) ?: state.mediaKeys[id])?.let { keys[id] = it }
        }
        return state.copy(mediaKeys = keys)
    }

    private fun write(state: SocialState) {
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.writeText(encodeState(withKeys(state)).toString())
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    companion object {
        /** Also named in the backup rules. */
        const val FILE_NAME = "cuisine.json"
    }
}

/** Every media id this state points at. */
fun SocialState.referencedMedia(): Set<Long> = buildSet {
    fun likeness(l: Likeness) {
        addAll(l.picked)
        l.samples.forEach { add(it.mediaId) }
        addAll(l.excluded)
    }
    profile.avatarId?.let(::add)
    likeness(profile.likeness)
    people.forEach { p ->
        p.avatarId?.let(::add)
        likeness(p.likeness)
    }
    posts.forEach { addAll(it.mediaIds) }
    addAll(favorites)
    addAll(hidden)
    addAll(mediaTags.keys)
    (likes + saves + captions.keys + notes.keys).forEach { id -> embeddedMediaId(id)?.let(::add) }
}

/**
 * Moves references from old media ids to new ones, for a restore onto a device where the same
 * photos have different MediaStore ids.
 */
fun SocialState.remapMedia(map: Map<Long, Long>): SocialState {
    if (map.isEmpty()) return this
    fun id(old: Long) = map[old] ?: old
    fun ids(old: Collection<Long>) = old.map(::id)
    fun post(old: String): String {
        val media = embeddedMediaId(old) ?: return old
        return old.substring(0, 2) + id(media)
    }
    fun likeness(l: Likeness) = Likeness(
        picked = ids(l.picked),
        samples = l.samples.map { FaceSample(id(it.mediaId), it.box, it.embedding) },
        excluded = ids(l.excluded).toSet(),
    )
    return copy(
        profile = profile.copy(avatarId = profile.avatarId?.let(::id), likeness = likeness(profile.likeness)),
        people = people.map { it.copy(avatarId = it.avatarId?.let(::id), likeness = likeness(it.likeness)) },
        posts = posts.map { it.copy(mediaIds = ids(it.mediaIds)) },
        likes = likes.mapTo(HashSet(), ::post),
        saves = saves.mapTo(HashSet(), ::post),
        favorites = ids(favorites).toSet(),
        captions = captions.mapKeys { post(it.key) },
        notes = notes.mapKeys { post(it.key) },
        hidden = ids(hidden).toSet(),
        mediaTags = mediaTags.mapKeys { id(it.key) },
        mediaKeys = mediaKeys.mapKeys { id(it.key) },
    )
}

/** Moment and reel ids ("a:123", "r:123") are anchored on a media id. */
private fun embeddedMediaId(postId: String): Long? =
    if (postId.startsWith("a:") || postId.startsWith("r:")) postId.substring(2).toLongOrNull() else null

private fun encodeState(s: SocialState): JSONObject = JSONObject().apply {
    put("app", "cuisine")
    put("version", 2)
    put(
        "profile",
        JSONObject()
            .put("name", s.profile.name)
            .put("handle", s.profile.handle)
            .put("bio", s.profile.bio)
            .put("avatarId", s.profile.avatarId ?: JSONObject.NULL)
            .put("likeness", encodeLikeness(s.profile.likeness)),
    )
    put(
        "people",
        JSONArray(
            s.people.map { p ->
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("handle", p.handle)
                    .put("avatarId", p.avatarId ?: JSONObject.NULL)
                    .put("likeness", encodeLikeness(p.likeness))
                    .put("createdAt", p.createdAt)
            },
        ),
    )
    put(
        "posts",
        JSONArray(
            s.posts.map { p ->
                JSONObject()
                    .put("id", p.id)
                    .put("authorIds", JSONArray(p.authorIds))
                    .put("mediaIds", JSONArray(p.mediaIds))
                    .put("caption", p.caption)
                    .put("createdAt", p.createdAt)
            },
        ),
    )
    put("likes", JSONArray(s.likes.toList()))
    put("saves", JSONArray(s.saves.toList()))
    put("favorites", JSONArray(s.favorites.toList()))
    put("hidden", JSONArray(s.hidden.toList()))
    put("captions", JSONObject(s.captions))
    put(
        "notes",
        JSONObject().apply {
            s.notes.forEach { (post, notes) ->
                put(
                    post,
                    JSONArray(
                        notes.map { JSONObject().put("id", it.id).put("text", it.text).put("createdAt", it.createdAt) },
                    ),
                )
            }
        },
    )
    put(
        "tags",
        JSONArray(s.tags.map { JSONObject().put("id", it.id).put("name", it.name).put("createdAt", it.createdAt) }),
    )
    put(
        "mediaTags",
        JSONObject().apply { s.mediaTags.forEach { (media, tags) -> put(media.toString(), JSONArray(tags.toList())) } },
    )
    put(
        "watched",
        JSONObject().apply { s.watched.forEach { (story, w) -> put(story, JSONObject().put("at", w.at).put("upTo", w.upTo)) } },
    )
    put("mediaKeys", JSONObject().apply { s.mediaKeys.forEach { (id, key) -> put(id.toString(), key) } })
    put(
        "settings",
        JSONObject()
            .put("themeMode", s.settings.themeMode.name)
            .put("flavor", s.settings.flavor.name)
            .put("feedIncludesScreenshots", s.settings.feedIncludesScreenshots)
            .put("setupDismissed", s.settings.setupDismissed),
    )
}

private fun encodeLikeness(l: Likeness): JSONObject = JSONObject()
    .put("picked", JSONArray(l.picked))
    .put("excluded", JSONArray(l.excluded.toList()))
    .put(
        "samples",
        JSONArray(
            l.samples.map { s ->
                JSONObject()
                    .put("mediaId", s.mediaId)
                    .put("box", JSONArray(listOf(s.box.left, s.box.top, s.box.right, s.box.bottom).map(Float::toDouble)))
                    .put("embedding", encodeFloats(s.embedding))
            },
        ),
    )

private fun decodeLikeness(o: JSONObject?, legacyPicked: List<Long> = emptyList()): Likeness {
    if (o == null) return Likeness(picked = legacyPicked)
    return Likeness(
        picked = o.optJSONArray("picked").longs(),
        excluded = o.optJSONArray("excluded").longs().toSet(),
        samples = o.optJSONArray("samples").objects().mapNotNull { s ->
            val box = s.optJSONArray("box") ?: return@mapNotNull null
            val embedding = runCatching { decodeFloats(s.getString("embedding")) }.getOrNull() ?: return@mapNotNull null
            FaceSample(
                mediaId = s.optLong("mediaId"),
                box = FaceBox(box.optDouble(0).toFloat(), box.optDouble(1).toFloat(), box.optDouble(2).toFloat(), box.optDouble(3).toFloat()),
                embedding = embedding,
            )
        },
    )
}

private fun encodeFloats(values: FloatArray): String {
    val buffer = ByteBuffer.allocate(values.size * 4).order(ByteOrder.LITTLE_ENDIAN)
    buffer.asFloatBuffer().put(values)
    return Base64.getEncoder().encodeToString(buffer.array())
}

private fun decodeFloats(text: String): FloatArray {
    val buffer = ByteBuffer.wrap(Base64.getDecoder().decode(text)).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
    return FloatArray(buffer.remaining()).also { buffer.get(it) }
}

private fun decodeState(o: JSONObject): SocialState {
    val profile = o.optJSONObject("profile")?.let {
        Profile(
            name = it.optString("name", "You"),
            handle = it.optString("handle", "you"),
            bio = it.optString("bio"),
            avatarId = it.optLongOrNull("avatarId"),
            likeness = decodeLikeness(it.optJSONObject("likeness")),
        )
    } ?: Profile()
    val people = o.optJSONArray("people").objects().map {
        Person(
            id = it.getString("id"),
            name = it.optString("name"),
            handle = it.optString("handle"),
            avatarId = it.optLongOrNull("avatarId"),
            // Version 1 stored a plain list of the person's photos.
            likeness = decodeLikeness(it.optJSONObject("likeness"), legacyPicked = it.optJSONArray("mediaIds").longs()),
            createdAt = it.optLong("createdAt"),
        )
    }
    val posts = o.optJSONArray("posts").objects().map {
        UserPost(
            id = it.getString("id"),
            authorIds = it.optJSONArray("authorIds")?.strings()?.ifEmpty { null } ?: listOf(it.optString("authorId", ME)),
            mediaIds = it.optJSONArray("mediaIds").longs(),
            caption = it.optString("caption"),
            createdAt = it.optLong("createdAt"),
        )
    }
    val notes = o.optJSONObject("notes")?.let { n ->
        n.keys().asSequence().associateWith { key ->
            n.optJSONArray(key).objects().map { Note(it.getString("id"), it.optString("text"), it.optLong("createdAt")) }
        }
    }.orEmpty()
    val tags = o.optJSONArray("tags").objects().map { Tag(it.getString("id"), it.optString("name"), it.optLong("createdAt")) }
    val mediaTags = o.optJSONObject("mediaTags")?.let { m ->
        m.keys().asSequence().mapNotNull { key ->
            val id = key.toLongOrNull() ?: return@mapNotNull null
            id to m.optJSONArray(key).strings().toSet()
        }.toMap()
    }.orEmpty()
    val watched = o.optJSONObject("watched")?.let { w ->
        w.keys().asSequence().associateWith { key ->
            val v = w.optJSONObject(key)
            Watch(at = v?.optLong("at") ?: 0L, upTo = v?.optLong("upTo") ?: 0L)
        }
    }.orEmpty()
    val mediaKeys = o.optJSONObject("mediaKeys")?.let { k ->
        k.keys().asSequence().mapNotNull { key -> key.toLongOrNull()?.let { it to k.optString(key) } }.toMap()
    }.orEmpty()
    val settings = o.optJSONObject("settings")?.let {
        Settings(
            themeMode = enumOrDefault(it.optString("themeMode"), ThemeMode.System),
            flavor = enumOrDefault(it.optString("flavor"), Flavor.Wallpaper),
            feedIncludesScreenshots = it.optBoolean("feedIncludesScreenshots"),
            setupDismissed = it.optBoolean("setupDismissed"),
        )
    } ?: Settings()
    return SocialState(
        profile = profile,
        people = people,
        posts = posts,
        likes = o.optJSONArray("likes").strings().toSet(),
        saves = o.optJSONArray("saves").strings().toSet(),
        favorites = o.optJSONArray("favorites").longs().toSet(),
        captions = o.optJSONObject("captions").stringMap(),
        notes = notes,
        hidden = o.optJSONArray("hidden").longs().toSet(),
        tags = tags,
        mediaTags = mediaTags,
        watched = watched,
        mediaKeys = mediaKeys,
        settings = settings,
    )
}

private inline fun <reified T : Enum<T>> enumOrDefault(name: String, default: T): T =
    enumValues<T>().firstOrNull { it.name == name } ?: default

private fun JSONObject.optLongOrNull(key: String): Long? = if (!has(key) || isNull(key)) null else optLong(key)

private fun JSONArray?.objects(): List<JSONObject> =
    if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }

private fun JSONArray?.longs(): List<Long> =
    if (this == null) emptyList() else (0 until length()).map { optLong(it) }

private fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).map { optString(it) }

private fun JSONObject?.stringMap(): Map<String, String> =
    if (this == null) emptyMap() else keys().asSequence().associateWith { optString(it) }
