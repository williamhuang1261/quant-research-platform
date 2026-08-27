// Assert-based correctness test for the arena and malloc-backed allocators.
//
// No test framework dependency: `make test` builds and runs this binary,
// and a non-zero exit code is the failure signal, matching how the rest of
// the native build is verified in this repo -- by actually running it, not
// by assuming it compiled.

#include "arena_allocator.hpp"

#include <cstdint>
#include <cstdio>
#include <initializer_list>

namespace {

int failures = 0;

#define CHECK(cond)                                                     \
    do {                                                                \
        if (!(cond)) {                                                  \
            std::fprintf(stderr, "FAIL %s:%d: %s\n", __FILE__, __LINE__, \
                          #cond);                                       \
            ++failures;                                                 \
        }                                                               \
    } while (0)

template <typename Alloc>
void test_basic_allocation(const char* name) {
    Alloc alloc(4096);
    void* p = alloc.alloc(64, 8);
    CHECK(p != nullptr);
    CHECK(reinterpret_cast<std::uintptr_t>(p) % 8 == 0);
    std::fprintf(stderr, "ok: %s basic allocation\n", name);
}

template <typename Alloc>
void test_alignment(const char* name) {
    Alloc alloc(4096);
    for (std::size_t align : {8u, 16u, 32u, 64u}) {
        void* p = alloc.alloc(16, align);
        CHECK(p != nullptr);
        CHECK(reinterpret_cast<std::uintptr_t>(p) % align == 0);
    }
    std::fprintf(stderr, "ok: %s alignment (8/16/32/64)\n", name);
}

template <typename Alloc>
void test_exhaustion(const char* name) {
    // ArenaAllocator rounds capacity up to a whole page, so a request sized
    // relative to capacity() (not a literal like 256) is what actually
    // guarantees exhaustion for both implementations.
    Alloc alloc(256);
    void* p1 = alloc.alloc(alloc.capacity() / 2, 8);
    CHECK(p1 != nullptr);
    void* p2 = alloc.alloc(alloc.capacity() + 1, 8);
    CHECK(p2 == nullptr);
    std::fprintf(stderr, "ok: %s exhaustion returns nullptr, not a crash\n", name);
}

void test_arena_reset_reuses_identical_offsets() {
    qrp::ArenaAllocator arena(4096);
    void* first_a = arena.alloc(64, 8);
    void* first_b = arena.alloc(32, 8);
    arena.reset();
    void* second_a = arena.alloc(64, 8);
    void* second_b = arena.alloc(32, 8);
    CHECK(first_a == second_a);
    CHECK(first_b == second_b);
    std::fprintf(stderr, "ok: arena reset reuses identical offsets\n");
}

void test_malloc_reset_frees_and_reopens_capacity() {
    qrp::MallocAllocator alloc(256);
    void* p1 = alloc.alloc(200, 8);
    CHECK(p1 != nullptr);
    CHECK(alloc.used() == 200);
    alloc.reset();
    CHECK(alloc.used() == 0);
    void* p2 = alloc.alloc(200, 8);
    CHECK(p2 != nullptr);
    std::fprintf(stderr, "ok: malloc allocator reset frees and reopens capacity\n");
}

}  // namespace

int main() {
    test_basic_allocation<qrp::ArenaAllocator>("arena");
    test_basic_allocation<qrp::MallocAllocator>("malloc");
    test_alignment<qrp::ArenaAllocator>("arena");
    test_alignment<qrp::MallocAllocator>("malloc");
    test_exhaustion<qrp::ArenaAllocator>("arena");
    test_exhaustion<qrp::MallocAllocator>("malloc");
    test_arena_reset_reuses_identical_offsets();
    test_malloc_reset_frees_and_reopens_capacity();

    if (failures > 0) {
        std::fprintf(stderr, "%d check(s) failed\n", failures);
        return 1;
    }
    std::fprintf(stderr, "all checks passed\n");
    return 0;
}
