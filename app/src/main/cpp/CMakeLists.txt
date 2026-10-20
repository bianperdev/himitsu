# SPDX-License-Identifier: GPL-3.0-or-later
#
# Native part of the feat/local-inference track: llama.cpp built from the pinned submodule in
# third_party/llama.cpp, plus a thin JNI layer. Nothing here downloads anything at build time.

cmake_minimum_required(VERSION 3.22.1)
project(himitsu_llama LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(THIRD_PARTY_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party)
set(LLAMA_SRC_DIR ${THIRD_PARTY_DIR}/llama.cpp)

# Strip absolute paths from the binaries, for reproducible builds (F-Droid).
add_compile_options("-ffile-prefix-map=${CMAKE_CURRENT_SOURCE_DIR}/../../../..=.")

# llama.cpp configuration
# - Shared libraries with the CPU backend as dynamically loaded variants: ggml picks the best
#   variant (dotprod, i8mm, SVE2, SME...) for the phone at runtime.
# - No llama-common: it links an HTTP client and Hugging Face download code. The chat template
#   engine is compiled on its own below.
# - OpenMP and llamafile off, as docs/android.md recommends. KleidiAI off for now: it is fetched
#   from the network at configure time.
set(BUILD_SHARED_LIBS     ON  CACHE BOOL "" FORCE)
set(LLAMA_BUILD_COMMON    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS     OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TOOLS     OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES  OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER    OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_APP       OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_UI        OFF CACHE BOOL "" FORCE)
set(LLAMA_CONNECT         OFF CACHE BOOL "" FORCE)
set(LLAMA_OPENSSL         OFF CACHE BOOL "" FORCE)
set(LLAMA_SUBPROCESS      OFF CACHE BOOL "" FORCE)
set(LLAMA_LLGUIDANCE      OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE           OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP           OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE        OFF CACHE BOOL "" FORCE)
set(GGML_CPU_KLEIDIAI     OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL       ON  CACHE BOOL "" FORCE)
set(GGML_CPU_ALL_VARIANTS ON  CACHE BOOL "" FORCE)

# GPU through OpenCL, arm64 only (Adreno). The Khronos ICD loader is built from source just to
# link against; it is not packaged (see app/build.gradle.kts), so at runtime libggml-opencl.so
# uses the phone's own libOpenCL.so, declared with <uses-native-library>. Phones without an
# OpenCL driver simply do not load the GPU backend. Prebuilt Adreno kernels stay off: they are
# a binary download.
if(ANDROID_ABI STREQUAL "arm64-v8a")
    set(OPENCL_ICD_LOADER_HEADERS_DIR ${THIRD_PARTY_DIR}/OpenCL-Headers CACHE PATH "" FORCE)
    set(OPENCL_ICD_LOADER_BUILD_TESTING OFF CACHE BOOL "" FORCE)
    set(ENABLE_OPENCL_LAYERS OFF CACHE BOOL "" FORCE)
    add_subdirectory(${THIRD_PARTY_DIR}/OpenCL-ICD-Loader opencl-icd-loader EXCLUDE_FROM_ALL)
    set(OpenCL_INCLUDE_DIR ${THIRD_PARTY_DIR}/OpenCL-Headers CACHE PATH "" FORCE)
    set(OpenCL_LIBRARY OpenCL CACHE STRING "" FORCE)
    set(GGML_OPENCL ON CACHE BOOL "" FORCE)
    set(GGML_OPENCL_USE_ADRENO_BIN_KERNELS OFF CACHE BOOL "" FORCE)
endif()

add_subdirectory(${LLAMA_SRC_DIR} llama.cpp)

# Chat template engine (Jinja) from llama.cpp's common/, without the rest of llama-common.
add_library(himitsu_jinja STATIC
    ${LLAMA_SRC_DIR}/common/jinja/caps.cpp
    ${LLAMA_SRC_DIR}/common/jinja/lexer.cpp
    ${LLAMA_SRC_DIR}/common/jinja/parser.cpp
    ${LLAMA_SRC_DIR}/common/jinja/runtime.cpp
    ${LLAMA_SRC_DIR}/common/jinja/string.cpp
    ${LLAMA_SRC_DIR}/common/jinja/value.cpp
    ${LLAMA_SRC_DIR}/common/json.cpp
    ${LLAMA_SRC_DIR}/common/unicode.cpp
)
target_include_directories(himitsu_jinja PUBLIC ${LLAMA_SRC_DIR}/common ${LLAMA_SRC_DIR}/vendor)
target_link_libraries(himitsu_jinja PRIVATE ggml)
set_target_properties(himitsu_jinja PROPERTIES POSITION_INDEPENDENT_CODE ON)

add_library(himitsu_llama SHARED himitsu_llama_jni.cpp)
target_link_libraries(himitsu_llama PRIVATE llama ggml himitsu_jinja)
# Only JNI_OnLoad is exported; the native methods are registered there.
target_compile_options(himitsu_llama PRIVATE -fvisibility=hidden -fvisibility-inlines-hidden)
target_link_options(himitsu_llama PRIVATE -Wl,--exclude-libs,ALL -Wl,--build-id=none)

# Himitsu: turns off SQLCipher's native logs (see the source). Plain C, no dependency besides libdl.
add_library(himitsu_sqlite_log SHARED himitsu_sqlite_log.c)
target_link_libraries(himitsu_sqlite_log PRIVATE dl)
target_compile_options(himitsu_sqlite_log PRIVATE -fvisibility=hidden)
target_link_options(himitsu_sqlite_log PRIVATE -Wl,--build-id=none)
