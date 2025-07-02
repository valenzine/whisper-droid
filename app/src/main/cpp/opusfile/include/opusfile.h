// Minimal stub for opusfile.h for build wiring. Replace with real opusfile.h from official source.
#ifndef OPUSFILE_H
#define OPUSFILE_H

#include <opus.h>
#include <ogg/ogg.h>

#ifdef __cplusplus
extern "C" {
#endif

typedef struct OggOpusFile OggOpusFile;
OggOpusFile *op_open_file(const char *path, int *error);
void op_free(OggOpusFile *of);
int op_read(OggOpusFile *of, opus_int16 *pcm, int buf_size, int *li);
int op_channel_count(OggOpusFile *of, int li);

#ifdef __cplusplus
}
#endif

#endif // OPUSFILE_H
