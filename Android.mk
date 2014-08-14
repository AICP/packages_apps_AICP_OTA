LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_SRC_FILES += $(call all-java-files-under, src)

LOCAL_STATIC_JAVA_LIBRARIES := \
    android-support-v4_13 \
    google-play-services \
    volley

LOCAL_PACKAGE_NAME := AICP_OTA
LOCAL_CERTIFICATE := platform
LOCAL_PRIVILEGED_MODULE := true
LOCAL_PROGUARD_FLAG_FILES := proguard.flags 

LOCAL_SDK_VERSION := 19

include $(BUILD_PACKAGE)

# Support library v4
include $(CLEAR_VARS)

LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := \
    android-support-v4_13:/libs/android-support-v4.jar \
    google-play-services:/libs/google-play-services.jar

include $(BUILD_MULTI_PREBUILT)
