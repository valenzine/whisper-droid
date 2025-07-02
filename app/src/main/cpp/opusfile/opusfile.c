// Minimal stub for opusfile.c for build wiring. Replace with real opusfile.c from official source.
#include "include/opusfile.h"
#include <stdio.h>
#include <string.h>  // For memcmp
#include <android/log.h>

#define LOG_TAG "opusfile_stub"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Define error codes matching those in the WhisperDroid codebase
#define OP_EREAD -128
#define OP_EFAULT -129
#define OP_EIMPL -130
#define OP_EINVAL -131
#define OP_ENOTFORMAT -132
#define OP_EBADHEADER -133
#define OP_EVERSION -134

OggOpusFile *op_open_file(const char *path, int *error) { 
    FILE* file = fopen(path, "rb");
    if (!file) {
        LOGE("opusfile stub: Cannot open file %s", path);
        if (error) *error = OP_EREAD;
        return NULL;
    }
    
    // Check for OggS magic signature
    char header[4];
    if (fread(header, 1, 4, file) != 4 || memcmp(header, "OggS", 4) != 0) {
        LOGE("opusfile stub: Not a valid Ogg file (missing OggS signature)");
        fclose(file);
        if (error) *error = OP_ENOTFORMAT;
        return NULL;
    }
    
    fclose(file);
    
    LOGE("opusfile stub: Using stub implementation of opusfile. Real opusfile library not linked!");
    if (error) *error = OP_EIMPL;
    return NULL; 
}

void op_free(OggOpusFile *of) {
    // Nothing to do in stub
}

int op_read(OggOpusFile *of, opus_int16 *pcm, int buf_size, int *li) { 
    LOGE("opusfile stub: op_read called on stub implementation");
    return -1;  // Error
}

int op_channel_count(OggOpusFile *of, int li) { 
    LOGE("opusfile stub: op_channel_count called on stub implementation");
    return 1; 
}
