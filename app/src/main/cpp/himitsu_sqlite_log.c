// SPDX-License-Identifier: GPL-3.0-or-later
//
// Silences the native logs of SQLCipher (net.zetetic:sqlcipher-android), which Java cannot turn off:
// - SQLite's error log, which sqlcipher-android routes to Logcat under the "SQLiteLog" tag and
//   which can quote SQL text and database paths;
// - SQLCipher's own log (cipher_log), which also writes to Logcat.
// It must run after libsqlcipher.so is loaded and before any database is opened. SQLite's global
// configuration can only change while the library is shut down, and shutdown keeps every other
// setting (threading mode, heap limit) as sqlcipher-android configured it.

#include <dlfcn.h>
#include <jni.h>
#include <stddef.h>

#define HIMITSU_SQLITE_OK 0
#define HIMITSU_SQLITE_CONFIG_LOG 16

typedef int (*sqlite_void_fn)(void);
typedef int (*sqlite_config_fn)(int, ...);
typedef int (*sqlite_open_fn)(const char *, void **);
typedef int (*sqlite_exec_fn)(void *, const char *, void *, void *, char **);
typedef int (*sqlite_close_fn)(void *);

JNIEXPORT jboolean JNICALL
Java_me_kafuuneko_rpclient_libs_security_database_SqlCipherLog_nativeSilence(JNIEnv * env, jclass clazz) {
    (void) env;
    (void) clazz;
    // Only the copy that sqlcipher-android already loaded; never load a second one
    void * library = dlopen("libsqlcipher.so", RTLD_NOW | RTLD_NOLOAD);
    if (library == NULL) return JNI_FALSE;
    sqlite_void_fn shutdown = (sqlite_void_fn) dlsym(library, "sqlite3_shutdown");
    sqlite_config_fn config = (sqlite_config_fn) dlsym(library, "sqlite3_config");
    sqlite_void_fn initialize = (sqlite_void_fn) dlsym(library, "sqlite3_initialize");
    sqlite_open_fn open_db = (sqlite_open_fn) dlsym(library, "sqlite3_open");
    sqlite_exec_fn exec = (sqlite_exec_fn) dlsym(library, "sqlite3_exec");
    sqlite_close_fn close_db = (sqlite_close_fn) dlsym(library, "sqlite3_close");
    jboolean silenced = JNI_FALSE;
    if (shutdown != NULL && config != NULL && initialize != NULL && open_db != NULL && exec != NULL && close_db != NULL) {
        // A null callback removes the error log handler
        int logged = shutdown() == HIMITSU_SQLITE_OK &&
            config(HIMITSU_SQLITE_CONFIG_LOG, (void *) NULL, (void *) NULL) == HIMITSU_SQLITE_OK;
        int ready = initialize() == HIMITSU_SQLITE_OK;
        // The cipher log level is global; any connection can set it
        int quiet = 0;
        void * memory = NULL;
        if (ready && open_db(":memory:", &memory) == HIMITSU_SQLITE_OK) {
            quiet = exec(memory, "PRAGMA cipher_log_level = NONE;", NULL, NULL, NULL) == HIMITSU_SQLITE_OK;
        }
        if (memory != NULL) close_db(memory);
        silenced = logged && ready && quiet ? JNI_TRUE : JNI_FALSE;
    }
    dlclose(library);
    return silenced;
}
