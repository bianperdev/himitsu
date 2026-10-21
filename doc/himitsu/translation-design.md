# Translation design (`feat/translation-*`)

Status: proposal, waiting for the maintainer's approval (2026-10-21).

## Goal

Role-play with a character in any language while reading and writing in your own. When a chat's character writes in a language that is not the user's, Himitsu translates the character's messages into the user's language for display, and translates the user's messages into the character's language before they are sent. The model only ever sees the conversation in the character's language.

Translation uses services the user configures, like AI providers. It is off by default.

## Decisions (maintainer, 2026-10-21)

| Topic | Decision |
|---|---|
| Engine | Remote translation services configured by the user (option C). No on-device translator in this version. |
| Providers in v1 | LibreTranslate, DeepL, Google Cloud Translation, and "an AI provider as translator" (any configured LLM provider, local ones included). |
| Settings note | The providers screen recommends LibreTranslate on the user's own server, to keep chats private. |
| Trigger | Automatic: on opening a chat, or when a new character message arrives, if the character's language differs from the user's. |
| Streaming | The original streams; the translation replaces it when the message is complete. |
| Scope | One-to-one and group chats. Stories later. |

## Core idea: the conversation language is the source of truth

- `ChatMessage.content` and `GroupChatMessage.content` always hold the text in the **conversation language** (the character's), exactly what the model sees.
  - Character message: `content` is the model's original reply; its translation for the user is stored apart.
  - User message: the user types in their language; Himitsu translates it, stores the translation as `content`, and keeps what the user typed apart.
- The prompt pipeline (presets, macros, lorebooks, Regex, Prompt Inspector), token counting, summaries and SillyTavern exports keep working unchanged, in the conversation language.
- The UI shows the stored display text when there is one, and the content otherwise.

## Privacy

- Off by default. Adding the first provider shows what is sent (the text of chat messages, never API keys of AI providers, never images) and to whom.
- Provider API keys live in the SQLCipher `primary.sqlite`, like AI provider keys, masked in the editor, never logged.
- Language detection runs on the device: Android's `TextClassifier` (`TextClassificationManager`, API 29+). On API 26-28 the provider's detection is used, and only once translation is already on for that chat.
- Providers follow the same network rules as AI providers: no redirects, system CAs only, plain HTTP allowed for LAN servers with the existing warning.
- Translations are stored in the encrypted database and deleted with their message (foreign key, cascade).
- Character names, `{{macros}}`, `*actions*` markers and markdown are protected before sending (see Text protection), so a provider never receives anything beyond the message text.
- PRIVACY.md, the README and every store listing gain the new data destination.

## Data model (main database, version 5, AutoMigration 4 to 5, new tables only)

```text
translation_providers
  id INTEGER PK, name TEXT, type TEXT (LibreTranslate | DeepL | GoogleCloud | LlmProvider),
  baseUrl TEXT, apiKey TEXT (''), llmProviderId INTEGER NULL (FK llm_providers, SET NULL),
  customHeadersJson TEXT (''), createTime INTEGER

chat_message_translations
  messageId INTEGER PK (FK chat_messages.id, CASCADE),
  displayText TEXT,            -- what the user reads (their language)
  displayLanguage TEXT,        -- BCP 47, for example "es"
  conversationLanguage TEXT,   -- BCP 47 of content, for example "en"
  contentHash TEXT,            -- SHA-256 of content when translated; a mismatch means stale
  providerId INTEGER NULL (FK translation_providers, SET NULL), createTime INTEGER

group_chat_message_translations
  same columns, messageId FK group_chat_messages.id CASCADE

chat_translation_settings
  sessionKind TEXT (Chat | GroupChat), sessionId INTEGER, PK(sessionKind, sessionId),
  mode TEXT (Auto | On | Off), conversationLanguage TEXT NULL, providerId INTEGER NULL
```

Device-level settings (not sensitive, Kotpref `TranslationPrefs`): `enabled` (master switch, default false), `userLanguage` (default: the app language), `defaultProviderId`, `translateOwnMessages` (default true).

Editing a message: editing `content` makes `contentHash` stale, so the display text is translated again; editing the display text of a user message translates it again into `content`.

## Contracts (package `me.kafuuneko.rpclient.libs.translation`)

```kotlin
enum class TranslationProviderType { LibreTranslate, DeepL, GoogleCloud, LlmProvider }

data class TranslationProviderConfig(
    val id: Long, val name: String, val type: TranslationProviderType,
    val baseUrl: String, val apiKey: String, val llmProviderId: Long?, val customHeadersJson: String
)

/** One translation service. Implementations never log text or keys. */
interface TranslationClient {
    /** Translates [texts] (already protected) from [source] (null: let the service detect) to [target]. */
    suspend fun translate(texts: List<String>, source: String?, target: String): List<String>
    /** Detects the language of [text], or null when the service cannot. */
    suspend fun detect(text: String): String?
}

fun interface TranslationClientFactory { fun create(config: TranslationProviderConfig): TranslationClient }

/** On-device language detection; null when unsure or unavailable (API < 29). */
fun interface LanguageDetector { suspend fun detect(text: String): String? }

/** Shields names, macros, action markers and markdown from translation and puts them back. */
interface TextProtector {
    fun protect(text: String, names: List<String>): ProtectedText
    fun restore(translated: String, protected: ProtectedText): String
}

/** What the chat screens call. */
interface ChatTranslator {
    /** Display text for an incoming (character) message, translating and storing it when needed. */
    suspend fun displayTextFor(message: TranslatableMessage, session: TranslationSessionKey): String?
    /** Conversation-language text for what the user typed, or the input unchanged when off. */
    suspend fun outgoing(input: String, session: TranslationSessionKey): OutgoingTranslation
    /** Decides, from the latest character messages, whether this chat translates and from which language. */
    suspend fun resolve(session: TranslationSessionKey): TranslationState
}
```

Errors map to `TranslationFailure` (Network, Auth, Quota, Unsupported, Invalid) shown as a quiet notice on the message, which then shows its original. A failure never blocks sending: the user can send untranslated.

## Text protection

Before sending: replace character and user names, `{{macros}}`, `*...*` action spans' markers, `"..."` quote marks the provider tends to change, markdown symbols and URLs with opaque tokens the providers keep (XML tags for DeepL and Google `ignore`/`notranslate`, numbered tokens for LibreTranslate and the LLM). After: restore, and fall back to the original if a token was lost.

## UI

- Settings › Translation: master switch, your language, default provider, translate my messages, list of providers (add, edit, test, delete) with the LibreTranslate note: "If you have a server, LibreTranslate keeps your chats private: nothing leaves your server." (13 locales, Spanish with "tú").
- Chat and group chat: a slim banner "Translating from English · Show original", a per-chat menu entry (Auto / Always / Never, provider), a small "translated" mark on messages, tap to toggle the original. While the model streams, the original is shown; on completion it switches to the translation.
- Composer: the user types in their language; an optional preview of the translation before sending (off by default).

## Tracks (parallel after the groundwork)

| Branch | Owns |
|---|---|
| groundwork on `main` (this design's step 0) | The four entities and DAOs, `AppDatabase` version 5 with the AutoMigration and exported schema, the contracts above with fakes (`FakeTranslationClient`, `FakeLanguageDetector`), `TranslationPrefs`, an empty `translationModule` in `HimitsuModules.kt` |
| `feat/translation-providers` | `libs/translation/provider/**` (LibreTranslate, DeepL, Google Cloud, LLM adapters, factory), `feature/translationsettings/**` (settings and provider editor, test connection, LibreTranslate note), its settings entry, `res/values*/strings_translation_providers.xml` |
| `feat/translation-core` | `libs/translation/core/**`: `ChatTranslator` implementation, `AndroidLanguageDetector`, `TextProtector`, caching and staleness by hash, failure mapping; unit tests |
| `feat/translation-chat` | `feature/chat/**` and `feature/groupchat/**` narrow edits (banner, toggle, per-chat menu, send hook through `ChatTranslator.outgoing`, display through `displayTextFor`), `res/values*/strings_translation_chat.xml`; instrumented tests with the fakes |

Merge order: providers, core, chat. Each track runs the unit tests, builds every flavor, and gets a privacy review before merging. Instrumented tests run only on an emulator with its lock file.

## Out of scope for v1

Stories and the story editor, translating lorebooks or character cards themselves, on-device translators (Bergamot, an LLM through llama.cpp as a dedicated translator), automatic glossary per character.
