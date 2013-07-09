LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)
LOCAL_MODULE    := kissFFT-jni
LOCAL_SRC_FILES := kissFFT-wrapper-jni.cpp \
kiss_fft.c \
kiss_fftr.c

LOCAL_LDLIBS    := -lm -llog -ljnigraphics

include $(BUILD_SHARED_LIBRARY)