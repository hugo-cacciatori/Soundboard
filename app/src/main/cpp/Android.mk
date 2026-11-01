LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := soundboard
LOCAL_SRC_FILES := mp3_jni.c $(patsubst $(LOCAL_PATH)/%,%,$(wildcard $(LOCAL_PATH)/lame/*.c))
LOCAL_C_INCLUDES := $(LOCAL_PATH)/lame
LOCAL_CFLAGS := -DHAVE_CONFIG_H -O2 -w
LOCAL_LDLIBS := -lm
include $(BUILD_SHARED_LIBRARY)
