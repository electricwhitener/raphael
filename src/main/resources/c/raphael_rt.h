/* Raphael's runtime header, put in front of every C program with gcc's -include (after the
   program has been checked on its own, so a missing #include is still an error).
   It sends malloc, calloc, realloc, free and strdup to raphael_rt.c, which keeps a table of every
   block, so Raphael can draw a malloc'd buffer as an array of the right length and a freed
   block as freed. */
#ifndef RAPHAEL_RT_H
#define RAPHAEL_RT_H
#include <stdlib.h>
#include <string.h>

void *raphael_malloc(size_t size);
void *raphael_calloc(size_t count, size_t size);
void *raphael_realloc(void *block, size_t size);
void raphael_free(void *block);
char *raphael_strdup(const char *text);

#define malloc(size) raphael_malloc(size)
#define calloc(count, size) raphael_calloc(count, size)
#define realloc(block, size) raphael_realloc(block, size)
#define free(block) raphael_free(block)
#define strdup(text) raphael_strdup(text)
#endif
