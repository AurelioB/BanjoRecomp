#include "null_renderer_context.hpp"

namespace banjo::android {
namespace {
class NullRendererContext final : public ultramodern::renderer::RendererContext {
public:
    NullRendererContext() {
        setup_result = ultramodern::renderer::SetupResult::Success;
        chosen_api = ultramodern::renderer::GraphicsApi::Vulkan;
    }

    bool valid() override { return true; }
    bool update_config(const ultramodern::renderer::GraphicsConfig&, const ultramodern::renderer::GraphicsConfig&) override { return false; }
    void enable_instant_present() override {}
    void send_dl(const OSTask*) override {}
    void send_dummy_workload(uint32_t) override {}
    void update_screen() override {}
    void shutdown() override {}
    uint32_t get_display_framerate() const override { return 60; }
    float get_resolution_scale() const override { return 1.0f; }
};
} // namespace

std::unique_ptr<ultramodern::renderer::RendererContext> create_null_renderer_context() {
    return std::make_unique<NullRendererContext>();
}
} // namespace banjo::android
