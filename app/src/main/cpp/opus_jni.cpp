#include <jni.h>
#include <string>
#include <android/log.h>
#include <ogg/ogg.h>
#include <opus.h>
#include <opusfile.h>
#include <cstdio>
#include <vector>
#include <cstring>
#include <errno.h>
#include <inttypes.h> // For PRId64 format specifier
#include <aacenc_lib.h>  // FDK-AAC encoder

// Define Opus error codes if not available
#ifndef OP_EREAD
#define OP_EREAD (-128)
#define OP_EFAULT (-129)
#define OP_EIMPL (-130)
#define OP_EINVAL (-131)
#define OP_ENOTFORMAT (-132)
#define OP_EBADHEADER (-133)
#define OP_EVERSION (-134)
#endif

#define LOG_TAG "opus_jni"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Helper functions for writing big-endian values
void writeBE32(FILE* file, uint32_t value) {
    uint32_t be_value = __builtin_bswap32(value);
    fwrite(&be_value, 4, 1, file);
}

void writeBE16(FILE* file, uint16_t value) {
    uint16_t be_value = __builtin_bswap16(value);
    fwrite(&be_value, 2, 1, file);
}

// MP4/M4A container writer for AAC data
class MP4Writer {
public:
    MP4Writer(const char* filePath, int channels, int sampleRate, int frameSize) : 
        file(nullptr), 
        mdatOffset(0), 
        mdatSize(0), 
        numberOfSamples(0),
        channels(channels),
        sampleRate(sampleRate),
        frameSize(frameSize) {
        
        file = fopen(filePath, "wb");
        if (!file) {
            LOGE("Failed to open output file: %s (errno: %d)", filePath, errno);
            return;
        }
        
        // Write ftyp box (file type)
        uint32_t ftypSize = 24; // 8 bytes header + 16 bytes data
        writeBE32(file, ftypSize);
        fwrite("ftyp", 1, 4, file);
        
        // Major brand
        fwrite("M4A ", 1, 4, file);
        
        // Minor version
        uint32_t minorVersion = 0;
        fwrite(&minorVersion, 4, 1, file);
        
        // Compatible brands
        fwrite("M4A mp42isom", 1, 12, file);
        
        // Write mdat box header - will be updated later with the correct size
        fwrite("\x00\x00\x00\x08mdat", 1, 8, file);
        mdatOffset = ftell(file);
        
        LOGI("MP4Writer initialized, mdat offset: %ld", mdatOffset);
    }
    
    ~MP4Writer() {
        if (file) {
            finalize();
            fclose(file);
            file = nullptr;
        }
    }
    
    bool isValid() const {
        return file != nullptr;
    }
    
    void writeAACSample(const void* data, size_t size) {
        if (!file) return;
        
        size_t written = fwrite(data, 1, size, file);
        if (written != size) {
            LOGE("Failed to write AAC sample: wrote %zu of %zu bytes (errno: %d)", 
                 written, size, errno);
            return;
        }
        
        aacSizes.push_back(size);
        numberOfSamples++;
        
        if (numberOfSamples % 100 == 0) {
            LOGI("Wrote %d AAC samples so far", numberOfSamples);
        }
    }
    
    void finalize() {
        if (!file || numberOfSamples == 0) {
            LOGE("Cannot finalize MP4: file=%p, samples=%d", file, numberOfSamples);
            return;
        }
        
        // Remember current position (end of mdat)
        long currentPos = ftell(file);
        mdatSize = currentPos - mdatOffset;
        
        LOGI("Finalizing MP4: %d samples, mdat size: %ld bytes", numberOfSamples, mdatSize);
        
        // Write moov box
        writeMovBox();
        
        // Update mdat box size
        fseek(file, mdatOffset - 8, SEEK_SET);
        uint32_t mdatBoxSize = mdatSize + 8;
        writeBE32(file, mdatBoxSize);
        
        // Go back to end of file
        fseek(file, 0, SEEK_END);
        LOGI("MP4 file finalized successfully with %d AAC samples, mdat size: %ld bytes", 
             numberOfSamples, mdatSize);
    }
    
private:
    FILE* file;
    long mdatOffset;
    long mdatSize;
    int numberOfSamples;
    int channels;
    int sampleRate;
    int frameSize; // AAC frame size
    std::vector<uint32_t> aacSizes; // Store sizes of each AAC frame
    
    void writeMovBox() {
        // moov box - container for all metadata
        long moovStart = ftell(file);
        writeBE32(file, 0); // Placeholder for size
        fwrite("moov", 1, 4, file);
        
        // mvhd box - movie header
        writeMvhdBox();
        
        // trak box - container for a single track
        writeTrakBox();
        
        // Update moov box size
        long currentPos = ftell(file);
        long moovSize = currentPos - moovStart;
        fseek(file, moovStart, SEEK_SET);
        writeBE32(file, moovSize);
        fseek(file, currentPos, SEEK_SET);
    }
    
    void writeMvhdBox() {
        // mvhd box
        writeBE32(file, 108); // Size
        fwrite("mvhd", 1, 4, file);
        
        uint8_t version = 0;
        uint32_t flags = 0;
        fwrite(&version, 1, 1, file);
        writeBE32(file, flags & 0xFFFFFF); // 3 bytes of flags
        
        uint32_t creationTime = 0;
        uint32_t modificationTime = 0;
        uint32_t timeScale = sampleRate; // Use audio sample rate
        uint32_t duration = frameSize * numberOfSamples; // Total samples
        
        writeBE32(file, creationTime);
        writeBE32(file, modificationTime);
        writeBE32(file, timeScale);
        writeBE32(file, duration);
        
        // Rate (1.0 = normal)
        uint32_t rate = 0x00010000;
        writeBE32(file, rate);
        
        // Volume (1.0 = full)
        uint16_t volume = 0x0100;
        writeBE16(file, volume);
        
        // Reserved
        uint8_t reserved[10] = {0};
        fwrite(reserved, 1, 10, file);
        
        // Matrix
        uint32_t matrix[9] = {
            0x00010000, 0, 0,
            0, 0x00010000, 0,
            0, 0, 0x40000000
        };
        for (int i = 0; i < 9; i++) {
            writeBE32(file, matrix[i]);
        }
        
        // Pre-defined
        uint8_t preDefined[24] = {0};
        fwrite(preDefined, 1, 24, file);
        
        // Next track ID
        uint32_t nextTrackId = 2;
        writeBE32(file, nextTrackId);
    }
    
    void writeTrakBox() {
        // trak box - container for a single track
        long trakStart = ftell(file);
        writeBE32(file, 0); // Placeholder
        fwrite("trak", 1, 4, file);
        
        // tkhd box - track header
        writeTkhdBox();
        
        // mdia box - media data container
        writeMdiaBox();
        
        // Update trak box size
        long currentPos = ftell(file);
        long trakSize = currentPos - trakStart;
        fseek(file, trakStart, SEEK_SET);
        writeBE32(file, trakSize);
        fseek(file, currentPos, SEEK_SET);
    }
    
    void writeTkhdBox() {
        // tkhd box - track header
        writeBE32(file, 92); // Size
        fwrite("tkhd", 1, 4, file);
        
        uint8_t version = 0;
        uint32_t flags = 0x000003; // Track enabled
        fwrite(&version, 1, 1, file);
        writeBE32(file, flags & 0xFFFFFF); // 3 bytes of flags
        
        uint32_t creationTime = 0;
        uint32_t modificationTime = 0;
        uint32_t trackId = 1;
        uint32_t reserved = 0;
        uint32_t duration = frameSize * numberOfSamples;
        
        writeBE32(file, creationTime);
        writeBE32(file, modificationTime);
        writeBE32(file, trackId);
        writeBE32(file, reserved);
        writeBE32(file, duration);
        
        // Reserved
        uint8_t reserved2[8] = {0};
        fwrite(reserved2, 1, 8, file);
        
        // Layer, alternate group
        uint16_t layer = 0;
        uint16_t alternateGroup = 0;
        writeBE16(file, layer);
        writeBE16(file, alternateGroup);
        
        // Volume (1.0 for audio)
        uint16_t volume = 0x0100;
        writeBE16(file, volume);
        
        // Reserved
        uint16_t reserved3 = 0;
        writeBE16(file, reserved3);
        
        // Matrix
        uint32_t matrix[9] = {
            0x00010000, 0, 0,
            0, 0x00010000, 0,
            0, 0, 0x40000000
        };
        for (int i = 0; i < 9; i++) {
            writeBE32(file, matrix[i]);
        }
        
        // Width and height (irrelevant for audio)
        uint32_t width = 0;
        uint32_t height = 0;
        writeBE32(file, width);
        writeBE32(file, height);
    }
    
    void writeMdiaBox() {
        // mdia box - media data container
        long mdiaStart = ftell(file);
        writeBE32(file, 0); // Placeholder
        fwrite("mdia", 1, 4, file);
        
        // mdhd box - media header
        writeMdhdBox();
        
        // hdlr box - handler reference
        writeHdlrBox();
        
        // minf box - media information
        writeMinfBox();
        
        // Update mdia box size
        long currentPos = ftell(file);
        long mdiaSize = currentPos - mdiaStart;
        fseek(file, mdiaStart, SEEK_SET);
        writeBE32(file, mdiaSize);
        fseek(file, currentPos, SEEK_SET);
    }
    
    void writeMdhdBox() {
        // mdhd box - media header
        writeBE32(file, 32); // Size
        fwrite("mdhd", 1, 4, file);
        
        uint8_t version = 0;
        uint32_t flags = 0;
        fwrite(&version, 1, 1, file);
        writeBE32(file, flags & 0xFFFFFF); // 3 bytes of flags
        
        uint32_t creationTime = 0;
        uint32_t modificationTime = 0;
        uint32_t timeScale = sampleRate;
        uint32_t duration = frameSize * numberOfSamples;
        
        writeBE32(file, creationTime);
        writeBE32(file, modificationTime);
        writeBE32(file, timeScale);
        writeBE32(file, duration);
        
        // Language (undetermined)
        uint16_t language = 0x55c4;
        writeBE16(file, language);
        
        // Quality
        uint16_t quality = 0;
        writeBE16(file, quality);
    }
    
    void writeHdlrBox() {
        // hdlr box - handler reference
        writeBE32(file, 33); // Size (fixed for "soun" handler + name)
        fwrite("hdlr", 1, 4, file);
        
        uint8_t version = 0;
        uint32_t flags = 0;
        fwrite(&version, 1, 1, file);
        writeBE32(file, flags & 0xFFFFFF); // 3 bytes of flags
        
        // Pre-defined
        uint32_t preDefined = 0;
        writeBE32(file, preDefined);
        
        // Handler type ('soun' for audio)
        fwrite("soun", 1, 4, file);
        
        // Reserved
        uint32_t reserved[3] = {0};
        for (int i = 0; i < 3; i++) {
            writeBE32(file, reserved[i]);
        }
        
        // Handler name (null-terminated string)
        fwrite("SoundHandler", 1, 13, file);
    }
    
    void writeMinfBox() {
        // minf box - media information
        long minfStart = ftell(file);
        writeBE32(file, 0); // Placeholder
        fwrite("minf", 1, 4, file);
        
        // smhd box - sound media header
        writeSmhdBox();
        
        // dinf box - data information
        writeDinfBox();
        
        // stbl box - sample table
        writeStblBox();
        
        // Update minf box size
        long currentPos = ftell(file);
        long minfSize = currentPos - minfStart;
        fseek(file, minfStart, SEEK_SET);
        writeBE32(file, minfSize);
        fseek(file, currentPos, SEEK_SET);
    }
    
    void writeSmhdBox() {
        // smhd box - sound media header
        writeBE32(file, 16); // Size
        fwrite("smhd", 1, 4, file);
        
        uint8_t version = 0;
        uint32_t flags = 0;
        fwrite(&version, 1, 1, file);
        writeBE32(file, flags & 0xFFFFFF); // 3 bytes of flags
        
        // Balance
        uint16_t balance = 0;
        writeBE16(file, balance);
        
        // Reserved
        uint16_t reserved = 0;
        writeBE16(file, reserved);
    }
    
    void writeDinfBox() {
        // dinf box - data information
        writeBE32(file, 36); // Size
        fwrite("dinf", 1, 4, file);
        
        // dref box - data reference
        writeBE32(file, 28); // Size
        fwrite("dref", 1, 4, file);
        
        uint8_t version = 0;
        uint32_t flags = 0;
        fwrite(&version, 1, 1, file);
        writeBE32(file, flags & 0xFFFFFF); // 3 bytes of flags
        
        // Entry count
        uint32_t entryCount = 1;
        writeBE32(file, entryCount);
        
        // url box - data entry URL
        writeBE32(file, 12); // Size
        fwrite("url ", 1, 4, file);
        
        // Self-contained flag
        uint32_t selfContainedFlag = 0x000001; // Data is in this file
        fwrite(&version, 1, 1, file);
        writeBE32(file, selfContainedFlag & 0xFFFFFF); // 3 bytes of flags
    }
    
    void writeStblBox() {
        // stbl box - sample table
        long stblStart = ftell(file);
        writeBE32(file, 0); // Placeholder
        fwrite("stbl", 1, 4, file);
        
        // stsd box - sample descriptions
        writeStsdBox();
        
        // stts box - time-to-sample
        writeSttsBox();
        
        // stsc box - sample-to-chunk
        writeStscBox();
        
        // stsz box - sample sizes
        writeStszBox();
        
        // stco box - chunk offsets
        writeStcoBox();
        
        // Update stbl box size
        long currentPos = ftell(file);
        long stblSize = currentPos - stblStart;
        fseek(file, stblStart, SEEK_SET);
        writeBE32(file, stblSize);
        fseek(file, currentPos, SEEK_SET);
    }
    
    void writeStsdBox() {
        // stsd box - sample descriptions
        long stsdStart = ftell(file);
        writeBE32(file, 0); // Placeholder
        fwrite("stsd", 1, 4, file);
        
        uint8_t version = 0;
        uint32_t flags = 0;
        fwrite(&version, 1, 1, file);
        writeBE32(file, flags & 0xFFFFFF); // 3 bytes of flags
        
        // Entry count
        uint32_t entryCount = 1;
        writeBE32(file, entryCount);
        
        // mp4a box - audio sample entry
        long mp4aStart = ftell(file);
        writeBE32(file, 0); // Placeholder
        fwrite("mp4a", 1, 4, file);
        
        // Reserved
        uint8_t reserved[6] = {0};
        fwrite(reserved, 1, 6, file);
        
        // Data reference index
        uint16_t dataRefIndex = 1;
        writeBE16(file, dataRefIndex);
        
        // Version and revision
        uint16_t audioVersion = 0;
        uint16_t audioRevision = 0;
        writeBE16(file, audioVersion);
        writeBE16(file, audioRevision);
        
        // Vendor
        uint32_t vendor = 0;
        writeBE32(file, vendor);
        
        // Channel count and sample size
        uint16_t channelCount = channels;
        uint16_t sampleSize = 16; // 16-bit
        writeBE16(file, channelCount);
        writeBE16(file, sampleSize);
        
        // Compression ID and packet size
        uint16_t compressionId = 0;
        uint16_t packetSize = 0;
        writeBE16(file, compressionId);
        writeBE16(file, packetSize);
        
        // Sample rate (16.16 fixed point)
        uint32_t sampleRate32 = sampleRate << 16;
        writeBE32(file, sampleRate32);
        
        // esds box - ES descriptor
        writeEsdsBox();
        
        // Update mp4a box size
        long currentPos = ftell(file);
        long mp4aSize = currentPos - mp4aStart;
        fseek(file, mp4aStart, SEEK_SET);
        writeBE32(file, mp4aSize);
        fseek(file, currentPos, SEEK_SET);
        
        // Update stsd box size
        currentPos = ftell(file);
        long stsdSize = currentPos - stsdStart;
        fseek(file, stsdStart, SEEK_SET);
        writeBE32(file, stsdSize);
        fseek(file, currentPos, SEEK_SET);
    }
    
    void writeEsdsBox() {
        // esds box - ES descriptor
        writeBE32(file, 39); // Size
        fwrite("esds", 1, 4, file);
        
        uint8_t version = 0;
        uint32_t flags = 0;
        fwrite(&version, 1, 1, file);
        writeBE32(file, flags & 0xFFFFFF); // 3 bytes of flags
        
        // Elementary Stream Descriptor (simplified)
        uint8_t esdsData[31] = {
            0x03, 0x19, 0x00, 0x00, // ES descriptor tag, size, ID
            0x04, 0x11, 0x40, 0x15, // Config descriptor tag, size, etc.
            0x00, 0x00, 0x00, 0x00, // Buffer size
            0x00, 0x00, 0x00, 0x00, // Max bitrate
            0x00, 0x00, 0x00, 0x00, // Avg bitrate
            0x05, 0x02, 0x12, 0x10, // Decoder config descriptor
            0x06, 0x01, 0x02        // SL config descriptor
        };
        
        // Set AAC LC object type (0x40 for AAC, 0x12 for specific config)
        esdsData[8] = 0x40; // Audio type
        esdsData[9] = 0x15; // Stream type + flags
        esdsData[21] = 0x05; // Decoder config descriptor tag
        esdsData[22] = 0x02; // Size
        esdsData[23] = 0x12; // AAC LC object type
        esdsData[24] = 0x10; // Freq index for 48000 Hz + channels
        
        fwrite(esdsData, 1, 31, file);
    }
    
    void writeSttsBox() {
        // stts box - time-to-sample
        writeBE32(file, 24); // Size
        fwrite("stts", 1, 4, file);
        
        uint8_t version = 0;
        uint32_t flags = 0;
        fwrite(&version, 1, 1, file);
        writeBE32(file, flags & 0xFFFFFF); // 3 bytes of flags
        
        // Entry count
        uint32_t entryCount = 1;
        writeBE32(file, entryCount);
        
        // Sample count and delta
        writeBE32(file, numberOfSamples);
        writeBE32(file, frameSize); // Samples per AAC frame
    }
    
    void writeStscBox() {
        // stsc box - sample-to-chunk
        writeBE32(file, 28); // Size
        fwrite("stsc", 1, 4, file);
        
        uint8_t version = 0;
        uint32_t flags = 0;
        fwrite(&version, 1, 1, file);
        writeBE32(file, flags & 0xFFFFFF); // 3 bytes of flags
        
        // Entry count
        uint32_t entryCount = 1;
        writeBE32(file, entryCount);
        
        // First chunk, samples per chunk, sample desc ID
        writeBE32(file, 1); // First chunk index
        writeBE32(file, numberOfSamples); // All samples in one chunk
        writeBE32(file, 1); // Sample description index
    }
    
    void writeStszBox() {
        // stsz box - sample sizes
        writeBE32(file, 20 + 4 * numberOfSamples); // Size
        fwrite("stsz", 1, 4, file);
        
        uint8_t version = 0;
        uint32_t flags = 0;
        fwrite(&version, 1, 1, file);
        writeBE32(file, flags & 0xFFFFFF); // 3 bytes of flags
        
        // Default sample size (0 means non-uniform sizes)
        uint32_t defaultSize = 0;
        writeBE32(file, defaultSize);
        
        // Sample count
        writeBE32(file, numberOfSamples);
        
        // Write each sample size
        for (int i = 0; i < numberOfSamples; i++) {
            writeBE32(file, aacSizes[i]);
        }
    }
    
    void writeStcoBox() {
        // stco box - chunk offsets
        writeBE32(file, 20); // Size
        fwrite("stco", 1, 4, file);
        
        uint8_t version = 0;
        uint32_t flags = 0;
        fwrite(&version, 1, 1, file);
        writeBE32(file, flags & 0xFFFFFF); // 3 bytes of flags
        
        // Entry count
        uint32_t entryCount = 1;
        writeBE32(file, entryCount);
        
        // Chunk offset - where mdat data starts
        writeBE32(file, mdatOffset);
    }
};

// JNI function implementations
extern "C" {

JNIEXPORT jboolean JNICALL Java_com_valenzine_whisperdroid_repository_TranscriptionRepository_decodeOpusToPCM(
    JNIEnv *env, jobject obj, jstring inputPath, jstring outputPath) {
    
    const char *inputPathCStr = env->GetStringUTFChars(inputPath, NULL);
    const char *outputPathCStr = env->GetStringUTFChars(outputPath, NULL);
    
    LOGI("decodeOpusToPCM: input=%s, output=%s", inputPathCStr, outputPathCStr);
    
    // Open Opus file for reading
    int error;
    OggOpusFile *of = op_open_file(inputPathCStr, &error);
    
    if (!of) {
        LOGE("Failed to open Opus file: %d", error);
        env->ReleaseStringUTFChars(inputPath, inputPathCStr);
        env->ReleaseStringUTFChars(outputPath, outputPathCStr);
        return JNI_FALSE;
    }
    
    // Get channel count from Opus file
    int channels = op_channel_count(of, -1);
    int sampleRate = 48000; // Opus always uses 48kHz internally
    
    LOGI("Opus file info: channels=%d, sample rate=%d", channels, sampleRate);
    
    // Open PCM file for writing
    FILE *outFile = fopen(outputPathCStr, "wb");
    if (!outFile) {
        LOGE("Failed to open output file for writing");
        op_free(of);
        env->ReleaseStringUTFChars(inputPath, inputPathCStr);
        env->ReleaseStringUTFChars(outputPath, outputPathCStr);
        return JNI_FALSE;
    }
    
    // Write WAV header
    // RIFF header
    fwrite("RIFF", 1, 4, outFile);
    uint32_t fileSize = 0; // Will update later
    fwrite(&fileSize, 4, 1, outFile);
    fwrite("WAVE", 1, 4, outFile);
    
    // Format chunk
    fwrite("fmt ", 1, 4, outFile);
    uint32_t fmtChunkSize = 16;
    fwrite(&fmtChunkSize, 4, 1, outFile);
    uint16_t audioFormat = 1; // PCM
    fwrite(&audioFormat, 2, 1, outFile);
    uint16_t numChannels = channels;
    fwrite(&numChannels, 2, 1, outFile);
    uint32_t sampleRateValue = sampleRate;
    fwrite(&sampleRateValue, 4, 1, outFile);
    uint32_t byteRate = sampleRate * channels * 2; // 16-bit = 2 bytes per sample
    fwrite(&byteRate, 4, 1, outFile);
    uint16_t blockAlign = channels * 2;
    fwrite(&blockAlign, 2, 1, outFile);
    uint16_t bitsPerSample = 16;
    fwrite(&bitsPerSample, 2, 1, outFile);
    
    // Data chunk
    fwrite("data", 1, 4, outFile);
    uint32_t dataSize = 0; // Will update later
    fwrite(&dataSize, 4, 1, outFile);
    
    // Start position of audio data
    long dataStart = ftell(outFile);
    
    // Decode Opus to PCM
    const int bufferSize = 5760; // 120ms at 48kHz
    opus_int16 buffer[bufferSize * 2]; // Max 2 channels
    int64_t totalSamples = 0;
    
    while (true) {
        int samplesRead = op_read(of, buffer, bufferSize, NULL);
        
        if (samplesRead <= 0) {
            if (samplesRead < 0) {
                LOGE("Error decoding Opus file: %d", samplesRead);
            }
            break;
        }
        
        // Write PCM data to file
        fwrite(buffer, sizeof(opus_int16), samplesRead * channels, outFile);
        totalSamples += samplesRead;
    }
    
    // Update WAV header with file size
    long fileEnd = ftell(outFile);
    
    // Update data chunk size
    uint32_t actualDataSize = (fileEnd - dataStart);
    fseek(outFile, dataStart - 4, SEEK_SET);
    fwrite(&actualDataSize, 4, 1, outFile);
    
    // Update RIFF chunk size
    uint32_t actualFileSize = fileEnd - 8; // File size - 8 bytes for RIFF header
    fseek(outFile, 4, SEEK_SET);
    fwrite(&actualFileSize, 4, 1, outFile);
    
    // Close files
    fclose(outFile);
    op_free(of);
    env->ReleaseStringUTFChars(inputPath, inputPathCStr);
    env->ReleaseStringUTFChars(outputPath, outputPathCStr);
    
    LOGI("Decoded %" PRId64 " samples (%" PRId64 " bytes) of PCM data", totalSamples, totalSamples * channels * 2);
    
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL Java_com_valenzine_whisperdroid_repository_TranscriptionRepository_convertOpusToAacNative(
    JNIEnv *env, jobject obj, jstring inputPath, jstring outputPath) {
    
    const char *inputPathCStr = env->GetStringUTFChars(inputPath, NULL);
    const char *outputPathCStr = env->GetStringUTFChars(outputPath, NULL);
    
    LOGI("convertOpusToAacNative: input=%s, output=%s", inputPathCStr, outputPathCStr);
    
    // First, check if file exists and is readable
    FILE* testFile = fopen(inputPathCStr, "rb");
    if (!testFile) {
        LOGE("File does not exist or is not readable: %s (errno: %d)", inputPathCStr, errno);
        env->ReleaseStringUTFChars(inputPath, inputPathCStr);
        env->ReleaseStringUTFChars(outputPath, outputPathCStr);
        return JNI_FALSE;
    }
    
    // Check if this is an Ogg file by reading the OggS magic signature
    char header[12];
    size_t bytes_read = fread(header, 1, 12, testFile);
    
    if (bytes_read < 4 || memcmp(header, "OggS", 4) != 0) {
        LOGE("Not a valid Ogg file. Magic signature not found. First 4 bytes: %02X %02X %02X %02X", 
            (bytes_read > 0) ? (unsigned char)header[0] : 0,
            (bytes_read > 1) ? (unsigned char)header[1] : 0,
            (bytes_read > 2) ? (unsigned char)header[2] : 0,
            (bytes_read > 3) ? (unsigned char)header[3] : 0);
        fclose(testFile);
        env->ReleaseStringUTFChars(inputPath, inputPathCStr);
        env->ReleaseStringUTFChars(outputPath, outputPathCStr);
        return JNI_FALSE;
    }
    
    // Scan the first 8KB of the file for the 'OpusHead' magic signature
    // This is a basic heuristic to check if this is an Opus stream rather than Vorbis or other Ogg formats
    bool foundOpusHead = false;
    char buffer[4096];
    fseek(testFile, 0, SEEK_SET); // Reset to start of file
    
    int bytesRead;
    int totalBytesScanned = 0;
    const int maxScanBytes = 8192; // Only scan first 8KB
    
    while ((bytesRead = fread(buffer, 1, sizeof(buffer), testFile)) > 0) {
        for (int i = 0; i < bytesRead - 8; i++) {
            if (memcmp(buffer + i, "OpusHead", 8) == 0) {
                foundOpusHead = true;
                break;
            }
        }
        
        if (foundOpusHead || (totalBytesScanned += bytesRead) >= maxScanBytes) {
            break;
        }
    }
    
    fclose(testFile);
    
    if (!foundOpusHead) {
        LOGE("File appears to be Ogg container but not Opus audio (OpusHead signature not found)");
        env->ReleaseStringUTFChars(inputPath, inputPathCStr);
        env->ReleaseStringUTFChars(outputPath, outputPathCStr);
        return JNI_FALSE;
    }
    
    LOGI("File exists and has valid Ogg/Opus headers");
    
    // Open Opus file for reading
    int error;
    OggOpusFile *of = op_open_file(inputPathCStr, &error);
    
    if (!of) {
        // Detailed error code analysis
        const char* errorMsg;
        switch(error) {
            case OP_EREAD:
                errorMsg = "Read error";
                break;
            case OP_EFAULT:
                errorMsg = "Memory allocation failure or internal library error";
                break;
            case OP_EIMPL:
                errorMsg = "Stream used an unimplemented feature";
                break;
            case OP_EINVAL:
                errorMsg = "File headers were invalid or contained an invalid value";
                break;
            case OP_ENOTFORMAT:
                errorMsg = "File not recognized as Ogg Opus";
                break;
            case OP_EBADHEADER:
                errorMsg = "Bad header packet";
                break;
            case OP_EVERSION:
                errorMsg = "ID header contained an unrecognized version number";
                break;
            default:
                errorMsg = "Unknown error";
        }
        LOGE("Failed to open Opus file: %d (%s)", error, errorMsg);
        env->ReleaseStringUTFChars(inputPath, inputPathCStr);
        env->ReleaseStringUTFChars(outputPath, outputPathCStr);
        return JNI_FALSE;
    }
    
    // Get channel count from Opus file
    int channels = op_channel_count(of, -1);
    int sampleRate = 48000; // Opus always uses 48kHz internally
    
    LOGI("Opus file info: channels=%d, sample rate=%d", channels, sampleRate);
    
    // Initialize AAC encoder
    HANDLE_AACENCODER aacEncoder;
    AACENC_ERROR err;
    
    err = aacEncOpen(&aacEncoder, 0, channels);
    if (err != AACENC_OK) {
        LOGE("Failed to open AAC encoder: %d", err);
        op_free(of);
        env->ReleaseStringUTFChars(inputPath, inputPathCStr);
        env->ReleaseStringUTFChars(outputPath, outputPathCStr);
        return JNI_FALSE;
    }
    
    // Configure AAC encoder
    err = aacEncoder_SetParam(aacEncoder, AACENC_AOT, AOT_AAC_LC);
    if (err != AACENC_OK) {
        LOGE("Failed to set AAC encoder AOT: %d", err);
        aacEncClose(&aacEncoder);
        op_free(of);
        env->ReleaseStringUTFChars(inputPath, inputPathCStr);
        env->ReleaseStringUTFChars(outputPath, outputPathCStr);
        return JNI_FALSE;
    }
    
    // Set bit rate (128k for stereo, 64k for mono)
    int bitRate = channels == 2 ? 128000 : 64000;
    err = aacEncoder_SetParam(aacEncoder, AACENC_BITRATE, bitRate);
    if (err != AACENC_OK) {
        LOGE("Failed to set AAC encoder bitrate: %d", err);
        aacEncClose(&aacEncoder);
        op_free(of);
        env->ReleaseStringUTFChars(inputPath, inputPathCStr);
        env->ReleaseStringUTFChars(outputPath, outputPathCStr);
        return JNI_FALSE;
    }
    
    // Set sample rate
    err = aacEncoder_SetParam(aacEncoder, AACENC_SAMPLERATE, sampleRate);
    if (err != AACENC_OK) {
        LOGE("Failed to set AAC encoder sample rate: %d", err);
        aacEncClose(&aacEncoder);
        op_free(of);
        env->ReleaseStringUTFChars(inputPath, inputPathCStr);
        env->ReleaseStringUTFChars(outputPath, outputPathCStr);
        return JNI_FALSE;
    }
    
    // Set channel mode
    CHANNEL_MODE channelMode = channels == 2 ? MODE_2 : MODE_1;
    err = aacEncoder_SetParam(aacEncoder, AACENC_CHANNELMODE, channelMode);
    if (err != AACENC_OK) {
        LOGE("Failed to set AAC encoder channel mode: %d", err);
        aacEncClose(&aacEncoder);
        op_free(of);
        env->ReleaseStringUTFChars(inputPath, inputPathCStr);
        env->ReleaseStringUTFChars(outputPath, outputPathCStr);
        return JNI_FALSE;
    }
    
    // Set transport type to raw AAC
    err = aacEncoder_SetParam(aacEncoder, AACENC_TRANSMUX, TT_MP4_RAW);
    if (err != AACENC_OK) {
        LOGE("Failed to set AAC encoder transmux: %d", err);
        aacEncClose(&aacEncoder);
        op_free(of);
        env->ReleaseStringUTFChars(inputPath, inputPathCStr);
        env->ReleaseStringUTFChars(outputPath, outputPathCStr);
        return JNI_FALSE;
    }
    
    // Initialize AAC encoder
    err = aacEncEncode(aacEncoder, NULL, NULL, NULL, NULL);
    if (err != AACENC_OK) {
        LOGE("Failed to initialize AAC encoder: %d", err);
        aacEncClose(&aacEncoder);
        op_free(of);
        env->ReleaseStringUTFChars(inputPath, inputPathCStr);
        env->ReleaseStringUTFChars(outputPath, outputPathCStr);
        return JNI_FALSE;
    }
    
    // Get encoder info
    AACENC_InfoStruct info;
    err = aacEncInfo(aacEncoder, &info);
    if (err != AACENC_OK) {
        LOGE("Failed to get AAC encoder info: %d", err);
        aacEncClose(&aacEncoder);
        op_free(of);
        env->ReleaseStringUTFChars(inputPath, inputPathCStr);
        env->ReleaseStringUTFChars(outputPath, outputPathCStr);
        return JNI_FALSE;
    }
    
    LOGI("AAC encoder info: frameLength=%d, maxOutBufBytes=%d", 
         info.frameLength, info.maxOutBufBytes);
    
    // Initialize MP4 writer
    MP4Writer mp4Writer(outputPathCStr, channels, sampleRate, info.frameLength);
    
    // Setup input/output buffers
    const int inputBufferSize = info.frameLength * channels;
    int16_t *inputBuffer = new int16_t[inputBufferSize];
    uint8_t *outputBuffer = new uint8_t[info.maxOutBufBytes];
    
    // Input buffer description
    AACENC_BufDesc inBufDesc;
    void *inBuffer[1] = { inputBuffer };
    INT inBufferIds[1] = { IN_AUDIO_DATA };
    INT inBufferSizes[1] = { static_cast<INT>(inputBufferSize * sizeof(int16_t)) };
    INT inBufferElSizes[1] = { sizeof(int16_t) };
    
    inBufDesc.numBufs = 1;
    inBufDesc.bufs = inBuffer;
    inBufDesc.bufferIdentifiers = inBufferIds;
    inBufDesc.bufSizes = inBufferSizes;
    inBufDesc.bufElSizes = inBufferElSizes;
    
    // Output buffer description
    AACENC_BufDesc outBufDesc;
    void *outBuffer[1] = { outputBuffer };
    INT outBufferIds[1] = { OUT_BITSTREAM_DATA };
    INT outBufferSizes[1] = { static_cast<INT>(info.maxOutBufBytes) };
    INT outBufferElSizes[1] = { sizeof(uint8_t) };
    
    outBufDesc.numBufs = 1;
    outBufDesc.bufs = outBuffer;
    outBufDesc.bufferIdentifiers = outBufferIds;
    outBufDesc.bufSizes = outBufferSizes;
    outBufDesc.bufElSizes = outBufferElSizes;
    
    // Process Opus file
    bool success = true;
    int totalFramesEncoded = 0;
    
    while (true) {
        int samplesRead = op_read(of, inputBuffer, info.frameLength, NULL);
        
        if (samplesRead <= 0) {
            if (samplesRead < 0) {
                LOGE("Error decoding Opus file: %d", samplesRead);
                success = false;
            }
            break;
        }
        
        // If we didn't get enough samples for a full frame, pad with zeros
        if (samplesRead < info.frameLength) {
            memset(inputBuffer + samplesRead * channels, 0, 
                   (info.frameLength - samplesRead) * channels * sizeof(int16_t));
        }
        
        // Setup input arguments
        AACENC_InArgs inArgs;
        inArgs.numInSamples = info.frameLength * channels;
        inArgs.numAncBytes = 0;
        
        // Setup output arguments
        AACENC_OutArgs outArgs;
        memset(&outArgs, 0, sizeof(outArgs));
        
        // Encode frame
        err = aacEncEncode(aacEncoder, &inBufDesc, &outBufDesc, &inArgs, &outArgs);
        
        if (err != AACENC_OK) {
            LOGE("AAC encoding error: %d", err);
            success = false;
            break;
        }
        
        // Write encoded AAC data to MP4 container
        if (outArgs.numOutBytes > 0) {
            mp4Writer.writeAACSample(outputBuffer, outArgs.numOutBytes);
            totalFramesEncoded++;
        }
    }
    
    LOGI("AAC encoding completed. Total frames encoded: %d", totalFramesEncoded);
    
    // Clean up
    delete[] inputBuffer;
    delete[] outputBuffer;
    aacEncClose(&aacEncoder);
    op_free(of);
    env->ReleaseStringUTFChars(inputPath, inputPathCStr);
    env->ReleaseStringUTFChars(outputPath, outputPathCStr);
    
    return success ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
