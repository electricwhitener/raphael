/* Raphael's runtime shim, linked into every C program Raphael runs. The debugger never shows its
   lines (they aren't in the student's files).
   - When Raphael names them (RAPHAEL_STDIN, RAPHAEL_STDOUT), the program reads its input from a
     file and writes its output (stdout and stderr) to another, so the debugger's own messages
     never mix with what the program prints.
   - With the terminal (RAPHAEL_STDIN_PIPE), the input comes through a pipe instead, which the
     shim makes and Raphael writes each typed line into, so a read waits for the user as in a
     terminal: a FIFO on Linux and macOS, a named pipe on Windows.
   - stdout's buffer is turned off, so what the program prints shows up at the step that printed
     it, even before a crash. (On Windows, line buffering is full buffering, so the buffer is
     turned off rather than set to lines.)
   - malloc and the rest (through raphael_rt.h) keep a table of the blocks, which the debugger
     reads at each step. New memory is filled with RAPHAEL_UNSET, so a field nobody has set yet
     shows as ?. A freed block isn't given back while the program runs (up to RAPHAEL_KEEP bytes),
     so it can be drawn as freed and a pointer still holding it shows as dangling. */
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#ifdef _WIN32
#include <windows.h>
#include <fcntl.h>
#include <io.h>
#define dup2 _dup2
#define fileno _fileno
#else
#include <errno.h>
#include <sys/stat.h>
#include <unistd.h>
#endif

#define RAPHAEL_MAX_BLOCKS 4096
#define RAPHAEL_UNSET 0xBE
#define RAPHAEL_KEEP (64u * 1024 * 1024)

struct raphael_block {
    void *address;
    size_t size;
    int freed;
};

struct raphael_block raphael_blocks[RAPHAEL_MAX_BLOCKS];
int raphael_block_count;
static size_t kept; /* bytes of freed blocks not given back */

/* stdin from a pipe Raphael writes into; 0 when it's open. Waits for Raphael to connect. */
static int raphael_pipe_input(const char *name) {
#ifdef _WIN32
    HANDLE pipe = CreateNamedPipeA(name, PIPE_ACCESS_INBOUND, PIPE_TYPE_BYTE | PIPE_READMODE_BYTE | PIPE_WAIT,
                                   1, 0, 1 << 16, 0, NULL);
    if (pipe == INVALID_HANDLE_VALUE) {
        return -1;
    }
    if (!ConnectNamedPipe(pipe, NULL) && GetLastError() != ERROR_PIPE_CONNECTED) {
        CloseHandle(pipe);
        return -1;
    }
    int fd = _open_osfhandle((intptr_t) pipe, _O_RDONLY | _O_TEXT);
    if (fd < 0 || _dup2(fd, 0) != 0) {
        return -1;
    }
    _close(fd);
    clearerr(stdin);
    return 0;
#else
    if (mkfifo(name, 0600) != 0 && errno != EEXIST) {
        return -1;
    }
    return freopen(name, "r", stdin) == NULL ? -1 : 0; /* opens once Raphael has opened its end */
#endif
}

__attribute__((constructor)) static void raphael_start(void) {
    const char *in = getenv("RAPHAEL_STDIN");
    const char *pipe = getenv("RAPHAEL_STDIN_PIPE");
    const char *out = getenv("RAPHAEL_STDOUT");
    if (pipe != NULL) {
        if (raphael_pipe_input(pipe) != 0) {
            fclose(stdin); /* no pipe: reading finds the end at once */
        }
    } else if (in != NULL && freopen(in, "r", stdin) == NULL) {
        fclose(stdin); /* the input file is missing: reading finds the end at once */
    }
    if (out != NULL && freopen(out, "w", stdout) != NULL) {
        dup2(fileno(stdout), fileno(stderr)); /* one file, in the order things were printed */
    }
    setvbuf(stdout, NULL, _IONBF, 0);
}

/* The newest block at this address, freed or not. */
static struct raphael_block *find(void *address) {
    for (int i = raphael_block_count - 1; i >= 0; i--) {
        if (raphael_blocks[i].address == address) {
            return &raphael_blocks[i];
        }
    }
    return NULL;
}

static void *remember(void *address, size_t size) {
    if (address != NULL && raphael_block_count < RAPHAEL_MAX_BLOCKS) {
        raphael_blocks[raphael_block_count].address = address;
        raphael_blocks[raphael_block_count].size = size;
        raphael_blocks[raphael_block_count].freed = 0;
        raphael_block_count++;
    }
    return address;
}

void *raphael_malloc(size_t size) {
    void *address = malloc(size == 0 ? 1 : size);
    if (address != NULL) {
        memset(address, RAPHAEL_UNSET, size);
    }
    return remember(address, size);
}

void *raphael_calloc(size_t count, size_t size) {
    return remember(calloc(count == 0 ? 1 : count, size == 0 ? 1 : size), count * size);
}

void raphael_free(void *address) {
    struct raphael_block *block = address == NULL ? NULL : find(address);
    if (block == NULL) {
        free(address); /* NULL, or not one of ours: as plain C would */
        return;
    }
    if (block->freed) { /* plain C would crash here too, or worse, carry on with a broken heap */
        fprintf(stderr, "free(): double free of a block\n");
        abort();
    }
    block->freed = 1;
    if (kept + block->size > RAPHAEL_KEEP) {
        free(address);
        block->address = NULL; /* given back: it may be reused, so it's forgotten */
    } else {
        kept += block->size;
    }
}

/* Always a new block: realloc may move a block, and this shows that it can. */
void *raphael_realloc(void *address, size_t size) {
    struct raphael_block *old = address == NULL ? NULL : find(address);
    void *moved = raphael_malloc(size);
    if (moved != NULL && address != NULL) {
        size_t old_size = old != NULL ? old->size : size;
        memcpy(moved, address, old_size < size ? old_size : size);
        raphael_free(address);
    }
    return moved;
}

char *raphael_strdup(const char *text) {
    size_t size = strlen(text) + 1;
    char *copy = raphael_malloc(size);
    if (copy != NULL) {
        memcpy(copy, text, size);
    }
    return copy;
}
