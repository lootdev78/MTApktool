package io.github.lootdev78.mtapktool.feature.explorer.bookmark

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Persistent bookmark model mirroring MT's grouped bookmark workflow. */
data class BookmarkGroup(
    val id: String,
    val name: String,
    val paths: List<String>,
)

data class BookmarkState(
    val groups: List<BookmarkGroup> = listOf(BookmarkGroup(DEFAULT_GROUP_ID, "Standard", emptyList())),
    val addNewToTop: Boolean = false,
    val positionAwareSwipe: Boolean = true,
) {
    companion object {
        const val DEFAULT_GROUP_ID = "default"
    }

    val defaultGroup: BookmarkGroup
        get() = groups.firstOrNull { it.id == DEFAULT_GROUP_ID }
            ?: BookmarkGroup(DEFAULT_GROUP_ID, "Standard", emptyList())
}

object BookmarkStore {
    private const val PREFS = "explorer_bookmarks_v2"
    private const val KEY_STATE = "state"
    private const val LEGACY_PREFS = "explorer_bookmarks"
    private const val LEGACY_PATHS = "paths"

    fun load(context: Context): BookmarkState {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_STATE, null)
        if (!raw.isNullOrBlank()) {
            runCatching { return normalize(decode(raw)) }
        }

        val legacy = context.applicationContext
            .getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
            .getStringSet(LEGACY_PATHS, emptySet())
            .orEmpty()
            .toList()
            .sorted()
        val migrated = BookmarkState(
            groups = listOf(BookmarkGroup(BookmarkState.DEFAULT_GROUP_ID, "Standard", legacy)),
        )
        save(context, migrated)
        return migrated
    }

    fun save(context: Context, state: BookmarkState) {
        val normalized = normalize(state)
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_STATE, encode(normalized))
            .apply()

        // Keep the old default-group storage in sync for older builds/overlays.
        context.applicationContext.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(LEGACY_PATHS, normalized.defaultGroup.paths.toSet())
            .apply()
    }

    fun addPath(state: BookmarkState, path: String, groupId: String): BookmarkState {
        val cleanPath = path.trim()
        if (cleanPath.isBlank()) return state
        val targetId = state.groups.firstOrNull { it.id == groupId }?.id ?: BookmarkState.DEFAULT_GROUP_ID
        val withoutDuplicate = state.groups.map { group -> group.copy(paths = group.paths.filterNot { it == cleanPath }) }
        val updated = withoutDuplicate.map { group ->
            if (group.id != targetId) group
            else group.copy(paths = if (state.addNewToTop) listOf(cleanPath) + group.paths else group.paths + cleanPath)
        }
        return normalize(state.copy(groups = updated))
    }

    fun addPaths(state: BookmarkState, paths: Collection<String>, groupId: String): BookmarkState {
        var result = state
        val ordered = if (state.addNewToTop) paths.toList().asReversed() else paths.toList()
        ordered.forEach { result = addPath(result, it, groupId) }
        return result
    }

    fun removePath(state: BookmarkState, path: String): BookmarkState = normalize(
        state.copy(groups = state.groups.map { it.copy(paths = it.paths.filterNot { entry -> entry == path }) })
    )

    fun addGroup(state: BookmarkState, name: String): BookmarkState {
        val clean = name.trim()
        if (clean.isBlank() || state.groups.any { it.name.equals(clean, ignoreCase = true) }) return state
        val id = "group-${System.currentTimeMillis()}"
        return normalize(state.copy(groups = state.groups + BookmarkGroup(id, clean, emptyList())))
    }

    fun renameGroup(state: BookmarkState, groupId: String, name: String): BookmarkState {
        if (groupId == BookmarkState.DEFAULT_GROUP_ID) return state
        val clean = name.trim()
        if (clean.isBlank() || state.groups.any { it.id != groupId && it.name.equals(clean, ignoreCase = true) }) return state
        return normalize(state.copy(groups = state.groups.map { if (it.id == groupId) it.copy(name = clean) else it }))
    }

    fun deleteGroup(state: BookmarkState, groupId: String): BookmarkState {
        if (groupId == BookmarkState.DEFAULT_GROUP_ID) return state
        val removed = state.groups.firstOrNull { it.id == groupId } ?: return state
        val remaining = state.groups.filterNot { it.id == groupId }.map { group ->
            if (group.id == BookmarkState.DEFAULT_GROUP_ID) {
                group.copy(paths = (group.paths + removed.paths).distinct())
            } else group
        }
        return normalize(state.copy(groups = remaining))
    }

    fun movePath(state: BookmarkState, groupId: String, path: String, delta: Int): BookmarkState {
        val groups = state.groups.map { group ->
            if (group.id != groupId) return@map group
            val index = group.paths.indexOf(path)
            if (index < 0) return@map group
            val target = (index + delta).coerceIn(0, group.paths.lastIndex)
            if (target == index) return@map group
            val list = group.paths.toMutableList()
            val item = list.removeAt(index)
            list.add(target, item)
            group.copy(paths = list)
        }
        return normalize(state.copy(groups = groups))
    }

    fun movePathToGroup(state: BookmarkState, path: String, groupId: String): BookmarkState = addPath(state, path, groupId)

    private fun normalize(state: BookmarkState): BookmarkState {
        val seenIds = mutableSetOf<String>()
        val groups = state.groups
            .filter { seenIds.add(it.id) }
            .map { it.copy(name = it.name.ifBlank { "Lesezeichen" }, paths = it.paths.filter(String::isNotBlank).distinct()) }
            .toMutableList()
        if (groups.none { it.id == BookmarkState.DEFAULT_GROUP_ID }) {
            groups.add(0, BookmarkGroup(BookmarkState.DEFAULT_GROUP_ID, "Standard", emptyList()))
        }
        val defaultIndex = groups.indexOfFirst { it.id == BookmarkState.DEFAULT_GROUP_ID }
        if (defaultIndex > 0) {
            val default = groups.removeAt(defaultIndex)
            groups.add(0, default)
        }
        return state.copy(groups = groups)
    }

    private fun encode(state: BookmarkState): String = JSONObject().apply {
        put("addNewToTop", state.addNewToTop)
        put("positionAwareSwipe", state.positionAwareSwipe)
        put("groups", JSONArray().apply {
            state.groups.forEach { group ->
                put(JSONObject().apply {
                    put("id", group.id)
                    put("name", group.name)
                    put("paths", JSONArray().apply { group.paths.forEach { path -> put(path) } })
                })
            }
        })
    }.toString()

    private fun decode(raw: String): BookmarkState {
        val root = JSONObject(raw)
        val groupsJson = root.optJSONArray("groups") ?: JSONArray()
        val groups = buildList {
            for (i in 0 until groupsJson.length()) {
                val item = groupsJson.optJSONObject(i) ?: continue
                val pathsJson = item.optJSONArray("paths") ?: JSONArray()
                val paths = buildList {
                    for (j in 0 until pathsJson.length()) {
                        pathsJson.optString(j).takeIf { it.isNotBlank() }?.let { add(it) }
                    }
                }
                add(
                    BookmarkGroup(
                        id = item.optString("id").ifBlank { "group-$i" },
                        name = item.optString("name").ifBlank { "Lesezeichen" },
                        paths = paths,
                    )
                )
            }
        }
        return BookmarkState(
            groups = groups,
            addNewToTop = root.optBoolean("addNewToTop", false),
            positionAwareSwipe = root.optBoolean("positionAwareSwipe", true),
        )
    }
}
