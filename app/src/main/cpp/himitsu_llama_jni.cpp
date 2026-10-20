// SPDX-License-Identifier: GPL-3.0-or-later
//
// JNI bridge between LlamaCppNative (Kotlin) and llama.cpp.
//
// - Text crosses the boundary as UTF-8 byte arrays, never as JNI strings: JNI uses modified UTF-8,
//   which breaks emoji and other characters outside the BMP.
// - Nothing is logged: llama.cpp and ggml logs are silenced at init, and errors thrown to Kotlin
//   carry fixed messages without prompt or model content.
// - A Session is used from a single thread (the engine's dispatcher); only `abort` is touched
//   from other threads.

#include <jni.h>

#include <algorithm>
#include <atomic>
#include <exception>
#include <string>
#include <vector>

#include "ggml-backend.h"
#include "ggml.h"
#include "llama.h"

#include "jinja/lexer.h"
#include "jinja/parser.h"
#include "jinja/runtime.h"
#include "jinja/value.h"
#include "json.h"

namespace {

// Finish codes shared with LlamaCppNative.kt.
constexpr jint FINISH_EOG = 0;
constexpr jint FINISH_MAX_TOKENS = 1;
constexpr jint FINISH_CONTEXT_FULL = 2;
constexpr jint FINISH_ABORTED = 3;
constexpr jint FINISH_SINK_STOP = 4;
constexpr jint FINISH_PROMPT_TOO_LONG = -1;
constexpr jint FINISH_DECODE_ERROR = -2;

constexpr int32_t PENALTY_LAST_N = 64;

struct Session {
    llama_model * model = nullptr;
    llama_context * ctx = nullptr;
    const llama_vocab * vocab = nullptr;
    // Tokens currently held in the KV cache, so the next prompt can reuse their common prefix.
    std::vector<llama_token> cached;
    std::atomic<bool> abort{false};
};

jmethodID g_sink_on_piece = nullptr;

void silent_log(ggml_log_level, const char *, void *) {}

bool abort_requested(void * data) {
    return static_cast<Session *>(data)->abort.load();
}

Session * as_session(jlong handle) {
    return reinterpret_cast<Session *>(handle);
}

void throw_state(JNIEnv * env, const char * message) {
    jclass cls = env->FindClass("java/lang/IllegalStateException");
    if (cls != nullptr) env->ThrowNew(cls, message);
}

std::string to_string(JNIEnv * env, jbyteArray bytes) {
    if (bytes == nullptr) return {};
    const jsize length = env->GetArrayLength(bytes);
    std::string out(static_cast<size_t>(length), '\0');
    if (length > 0) env->GetByteArrayRegion(bytes, 0, length, reinterpret_cast<jbyte *>(out.data()));
    return out;
}

jbyteArray to_bytes(JNIEnv * env, const std::string & text) {
    jbyteArray out = env->NewByteArray(static_cast<jsize>(text.size()));
    if (out != nullptr && !text.empty()) {
        env->SetByteArrayRegion(out, 0, static_cast<jsize>(text.size()),
                                reinterpret_cast<const jbyte *>(text.data()));
    }
    return out;
}

std::vector<std::string> to_strings(JNIEnv * env, jobjectArray array) {
    std::vector<std::string> out;
    const jsize count = env->GetArrayLength(array);
    out.reserve(static_cast<size_t>(count));
    for (jsize i = 0; i < count; i++) {
        auto item = static_cast<jbyteArray>(env->GetObjectArrayElement(array, i));
        out.push_back(to_string(env, item));
        env->DeleteLocalRef(item);
    }
    return out;
}

std::vector<bool> to_bools(JNIEnv * env, jbooleanArray array) {
    const jsize count = env->GetArrayLength(array);
    std::vector<jboolean> raw(static_cast<size_t>(count));
    if (count > 0) env->GetBooleanArrayRegion(array, 0, count, raw.data());
    return {raw.begin(), raw.end()};
}

std::string token_text(const llama_vocab * vocab, llama_token token, bool special) {
    std::string out(64, '\0');
    int32_t n = llama_token_to_piece(vocab, token, out.data(), static_cast<int32_t>(out.size()), 0, special);
    if (n < 0) {
        out.resize(static_cast<size_t>(-n));
        n = llama_token_to_piece(vocab, token, out.data(), static_cast<int32_t>(out.size()), 0, special);
    }
    out.resize(static_cast<size_t>(std::max(n, 0)));
    return out;
}

// Length of the longest prefix of `text` that ends on a complete UTF-8 character, so a piece
// that splits a multi-byte character is held back until the rest arrives.
size_t complete_utf8_prefix(const std::string & text) {
    const size_t size = text.size();
    for (size_t back = 1; back <= 4 && back <= size; back++) {
        const auto byte = static_cast<unsigned char>(text[size - back]);
        if ((byte & 0xC0) == 0x80) continue;
        size_t needed = 1;
        if ((byte & 0xE0) == 0xC0) needed = 2;
        else if ((byte & 0xF0) == 0xE0) needed = 3;
        else if ((byte & 0xF8) == 0xF0) needed = 4;
        return back >= needed ? size : size - back;
    }
    return size;
}

// Tokenizes rendered template parts. Parts that came from user input are tokenized without
// special-token parsing, so text like "<|im_end|>" inside a message cannot inject control tokens.
bool tokenize_parts(const llama_vocab * vocab,
                    const std::vector<std::string> & parts,
                    const std::vector<bool> & is_input,
                    std::vector<llama_token> & out) {
    const bool add_bos = llama_vocab_get_add_bos(vocab);
    const std::string bos_text = add_bos ? token_text(vocab, llama_vocab_bos(vocab), true) : std::string();
    if (add_bos) out.push_back(llama_vocab_bos(vocab));
    for (size_t i = 0; i < parts.size(); i++) {
        std::string text = parts[i];
        const bool input = i < is_input.size() && is_input[i];
        // Templates usually print the BOS text themselves; drop it, the BOS token is already there
        if (i == 0 && add_bos && !input && !bos_text.empty() && text.rfind(bos_text, 0) == 0) {
            text.erase(0, bos_text.size());
        }
        if (text.empty()) continue;
        const auto length = static_cast<int32_t>(text.size());
        const int32_t needed = -llama_tokenize(vocab, text.data(), length, nullptr, 0, false, !input);
        if (needed <= 0) return needed == 0;
        const size_t start = out.size();
        out.resize(start + static_cast<size_t>(needed));
        const int32_t written = llama_tokenize(vocab, text.data(), length, out.data() + start, needed, false, !input);
        if (written < 0) return false;
        out.resize(start + static_cast<size_t>(written));
    }
    return true;
}

jintArray finish(JNIEnv * env, jint reason, size_t prompt_tokens, int completion_tokens) {
    jintArray out = env->NewIntArray(3);
    if (out == nullptr) return nullptr;
    const jint values[3] = {reason, static_cast<jint>(prompt_tokens), completion_tokens};
    env->SetIntArrayRegion(out, 0, 3, values);
    return out;
}

llama_sampler * build_sampler(const Session & s, jfloat temperature, jfloat top_p, jint top_k, jfloat min_p,
                              jfloat repeat_penalty, jlong seed) {
    llama_sampler_chain_params params = llama_sampler_chain_default_params();
    params.no_perf = true;
    llama_sampler * chain = llama_sampler_chain_init(params);
    if (repeat_penalty > 0.0f && repeat_penalty != 1.0f) {
        llama_sampler_chain_add(chain, llama_sampler_init_penalties(
            llama_vocab_n_tokens(s.vocab), PENALTY_LAST_N, repeat_penalty, 0.0f, 0.0f));
    }
    if (temperature <= 0.0f) {
        llama_sampler_chain_add(chain, llama_sampler_init_greedy());
        return chain;
    }
    if (top_k > 0) llama_sampler_chain_add(chain, llama_sampler_init_top_k(top_k));
    if (top_p > 0.0f && top_p < 1.0f) llama_sampler_chain_add(chain, llama_sampler_init_top_p(top_p, 1));
    if (min_p > 0.0f) llama_sampler_chain_add(chain, llama_sampler_init_min_p(min_p, 1));
    llama_sampler_chain_add(chain, llama_sampler_init_temp(temperature));
    const uint32_t dist_seed = seed < 0 ? LLAMA_DEFAULT_SEED : static_cast<uint32_t>(seed);
    llama_sampler_chain_add(chain, llama_sampler_init_dist(dist_seed));
    return chain;
}

// Brings the KV cache in line with `prompt`, reusing the longest common prefix of the previous one.
jint process_prompt(Session & s, const std::vector<llama_token> & prompt) {
    llama_memory_t memory = llama_get_memory(s.ctx);
    size_t keep = 0;
    while (keep < s.cached.size() && keep < prompt.size() && s.cached[keep] == prompt[keep]) keep++;
    // The last prompt token is always decoded again, so there are logits to sample from
    if (keep == prompt.size()) keep--;
    if (!llama_memory_seq_rm(memory, 0, static_cast<llama_pos>(keep), -1)) {
        llama_memory_clear(memory, true);
        keep = 0;
    }
    s.cached.resize(keep);
    const size_t batch = std::max<uint32_t>(1, llama_n_batch(s.ctx));
    for (size_t i = keep; i < prompt.size(); i += batch) {
        const auto count = static_cast<int32_t>(std::min(batch, prompt.size() - i));
        auto * tokens = const_cast<llama_token *>(prompt.data() + i);
        const int32_t rc = s.abort.load() ? 2 : llama_decode(s.ctx, llama_batch_get_one(tokens, count));
        if (rc != 0) {
            // A failed or aborted decode leaves a partial cache; start clean next time
            llama_memory_clear(memory, true);
            s.cached.clear();
            return rc == 2 ? FINISH_ABORTED : FINISH_DECODE_ERROR;
        }
        s.cached.insert(s.cached.end(), prompt.begin() + static_cast<long>(i), prompt.begin() + static_cast<long>(i) + count);
    }
    return FINISH_EOG;
}

// Hands complete UTF-8 text to the Kotlin sink. Returns false when generation must stop.
bool deliver(JNIEnv * env, jobject sink, std::string & pending, bool flush) {
    const size_t ready = flush ? pending.size() : complete_utf8_prefix(pending);
    if (ready == 0) return true;
    jbyteArray bytes = to_bytes(env, pending.substr(0, ready));
    pending.erase(0, ready);
    const jboolean keep_going = env->CallBooleanMethod(sink, g_sink_on_piece, bytes);
    env->DeleteLocalRef(bytes);
    return !env->ExceptionCheck() && keep_going == JNI_TRUE;
}

// ---- Native methods ------------------------------------------------------------------------

void native_init(JNIEnv * env, jclass, jstring native_lib_dir) {
    llama_log_set(silent_log, nullptr);
    ggml_log_set(silent_log, nullptr);
    const char * dir = env->GetStringUTFChars(native_lib_dir, nullptr);
    // Loads the CPU backend variant that best matches this processor
    ggml_backend_load_all_from_path(dir);
    env->ReleaseStringUTFChars(native_lib_dir, dir);
    llama_backend_init();
}

jlong native_load(JNIEnv * env, jclass, jstring path, jint context_tokens, jint threads, jboolean vocab_only) {
    llama_model_params model_params = llama_model_default_params();
    model_params.n_gpu_layers = 0;
    model_params.load_mode = LLAMA_LOAD_MODE_MMAP;
    model_params.vocab_only = vocab_only == JNI_TRUE;
    const char * model_path = env->GetStringUTFChars(path, nullptr);
    llama_model * model = llama_model_load_from_file(model_path, model_params);
    env->ReleaseStringUTFChars(path, model_path);
    if (model == nullptr) return 0;

    auto * session = new Session();
    session->model = model;
    session->vocab = llama_model_get_vocab(model);
    if (vocab_only == JNI_TRUE) return reinterpret_cast<jlong>(session);

    llama_context_params context_params = llama_context_default_params();
    context_params.n_ctx = static_cast<uint32_t>(std::max(context_tokens, 0));
    context_params.n_batch = 512;
    context_params.n_ubatch = 512;
    context_params.n_threads = threads;
    context_params.n_threads_batch = threads;
    context_params.no_perf = true;
    session->ctx = llama_init_from_model(model, context_params);
    if (session->ctx == nullptr) {
        llama_model_free(model);
        delete session;
        return 0;
    }
    llama_set_abort_callback(session->ctx, abort_requested, session);
    return reinterpret_cast<jlong>(session);
}

void native_free(JNIEnv *, jclass, jlong handle) {
    Session * session = as_session(handle);
    if (session == nullptr) return;
    if (session->ctx != nullptr) llama_free(session->ctx);
    if (session->model != nullptr) llama_model_free(session->model);
    delete session;
}

// Returns {chat template or null, BOS text, EOS text, trained context length, model name}.
jobjectArray native_info(JNIEnv * env, jclass, jlong handle) {
    Session * s = as_session(handle);
    jclass bytes_class = env->FindClass("[B");
    jobjectArray out = env->NewObjectArray(5, bytes_class, nullptr);
    if (out == nullptr) return nullptr;
    const char * tmpl = llama_model_chat_template(s->model, nullptr);
    if (tmpl != nullptr) env->SetObjectArrayElement(out, 0, to_bytes(env, tmpl));
    env->SetObjectArrayElement(out, 1, to_bytes(env, token_text(s->vocab, llama_vocab_bos(s->vocab), true)));
    env->SetObjectArrayElement(out, 2, to_bytes(env, token_text(s->vocab, llama_vocab_eos(s->vocab), true)));
    env->SetObjectArrayElement(out, 3, to_bytes(env, std::to_string(llama_model_n_ctx_train(s->model))));
    char name[256] = {0};
    if (llama_model_meta_val_str(s->model, "general.name", name, sizeof(name)) > 0) {
        env->SetObjectArrayElement(out, 4, to_bytes(env, name));
    }
    return out;
}

// Renders a Jinja chat template. Returns {byte[][] parts, boolean[] isInput}.
jobjectArray native_render(JNIEnv * env, jclass, jbyteArray template_bytes, jobjectArray roles,
                           jobjectArray contents, jboolean add_generation_prompt, jboolean enable_thinking,
                           jbyteArray bos, jbyteArray eos) {
    std::vector<jinja::string_part> parts;
    try {
        const std::string source = to_string(env, template_bytes);
        const std::string bos_text = to_string(env, bos);
        const std::string eos_text = to_string(env, eos);
        const std::vector<std::string> role_list = to_strings(env, roles);
        const std::vector<std::string> content_list = to_strings(env, contents);

        jinja::lexer lexer;
        auto lexed = lexer.tokenize(source);
        jinja::program program = jinja::parse_from_tokens(lexed);
        jinja::context context(lexed.source);

        common_json messages = common_json::array();
        for (size_t i = 0; i < role_list.size() && i < content_list.size(); i++) {
            messages.push_back({{"role", role_list[i]}, {"content", content_list[i]}});
        }
        common_json input = common_json::object();
        input["messages"] = messages;
        input["add_generation_prompt"] = add_generation_prompt == JNI_TRUE;
        input["enable_thinking"] = enable_thinking == JNI_TRUE;
        // Every value from the app is marked as input, so its text never becomes special tokens
        jinja::global_from_json(context, input, true);
        // BOS and EOS come from the model, not from the user
        context.set_val("bos_token", jinja::mk_val<jinja::value_string>(bos_text));
        context.set_val("eos_token", jinja::mk_val<jinja::value_string>(eos_text));

        jinja::runtime runtime(context);
        const jinja::value result = runtime.execute(program);
        auto gathered = jinja::runtime::gather_string_parts(result);
        parts = gathered->val_str.parts;
    } catch (const std::exception &) {
        // The template's own message may quote chat content, so it is not passed on
        throw_state(env, "Chat template could not be rendered");
        return nullptr;
    }

    jclass bytes_class = env->FindClass("[B");
    jobjectArray texts = env->NewObjectArray(static_cast<jsize>(parts.size()), bytes_class, nullptr);
    jbooleanArray flags = env->NewBooleanArray(static_cast<jsize>(parts.size()));
    if (texts == nullptr || flags == nullptr) return nullptr;
    for (size_t i = 0; i < parts.size(); i++) {
        jbyteArray text = to_bytes(env, parts[i].val);
        env->SetObjectArrayElement(texts, static_cast<jsize>(i), text);
        env->DeleteLocalRef(text);
        const jboolean flag = parts[i].is_input ? JNI_TRUE : JNI_FALSE;
        env->SetBooleanArrayRegion(flags, static_cast<jsize>(i), 1, &flag);
    }
    jobjectArray out = env->NewObjectArray(2, env->FindClass("java/lang/Object"), nullptr);
    if (out == nullptr) return nullptr;
    env->SetObjectArrayElement(out, 0, texts);
    env->SetObjectArrayElement(out, 1, flags);
    return out;
}

jint native_count_tokens(JNIEnv * env, jclass, jlong handle, jobjectArray parts, jbooleanArray is_input) {
    Session * s = as_session(handle);
    std::vector<llama_token> tokens;
    if (!tokenize_parts(s->vocab, to_strings(env, parts), to_bools(env, is_input), tokens)) return -1;
    return static_cast<jint>(tokens.size());
}

// Generates a reply and streams it to `sink`. Returns {finish reason, prompt tokens, completion tokens}.
jintArray native_generate(JNIEnv * env, jclass, jlong handle, jobjectArray parts, jbooleanArray is_input,
                          jint max_tokens, jfloat temperature, jfloat top_p, jint top_k, jfloat min_p,
                          jfloat repeat_penalty, jlong seed, jobject sink) {
    Session & s = *as_session(handle);
    s.abort.store(false);

    // Tokenize and check that the prompt leaves room for at least one reply token
    std::vector<llama_token> prompt;
    if (!tokenize_parts(s.vocab, to_strings(env, parts), to_bools(env, is_input), prompt) || prompt.empty()) {
        return finish(env, FINISH_DECODE_ERROR, 0, 0);
    }
    const uint32_t context_size = llama_n_ctx(s.ctx);
    if (prompt.size() >= context_size) return finish(env, FINISH_PROMPT_TOO_LONG, prompt.size(), 0);

    const jint prompt_status = process_prompt(s, prompt);
    if (prompt_status != FINISH_EOG) return finish(env, prompt_status, prompt.size(), 0);

    // Penalties look back at the end of the prompt, as llama-server does
    llama_sampler * sampler = build_sampler(s, temperature, top_p, top_k, min_p, repeat_penalty, seed);
    const size_t history = std::min<size_t>(prompt.size(), PENALTY_LAST_N);
    for (size_t i = prompt.size() - history; i < prompt.size(); i++) llama_sampler_accept(sampler, prompt[i]);

    // Decode loop: sample, stream the piece, feed the token back
    jint reason = FINISH_MAX_TOKENS;
    int generated = 0;
    std::string pending;
    while (generated < max_tokens) {
        if (s.abort.load()) { reason = FINISH_ABORTED; break; }
        if (s.cached.size() >= context_size) { reason = FINISH_CONTEXT_FULL; break; }
        llama_token token = llama_sampler_sample(sampler, s.ctx, -1);
        if (llama_vocab_is_eog(s.vocab, token)) { reason = FINISH_EOG; break; }
        pending += token_text(s.vocab, token, false);
        generated++;
        if (!deliver(env, sink, pending, false)) { reason = FINISH_SINK_STOP; break; }
        if (llama_decode(s.ctx, llama_batch_get_one(&token, 1)) != 0) {
            llama_memory_clear(llama_get_memory(s.ctx), true);
            s.cached.clear();
            reason = s.abort.load() ? FINISH_ABORTED : FINISH_DECODE_ERROR;
            break;
        }
        s.cached.push_back(token);
    }
    llama_sampler_free(sampler);
    if (env->ExceptionCheck()) return nullptr;
    if (reason != FINISH_ABORTED && reason != FINISH_SINK_STOP && !pending.empty()) {
        deliver(env, sink, pending, true);
        if (env->ExceptionCheck()) return nullptr;
    }
    return finish(env, reason, prompt.size(), generated);
}

void native_abort(JNIEnv *, jclass, jlong handle) {
    Session * session = as_session(handle);
    if (session != nullptr) session->abort.store(true);
}

const JNINativeMethod kMethods[] = {
    {"nativeInit", "(Ljava/lang/String;)V", reinterpret_cast<void *>(native_init)},
    {"nativeLoad", "(Ljava/lang/String;IIZ)J", reinterpret_cast<void *>(native_load)},
    {"nativeFree", "(J)V", reinterpret_cast<void *>(native_free)},
    {"nativeInfo", "(J)[[B", reinterpret_cast<void *>(native_info)},
    {"nativeRender", "([B[[B[[BZZ[B[B)[Ljava/lang/Object;", reinterpret_cast<void *>(native_render)},
    {"nativeCountTokens", "(J[[B[Z)I", reinterpret_cast<void *>(native_count_tokens)},
    {"nativeGenerate",
     "(J[[B[ZIFFIFFJLme/kafuuneko/rpclient/libs/llm/local/llamacpp/LlamaTokenSink;)[I",
     reinterpret_cast<void *>(native_generate)},
    {"nativeAbort", "(J)V", reinterpret_cast<void *>(native_abort)},
};

} // namespace

extern "C" JNIEXPORT jint JNI_OnLoad(JavaVM * vm, void *) {
    JNIEnv * env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) return JNI_ERR;
    jclass native_class = env->FindClass("me/kafuuneko/rpclient/libs/llm/local/llamacpp/LlamaCppNative");
    jclass sink_class = env->FindClass("me/kafuuneko/rpclient/libs/llm/local/llamacpp/LlamaTokenSink");
    if (native_class == nullptr || sink_class == nullptr) return JNI_ERR;
    g_sink_on_piece = env->GetMethodID(sink_class, "onPiece", "([B)Z");
    if (g_sink_on_piece == nullptr) return JNI_ERR;
    const auto count = static_cast<jint>(sizeof(kMethods) / sizeof(kMethods[0]));
    if (env->RegisterNatives(native_class, kMethods, count) != JNI_OK) return JNI_ERR;
    return JNI_VERSION_1_6;
}
