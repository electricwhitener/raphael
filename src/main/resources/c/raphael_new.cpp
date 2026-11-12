// Raphael's C++ runtime: every new and delete goes through raphael_rt.c's table of blocks, as
// malloc and free do in C, so an object made with new is drawn with its size, and one deleted is
// drawn as freed (and a second delete of it is caught). Replacing the global operator new and
// delete is allowed in any C++ program; this file is compiled once and linked into each one.
#include <cstddef>
#include <new>

extern "C" {
void *raphael_malloc(std::size_t size);
void raphael_free(void *block);
}

static void *allocate(std::size_t size) {
    void *block = raphael_malloc(size == 0 ? 1 : size);
    if (block == nullptr) {
        throw std::bad_alloc();
    }
    return block;
}

void *operator new(std::size_t size) { return allocate(size); }
void *operator new[](std::size_t size) { return allocate(size); }
void *operator new(std::size_t size, const std::nothrow_t &) noexcept { return raphael_malloc(size == 0 ? 1 : size); }
void *operator new[](std::size_t size, const std::nothrow_t &) noexcept { return raphael_malloc(size == 0 ? 1 : size); }
void operator delete(void *block) noexcept { if (block) raphael_free(block); }
void operator delete[](void *block) noexcept { if (block) raphael_free(block); }
void operator delete(void *block, std::size_t) noexcept { if (block) raphael_free(block); }
void operator delete[](void *block, std::size_t) noexcept { if (block) raphael_free(block); }
