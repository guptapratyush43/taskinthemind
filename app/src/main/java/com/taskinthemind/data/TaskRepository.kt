package com.taskinthemind.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class Task(
    val id: Int,
    val title: String,
    val description: String,
    val triggerAt: Long,
    val done: Boolean = false,
    /** The custom list this task belongs to; null means it only shows under All tasks. */
    val listId: Int? = null
)

data class TaskList(val id: Int, val name: String)

/**
 * Tasks live in one JSON array in SharedPreferences. The list is small and
 * read by the alarm receiver in a cold process, so a database would be
 * heavier than it is worth.
 */
object TaskRepository {
    private const val KEY_TASKS = "tasks"
    private const val KEY_NEXT_ID = "next_id"
    private const val KEY_LISTS = "lists"
    private const val KEY_NEXT_LIST_ID = "next_list_id"

    private lateinit var prefs: SharedPreferences
    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks: StateFlow<List<Task>> = _tasks.asStateFlow()
    private val _lists = MutableStateFlow<List<TaskList>>(emptyList())
    val lists: StateFlow<List<TaskList>> = _lists.asStateFlow()

    /** Fired after every change; the backup uses it to schedule an upload. */
    var onChanged: (() -> Unit)? = null

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.getSharedPreferences("tasks", Context.MODE_PRIVATE)
        _tasks.value = load()
        _lists.value = loadLists()
    }

    fun get(id: Int): Task? = _tasks.value.firstOrNull { it.id == id }

    @Synchronized
    fun newId(): Int {
        val id = prefs.getInt(KEY_NEXT_ID, 1)
        prefs.edit().putInt(KEY_NEXT_ID, id + 1).apply()
        return id
    }

    // --- lists ------------------------------------------------------------

    fun peekNextListId(): Int = prefs.getInt(KEY_NEXT_LIST_ID, 1)

    @Synchronized
    fun createList(name: String): Int {
        val id = peekNextListId()
        prefs.edit().putInt(KEY_NEXT_LIST_ID, id + 1).apply()
        saveLists(_lists.value + TaskList(id, name.trim()))
        return id
    }

    @Synchronized
    fun renameList(id: Int, name: String) = saveLists(_lists.value.map { if (it.id == id) it.copy(name = name.trim()) else it })

    /** Removes the list; its tasks stay, back under All tasks. */
    @Synchronized
    fun deleteList(id: Int) {
        saveLists(_lists.value.filterNot { it.id == id })
        if (_tasks.value.any { it.listId == id }) save(_tasks.value.map { if (it.listId == id) it.copy(listId = null) else it })
    }

    @Synchronized
    fun replaceLists(list: List<TaskList>, nextId: Int) {
        prefs.edit().putInt(KEY_NEXT_LIST_ID, maxOf(nextId, (list.maxOfOrNull { it.id } ?: 0) + 1, peekNextListId())).apply()
        saveLists(list)
    }

    private fun saveLists(list: List<TaskList>) {
        _lists.value = list
        val array = JSONArray()
        list.forEach { array.put(JSONObject().put("id", it.id).put("name", it.name)) }
        prefs.edit().putString(KEY_LISTS, array.toString()).apply()
        onChanged?.invoke()
    }

    private fun loadLists(): List<TaskList> = runCatching {
        val array = JSONArray(prefs.getString(KEY_LISTS, null) ?: return emptyList())
        List(array.length()) { i -> array.getJSONObject(i).let { TaskList(it.getInt("id"), it.getString("name")) } }
    }.getOrDefault(emptyList())

    fun peekNextId(): Int = prefs.getInt(KEY_NEXT_ID, 1)

    /** Swaps in a restored list, keeping new ids clear of restored ones. */
    @Synchronized
    fun replaceAll(list: List<Task>, nextId: Int) {
        val safeNext = maxOf(nextId, (list.maxOfOrNull { it.id } ?: 0) + 1, peekNextId())
        prefs.edit().putInt(KEY_NEXT_ID, safeNext).apply()
        save(list)
    }

    @Synchronized
    fun upsert(task: Task) {
        val list = _tasks.value.filterNot { it.id == task.id } + task
        save(list)
    }

    @Synchronized
    fun delete(id: Int) = save(_tasks.value.filterNot { it.id == id })

    private fun save(list: List<Task>) {
        _tasks.value = list.sortedBy { it.triggerAt }
        val array = JSONArray()
        list.forEach {
            array.put(
                JSONObject()
                    .put("id", it.id)
                    .put("title", it.title)
                    .put("description", it.description)
                    .put("triggerAt", it.triggerAt)
                    .put("done", it.done)
                    .put("listId", it.listId ?: JSONObject.NULL)
            )
        }
        prefs.edit().putString(KEY_TASKS, array.toString()).apply()
        onChanged?.invoke()
    }

    private fun load(): List<Task> {
        val raw = prefs.getString(KEY_TASKS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            List(array.length()) { i ->
                val o = array.getJSONObject(i)
                Task(
                    id = o.getInt("id"),
                    title = o.getString("title"),
                    description = o.optString("description"),
                    triggerAt = o.getLong("triggerAt"),
                    done = o.optBoolean("done"),
                    listId = if (o.isNull("listId")) null else o.getInt("listId")
                )
            }.sortedBy { it.triggerAt }
        }.getOrDefault(emptyList())
    }
}

/** User choices from the settings screen. */
object AppSettings {
    data class State(val toneUri: String?, val toneName: String, val vibrate: Boolean, val toneFromFile: Boolean = false)

    private lateinit var prefs: SharedPreferences
    private val _state = MutableStateFlow(State(null, "Default alarm", true))
    val state: StateFlow<State> = _state.asStateFlow()
    var onChanged: (() -> Unit)? = null

    fun init(context: Context) {
        if (::prefs.isInitialized) return
        prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        _state.value = State(
            toneUri = prefs.getString("tone_uri", null),
            toneName = prefs.getString("tone_name", null) ?: "Default alarm",
            vibrate = prefs.getBoolean("vibrate", true),
            // Older installs did not record the source; SAF picks are the non-media URIs.
            toneFromFile = prefs.getString("tone_uri", null)?.let {
                prefs.getBoolean("tone_file", !it.startsWith("content://media") && !it.startsWith("content://settings"))
            } ?: false
        )
    }

    fun setTone(uri: String?, name: String, fromFile: Boolean) {
        prefs.edit().putString("tone_uri", uri).putString("tone_name", name).putBoolean("tone_file", fromFile).apply()
        _state.value = _state.value.copy(toneUri = uri, toneName = name, toneFromFile = fromFile)
        onChanged?.invoke()
    }

    fun setVibrate(on: Boolean) {
        prefs.edit().putBoolean("vibrate", on).apply()
        _state.value = _state.value.copy(vibrate = on)
        onChanged?.invoke()
    }
}
