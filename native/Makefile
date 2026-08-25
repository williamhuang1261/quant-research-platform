# Builds the optional compute kernel.
#
# Optional is the point: a clone with no C++ toolchain, or no OpenMP runtime,
# still runs every test and every backtest on the Java engine. This Makefile
# therefore detects what is present and reports it rather than failing.
#
#   make          build the shared library into build/
#   make info     print what was detected, without building
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

.PHONY: all info clean

all: $(LIB)

$(LIB): src/kernel.cpp | $(BUILD_DIR)
	$(CXX) $(CXXFLAGS) $(OMP_CXXFLAGS) $(SHARED_FLAG) -o $@ $< $(OMP_LDFLAGS)
	@echo "built $@  (OpenMP: $(OMP_STATUS))"

$(BUILD_DIR):
	@mkdir -p $(BUILD_DIR)

info:
	@echo "platform : $(UNAME_S)"
	@echo "compiler : $(CXX)"
	@echo "library  : $(LIB)"
	@echo "OpenMP   : $(OMP_STATUS)"

clean:
	rm -rf $(BUILD_DIR)
