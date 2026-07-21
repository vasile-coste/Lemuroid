package com.swordfish.lemuroid.lib.storage.patch

val SUPPORTED_PATCH_EXTENSIONS = setOf("ips")

private val PATCH_INDEX_REGEX = Regex("""^(.*)\.(\d+)$""")

fun isPatchFile(fileName: String): Boolean {
    return fileName.substringAfterLast('.', "").lowercase() in SUPPORTED_PATCH_EXTENSIONS
}

/**
 * Parses the ROM base name and application order out of a patch file name, supporting stacking
 * multiple patches on the same ROM via a numbered suffix: "game.ips" (order 0), "game.1.ips"
 * (order 1), "game.2.ips" (order 2), etc.
 */
fun patchBaseNameAndOrder(fileName: String): Pair<String, Int> {
    val nameWithoutPatchExtension = fileName.substringBeforeLast('.', "")
    val match = PATCH_INDEX_REGEX.find(nameWithoutPatchExtension)
    return if (match != null) {
        match.groupValues[1] to match.groupValues[2].toInt()
    } else {
        nameWithoutPatchExtension to 0
    }
}
