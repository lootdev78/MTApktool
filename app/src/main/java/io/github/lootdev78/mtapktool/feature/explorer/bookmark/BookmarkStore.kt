package io.github.lootdev78.mtapktool.feature.explorer.bookmark

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class BookmarkGroup(val id: String, val name: String, val paths: List<String>)
data class BookmarkState(
    val groups: List<BookmarkGroup> = listOf(BookmarkGroup(DEFAULT, "Default", emptyList())),
    val addNewToTop: Boolean = false,
    val positionAwareSwipe: Boolean = true,
) { companion object { const val DEFAULT = "default" } }

object BookmarkStore {
    private const val PREF = "explorer_bookmarks_v2"
    private const val KEY = "state"

    fun load(context: Context): BookmarkState {
        val raw = context.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString(KEY, null)
        if (!raw.isNullOrBlank()) runCatching { return normalize(decode(raw)) }
        val legacy = context.getSharedPreferences("explorer_bookmarks", Context.MODE_PRIVATE)
            .getStringSet("paths", emptySet()).orEmpty().toList().sorted()
        return BookmarkState(listOf(BookmarkGroup(BookmarkState.DEFAULT, "Default", legacy))).also { save(context, it) }
    }

    fun save(context: Context, state: BookmarkState) {
        val normalized = normalize(state)
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString(KEY, encode(normalized)).apply()
        context.getSharedPreferences("explorer_bookmarks", Context.MODE_PRIVATE).edit()
            .putStringSet("paths", normalized.groups.first { it.id == BookmarkState.DEFAULT }.paths.toSet()).apply()
    }

    fun add(state: BookmarkState, paths: Collection<String>, groupId: String): BookmarkState {
        val cleaned = paths.map(String::trim).filter(String::isNotBlank).distinct()
        if (cleaned.isEmpty()) return state
        val selectedGroup = state.groups.firstOrNull { it.id == groupId }?.id ?: BookmarkState.DEFAULT
        val toMove = cleaned.toSet()
        var groups = state.groups.map { group -> group.copy(paths = group.paths.filterNot { it in toMove }) }
        groups = groups.map { group ->
            if (group.id != selectedGroup) group
            else group.copy(paths = if (state.addNewToTop) cleaned + group.paths else group.paths + cleaned)
        }
        return normalize(state.copy(groups = groups))
    }

    fun remove(state: BookmarkState, path: String): BookmarkState = normalize(state.copy(groups = state.groups.map { it.copy(paths = it.paths.filterNot { p -> p == path }) }))
    fun addGroup(state: BookmarkState, name: String): BookmarkState {
        val clean = name.trim()
        if (clean.isBlank() || state.groups.any { it.name.equals(clean, ignoreCase = true) }) return state
        return normalize(state.copy(groups = state.groups + BookmarkGroup("g-${System.currentTimeMillis()}", clean, emptyList())))
    }
    fun renameGroup(state: BookmarkState, id: String, name: String): BookmarkState = if (id == BookmarkState.DEFAULT || name.isBlank()) state else normalize(state.copy(groups = state.groups.map { if (it.id == id) it.copy(name = name.trim()) else it }))
    fun deleteGroup(state: BookmarkState, id: String): BookmarkState {
        if (id == BookmarkState.DEFAULT) return state
        val removed = state.groups.firstOrNull { it.id == id } ?: return state
        return normalize(state.copy(groups = state.groups.filterNot { it.id == id }.map { if (it.id == BookmarkState.DEFAULT) it.copy(paths = (it.paths + removed.paths).distinct()) else it }))
    }
    fun move(state: BookmarkState, groupId: String, path: String, delta: Int): BookmarkState = normalize(state.copy(groups = state.groups.map { g ->
        if (g.id != groupId) g else {
            val i = g.paths.indexOf(path); if (i < 0) g else {
                val t=(i+delta).coerceIn(0,g.paths.lastIndex); val l=g.paths.toMutableList(); val v=l.removeAt(i); l.add(t,v); g.copy(paths=l)
            }
        }
    }))

    private fun normalize(state: BookmarkState): BookmarkState {
        val groups = state.groups.map { it.copy(paths = it.paths.filter(String::isNotBlank).distinct()) }.toMutableList()
        if (groups.none { it.id == BookmarkState.DEFAULT }) groups.add(0, BookmarkGroup(BookmarkState.DEFAULT,"Default",emptyList()))
        val i=groups.indexOfFirst { it.id==BookmarkState.DEFAULT }; if(i>0){ val d=groups.removeAt(i); groups.add(0,d) }
        return state.copy(groups=groups)
    }
    private fun encode(state: BookmarkState)=JSONObject().apply {
        put("top",state.addNewToTop); put("pos",state.positionAwareSwipe); put("groups", JSONArray().apply { state.groups.forEach { g -> put(JSONObject().apply { put("id",g.id); put("name",g.name); put("paths",JSONArray(g.paths)) }) } })
    }.toString()
    private fun decode(raw:String):BookmarkState { val o=JSONObject(raw); val a=o.optJSONArray("groups")?:JSONArray(); val groups=buildList { for(i in 0 until a.length()){ val g=a.getJSONObject(i); val p=g.optJSONArray("paths")?:JSONArray(); add(BookmarkGroup(g.optString("id","g-$i"),g.optString("name","Bookmarks"),buildList{for(j in 0 until p.length()) add(p.getString(j))})) } }; return BookmarkState(groups,o.optBoolean("top"),o.optBoolean("pos",true)) }
}
