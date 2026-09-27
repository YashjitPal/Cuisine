package app.cuisine.faces

import app.cuisine.data.FaceSample
import app.cuisine.data.Likeness

/** Decides who is in which photo from the face index and what the user taught Cuisine. */
object FaceMatcher {
    /** Cosine similarity at which two MobileFaceNet faces are taken to be the same person. */
    const val SAME_PERSON = 0.52f

    /** A face must look this much more like its best match than the runner-up to count. */
    private const val MARGIN = 0.05f
    private const val MAX_SAMPLES = 24

    /** Media id to the ids of everyone in it: picked by hand, or recognised by face. */
    fun match(index: FaceIndex, likenesses: Map<String, Likeness>): Map<Long, Set<String>> {
        val out = HashMap<Long, MutableSet<String>>()
        likenesses.forEach { (id, l) -> l.picked.forEach { out.getOrPut(it) { HashSet() } += id } }
        val known = likenesses.filterValues { it.samples.isNotEmpty() }.toList()
        if (known.isEmpty()) return out
        for ((mediaId, faces) in index.faces) {
            for (face in faces) {
                var bestId: String? = null
                var best = -1f
                var second = -1f
                for ((id, l) in known) {
                    var s = -1f
                    for (sample in l.samples) s = maxOf(s, sample.embedding similarity face.embedding)
                    if (s > best) {
                        second = best
                        best = s
                        bestId = id
                    } else if (s > second) {
                        second = s
                    }
                }
                val id = bestId ?: continue
                if (best >= SAME_PERSON && best - second >= MARGIN && mediaId !in likenesses.getValue(id).excluded) {
                    out.getOrPut(mediaId) { HashSet() } += id
                }
            }
        }
        return out
    }

    /** Media with a face that looks like any of [samples]; a preview before anything is saved. */
    fun matchesFor(index: FaceIndex, samples: List<FaceSample>): Set<Long> {
        if (samples.isEmpty()) return emptySet()
        return index.faces.filter { (_, faces) ->
            faces.any { face -> samples.any { it.embedding similarity face.embedding >= SAME_PERSON } }
        }.keys
    }

    /**
     * From photos the user picked as someone, the face most likely to be theirs in each: the one
     * that keeps turning up across the picks, so group shots don't teach Cuisine the wrong face.
     */
    fun samplesFrom(picked: List<Pair<Long, List<DetectedFace>>>): List<FaceSample> {
        val withFaces = picked.filter { it.second.isNotEmpty() }
        if (withFaces.isEmpty()) return emptyList()
        val (anchorMedia, anchor) = withFaces
            .flatMap { (id, faces) -> faces.map { id to it } }
            .maxBy { (id, face) ->
                val recurs = withFaces.count { (other, faces) ->
                    other != id && faces.any { it.embedding similarity face.embedding >= SAME_PERSON - 0.08f }
                }
                recurs + face.box.width
            }
        return withFaces.mapNotNull { (id, faces) ->
            val face = faces.maxBy { it.embedding similarity anchor.embedding }
            val close = face === anchor || face.embedding similarity anchor.embedding >= SAME_PERSON - 0.1f
            if (id == anchorMedia || close) FaceSample(id, face.box, face.embedding) else null
        }.take(MAX_SAMPLES)
    }
}
