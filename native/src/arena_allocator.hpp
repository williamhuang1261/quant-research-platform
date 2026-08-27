// Page-aligned bump allocators for the native kernel's scratch buffers.
//
// Two implementations behind the same small interface (duck-typed, not
// virtual -- callers select between them with a runtime flag but do so
// through a template, so neither pays for a vtable it doesn't need):
//
//   ArenaAllocator  -- reserves one mmap'd region sized to whole pages and
//                      bumps a cursor through it. alloc() never touches the
//                      global heap; reset() rewinds the cursor so the same
//                      region serves many rounds of allocations.
//   MallocAllocator -- the same alloc/reset/capacity/used surface over
//                      malloc/free, so the two paths are interchangeable
//                      from a caller's point of view and only the
//                      allocation strategy differs.
//
// Neither implementation frees an individual allocation -- reset() is the
// only way back to empty. There is no free-list, because the kernel's use
// case (many same-shaped scratch buffers, one per draw, all dead by the
// time the next draw starts) never needs one.

#pragma once

#include <cstddef>
#include <vector>

namespace qrp {

class ArenaAllocator {
public:
    // Rounds capacity_bytes up to a whole number of pages and reserves that
    // many bytes with mmap. Throws std::bad_alloc if the mapping fails.
    explicit ArenaAllocator(std::size_t capacity_bytes);
    ~ArenaAllocator();

    ArenaAllocator(const ArenaAllocator&) = delete;
    ArenaAllocator& operator=(const ArenaAllocator&) = delete;

    // Returns a pointer to `size` bytes aligned to `align` (must be a power
    // of two), or nullptr if the arena has no room left -- never overruns
    // the mapped region.
    void* alloc(std::size_t size, std::size_t align = alignof(std::max_align_t));

    // Rewinds the bump cursor to the start of the arena. Does not unmap;
    // the same pages serve the next round of allocations.
    void reset();

    std::size_t capacity() const { return capacity_; }
    std::size_t used() const { return offset_; }

private:
    unsigned char* base_;
    std::size_t capacity_;
    std::size_t offset_;
};

class MallocAllocator {
public:
    // capacity_bytes is a soft budget only, tracked so this class reports
    // exhaustion the same way ArenaAllocator does -- malloc itself has no
    // fixed capacity.
    explicit MallocAllocator(std::size_t capacity_bytes);
    ~MallocAllocator();

    MallocAllocator(const MallocAllocator&) = delete;
    MallocAllocator& operator=(const MallocAllocator&) = delete;

    void* alloc(std::size_t size, std::size_t align = alignof(std::max_align_t));
    void reset();

    std::size_t capacity() const { return capacity_; }
    std::size_t used() const { return used_; }

private:
    std::size_t capacity_;
    std::size_t used_;
    std::vector<void*> live_;
};

}  // namespace qrp
