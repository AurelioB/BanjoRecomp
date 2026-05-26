#pragma once

#include <memory>

#include "ultramodern/renderer_context.hpp"

namespace banjo::android {
std::unique_ptr<ultramodern::renderer::RendererContext> create_null_renderer_context();
}
