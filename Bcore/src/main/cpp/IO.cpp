//
// Created by Milk on 4/10/21.
//

#include "IO.h"
#include "Log.h"
#include "BoxCore.h"
#include <string>
#include <vector>
#include <mutex>
#include <cstring>

static jmethodID getAbsolutePathMethodId = nullptr;

struct RelocateEntry {
    std::string targetPath;
    std::string relocatePath;
};

static std::vector<RelocateEntry> s_relocate_rules;
static std::mutex s_io_mutex;
static thread_local char tls_path_buffer[4096];

const char *IO::redirectPath(const char *__path) {
    if (__path == nullptr) return nullptr;

    std::lock_guard<std::mutex> lock(s_io_mutex);
    for (const auto &rule : s_relocate_rules) {
        if (strncmp(__path, rule.targetPath.c_str(), rule.targetPath.length()) == 0 && !strstr(__path, "/SdCard/")) {
            std::string path_str(__path);
            size_t pos = 0;
            while ((pos = path_str.find(rule.targetPath, pos)) != std::string::npos) {
                path_str.replace(pos, rule.targetPath.length(), rule.relocatePath);
                pos += rule.relocatePath.length();
            }

            // Copy to thread-local buffer to avoid heap memory leak and race conditions
            strncpy(tls_path_buffer, path_str.c_str(), sizeof(tls_path_buffer) - 1);
            tls_path_buffer[sizeof(tls_path_buffer) - 1] = '\0';
            return tls_path_buffer;
        }
    }
    return __path;
}

jstring IO::redirectPath(JNIEnv *env, jstring path) {
    return BoxCore::redirectPathString(env, path);
}

jobject IO::redirectPath(JNIEnv *env, jobject path) {
    return BoxCore::redirectPathFile(env, path);
}

void IO::addRule(const char *targetPath, const char *relocatePath) {
    if (targetPath == nullptr || relocatePath == nullptr) return;

    std::lock_guard<std::mutex> lock(s_io_mutex);
    s_relocate_rules.push_back({std::string(targetPath), std::string(relocatePath)});
}

void IO::init(JNIEnv *env) {
    jclass tmpFile = env->FindClass("java/io/File");
    if (tmpFile != nullptr) {
        getAbsolutePathMethodId = env->GetMethodID(tmpFile, "getAbsolutePath", "()Ljava/lang/String;");
        env->DeleteLocalRef(tmpFile);
    }
}
