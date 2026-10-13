package tachiyomi.domain.release.interactor

/**
 * Version of a tiger-git2022/mihon release (fork-only).
 *
 * Fork releases are tagged `v<upstream version>-t<build number>`, e.g. `v0.21.2-t5`. A version
 * without the `-t` suffix (an upstream version, or a local build) has build number 0, so it is
 * older than any fork build of the same upstream version.
 */
internal data class ForkVersion(
    val upstream: List<Int>,
    val build: Int,
) : Comparable<ForkVersion> {

    override fun compareTo(other: ForkVersion): Int {
        val size = maxOf(upstream.size, other.upstream.size)
        for (index in 0..<size) {
            val result = upstream.getOrElse(index) { 0 }.compareTo(other.upstream.getOrElse(index) { 0 })
            if (result != 0) return result
        }
        return build.compareTo(other.build)
    }

    companion object {
        private val VERSION_REGEX = """^v?(\d+(?:\.\d+)*)(?:-t(\d+))?""".toRegex()

        fun parse(version: String): ForkVersion? {
            val match = VERSION_REGEX.find(version.trim()) ?: return null
            return ForkVersion(
                upstream = match.groupValues[1].split(".").map(String::toInt),
                build = match.groupValues[2].toIntOrNull() ?: 0,
            )
        }

        /**
         * Whether [versionTag] (a release tag) is newer than [versionName] (the installed version).
         */
        fun isNewer(versionName: String, versionTag: String): Boolean {
            val current = parse(versionName) ?: return false
            val latest = parse(versionTag) ?: return false
            return latest > current
        }
    }
}
