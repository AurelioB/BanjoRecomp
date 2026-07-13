#pragma once

#include <string>

namespace banjo::android::save_storage {

void register_frontend_provider();
void request_import();
void request_export();
void request_folder();
void reset_folder();
std::string location();
std::string operation_status();

} // namespace banjo::android::save_storage
