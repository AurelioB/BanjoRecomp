#include "custom_driver_manager.hpp"

#include <android/log.h>
#include <cstdlib>
#include <dlfcn.h>
#include <fstream>
#include <mutex>
#include <system_error>
#include <utility>

#include <jni.h>
#include <SDL2/SDL_system.h>

#include <vulkan/vulkan.h>

#if defined(BANJO_ANDROID_CUSTOM_VULKAN_DRIVER)
#include <adrenotools/driver.h>
#endif

#include "json/json.hpp"

extern "C" VkResult volkInitialize();
extern "C" void volkInitializeCustom(PFN_vkGetInstanceProcAddr handler);

namespace banjo::android::custom_driver {
namespace {

constexpr const char* kLogTag = "BanjoGpuDriver";
constexpr const char* kEnvRoot = "BANJO_GPU_DRIVER_ROOT";
constexpr const char* kEnvTmp = "BANJO_GPU_DRIVER_TMP";
constexpr const char* kEnvNativeLibraryDir = "BANJO_NATIVE_LIBRARY_DIR";
constexpr const char* kEnvForceSystemDriver = "BANJO_FORCE_SYSTEM_DRIVER";
constexpr const char* kDefaultRootName = "gpu-drivers";
constexpr const char* kImportsDirName = "imports";
constexpr const char* kActiveJsonName = "active.json";
constexpr const char* kOpenPickerMethodName = "openGpuDriverFilePicker";
constexpr const char* kOpenPickerMethodSignature = "()V";

std::mutex g_status_mutex;
RuntimeStatus g_runtime_status{};

bool set_error(std::string* error, const std::string& message);

const char* mode_to_string(Mode mode) {
    switch (mode) {
    case Mode::System:
        return "system";
    case Mode::Custom:
        return "custom";
    }
    return "system";
}

Mode mode_from_string(const std::string& mode) {
    return mode == "custom" ? Mode::Custom : Mode::System;
}

const char* load_state_to_string(LoadState state) {
    switch (state) {
    case LoadState::NotAttempted:
        return "NotAttempted";
    case LoadState::Disabled:
        return "Disabled";
    case LoadState::LoadedCustom:
        return "LoadedCustom";
    case LoadState::FallbackSystem:
        return "FallbackSystem";
    case LoadState::Failed:
        return "Failed";
    }
    return "NotAttempted";
}

nlohmann::json selection_to_json(const Selection& selection) {
    return nlohmann::json{
        {"mode", mode_to_string(selection.mode)},
        {"driver_id", selection.driver_id},
        {"display_name", selection.display_name},
        {"driver_dir", selection.driver_dir},
        {"driver_soname", selection.driver_soname},
    };
}

Selection selection_from_json(const nlohmann::json& json) {
    Selection selection{};
    selection.mode = mode_from_string(json.value("mode", "system"));
    selection.driver_id = json.value("driver_id", std::string{});
    selection.display_name = json.value("display_name", std::string{});
    selection.driver_dir = json.value("driver_dir", std::string{});
    selection.driver_soname = json.value("driver_soname", std::string{});

    if (selection.mode == Mode::Custom && (selection.driver_dir.empty() || selection.driver_soname.empty())) {
        __android_log_print(ANDROID_LOG_WARN, kLogTag,
            "active.json requested custom driver but required fields are missing; using system driver");
        return {};
    }

    return selection;
}

bool path_is_within(const std::filesystem::path& path, const std::filesystem::path& parent) {
    auto path_it = path.begin();
    for (auto parent_it = parent.begin(); parent_it != parent.end(); ++parent_it, ++path_it) {
        if (path_it == path.end() || *path_it != *parent_it) {
            return false;
        }
    }
    return true;
}

bool validate_custom_selection(Selection* selection, std::string* error) {
    if (selection == nullptr || selection->mode != Mode::Custom) {
        return true;
    }
    if (selection->driver_dir.empty() || selection->driver_soname.empty()) {
        return set_error(error, "Custom driver selection is missing driver_dir or driver_soname");
    }

    const std::filesystem::path soname{selection->driver_soname};
    if (soname != soname.filename() || soname.empty() || soname == "." || soname == "..") {
        return set_error(error, "Custom driver soname must be a filename without path components");
    }

    std::error_code ec;
    const std::filesystem::path imports = std::filesystem::weakly_canonical(root_dir() / kImportsDirName, ec);
    if (ec) {
        return set_error(error, "Failed to resolve GPU driver imports directory: " + ec.message());
    }
    const std::filesystem::path driver_dir = std::filesystem::weakly_canonical(selection->driver_dir, ec);
    if (ec || !path_is_within(driver_dir, imports) || driver_dir == imports) {
        return set_error(error, "Custom driver directory is outside the managed imports directory");
    }
    const std::filesystem::path driver_library = driver_dir / soname;
    if (!std::filesystem::is_regular_file(driver_library, ec) || ec) {
        return set_error(error, "Custom driver library does not exist or is not a regular file: " + driver_library.string());
    }

    selection->driver_dir = driver_dir.string();
    return true;
}

bool set_error(std::string* error, const std::string& message) {
    if (error != nullptr) {
        *error = message;
    }
    return false;
}

std::string selection_label(const Selection& selection) {
    if (!selection.display_name.empty()) {
        return selection.display_name;
    }

    if (!selection.driver_id.empty()) {
        return selection.driver_id;
    }

    if (!selection.driver_soname.empty()) {
        return selection.driver_soname;
    }

    return "Custom driver";
}

VkResult initialize_system_vulkan_with_status(Selection selection, LoadState state, const std::string& message) {
    RuntimeStatus status{};
    status.selection = std::move(selection);
    status.load_state = state;
    status.message = message;
    status.loaded_driver_label = "System Default";
    set_runtime_status(std::move(status));

    __android_log_print(ANDROID_LOG_INFO, kLogTag, "%s", message.c_str());
    return volkInitialize();
}

VkResult fallback_to_system_vulkan(Selection selection, const std::string& reason) {
    const std::string message = "Custom Vulkan driver fallback to system driver: " + reason;
    return initialize_system_vulkan_with_status(std::move(selection), LoadState::FallbackSystem, message);
}

bool force_system_driver_requested() {
    const char* value = std::getenv(kEnvForceSystemDriver);
    if (value == nullptr || value[0] == '\0') {
        return false;
    }

    const std::string flag{value};
    return flag == "1" || flag == "true" || flag == "TRUE" || flag == "yes" || flag == "on";
}

} // namespace

std::filesystem::path root_dir() {
    if (const char* root = std::getenv(kEnvRoot); root != nullptr && root[0] != '\0') {
        return std::filesystem::path{root};
    }

    if (const char* app_folder = std::getenv("APP_FOLDER_PATH"); app_folder != nullptr && app_folder[0] != '\0') {
        return std::filesystem::path{app_folder}.parent_path() / kDefaultRootName;
    }

    return std::filesystem::path{kDefaultRootName};
}

std::filesystem::path active_json_path() {
    return root_dir() / kActiveJsonName;
}

Selection load_selection() {
    const std::filesystem::path path = active_json_path();
    std::ifstream input{path};
    if (!input.good()) {
        __android_log_print(ANDROID_LOG_INFO, kLogTag,
            "No %s found under %s; using system Vulkan driver",
            kActiveJsonName, root_dir().string().c_str());
        return {};
    }

    try {
        nlohmann::json json = nlohmann::json::parse(input, nullptr, true, true);
        Selection selection = selection_from_json(json);
        std::string validation_error;
        if (!validate_custom_selection(&selection, &validation_error)) {
            __android_log_print(ANDROID_LOG_WARN, kLogTag,
                "Rejected unsafe or stale active driver selection: %s; using system driver",
                validation_error.c_str());
            return {};
        }
        __android_log_print(ANDROID_LOG_INFO, kLogTag,
            "Loaded active driver selection mode=%s id=%s soname=%s",
            mode_to_string(selection.mode), selection.driver_id.c_str(), selection.driver_soname.c_str());
        return selection;
    } catch (const std::exception& e) {
        __android_log_print(ANDROID_LOG_WARN, kLogTag,
            "Failed to parse %s: %s; using system Vulkan driver", path.string().c_str(), e.what());
        return {};
    }
}

bool save_selection(const Selection& selection, std::string* error) {
    Selection validated_selection = selection;
    if (!validate_custom_selection(&validated_selection, error)) {
        return false;
    }
    std::error_code ec;
    const std::filesystem::path root = root_dir();
    std::filesystem::create_directories(root, ec);
    if (ec) {
        return set_error(error, "Failed to create " + root.string() + ": " + ec.message());
    }

    const std::filesystem::path path = active_json_path();
    const std::filesystem::path tmp_path = path.string() + ".tmp";
    {
        std::ofstream output{tmp_path, std::ios::trunc};
        if (!output.good()) {
            return set_error(error, "Failed to open " + tmp_path.string() + " for writing");
        }
        output << selection_to_json(validated_selection).dump(2) << '\n';
        if (!output.good()) {
            return set_error(error, "Failed to write " + tmp_path.string());
        }
    }

    std::filesystem::rename(tmp_path, path, ec);
    if (ec) {
        std::filesystem::remove(tmp_path);
        return set_error(error, "Failed to replace " + path.string() + ": " + ec.message());
    }

    __android_log_print(ANDROID_LOG_INFO, kLogTag,
        "Saved active driver selection mode=%s id=%s soname=%s to %s",
        mode_to_string(validated_selection.mode), validated_selection.driver_id.c_str(), validated_selection.driver_soname.c_str(), path.string().c_str());
    return true;
}

bool reset_to_system(std::string* error) {
    std::error_code ec;
    const std::filesystem::path path = active_json_path();
    if (std::filesystem::exists(path, ec)) {
        std::filesystem::remove(path, ec);
        if (ec) {
            return set_error(error, "Failed to remove " + path.string() + ": " + ec.message());
        }
    } else if (ec) {
        return set_error(error, "Failed to inspect " + path.string() + ": " + ec.message());
    }

    __android_log_print(ANDROID_LOG_INFO, kLogTag, "Reset custom driver selection to System Default");
    return true;
}

void request_driver_picker() {
    __android_log_print(ANDROID_LOG_INFO, kLogTag,
        "request_driver_picker calling BanjoSDLActivity.%s%s via SDL_AndroidGetActivity",
        kOpenPickerMethodName, kOpenPickerMethodSignature);

    JNIEnv* env = static_cast<JNIEnv*>(SDL_AndroidGetJNIEnv());
    if (env == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, kLogTag,
            "request_driver_picker failed: SDL_AndroidGetJNIEnv returned null");
        return;
    }

    jobject activity = static_cast<jobject>(SDL_AndroidGetActivity());
    if (activity == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, kLogTag,
            "request_driver_picker failed: SDL_AndroidGetActivity returned null");
        return;
    }

    jclass activity_class = env->GetObjectClass(activity);
    if (activity_class == nullptr) {
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        }
        env->DeleteLocalRef(activity);
        __android_log_print(ANDROID_LOG_ERROR, kLogTag,
            "request_driver_picker failed: unable to get BanjoSDLActivity class");
        return;
    }

    jmethodID open_picker = env->GetMethodID(activity_class, kOpenPickerMethodName, kOpenPickerMethodSignature);
    if (open_picker == nullptr) {
        if (env->ExceptionCheck()) {
            env->ExceptionClear();
        }
        env->DeleteLocalRef(activity_class);
        env->DeleteLocalRef(activity);
        __android_log_print(ANDROID_LOG_ERROR, kLogTag,
            "request_driver_picker failed: missing BanjoSDLActivity.%s%s",
            kOpenPickerMethodName, kOpenPickerMethodSignature);
        return;
    }

    env->CallVoidMethod(activity, open_picker);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        __android_log_print(ANDROID_LOG_ERROR, kLogTag,
            "request_driver_picker failed: exception from BanjoSDLActivity.%s%s",
            kOpenPickerMethodName, kOpenPickerMethodSignature);
    } else {
        __android_log_print(ANDROID_LOG_INFO, kLogTag,
            "request_driver_picker dispatched BanjoSDLActivity.%s%s",
            kOpenPickerMethodName, kOpenPickerMethodSignature);
    }

    env->DeleteLocalRef(activity_class);
    env->DeleteLocalRef(activity);
}

bool reset_to_system_from_ui(std::string* error) {
    RuntimeStatus status = get_runtime_status();
    if (!reset_to_system(error)) {
        __android_log_print(ANDROID_LOG_ERROR, kLogTag,
            "reset_to_system_from_ui failed: %s", error != nullptr ? error->c_str() : "unknown error");
        return false;
    }

    status.selection = {};
    status.message = "System Default selected; restart required";
    set_runtime_status(std::move(status));

    __android_log_print(ANDROID_LOG_INFO, kLogTag,
        "reset_to_system_from_ui cleared active selection; restart required");
    return true;
}

void set_runtime_status(RuntimeStatus status) {
    __android_log_print(ANDROID_LOG_INFO, kLogTag,
        "Runtime status state=%s selected=%s loaded=%s message=%s device=%s vendor=0x%08x device_id=0x%08x driver=0x%08x",
        load_state_to_string(status.load_state),
        mode_to_string(status.selection.mode),
        status.loaded_driver_label.c_str(),
        status.message.c_str(),
        status.physical_device_name.c_str(),
        status.vendor_id,
        status.device_id,
        status.driver_version);

    std::lock_guard lock{g_status_mutex};
    g_runtime_status = std::move(status);
}

RuntimeStatus get_runtime_status() {
    std::lock_guard lock{g_status_mutex};
    return g_runtime_status;
}

void record_selected_vulkan_device(const char* device_name, uint32_t vendor_id, uint32_t device_id, uint32_t driver_version) {
    RuntimeStatus status{};
    {
        std::lock_guard lock{g_status_mutex};
        status = g_runtime_status;
        status.physical_device_name = device_name != nullptr ? device_name : "";
        status.vendor_id = vendor_id;
        status.device_id = device_id;
        status.driver_version = driver_version;

        if (status.loaded_driver_label.empty()) {
            status.loaded_driver_label = "System Default";
        }

        g_runtime_status = status;
    }

    __android_log_print(ANDROID_LOG_INFO, kLogTag,
        "Selected Vulkan physical device: name=%s vendor=0x%08x device_id=0x%08x driverVersion=0x%08x",
        status.physical_device_name.c_str(),
        status.vendor_id,
        status.device_id,
        status.driver_version);
    __android_log_print(ANDROID_LOG_INFO, kLogTag,
        "Device Name: %s", status.physical_device_name.c_str());
    __android_log_print(ANDROID_LOG_INFO, kLogTag,
        "Driver Version: 0x%08x", status.driver_version);
    __android_log_print(ANDROID_LOG_INFO, kLogTag,
        "Loaded: %s state=%s selected=%s message=%s",
        status.loaded_driver_label.c_str(),
        load_state_to_string(status.load_state),
        mode_to_string(status.selection.mode),
        status.message.c_str());
}

void log_environment() {
    const char* root = std::getenv(kEnvRoot);
    const char* tmp = std::getenv(kEnvTmp);
    const char* native_library_dir = std::getenv(kEnvNativeLibraryDir);
    const char* force_system_driver = std::getenv(kEnvForceSystemDriver);

#if defined(BANJO_ANDROID_CUSTOM_VULKAN_DRIVER)
    auto* adrenotools_open_fn = reinterpret_cast<void*>(&adrenotools_open_libvulkan);
#else
    void* adrenotools_open_fn = nullptr;
#endif

    __android_log_print(ANDROID_LOG_INFO, kLogTag, "AdrenoTools custom Vulkan driver support=%s feature_flag=0x%x",
        adrenotools_custom_driver_feature_flag() != 0 ? "enabled" : "disabled",
        adrenotools_custom_driver_feature_flag());
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "AdrenoTools open_libvulkan entry=%p", adrenotools_open_fn);
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "%s=%s", kEnvRoot, root != nullptr ? root : "<unset>");
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "%s=%s", kEnvTmp, tmp != nullptr ? tmp : "<unset>");
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "%s=%s", kEnvNativeLibraryDir, native_library_dir != nullptr ? native_library_dir : "<unset>");
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "%s=%s", kEnvForceSystemDriver, force_system_driver != nullptr ? force_system_driver : "<unset>");
    __android_log_print(ANDROID_LOG_INFO, kLogTag, "active.json path=%s", active_json_path().string().c_str());
}

int initialize_vulkan_loader_for_volk() {
    if (force_system_driver_requested()) {
        return static_cast<int>(initialize_system_vulkan_with_status(
            {},
            LoadState::Disabled,
            "BANJO_FORCE_SYSTEM_DRIVER requested; ignoring active custom Vulkan driver selection and using system Vulkan driver"));
    }
    return initialize_vulkan_loader_for_volk_with_selection(load_selection());
}

int initialize_vulkan_loader_for_volk_with_selection(Selection selection) {
    if (force_system_driver_requested()) {
        return static_cast<int>(initialize_system_vulkan_with_status(
            std::move(selection),
            LoadState::Disabled,
            "BANJO_FORCE_SYSTEM_DRIVER requested; ignoring active custom Vulkan driver selection and using system Vulkan driver"));
    }

    if (selection.mode != Mode::Custom) {
        return static_cast<int>(initialize_system_vulkan_with_status(
            std::move(selection),
            LoadState::Disabled,
            "Custom Vulkan driver disabled; using system Vulkan driver"));
    }

#if defined(BANJO_ANDROID_CUSTOM_VULKAN_DRIVER)
    const char* tmp_dir = std::getenv(kEnvTmp);
    if (tmp_dir == nullptr || tmp_dir[0] == '\0') {
        return static_cast<int>(fallback_to_system_vulkan(std::move(selection), std::string{kEnvTmp} + " is not set"));
    }

    const char* native_library_dir = std::getenv(kEnvNativeLibraryDir);
    if (native_library_dir == nullptr || native_library_dir[0] == '\0') {
        return static_cast<int>(fallback_to_system_vulkan(std::move(selection), std::string{kEnvNativeLibraryDir} + " is not set"));
    }

    if (selection.driver_dir.empty() || selection.driver_soname.empty()) {
        return static_cast<int>(fallback_to_system_vulkan(std::move(selection), "active custom driver selection is missing driver_dir or driver_soname"));
    }

    std::string validation_error;
    if (!validate_custom_selection(&selection, &validation_error)) {
        return static_cast<int>(fallback_to_system_vulkan(std::move(selection), validation_error));
    }

    // AdrenoTools concatenates this argument with the soname rather than joining
    // path components, so it requires a trailing directory separator.
    std::string driver_dir = selection.driver_dir;
    if (driver_dir.back() != '/') {
        driver_dir.push_back('/');
    }

    const std::string selected_label = selection_label(selection);
    __android_log_print(ANDROID_LOG_INFO, kLogTag,
        "Attempting custom Vulkan driver load label=%s dir=%s soname=%s tmp=%s nativeLibraryDir=%s",
        selected_label.c_str(), selection.driver_dir.c_str(), selection.driver_soname.c_str(), tmp_dir, native_library_dir);

    void* lib_vulkan = adrenotools_open_libvulkan(
        RTLD_NOW | RTLD_LOCAL,
        ADRENOTOOLS_DRIVER_CUSTOM,
        tmp_dir,
        native_library_dir,
        driver_dir.c_str(),
        selection.driver_soname.c_str(),
        nullptr,
        nullptr);

    if (lib_vulkan == nullptr) {
        const char* error = dlerror();
        return static_cast<int>(fallback_to_system_vulkan(
            std::move(selection),
            std::string{"adrenotools_open_libvulkan failed"} + (error != nullptr ? std::string{": "} + error : std::string{})));
    }

    dlerror();
    auto* get_instance_proc_addr = reinterpret_cast<PFN_vkGetInstanceProcAddr>(
        dlsym(lib_vulkan, "vkGetInstanceProcAddr"));
    const char* dlsym_error = dlerror();
    if (get_instance_proc_addr == nullptr) {
        dlclose(lib_vulkan);
        return static_cast<int>(fallback_to_system_vulkan(
            std::move(selection),
            std::string{"custom libvulkan is missing vkGetInstanceProcAddr"} +
                (dlsym_error != nullptr ? std::string{": "} + dlsym_error : std::string{})));
    }

    RuntimeStatus status{};
    status.selection = selection;
    status.load_state = LoadState::LoadedCustom;
    status.message = "Custom Vulkan driver loaded: " + selected_label;
    status.loaded_driver_label = selected_label;
    set_runtime_status(std::move(status));

    __android_log_print(ANDROID_LOG_INFO, kLogTag,
        "Loaded custom Vulkan driver label=%s via AdrenoTools; initializing volk with custom vkGetInstanceProcAddr=%p",
        selected_label.c_str(), reinterpret_cast<void*>(get_instance_proc_addr));
    volkInitializeCustom(get_instance_proc_addr);
    return static_cast<int>(VK_SUCCESS);
#else
    return static_cast<int>(fallback_to_system_vulkan(std::move(selection), "BANJO_ANDROID_CUSTOM_VULKAN_DRIVER is not enabled"));
#endif
}

int adrenotools_custom_driver_feature_flag() {
#if defined(BANJO_ANDROID_CUSTOM_VULKAN_DRIVER)
    return ADRENOTOOLS_DRIVER_CUSTOM;
#else
    return 0;
#endif
}

} // namespace banjo::android::custom_driver
