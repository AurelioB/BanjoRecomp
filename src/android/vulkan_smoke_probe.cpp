#include <android/log.h>
#include <SDL2/SDL.h>
#include <SDL2/SDL_vulkan.h>
#include <volk.h>

#include <algorithm>
#include <atomic>
#include <thread>
#include <vector>

namespace {
constexpr const char* TAG = "BanjoVkSmoke";
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

const char* vkres(VkResult r) {
    switch (r) {
        case VK_SUCCESS: return "VK_SUCCESS";
        case VK_NOT_READY: return "VK_NOT_READY";
        case VK_TIMEOUT: return "VK_TIMEOUT";
        case VK_ERROR_INITIALIZATION_FAILED: return "VK_ERROR_INITIALIZATION_FAILED";
        case VK_ERROR_DEVICE_LOST: return "VK_ERROR_DEVICE_LOST";
        case VK_ERROR_SURFACE_LOST_KHR: return "VK_ERROR_SURFACE_LOST_KHR";
        case VK_ERROR_NATIVE_WINDOW_IN_USE_KHR: return "VK_ERROR_NATIVE_WINDOW_IN_USE_KHR";
        case VK_SUBOPTIMAL_KHR: return "VK_SUBOPTIMAL_KHR";
        case VK_ERROR_OUT_OF_DATE_KHR: return "VK_ERROR_OUT_OF_DATE_KHR";
        default: return "VK_OTHER";
    }
}

VkCompositeAlphaFlagBitsKHR pick_alpha(VkCompositeAlphaFlagsKHR flags) {
    if (flags & VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR) return VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR;
    if (flags & VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR) return VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR;
    if (flags & VK_COMPOSITE_ALPHA_PRE_MULTIPLIED_BIT_KHR) return VK_COMPOSITE_ALPHA_PRE_MULTIPLIED_BIT_KHR;
    return VK_COMPOSITE_ALPHA_POST_MULTIPLIED_BIT_KHR;
}
}

extern "C" int android_vulkan_smoke_main() {
    LOGI("entered");
    if (SDL_Init(SDL_INIT_VIDEO) != 0) {
        LOGE("SDL_Init failed: %s", SDL_GetError());
        return 1;
    }
    if (SDL_Vulkan_LoadLibrary(nullptr) != 0) {
        LOGE("SDL_Vulkan_LoadLibrary failed: %s", SDL_GetError());
        return 2;
    }
    VkResult volk_res = volkInitialize();
    if (volk_res != VK_SUCCESS) {
        LOGE("volkInitialize failed: %s", vkres(volk_res));
        return 20;
    }

    SDL_Window* window = SDL_CreateWindow("Banjo Vulkan Smoke", SDL_WINDOWPOS_CENTERED, SDL_WINDOWPOS_CENTERED, 1280, 720, SDL_WINDOW_SHOWN | SDL_WINDOW_RESIZABLE | SDL_WINDOW_VULKAN);
    if (!window) {
        LOGE("SDL_CreateWindow failed: %s", SDL_GetError());
        return 3;
    }

    std::atomic<bool> done{false};
    int result = 0;
    std::thread worker([&]() {
        auto run_vk = [&]() -> int {
    unsigned ext_count = 0;
    if (!SDL_Vulkan_GetInstanceExtensions(window, &ext_count, nullptr)) {
        LOGE("SDL_Vulkan_GetInstanceExtensions count failed: %s", SDL_GetError());
        return 4;
    }
    std::vector<const char*> exts(ext_count);
    if (!SDL_Vulkan_GetInstanceExtensions(window, &ext_count, exts.data())) {
        LOGE("SDL_Vulkan_GetInstanceExtensions failed: %s", SDL_GetError());
        return 5;
    }
    for (unsigned i = 0; i < ext_count; i++) LOGI("instance ext[%u]=%s", i, exts[i]);

    VkApplicationInfo app{VK_STRUCTURE_TYPE_APPLICATION_INFO};
    app.pApplicationName = "BanjoVkSmoke";
    app.apiVersion = VK_API_VERSION_1_0;
    VkInstanceCreateInfo ici{VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO};
    ici.pApplicationInfo = &app;
    ici.enabledExtensionCount = ext_count;
    ici.ppEnabledExtensionNames = exts.data();
    VkInstance instance = VK_NULL_HANDLE;
    VkResult res = vkCreateInstance(&ici, nullptr, &instance);
    if (res != VK_SUCCESS) { LOGE("vkCreateInstance %s", vkres(res)); return 6; }
    volkLoadInstance(instance);

    VkSurfaceKHR surface = VK_NULL_HANDLE;
    if (!SDL_Vulkan_CreateSurface(window, instance, &surface)) {
        LOGE("SDL_Vulkan_CreateSurface failed: %s", SDL_GetError());
        return 7;
    }

    uint32_t pd_count = 0;
    vkEnumeratePhysicalDevices(instance, &pd_count, nullptr);
    std::vector<VkPhysicalDevice> pds(pd_count);
    vkEnumeratePhysicalDevices(instance, &pd_count, pds.data());
    if (pds.empty()) { LOGE("no physical devices"); return 8; }
    VkPhysicalDevice pd = pds[0];

    uint32_t q_count = 0;
    vkGetPhysicalDeviceQueueFamilyProperties(pd, &q_count, nullptr);
    std::vector<VkQueueFamilyProperties> qprops(q_count);
    vkGetPhysicalDeviceQueueFamilyProperties(pd, &q_count, qprops.data());
    uint32_t qfam = UINT32_MAX;
    for (uint32_t i = 0; i < q_count; i++) {
        VkBool32 present = VK_FALSE;
        vkGetPhysicalDeviceSurfaceSupportKHR(pd, i, surface, &present);
        if ((qprops[i].queueFlags & VK_QUEUE_GRAPHICS_BIT) && present) { qfam = i; break; }
    }
    if (qfam == UINT32_MAX) { LOGE("no graphics+present queue"); return 9; }

    float priority = 1.0f;
    VkDeviceQueueCreateInfo qci{VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO};
    qci.queueFamilyIndex = qfam;
    qci.queueCount = 1;
    qci.pQueuePriorities = &priority;
    const char* dev_ext = VK_KHR_SWAPCHAIN_EXTENSION_NAME;
    VkDeviceCreateInfo dci{VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO};
    dci.queueCreateInfoCount = 1;
    dci.pQueueCreateInfos = &qci;
    dci.enabledExtensionCount = 1;
    dci.ppEnabledExtensionNames = &dev_ext;
    VkDevice dev = VK_NULL_HANDLE;
    res = vkCreateDevice(pd, &dci, nullptr, &dev);
    if (res != VK_SUCCESS) { LOGE("vkCreateDevice %s", vkres(res)); return 10; }
    volkLoadDevice(dev);
    VkQueue queue = VK_NULL_HANDLE;
    vkGetDeviceQueue(dev, qfam, 0, &queue);

    VkSurfaceCapabilitiesKHR caps{};
    vkGetPhysicalDeviceSurfaceCapabilitiesKHR(pd, surface, &caps);
    LOGI("caps extent=%ux%u minImages=%u maxImages=%u usage=0x%x transform=0x%x alpha=0x%x", caps.currentExtent.width, caps.currentExtent.height, caps.minImageCount, caps.maxImageCount, caps.supportedUsageFlags, caps.currentTransform, caps.supportedCompositeAlpha);
    uint32_t fmt_count = 0;
    vkGetPhysicalDeviceSurfaceFormatsKHR(pd, surface, &fmt_count, nullptr);
    std::vector<VkSurfaceFormatKHR> fmts(fmt_count);
    vkGetPhysicalDeviceSurfaceFormatsKHR(pd, surface, &fmt_count, fmts.data());
    VkSurfaceFormatKHR fmt = fmts[0];
    for (auto f : fmts) if (f.format == VK_FORMAT_B8G8R8A8_UNORM) { fmt = f; break; }
    LOGI("picked format=%u colorspace=%u", fmt.format, fmt.colorSpace);

    uint32_t image_count = caps.minImageCount + 1;
    if (caps.maxImageCount && image_count > caps.maxImageCount) image_count = caps.maxImageCount;
    VkSwapchainCreateInfoKHR sci{VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR};
    sci.surface = surface;
    sci.minImageCount = image_count;
    sci.imageFormat = fmt.format;
    sci.imageColorSpace = fmt.colorSpace;
    sci.imageExtent = caps.currentExtent;
    sci.imageArrayLayers = 1;
    sci.imageUsage = VK_IMAGE_USAGE_TRANSFER_DST_BIT;
    sci.imageSharingMode = VK_SHARING_MODE_EXCLUSIVE;
    sci.preTransform = caps.currentTransform;
    sci.compositeAlpha = pick_alpha(caps.supportedCompositeAlpha);
    sci.presentMode = VK_PRESENT_MODE_FIFO_KHR;
    sci.clipped = VK_TRUE;
    VkSwapchainKHR swap = VK_NULL_HANDLE;
    res = vkCreateSwapchainKHR(dev, &sci, nullptr, &swap);
    if (res != VK_SUCCESS) { LOGE("vkCreateSwapchainKHR %s", vkres(res)); return 11; }

    uint32_t actual_count = 0;
    vkGetSwapchainImagesKHR(dev, swap, &actual_count, nullptr);
    std::vector<VkImage> images(actual_count);
    vkGetSwapchainImagesKHR(dev, swap, &actual_count, images.data());
    std::vector<VkImageLayout> layouts(actual_count, VK_IMAGE_LAYOUT_UNDEFINED);
    LOGI("swapchain images=%u", actual_count);

    VkCommandPoolCreateInfo cpci{VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO};
    cpci.flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
    cpci.queueFamilyIndex = qfam;
    VkCommandPool pool = VK_NULL_HANDLE;
    vkCreateCommandPool(dev, &cpci, nullptr, &pool);
    VkCommandBufferAllocateInfo cbai{VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO};
    cbai.commandPool = pool;
    cbai.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
    cbai.commandBufferCount = 1;
    VkCommandBuffer cmd = VK_NULL_HANDLE;
    vkAllocateCommandBuffers(dev, &cbai, &cmd);
    VkSemaphoreCreateInfo semci{VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO};
    VkSemaphore acquired = VK_NULL_HANDLE, rendered = VK_NULL_HANDLE;
    vkCreateSemaphore(dev, &semci, nullptr, &acquired);
    vkCreateSemaphore(dev, &semci, nullptr, &rendered);
    VkFenceCreateInfo fci{VK_STRUCTURE_TYPE_FENCE_CREATE_INFO};
    VkFence fence = VK_NULL_HANDLE;
    vkCreateFence(dev, &fci, nullptr, &fence);

    const Uint32 start = SDL_GetTicks();
    SDL_Event ev;
    while (SDL_GetTicks() - start < 8000) {
        while (SDL_PollEvent(&ev)) {}
        uint32_t idx = 0;
        res = vkAcquireNextImageKHR(dev, swap, UINT64_MAX, acquired, VK_NULL_HANDLE, &idx);
        if (res != VK_SUCCESS && res != VK_SUBOPTIMAL_KHR) { LOGE("acquire %s", vkres(res)); break; }
        vkResetCommandBuffer(cmd, 0);
        VkCommandBufferBeginInfo bi{VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO};
        vkBeginCommandBuffer(cmd, &bi);
        VkImageMemoryBarrier b1{VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER};
        b1.srcAccessMask = 0;
        b1.dstAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
        b1.oldLayout = layouts[idx];
        b1.newLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
        b1.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        b1.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        b1.image = images[idx];
        b1.subresourceRange.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
        b1.subresourceRange.levelCount = 1;
        b1.subresourceRange.layerCount = 1;
        vkCmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT, 0, 0, nullptr, 0, nullptr, 1, &b1);
        VkClearColorValue color{};
        color.float32[0] = 0.15f; color.float32[1] = 0.0f; color.float32[2] = 0.35f; color.float32[3] = 1.0f;
        VkImageSubresourceRange range{};
        range.aspectMask = VK_IMAGE_ASPECT_COLOR_BIT;
        range.levelCount = 1;
        range.layerCount = 1;
        vkCmdClearColorImage(cmd, images[idx], VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, &color, 1, &range);
        VkImageMemoryBarrier b2 = b1;
        b2.srcAccessMask = VK_ACCESS_TRANSFER_WRITE_BIT;
        b2.dstAccessMask = 0;
        b2.oldLayout = VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL;
        b2.newLayout = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
        vkCmdPipelineBarrier(cmd, VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, 0, 0, nullptr, 0, nullptr, 1, &b2);
        vkEndCommandBuffer(cmd);
        layouts[idx] = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR;
        VkPipelineStageFlags wait_stage = VK_PIPELINE_STAGE_TRANSFER_BIT;
        VkSubmitInfo submit{VK_STRUCTURE_TYPE_SUBMIT_INFO};
        submit.waitSemaphoreCount = 1;
        submit.pWaitSemaphores = &acquired;
        submit.pWaitDstStageMask = &wait_stage;
        submit.commandBufferCount = 1;
        submit.pCommandBuffers = &cmd;
        submit.signalSemaphoreCount = 1;
        submit.pSignalSemaphores = &rendered;
        vkResetFences(dev, 1, &fence);
        res = vkQueueSubmit(queue, 1, &submit, fence);
        if (res != VK_SUCCESS) { LOGE("submit %s", vkres(res)); break; }
        vkWaitForFences(dev, 1, &fence, VK_TRUE, UINT64_MAX);
        VkPresentInfoKHR pi{VK_STRUCTURE_TYPE_PRESENT_INFO_KHR};
        pi.waitSemaphoreCount = 1;
        pi.pWaitSemaphores = &rendered;
        pi.swapchainCount = 1;
        pi.pSwapchains = &swap;
        pi.pImageIndices = &idx;
        res = vkQueuePresentKHR(queue, &pi);
        if (res != VK_SUCCESS && res != VK_SUBOPTIMAL_KHR) { LOGE("present %s", vkres(res)); break; }
        SDL_Delay(16);
    }
    vkDeviceWaitIdle(dev);
    LOGI("worker completed");
    return 0;
        };
        result = run_vk();
        done.store(true);
    });

    SDL_Event main_ev;
    while (!done.load()) {
        while (SDL_PollEvent(&main_ev)) {}
        SDL_Delay(16);
    }
    worker.join();
    LOGI("completed result=%d", result);
    return result;
}
