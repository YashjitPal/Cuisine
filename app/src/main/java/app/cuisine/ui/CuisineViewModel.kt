package app.cuisine.ui

import android.app.Application
import android.content.IntentSender
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.cuisine.CuisineApplication
import app.cuisine.data.FaceSample
import app.cuisine.data.Feed
import app.cuisine.data.FeedBuilder
import app.cuisine.data.Flavor
import app.cuisine.data.Library
import app.cuisine.data.Likeness
import app.cuisine.data.ME
import app.cuisine.data.Media
import app.cuisine.data.Note
import app.cuisine.data.Person
import app.cuisine.data.Profile
import app.cuisine.data.SocialState
import app.cuisine.data.Story
import app.cuisine.data.Tag
import app.cuisine.data.ThemeMode
import app.cuisine.data.UserPost
import app.cuisine.data.Watch
import app.cuisine.data.newId
import app.cuisine.data.remapMedia
import app.cuisine.faces.FaceMatcher
import app.cuisine.faces.IndexProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CuisineViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as CuisineApplication).media
    private val store = (app as CuisineApplication).social
    private val faces = (app as CuisineApplication).faces
    private val resolver = app.contentResolver

    val social: StateFlow<SocialState> = store.state

    val library: StateFlow<Library?> = repo.media
        .map { list -> list?.let(Library::from) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val faceProgress: StateFlow<IndexProgress> = faces.progress

    // Only what face matching depends on, so likes and captions don't re-run it.
    private val likenesses: Flow<Map<String, Likeness>> = store.state
        .map { s -> buildMap { put(ME, s.profile.likeness); s.people.forEach { put(it.id, it.likeness) } } }
        .distinctUntilChanged()

    /** Media id to everyone in it. */
    val peopleIn: StateFlow<Map<Long, Set<String>>> = combine(faces.index, likenesses, FaceMatcher::match)
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    val feed: StateFlow<Feed?> = combine(library, store.state, peopleIn) { lib, s, people ->
        if (lib == null || !s.loaded) null else FeedBuilder.build(lib, s, people)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _deleteRequests = Channel<IntentSender>(Channel.BUFFERED)
    val deleteRequests: Flow<IntentSender> = _deleteRequests.receiveAsFlow()

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    init {
        viewModelScope.launch {
            library.filterNotNull().collect { lib -> store.fingerprintOf = { lib.byId[it]?.fingerprint } }
        }
        // After a restore onto another phone, the same photos have new ids: follow them.
        viewModelScope.launch {
            val lib = library.filterNotNull().first()
            store.state.filter { it.loaded }.first()
            store.update { reconcile(it, lib) }
        }
    }

    fun onAccessGranted() {
        repo.startObserving()
        faces.start()
        viewModelScope.launch { repo.reload() }
    }

    fun refresh() = viewModelScope.launch { repo.reload() }

    fun delete(items: List<Media>) {
        val sender = repo.deleteRequest(items)
        if (sender != null) _deleteRequests.trySend(sender)
    }

    fun onDeleted() {
        repo.refresh()
        toast("Moved to trash")
    }

    fun toast(message: String) {
        _messages.trySend(message)
    }

    // Social -------------------------------------------------------------------------------

    fun toggleLike(id: String, liked: Boolean? = null) = store.update {
        val on = liked ?: (id !in it.likes)
        it.copy(likes = if (on) it.likes + id else it.likes - id)
    }

    fun toggleSave(id: String) {
        val on = id !in store.state.value.saves
        store.update { it.copy(saves = if (on) it.saves + id else it.saves - id) }
        if (on) toast("Saved to your collection")
    }

    fun toggleFavorite(mediaId: Long) = store.update {
        it.copy(favorites = if (mediaId in it.favorites) it.favorites - mediaId else it.favorites + mediaId)
    }

    fun setFavorite(ids: Collection<Long>, favorite: Boolean) = store.update {
        it.copy(favorites = if (favorite) it.favorites + ids else it.favorites - ids.toSet())
    }

    fun setCaption(postId: String, caption: String) = store.update { s ->
        if (postId.startsWith("u:")) {
            val id = postId.removePrefix("u:")
            s.copy(posts = s.posts.map { if (it.id == id) it.copy(caption = caption) else it })
        } else {
            s.copy(captions = if (caption.isBlank()) s.captions - postId else s.captions + (postId to caption.trim()))
        }
    }

    fun addNote(postId: String, text: String) = store.update { s ->
        val note = Note(newId(), text.trim(), System.currentTimeMillis())
        s.copy(notes = s.notes + (postId to (s.notes[postId].orEmpty() + note)))
    }

    fun deleteNote(postId: String, noteId: String) = store.update { s ->
        s.copy(notes = s.notes + (postId to s.notes[postId].orEmpty().filterNot { it.id == noteId }))
    }

    fun hideFromFeed(media: List<Media>) {
        store.update { s -> s.copy(hidden = s.hidden + media.map { it.id }) }
        toast(if (media.size == 1) "Hidden from your feed" else "${media.size} items hidden from your feed")
    }

    fun restoreHidden() = store.update { it.copy(hidden = emptySet()) }

    fun createPost(authorIds: List<String>, media: List<Media>, caption: String) = store.update { s ->
        val post = UserPost(newId(), authorIds.ifEmpty { listOf(ME) }, media.map { it.id }, caption.trim(), System.currentTimeMillis())
        s.copy(posts = s.posts + post)
    }

    fun deletePost(postId: String) = store.update { s ->
        val id = postId.removePrefix("u:")
        s.copy(posts = s.posts.filterNot { it.id == id })
    }

    /** The user has watched [story] up to and including [item]. */
    fun markSeen(story: Story, item: Media) = store.update { s ->
        val upTo = maxOf(s.watched[story.id]?.upTo ?: Long.MIN_VALUE, item.addedAt)
        s.copy(watched = s.watched + (story.id to Watch(System.currentTimeMillis(), upTo)))
    }

    // Tags ---------------------------------------------------------------------------------

    /** Creates a tag, or returns the existing one with the same name. */
    fun createTag(name: String): String {
        val clean = name.trim().replace(Regex("\\s+"), " ")
        store.state.value.tags.firstOrNull { it.name.equals(clean, ignoreCase = true) }?.let { return it.id }
        val tag = Tag(newId(), clean, System.currentTimeMillis())
        store.update { it.copy(tags = it.tags + tag) }
        return tag.id
    }

    fun renameTag(id: String, name: String) = store.update { s ->
        s.copy(tags = s.tags.map { if (it.id == id) it.copy(name = name.trim()) else it })
    }

    fun deleteTag(id: String) = store.update { s ->
        s.copy(
            tags = s.tags.filterNot { it.id == id },
            mediaTags = s.mediaTags.mapValues { it.value - id }.filterValues { it.isNotEmpty() },
        )
    }

    fun setTag(mediaIds: Collection<Long>, tagId: String, on: Boolean) = store.update { s ->
        val next = s.mediaTags.toMutableMap()
        for (id in mediaIds) {
            val tags = next[id].orEmpty().let { if (on) it + tagId else it - tagId }
            if (tags.isEmpty()) next.remove(id) else next[id] = tags
        }
        s.copy(mediaTags = next)
    }

    // People -------------------------------------------------------------------------------

    /** The faces to look for, learned from photos the user picked as being of someone. */
    suspend fun learnFaces(picked: List<Media>): List<FaceSample> {
        if (picked.isEmpty()) return emptyList()
        val found = faces.facesOf(picked.take(40))
        return withContext(Dispatchers.Default) {
            FaceMatcher.samplesFrom(picked.mapNotNull { m -> found[m.id]?.let { m.id to it } })
        }
    }

    /** Photos those faces would be found in, before the person is saved. */
    suspend fun previewMatches(samples: List<FaceSample>): Set<Long> = withContext(Dispatchers.Default) {
        FaceMatcher.matchesFor(faces.index.value, samples)
    }

    fun updateProfile(profile: Profile) = store.update { it.copy(profile = profile) }

    fun savePerson(person: Person) = store.update { s ->
        val exists = s.people.any { it.id == person.id }
        s.copy(people = if (exists) s.people.map { if (it.id == person.id) person else it } else s.people + person)
    }

    fun removePerson(id: String) = store.update { s ->
        s.copy(
            people = s.people.filterNot { it.id == id },
            posts = s.posts.map { p -> p.copy(authorIds = (p.authorIds - id).ifEmpty { listOf(ME) }) },
            watched = s.watched - id,
        )
    }

    /** The user says [personId] isn't in these photos after all. */
    fun notThem(personId: String, mediaIds: Collection<Long>) {
        val ids = mediaIds.toSet()
        fun Likeness.without() = copy(picked = picked - ids, samples = samples.filterNot { it.mediaId in ids }, excluded = excluded + ids)
        store.update { s ->
            if (personId == ME) {
                s.copy(profile = s.profile.copy(likeness = s.profile.likeness.without()))
            } else {
                s.copy(people = s.people.map { if (it.id == personId) it.copy(likeness = it.likeness.without()) else it })
            }
        }
    }

    // Settings -----------------------------------------------------------------------------

    fun setThemeMode(mode: ThemeMode) = store.update { it.copy(settings = it.settings.copy(themeMode = mode)) }

    fun setFlavor(flavor: Flavor) = store.update { it.copy(settings = it.settings.copy(flavor = flavor)) }

    fun setFeedScreenshots(enabled: Boolean) =
        store.update { it.copy(settings = it.settings.copy(feedIncludesScreenshots = enabled)) }

    fun dismissSetup() = store.update { it.copy(settings = it.settings.copy(setupDismissed = true)) }

    // Backup -------------------------------------------------------------------------------

    fun exportBackup(target: Uri) = viewModelScope.launch {
        val ok = withContext(Dispatchers.IO) {
            runCatching { resolver.openOutputStream(target, "wt")!!.bufferedWriter().use { it.write(store.export()) } }.isSuccess
        }
        toast(if (ok) "Backup saved" else "Couldn't save the backup")
    }

    fun importBackup(source: Uri) = viewModelScope.launch {
        val restored = withContext(Dispatchers.IO) {
            runCatching { store.parse(resolver.openInputStream(source)!!.bufferedReader().use { it.readText() }) }.getOrNull()
        }
        if (restored == null) {
            toast("That file isn't a Cuisine backup")
            return@launch
        }
        val lib = library.value
        store.update { if (lib != null) reconcile(restored, lib) else restored }
        toast("Backup restored")
    }

    private fun reconcile(state: SocialState, lib: Library): SocialState {
        if (state.mediaKeys.isEmpty()) return state
        val byKey = lib.all.associateBy { it.fingerprint }
        val moves = HashMap<Long, Long>()
        for ((id, key) in state.mediaKeys) {
            if (lib.byId[id]?.fingerprint == key) continue
            val moved = byKey[key] ?: continue
            if (moved.id != id) moves[id] = moved.id
        }
        return state.remapMedia(moves)
    }
}
