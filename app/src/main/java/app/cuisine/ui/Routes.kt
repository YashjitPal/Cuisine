package app.cuisine.ui

sealed interface Route {
    data object Home : Route
    data class Viewer(val source: ViewerSource, val startId: Long) : Route
    data class Stories(val source: StorySource, val startId: String) : Route
    data class Profile(val authorId: String, val tab: ProfileTab = ProfileTab.Posts) : Route
    data object NewPost : Route

    /** A `null` [personId] adds someone new; `ME` edits your own profile. */
    data class EditProfile(val personId: String?) : Route
}

enum class ProfileTab { Posts, Reels, Saved, Liked }

/** Where the viewer's pager gets its items from. */
sealed interface ViewerSource {
    data object All : ViewerSource
    data object Photos : ViewerSource
    data object Videos : ViewerSource
    data class Gallery(val filter: GalleryFilter) : ViewerSource
    data class Items(val ids: List<Long>) : ViewerSource
}

/** The pills above the Photos tab. */
sealed interface GalleryFilter {
    data object All : GalleryFilter
    data object Favorites : GalleryFilter
    data class Tag(val id: String) : GalleryFilter
    data class Album(val id: Long, val name: String) : GalleryFilter
}

sealed interface StorySource {
    data object Feed : StorySource
    data object Highlights : StorySource

    /** A profile's highlights: their albums, played like stories. */
    data class Albums(val authorId: String) : StorySource
}
