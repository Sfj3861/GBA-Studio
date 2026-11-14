#include <jni.h>

extern "C"
JNIEXPORT jstring JNICALL
Java_com_sfj3861_gbastudio_NativeBridge_engineVersion(JNIEnv* env, jobject /* thiz */) {
    return env->NewStringUTF("GBA Studio Native Core 0.1.0");
}
