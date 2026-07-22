package com.savoo.scclient.telegram

import android.content.Context
import com.savoo.scclient.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** BETA: real TDLib-backed [TelegramClient], wired against org.drinkless.tdlib.{Client,TdApi}. */
@Singleton
class TdLibTelegramClient @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sessionKeyStore: TelegramSessionKeyStore,
) : TelegramClient {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _authState = MutableStateFlow<TelegramAuthState>(TelegramAuthState.LoggedOut)
    override val authState: StateFlow<TelegramAuthState> = _authState
    override val isAvailable = true

    // Every UpdateXxx TDLib pushes asynchronously (not tied to a specific request/response) is
    // replayed here so other methods (e.g. file download progress) can subscribe to just the
    // updates they care about without a second, separate update-routing mechanism.
    private val updates = MutableSharedFlow<TdApi.Object>(extraBufferCapacity = 256)

    private var lastPhoneNumber: String = ""

    private val _chatFolders = MutableStateFlow<List<TelegramChatFolder>>(emptyList())
    override val chatFolders: StateFlow<List<TelegramChatFolder>> = _chatFolders

    /** fileId -> chatId for avatar downloads kicked off but not yet finished. */
    private val pendingChatAvatars = ConcurrentHashMap<Int, Long>()

    private val client: Client by lazy {
        Client.create(this::onUpdate, null, null)
    }

    override suspend fun start() {
        client // lazily creates the Client and starts its event loop
    }

    private fun onUpdate(obj: TdApi.Object) {
        if (obj is TdApi.UpdateAuthorizationState) handleAuthState(obj.authorizationState)
        if (obj is TdApi.UpdateChatFolders) {
            _chatFolders.value = obj.chatFolders.map { TelegramChatFolder(it.id, it.name.text.text) }
        }
        updates.tryEmit(obj)
    }

    private fun handleAuthState(state: TdApi.AuthorizationState) {
        when (state) {
            is TdApi.AuthorizationStateWaitTdlibParameters -> scope.launch {
                val params = TdApi.SetTdlibParameters().apply {
                    databaseDirectory = File(context.filesDir, "tdlib").absolutePath
                    useFileDatabase = true
                    useChatInfoDatabase = true
                    useMessageDatabase = true
                    useSecretChats = false
                    apiId = BuildConfig.TELEGRAM_API_ID.toIntOrNull() ?: 0
                    apiHash = BuildConfig.TELEGRAM_API_HASH
                    systemLanguageCode = "en"
                    deviceModel = "Android"
                    applicationVersion = "1.0"
                    databaseEncryptionKey = sessionKeyStore.getOrCreateDatabaseEncryptionKey()
                }
                try {
                    send(params)
                } catch (e: Exception) {
                    _authState.value = TelegramAuthState.Error(e.message ?: "Failed to initialize Telegram client")
                }
            }
            is TdApi.AuthorizationStateWaitPhoneNumber -> _authState.value = TelegramAuthState.WaitPhoneNumber
            is TdApi.AuthorizationStateWaitCode -> _authState.value =
                TelegramAuthState.WaitCode(lastPhoneNumber, state.codeInfo?.type?.let { it::class.simpleName })
            is TdApi.AuthorizationStateWaitPassword -> _authState.value =
                TelegramAuthState.WaitPassword(state.passwordHint.takeIf { it.isNotBlank() })
            is TdApi.AuthorizationStateReady -> _authState.value = TelegramAuthState.Ready
            is TdApi.AuthorizationStateLoggingOut, is TdApi.AuthorizationStateClosing -> _authState.value = TelegramAuthState.Connecting
            is TdApi.AuthorizationStateClosed -> _authState.value = TelegramAuthState.LoggedOut
            else -> _authState.value = TelegramAuthState.Connecting
        }
    }

    override suspend fun sendPhoneNumber(phoneNumber: String) {
        lastPhoneNumber = phoneNumber
        send(TdApi.SetAuthenticationPhoneNumber(phoneNumber, null))
    }

    override suspend fun sendCode(code: String) {
        send(TdApi.CheckAuthenticationCode(code))
    }

    override suspend fun sendPassword(password: String) {
        send(TdApi.CheckAuthenticationPassword(password))
    }

    override suspend fun logOut() {
        send(TdApi.LogOut())
    }

    override suspend fun getChats(chatList: TelegramChatList, limit: Int): Result<List<TelegramChatSummary>> = runCatching {
        val tdList: TdApi.ChatList = when (chatList) {
            is TelegramChatList.Main -> TdApi.ChatListMain()
            is TelegramChatList.Folder -> TdApi.ChatListFolder(chatList.folderId)
        }
        // GetChats already returns chat ids pre-sorted for this exact list (pinned chats first, in
        // their pinned order, then the rest by recent activity) - same order the real Telegram app
        // shows, so there's no need to re-derive that ordering client-side.
        val chatIds = (send(TdApi.GetChats(tdList, limit)) as TdApi.Chats).chatIds
        chatIds.map { chatId ->
            val chat = send(TdApi.GetChat(chatId)) as TdApi.Chat
            val type = chat.type
            TelegramChatSummary(
                chatId = chat.id,
                title = chat.title,
                avatarUrl = resolveOrQueueChatAvatar(chat),
                isChannel = type is TdApi.ChatTypeSupergroup && type.isChannel,
                isPinned = chat.positions.orEmpty().any { it.isPinned && it.list.matches(tdList) },
            )
        }
    }

    private fun TdApi.ChatList.matches(other: TdApi.ChatList): Boolean = when (this) {
        is TdApi.ChatListMain -> other is TdApi.ChatListMain
        is TdApi.ChatListFolder -> other is TdApi.ChatListFolder && other.chatFolderId == chatFolderId
        else -> false
    }

    /**
     * Chat photos aren't served over HTTP - the small (160x160) variant has to be pulled through
     * TDLib and read from its local cache path. Returns immediately: if already cached, the path
     * is returned synchronously; otherwise a download is kicked off in the background (not
     * awaited - awaiting per-chat downloads here is what made the chat list take forever to
     * appear) and the result later shows up via [observeChatAvatarUpdates].
     */
    private fun resolveOrQueueChatAvatar(chat: TdApi.Chat): String? {
        val photoFile = chat.photo?.small ?: return null
        if (photoFile.local?.isDownloadingCompleted == true && photoFile.local.path.isNotBlank()) {
            return "file://${photoFile.local.path}"
        }
        pendingChatAvatars[photoFile.id] = chat.id
        scope.launch { runCatching { send(TdApi.DownloadFile(photoFile.id, 1, 0, 0, false)) } }
        return null
    }

    /** Emits (chatId, local file uri) as queued chat-avatar downloads finish. */
    override fun observeChatAvatarUpdates(): Flow<Pair<Long, String>> = updates
        .mapNotNull { update ->
            if (update !is TdApi.UpdateFile || !update.file.local.isDownloadingCompleted) return@mapNotNull null
            val chatId = pendingChatAvatars.remove(update.file.id) ?: return@mapNotNull null
            val path = update.file.local.path.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            chatId to "file://$path"
        }

    override fun getAudioMessages(chatId: Long): Flow<TelegramAudioMessage> = callbackFlow {
        var fromMessageId = 0L
        while (true) {
            val chunk = send(TdApi.GetChatHistory(chatId, fromMessageId, 0, 50, false)) as TdApi.Messages
            val messages = chunk.messages?.filterNotNull().orEmpty()
            if (messages.isEmpty()) break
            for (message in messages) {
                val audio = (message.content as? TdApi.MessageAudio)?.audio ?: continue
                trySendBlocking(
                    TelegramAudioMessage(
                        chatId = chatId,
                        messageId = message.id,
                        fileId = audio.audio.id,
                        fileUniqueId = audio.audio.remote?.uniqueId.orEmpty(),
                        fileSizeBytes = audio.audio.size,
                        title = audio.title?.takeIf { it.isNotBlank() },
                        performer = audio.performer?.takeIf { it.isNotBlank() },
                        durationSec = audio.duration,
                        dateUnixSec = message.date,
                    )
                )
            }
            fromMessageId = messages.last().id
        }
        awaitClose { }
    }

    override fun downloadFile(fileId: Int, expectedSizeBytes: Long, limitBytes: Long): Flow<Pair<Float, File?>> = callbackFlow {
        fun isDone(file: TdApi.File): Boolean =
            file.local.isDownloadingCompleted || (limitBytes > 0 && file.local.downloadedPrefixSize >= limitBytes)

        // The direct result of DownloadFile is the file's state AT THE MOMENT of the call - if the
        // file happens to already be fully (or, for a prefix request, sufficiently) downloaded right
        // then (common with Telegram's content-dedup: the same audio shared across many messages, or
        // our own earlier ID3-prefix fetch having already pulled a small file in full), no *new*
        // UpdateFile event will ever fire, and only listening on the update stream would hang forever.
        val initial = runCatching { send(TdApi.DownloadFile(fileId, 1, 0, limitBytes, false)) as? TdApi.File }.getOrNull()
        if (initial != null && isDone(initial)) {
            trySendBlocking(1f to File(initial.local.path))
            close()
            return@callbackFlow
        }

        val job = scope.launch {
            updates
                .filter { it is TdApi.UpdateFile && it.file.id == fileId }
                .collect { update ->
                    val file = (update as TdApi.UpdateFile).file
                    val size = expectedSizeBytes.takeIf { it > 0 } ?: file.expectedSize.takeIf { it > 0 } ?: file.size
                    val target = if (limitBytes > 0) minOf(limitBytes, size.takeIf { it > 0 } ?: limitBytes) else size
                    val progress = if (target > 0) (file.local.downloadedPrefixSize.toFloat() / target).coerceIn(0f, 1f) else 0f
                    if (isDone(file)) {
                        trySendBlocking(1f to File(file.local.path))
                        close()
                    } else {
                        trySendBlocking(progress to null)
                    }
                }
        }
        awaitClose { job.cancel() }
    }

    private suspend fun send(function: TdApi.Function<*>): TdApi.Object = suspendCancellableCoroutine { cont ->
        client.send(function) { result ->
            if (result is TdApi.Error) {
                cont.resumeWithException(RuntimeException("${result.code}: ${result.message}"))
            } else {
                cont.resume(result)
            }
        }
    }
}
