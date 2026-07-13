#include "save_storage_manager.hpp"

#include "recompui/config.h"
#include "ultramodern/ultramodern.hpp"

#include <SDL.h>
#include <jni.h>

#include <fstream>
#include <mutex>
#include <vector>

namespace banjo::android::save_storage {
namespace {
std::mutex state_mutex;
std::string current_location = "App storage";
std::string current_status = "Ready";

void call_activity(const char* method) {
    auto* env = static_cast<JNIEnv*>(SDL_AndroidGetJNIEnv());
    jobject activity = static_cast<jobject>(SDL_AndroidGetActivity());
    if (!env || !activity) return;
    jclass cls = env->GetObjectClass(activity);
    jmethodID id = cls ? env->GetMethodID(cls, method, "()V") : nullptr;
    if (id) env->CallVoidMethod(activity, id);
    if (env->ExceptionCheck()) env->ExceptionClear();
    if (cls) env->DeleteLocalRef(cls);
    env->DeleteLocalRef(activity);
}

void update_state(std::string status, std::string location_text = {}) {
    {
        std::lock_guard lock{state_mutex};
        current_status = std::move(status);
        if (!location_text.empty()) current_location = std::move(location_text);
    }
}
}

void request_import() { call_activity("openSaveImportPicker"); }
void request_export() { call_activity("openSaveExportPicker"); }
void request_folder() { call_activity("openSaveFolderPicker"); }
void reset_folder() { call_activity("resetSaveFolder"); }

std::string location() { std::lock_guard lock{state_mutex}; return current_location; }
std::string operation_status() { std::lock_guard lock{state_mutex}; return current_status; }

void register_frontend_provider() {
    recompui::config::saves::set_settings_provider({
        .location = location,
        .operation_status = operation_status,
        .import_save = request_import,
        .export_save = request_export,
        .choose_folder = request_folder,
        .reset_folder = reset_folder,
    });
}

void on_operation(std::string status, std::string location_text) {
    update_state(std::move(status), std::move(location_text));
}
} // namespace banjo::android::save_storage

extern "C" JNIEXPORT jboolean JNICALL
Java_io_github_banjorecomp_BanjoSDLActivity_nativePrepareSaveExport(JNIEnv* env, jclass, jstring path) {
    if (!path) return JNI_FALSE;
    const char* chars = env->GetStringUTFChars(path, nullptr);
    std::vector<uint8_t> snapshot;
    bool ok = ultramodern::snapshot_save_file(snapshot);
    if (ok) {
        std::ofstream out(chars, std::ios::binary | std::ios::trunc);
        out.write(reinterpret_cast<const char*>(snapshot.data()), snapshot.size());
        ok = out.good();
    }
    env->ReleaseStringUTFChars(path, chars);
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_io_github_banjorecomp_BanjoSDLActivity_nativeImportSave(JNIEnv* env, jclass, jstring path) {
    if (!path) return JNI_FALSE;
    const char* chars = env->GetStringUTFChars(path, nullptr);
    std::ifstream in(chars, std::ios::binary | std::ios::ate);
    const auto size = in.good() ? in.tellg() : std::streampos{-1};
    const size_t expected = ultramodern::get_save_file_size();
    bool ok = size >= 0 && static_cast<size_t>(size) == expected;
    std::vector<uint8_t> data(expected);
    if (ok) {
        in.seekg(0);
        in.read(reinterpret_cast<char*>(data.data()), data.size());
        ok = in.good() || in.eof();
    }
    if (ok) ok = ultramodern::import_save_file(data);
    env->ReleaseStringUTFChars(path, chars);
    return ok ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_io_github_banjorecomp_BanjoSDLActivity_nativeOnSaveOperation(JNIEnv* env, jclass, jstring status, jstring location) {
    auto read = [env](jstring value) {
        if (!value) return std::string{};
        const char* chars = env->GetStringUTFChars(value, nullptr);
        std::string result = chars;
        env->ReleaseStringUTFChars(value, chars);
        return result;
    };
    banjo::android::save_storage::on_operation(read(status), read(location));
}
