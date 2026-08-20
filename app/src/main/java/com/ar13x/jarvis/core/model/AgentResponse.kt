package com.ar13x.jarvis.core.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.LocalDate

/**
 * Agent responses are **structured, never bare text** (plan §4.5) — the app
 * renders cards and buttons, not markdown.
 */
@Serializable
data class AgentResponse(
    val text: String = "",
    val components: List<AgentComponent> = emptyList(),
)

@Serializable(with = AgentComponentSerializer::class)
sealed interface AgentComponent {

    /**
     * A proposal awaiting the user's accept/reject. Nothing is written until
     * confirmed (parent plan §2.4).
     */
    @Serializable
    data class Confirm(
        @SerialName("proposal_id") val proposalId: String,
        val summary: ProposalSummary,
        /**
         * Contract addition — see §13 note in the app plan.
         *
         * §4.5 shows only `proposal_id` and `summary`, which is enough for a
         * *live* card but not for history: a resolved card must render in its
         * resolved state when you scroll back (§5.3). `agent.proposals.status`
         * already holds exactly this, so it costs the gateway nothing to send.
         * Defaults to [ProposalStatus.Pending] so a gateway that omits it still
         * parses.
         */
        val status: ProposalStatus = ProposalStatus.Pending,
    ) : AgentComponent

    /** Disambiguation. Rendered as buttons, 3 at a time (plan §5.4). */
    @Serializable
    data class TaskOptions(
        val options: List<TaskOption> = emptyList(),
        /** Drives "Show more", which appends the next 3. Null when exhausted. */
        @SerialName("more_cursor") val moreCursor: String? = null,
    ) : AgentComponent

    /**
     * A handoff out of the general session into a new task session.
     *
     * The general session is offered `find_tasks` and `get_task` and no create
     * tool (parent plan §2.3), because a general session can never become bound
     * and one that created tasks would accumulate them — breaking the
     * one-session-per-task invariant §2.2 is built on.
     *
     * That is the right rule and the wrong dead end: asked to create something,
     * the agent could only decline. This component lets it decline *and* hand
     * over — the app opens a new unbound session and sends [seed] straight
     * away, so the user never retypes what they already said.
     *
     * Contract addition — see BUILD_NOTES §3.11.
     */
    @Serializable
    data class NewTask(
        /** Button text. Server-supplied so the wording stays the agent's. */
        val label: String = "Set this up",
        /** What to send in the new session. Usually a tidied version of the ask. */
        val seed: String,
    ) : AgentComponent

    /**
     * Forward-compatibility branch. The gateway *will* grow component types
     * (attachments, task cards), and an app that throws on an unknown `type`
     * cannot be forward-compatible — it would blank the whole message list over
     * one field it did not recognise.
     *
     * The raw object is kept so the component round-trips losslessly, and [text]
     * is rendered if present. Nothing else about it is assumed.
     */
    data class Unknown(
        val type: String?,
        val raw: JsonObject,
    ) : AgentComponent {
        val text: String? get() = raw["text"]?.jsonPrimitive?.contentOrNull
    }
}

@Serializable
enum class ProposalAction {
    @SerialName("create") Create,
    @SerialName("update") Update,
    @SerialName("cancel") Cancel,
    @SerialName("complete") Complete,
}

@Serializable
enum class ProposalStatus {
    @SerialName("pending") Pending,
    @SerialName("confirmed") Confirmed,
    @SerialName("rejected") Rejected,
    @SerialName("superseded") Superseded;

    val isResolved: Boolean get() = this != Pending
}

@Serializable
data class ProposalSummary(
    val action: ProposalAction,
    val title: String = "",
    @Serializable(InstantSerializer::class)
    @SerialName("due_at") val dueAt: Instant? = null,
    @Serializable(LocalDateSerializer::class)
    @SerialName("due_date") val dueDate: LocalDate? = null,
    @SerialName("is_priority") val isPriority: Boolean = false,
    /**
     * **Load-bearing.** The parent plan (§2.5) measured the model emitting
     * malformed tool arguments roughly 1 in 5 on the "last Friday of every
     * month" pattern. A wrong day set is only catchable by a human reading it
     * *before* confirming — so this is rendered prominently and is never
     * collapsed behind a "details" affordance (plan §4.5).
     */
    @SerialName("recurrence_text") val recurrenceText: String? = null,
    val description: String = "",
)

@Serializable
data class TaskOption(
    @SerialName("task_id") val taskId: Long,
    val title: String,
    @Serializable(InstantSerializer::class)
    @SerialName("due_at") val dueAt: Instant? = null,
    /**
     * Nullable only because the gateway does not send it yet — see
     * BUILD_NOTES §3.13.
     *
     * Asking "what's due today" returns everything due today, and without this
     * a task already done looks exactly like one still outstanding. The list is
     * meant to answer "what is left", and a button that cannot say "done"
     * answers a different question.
     */
    val status: TaskStatus? = null,
)

/**
 * Hand-written rather than a generated sealed-class serializer, for one reason:
 * a closed polymorphic serializer **throws** on an unrecognised discriminator,
 * and this list has to survive the gateway shipping a component type this build
 * has never heard of. Unknown types fall through to [AgentComponent.Unknown].
 *
 * It is written for both directions so the round-trip test in §8.3 is real:
 * decode → encode → decode must be stable for every component type, including
 * an unknown one.
 */
object AgentComponentSerializer : KSerializer<AgentComponent> {

    private const val TYPE = "type"
    private const val CONFIRM = "confirm"
    private const val TASK_OPTIONS = "task_options"
    private const val NEW_TASK = "new_task"

    @OptIn(ExperimentalSerializationApi::class)
    override val descriptor: SerialDescriptor = SerialDescriptor(
        "com.ar13x.jarvis.core.model.AgentComponent",
        JsonObject.serializer().descriptor,
    )

    override fun deserialize(decoder: Decoder): AgentComponent {
        val input = decoder as? JsonDecoder
            ?: throw SerializationException("AgentComponent is JSON-only")
        val obj = input.decodeJsonElement() as? JsonObject
            ?: throw SerializationException("AgentComponent must be a JSON object")

        return when (val type = obj[TYPE]?.jsonPrimitive?.contentOrNull) {
            CONFIRM -> input.json.decodeFromJsonElement(AgentComponent.Confirm.serializer(), obj)
            TASK_OPTIONS -> input.json.decodeFromJsonElement(AgentComponent.TaskOptions.serializer(), obj)
            NEW_TASK -> input.json.decodeFromJsonElement(AgentComponent.NewTask.serializer(), obj)
            else -> AgentComponent.Unknown(type, obj)
        }
    }

    override fun serialize(encoder: Encoder, value: AgentComponent) {
        val output = encoder as? JsonEncoder
            ?: throw SerializationException("AgentComponent is JSON-only")

        val element: JsonElement = when (value) {
            is AgentComponent.Confirm ->
                output.json.encodeToJsonElement(AgentComponent.Confirm.serializer(), value).withType(CONFIRM)
            is AgentComponent.TaskOptions ->
                output.json.encodeToJsonElement(AgentComponent.TaskOptions.serializer(), value).withType(TASK_OPTIONS)
            is AgentComponent.NewTask ->
                output.json.encodeToJsonElement(AgentComponent.NewTask.serializer(), value).withType(NEW_TASK)
            is AgentComponent.Unknown -> value.raw
        }
        output.encodeJsonElement(element)
    }

    private fun JsonElement.withType(type: String): JsonObject =
        JsonObject(jsonObject + (TYPE to JsonPrimitive(type)))
}
