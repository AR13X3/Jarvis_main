package com.ar13x.jarvis.core.network

import com.ar13x.jarvis.core.data.TodoRepository
import com.ar13x.jarvis.core.model.PagedTodos
import com.ar13x.jarvis.core.model.Todo
import com.ar13x.jarvis.core.model.TodoEvents
import com.ar13x.jarvis.core.model.TodoPriority
import com.ar13x.jarvis.core.model.TodoStatus
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * To-dos against the gateway. A translation layer and nothing more — no cache,
 * no local aggregation, no write queue (plan §3.5).
 */
@Singleton
class RemoteTodoRepository @Inject constructor(
    private val api: JarvisApi,
) : TodoRepository {

    override suspend fun todos(
        statuses: Set<TodoStatus>,
        tag: String?,
        undated: Boolean,
        priorities: Set<TodoPriority>,
        page: Int,
    ): PagedTodos = gatewayCall {
        api.todos(
            // Null rather than an empty list: an empty `status` list would be a
            // filter matching nothing, while "no filter" is what an empty
            // selection means in the UI. Sending `[]` would show a blank screen
            // and look like there are no to-dos.
            status = statuses.takeIf { it.isNotEmpty() }?.map { it.wireName() },
            tag = tag,
            undatedOnly = undated,
            // Same rule as `status`, for the same reason.
            priority = priorities.takeIf { it.isNotEmpty() }?.map { it.wireName() },
            page = page,
        )
    }

    /**
     * `include_children` is **not** set here.
     *
     * Asking by `parent_id` already narrows to one to-do's children, and the
     * flag governs whether children appear in an unfiltered list. Setting both
     * would read as "and also include children", which is what it is not.
     */
    override suspend fun children(parentId: Long): List<Todo> = gatewayCall {
        api.todos(parentId = parentId).todos
    }

    override suspend fun history(todoId: Long, page: Int): TodoEvents =
        gatewayCall { api.todoHistory(todoId, page) }

    override suspend fun todo(todoId: Long): Todo = gatewayCall { api.todo(todoId) }

    override suspend fun create(
        title: String,
        description: String?,
        startsAt: Instant?,
        dueAt: Instant?,
        tags: List<String>,
        priority: TodoPriority?,
        parentId: Long?,
    ): Todo = gatewayCall(mutating = true) {
        api.createTodo(
            CreateTodoBody(
                title = title,
                description = description,
                startsAt = startsAt,
                dueAt = dueAt,
                tags = tags.takeIf { it.isNotEmpty() },
                priority = priority,
                parentId = parentId,
            ),
        ).todo
    }

    override suspend fun setStatus(todoId: Long, status: TodoStatus): Todo =
        patch(todoId, todoPatchBody(status = Patch.Set(status)))

    override suspend fun setPriority(todoId: Long, priority: TodoPriority): Todo =
        patch(todoId, todoPatchBody(priority = Patch.Set(priority)))

    override suspend fun setTitle(todoId: Long, title: String): Todo =
        patch(todoId, todoPatchBody(title = Patch.Set(title)))

    override suspend fun setDescription(todoId: Long, description: String): Todo =
        patch(todoId, todoPatchBody(description = Patch.Set(description)))

    override suspend fun setTags(todoId: Long, tags: List<String>): Todo =
        patch(todoId, todoPatchBody(tags = Patch.Set(tags)))

    /**
     * The one call where omission and null are different requests.
     *
     * `null` becomes [Patch.Clear], which puts an explicit `null` on the wire
     * and clears the deadline. Anything else sets it. What it must never do is
     * *omit* the key, which is what a plain nullable field would have done and
     * which the server would read as "leave the deadline alone".
     */
    override suspend fun setDueAt(todoId: Long, dueAt: Instant?): Todo = patch(
        todoId,
        todoPatchBody(dueAt = if (dueAt == null) Patch.Clear else Patch.Set(dueAt)),
    )

    private suspend fun patch(
        todoId: Long,
        body: kotlinx.serialization.json.JsonObject,
    ): Todo = gatewayCall(mutating = true) { api.patchTodo(todoId, body).todo }
}
