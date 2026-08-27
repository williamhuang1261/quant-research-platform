#include "arena_allocator.hpp"

#include <cstdlib>
#include <new>
#include <sys/mman.h>
#include <unistd.h>

namespace qrp {

namespace {

std::size_t page_size() {
    static const std::size_t size = static_cast<std::size_t>(sysconf(_SC_PAGESIZE));
    return size;
}

std::size_t round_up_to_page(std::size_t bytes) {
    const std::size_t page = page_size();
    return ((bytes + page - 1) / page) * page;
}

std::size_t align_up(std::size_t value, std::size_t align) {
    return (value + align - 1) & ~(align - 1);
}

}  // namespace

ArenaAllocator::ArenaAllocator(std::size_t capacity_bytes)
    : base_(nullptr),
      capacity_(round_up_to_page(capacity_bytes == 0 ? page_size() : capacity_bytes)),
      offset_(0) {
    void* region = mmap(nullptr, capacity_, PROT_READ | PROT_WRITE,
                         MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    if (region == MAP_FAILED) {
        throw std::bad_alloc();
    }
    base_ = static_cast<unsigned char*>(region);
}

ArenaAllocator::~ArenaAllocator() {
    if (base_ != nullptr) {
        munmap(base_, capacity_);
    }
}

void* ArenaAllocator::alloc(std::size_t size, std::size_t align) {
    const std::size_t aligned_offset = align_up(offset_, align);
    if (aligned_offset > capacity_ || size > capacity_ - aligned_offset) {
        return nullptr;
    }
    offset_ = aligned_offset + size;
    return base_ + aligned_offset;
}

void ArenaAllocator::reset() {
    offset_ = 0;
}

MallocAllocator::MallocAllocator(std::size_t capacity_bytes)
    : capacity_(capacity_bytes), used_(0) {}

MallocAllocator::~MallocAllocator() {
    reset();
}

void* MallocAllocator::alloc(std::size_t size, std::size_t align) {
    if (used_ > capacity_ || size > capacity_ - used_) {
        return nullptr;
    }
    const std::size_t effective_align = align < sizeof(void*) ? sizeof(void*) : align;
    void* ptr = nullptr;
    if (posix_memalign(&ptr, effective_align, size) != 0) {
        return nullptr;
    }
    used_ += size;
    live_.push_back(ptr);
    return ptr;
}

void MallocAllocator::reset() {
    for (void* ptr : live_) {
        std::free(ptr);
    }
    live_.clear();
    used_ = 0;
}

}  // namespace qrp
