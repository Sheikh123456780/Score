//
// Native path redirection used by hCore IOCore.
//

#include "IO.h"
#include "Log.h"

#include <cstdlib>
#include <cstring>
#include <mutex>
#include <string>
#include <utility>

jmethodID getAbsolutePathMethodId = nullptr;

static std::list<IO::RelocateInfo> relocate_rule;
static std::mutex relocate_rule_mutex;

static std::string replaceAll(const std::string &value,
                              const std::string &source,
                              const std::string &destination) {
    if (source.empty() || value.empty() || value.find(source) == std::string::npos) {
        return value;
    }

    std::string result;
    result.reserve(value.size() + destination.size());
    std::string::size_type cursor = 0;
    while (cursor < value.size()) {
        std::string::size_type match = value.find(source, cursor);
        if (match == std::string::npos) {
            result.append(value, cursor, std::string::npos);
            break;
        }
        result.append(value, cursor, match - cursor);
        result.append(destination);
        cursor = match + source.size();
    }
    return result;
}

const char *IO::redirectPath(const char *__path) {
    if (__path == nullptr || *__path == '\0') {
        return __path;
    }

    // The returned buffer is thread-local because callers use the pointer only
    // for the duration of the current native call. This avoids leaking every
    // redirected path while remaining safe across WebView worker threads.
    thread_local std::string redirectedPath;
    redirectedPath.assign(__path);

    std::lock_guard<std::mutex> lock(relocate_rule_mutex);
    for (const IO::RelocateInfo &info : relocate_rule) {
        if (info.targetPath.empty() || info.relocatePath.empty()) {
            continue;
        }
        if (redirectedPath.find(info.targetPath) == std::string::npos) {
            continue;
        }
        redirectedPath = replaceAll(redirectedPath, info.targetPath, info.relocatePath);
    }
    return redirectedPath.c_str();
}

jstring IO::redirectPath(JNIEnv *env, jstring path) {
    return BoxCore::redirectPathString(env, path);
}

jobject IO::redirectPath(JNIEnv *env, jobject path) {
    return BoxCore::redirectPathFile(env, path);
}

void IO::addRule(const char *targetPath, const char *relocatePath) {
    if (targetPath == nullptr || relocatePath == nullptr ||
        *targetPath == '\0' || *relocatePath == '\0') {
        return;
    }

    std::lock_guard<std::mutex> lock(relocate_rule_mutex);
    for (const IO::RelocateInfo &existing : relocate_rule) {
        if (existing.targetPath == targetPath && existing.relocatePath == relocatePath) {
            return;
        }
    }

    // Copy the strings before BoxCore releases GetStringUTFChars(). Never keep
    // JNI-owned char pointers in the global rule list.
    IO::RelocateInfo info;
    info.targetPath.assign(targetPath);
    info.relocatePath.assign(relocatePath);
    relocate_rule.push_back(std::move(info));
}

void IO::init(JNIEnv *env) {
    if (env == nullptr) {
        return;
    }
    jclass tmpFile = env->FindClass("java/io/File");
    if (tmpFile == nullptr) {
        return;
    }
    getAbsolutePathMethodId = env->GetMethodID(
            tmpFile, "getAbsolutePath", "()Ljava/lang/String;");
    env->DeleteLocalRef(tmpFile);
}
