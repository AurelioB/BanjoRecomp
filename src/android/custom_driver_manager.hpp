#pragma once

#include <cstdint>
#include <filesystem>
#include <string>

namespace banjo::android::custom_driver {

enum class Mode {
    System,
    Custom,
};

enum class LoadState {
    NotAttempted,
    Disabled,
    LoadedCustom,
    FallbackSystem,
    Failed,
};

struct Selection {
    Mode mode = Mode::System;
    std::string driver_id;
    std::string display_name;
    std::string driver_dir;
    std::string driver_soname;
};

struct RuntimeStatus {
    Selection selection;
    LoadState load_state = LoadState::NotAttempted;
    std::string message;
    std::string loaded_driver_label;
    std::string physical_device_name;
    uint32_t vendor_id = 0;
    uint32_t device_id = 0;
    uint32_t driver_version = 0;
};

std::filesystem::path root_dir();
std::filesystem::path active_json_path();
Selection load_selection();
bool save_selection(const Selection& selection, std::string* error);
bool reset_to_system(std::string* error);
void request_driver_picker();
bool reset_to_system_from_ui(std::string* error);
void set_runtime_status(RuntimeStatus status);
RuntimeStatus get_runtime_status();
void record_selected_vulkan_device(const char* device_name, uint32_t vendor_id, uint32_t device_id, uint32_t driver_version);
void log_environment();
int initialize_vulkan_loader_for_volk();
int initialize_vulkan_loader_for_volk_with_selection(Selection selection);
int adrenotools_custom_driver_feature_flag();

} // namespace banjo::android::custom_driver
