# Builds the optional compute kernel.
#
# Optional is the point: a clone with no C++ toolchain, or no OpenMP runtime,
# still runs every test and every backtest on the Java engine. This Makefile
# therefore detects what is present and reports it rather than failing.
#
#   make          build the shared library into build/
#   make info     print what was detected, without building
#   make test     build and run the arena/malloc allocator correctness test
#   make bench    build and run the arena vs. malloc allocation benchmark
#   make clean    remove build/ (the platform falls back to Java)

UNAME_S := $(shell uname -s)
CXX ?= c++
CXXFLAGS := -std=c++17 -O3 -fPIC -Wall -Wextra
BUILD_DIR := build
LIB_STEM := libqrpkernel

ifeq ($(UNAME_S),Darwin)
  LIB_EXT := dylib
  SHARED_FLAG := -dynamiclib
  # Apple clang ships no OpenMP runtime; Homebrew's libomp supplies one.
  OMP_PREFIX := $(shell brew --prefix libomp 2>/dev/null)
  ifneq ($(wildcard $(OMP_PREFIX)/include/omp.h),)
    OMP_CXXFLAGS := -Xpreprocessor -fopenmp -I$(OMP_PREFIX)/include
    OMP_LDFLAGS := -L$(OMP_PREFIX)/lib -lomp -Wl,-rpath,$(OMP_PREFIX)/lib
    OMP_STATUS := yes (libomp at $(OMP_PREFIX))
  else
    OMP_CXXFLAGS := -Wno-unknown-pragmas
    OMP_LDFLAGS :=
    OMP_STATUS := no (install with: brew install libomp)
  endif
else
  LIB_EXT := so
  SHARED_FLAG := -shared
  OMP_CXXFLAGS := -fopenmp
  OMP_LDFLAGS := -fopenmp
  OMP_STATUS := yes (gcc/clang -fopenmp)
endif

LIB := $(BUILD_DIR)/$(LIB_STEM).$(LIB_EXT)
TEST_BIN := $(BUILD_DIR)/arena_allocator_test
BENCH_BIN := $(BUILD_DIR)/allocator_benchmark

.PHONY: all info clean test bench

all: $(LIB)

$(LIB): src/kernel.cpp src/arena_allocator.cpp src/arena_allocator.hpp | $(BUILD_DIR)
	$(CXX) $(CXXFLAGS) $(OMP_CXXFLAGS) $(SHARED_FLAG) -o $@ src/kernel.cpp src/arena_allocator.cpp $(OMP_LDFLAGS)
	@echo "built $@  (OpenMP: $(OMP_STATUS))"

# Standalone correctness test for the arena/malloc allocators (Extension 8).
# No OpenMP needed: this binary only exercises single-threaded allocation.
test: $(TEST_BIN)
	./$(TEST_BIN)

$(TEST_BIN): src/arena_allocator.cpp src/arena_allocator_test.cpp src/arena_allocator.hpp | $(BUILD_DIR)
	$(CXX) $(CXXFLAGS) -o $@ src/arena_allocator.cpp src/arena_allocator_test.cpp

# Arena vs. malloc allocation benchmark (Extension 8). Also no OpenMP: it
# measures single-threaded allocation cost, not the kernel's parallel loop.
bench: $(BENCH_BIN)
	./$(BENCH_BIN)

$(BENCH_BIN): src/arena_allocator.cpp src/allocator_benchmark.cpp src/arena_allocator.hpp | $(BUILD_DIR)
	$(CXX) $(CXXFLAGS) -o $@ src/arena_allocator.cpp src/allocator_benchmark.cpp

$(BUILD_DIR):
	@mkdir -p $(BUILD_DIR)

info:
	@echo "platform : $(UNAME_S)"
	@echo "compiler : $(CXX)"
	@echo "library  : $(LIB)"
	@echo "OpenMP   : $(OMP_STATUS)"

clean:
	rm -rf $(BUILD_DIR)
