package `in`.voxagent.mobile.voice

class PlaybackProgress {
    private var submitted = 0L
    private val marks = ArrayDeque<Pair<String, Long>>()

    @Synchronized
    fun enqueue(frames: Int) {
        submitted += frames
    }

    @Synchronized
    fun mark(name: String) {
        check(marks.size < 64) { "Too many pending playback marks" }
        marks.addLast(name to submitted)
    }

    @Synchronized
    fun drain(played: Long): List<String> {
        val completed = mutableListOf<String>()
        while (marks.firstOrNull()?.second?.let { it <= played } == true) completed.add(
            marks.removeFirst().first
        )
        return completed
    }

    @Synchronized
    fun clear() {
        submitted = 0
        marks.clear()
    }

    @Synchronized fun remaining(played: Long): Long = (submitted - played).coerceAtLeast(0)
}
