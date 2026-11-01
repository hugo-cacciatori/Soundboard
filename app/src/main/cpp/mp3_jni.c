/* JNI bridge to LAME: encodes a whole mono 16-bit clip to CBR MP3 in one call. */
#include <jni.h>
#include <stdlib.h>
#include "lame/lame.h"

JNIEXPORT jbyteArray JNICALL
Java_com_example_soundboard_audio_Mp3Encoder_nativeEncode(
        JNIEnv *env, jclass clazz, jshortArray pcm, jint inRate, jint outRate, jint kbps) {
    (void) clazz;
    jsize frames = (*env)->GetArrayLength(env, pcm);
    lame_t lame = lame_init();
    if (lame == NULL) return NULL;
    lame_set_num_channels(lame, 1);
    lame_set_mode(lame, MONO);
    lame_set_in_samplerate(lame, inRate);
    lame_set_out_samplerate(lame, outRate);
    lame_set_VBR(lame, vbr_off);
    lame_set_brate(lame, kbps);
    lame_set_quality(lame, 2);
    lame_set_bWriteVbrTag(lame, 0);
    lame_set_write_id3tag_automatic(lame, 0);
    if (lame_init_params(lame) < 0) {
        lame_close(lame);
        return NULL;
    }

    /* Worst case from lame.h: 1.25 * samples + 7200, plus room for the flush. */
    int capacity = frames + frames / 4 + 7200 + 7200;
    unsigned char *mp3 = malloc((size_t) capacity);
    jshort *samples = (*env)->GetShortArrayElements(env, pcm, NULL);
    jbyteArray result = NULL;
    if (mp3 != NULL && samples != NULL) {
        int written = lame_encode_buffer(lame, samples, NULL, frames, mp3, capacity);
        if (written >= 0) {
            int flushed = lame_encode_flush(lame, mp3 + written, capacity - written);
            if (flushed >= 0) {
                result = (*env)->NewByteArray(env, written + flushed);
                if (result != NULL) {
                    (*env)->SetByteArrayRegion(env, result, 0, written + flushed, (const jbyte *) mp3);
                }
            }
        }
    }
    if (samples != NULL) (*env)->ReleaseShortArrayElements(env, pcm, samples, JNI_ABORT);
    free(mp3);
    lame_close(lame);
    return result;
}
