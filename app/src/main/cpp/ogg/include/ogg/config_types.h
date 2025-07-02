#ifndef __OGG_CONFIG_TYPES_H__
#define __OGG_CONFIG_TYPES_H__

#if defined(__LP64__) || defined(_LP64)
typedef long ogg_int64_t;
#else
typedef long long ogg_int64_t;
#endif
typedef int ogg_int32_t;
typedef unsigned int ogg_uint32_t;
typedef short ogg_int16_t;
typedef unsigned short ogg_uint16_t;
typedef unsigned long long ogg_uint64_t;

#endif
