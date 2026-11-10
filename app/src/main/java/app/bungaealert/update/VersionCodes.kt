package app.bungaealert.update

/**
 * Release identity for in-app updates.
 * The release workflow writes `versionCode: 42` in the GitHub release body
 * and `vc42` in the APK asset name. The body wins when both are present.
 */
object VersionCodes {
    private val bodyPattern = Regex("""(?i)versionCode\s*[:=]\s*(\d+)""")
    private val assetPattern = Regex("""(?i)(?:^|[^A-Za-z0-9])vc(\d+)(?=[^0-9]|$)""")

    fun isNewer(remote: Int, installed: Int): Boolean = remote > installed

    fun isNewer(remote: Long, installed: Long): Boolean = remote > installed

    fun parse(body: String, assetNames: List<String>): Int? {
        bodyPattern.find(body)?.groupValues?.getOrNull(1)?.toIntOrNull()?.takeIf { it > 0 }?.let { return it }
        for (name in assetNames) {
            assetPattern.find(name)?.groupValues?.getOrNull(1)?.toIntOrNull()?.takeIf { it > 0 }?.let { return it }
        }
        return null
    }
}
