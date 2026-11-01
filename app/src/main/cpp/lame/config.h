/* Hand-written replacement for the configure-generated config.h (Android, clang, ARM). */
#ifndef LAME_CONFIG_H
#define LAME_CONFIG_H

#define STDC_HEADERS 1
#define HAVE_ERRNO_H 1
#define HAVE_FCNTL_H 1
#define HAVE_LIMITS_H 1
#define HAVE_STDINT_H 1
#define HAVE_INTTYPES_H 1
#define HAVE_STRING_H 1
#define HAVE_UNISTD_H 1

typedef float ieee754_float32_t;
typedef double ieee754_float64_t;

/* ARM floats are IEEE 754: LAME's faster quantization and log paths are safe. */
#define TAKEHIRO_IEEE754_HACK 1
#define USE_FAST_LOG 1

#endif
