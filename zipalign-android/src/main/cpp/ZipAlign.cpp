#include <jni.h>
#include <zipalign/ZipAlign.h>

using namespace android;

extern "C"
JNIEXPORT jboolean JNICALL
Java_io_github_muntashirakon_zipalign_ZipAlign_doZipAlignNative(
        JNIEnv *env, jclass, jstring inZipFile, jstring outZipFile,
        jint alignment, jint sharedLibraryPageAlignment, jboolean force) {
    const char *inFileName = env->GetStringUTFChars(inZipFile, nullptr);
    if (!inFileName) return JNI_FALSE;
    const char *outFileName = env->GetStringUTFChars(outZipFile, nullptr);
    if (!outFileName) {
        env->ReleaseStringUTFChars(inZipFile, inFileName);
        return JNI_FALSE;
    }
    bool aligned = process(inFileName, outFileName, alignment,
            sharedLibraryPageAlignment, force) == 0;
    env->ReleaseStringUTFChars(inZipFile, inFileName);
    env->ReleaseStringUTFChars(outZipFile, outFileName);
    return aligned ? JNI_TRUE : JNI_FALSE;
}

extern "C"
JNIEXPORT jboolean JNICALL
Java_io_github_muntashirakon_zipalign_ZipAlign_isZipAlignedNative(
        JNIEnv *env, jclass, jstring zipFile, jint alignment,
        jint sharedLibraryPageAlignment) {
    const char *fileName = env->GetStringUTFChars(zipFile, nullptr);
    if (!fileName) return JNI_FALSE;
    bool verified = verify(fileName, alignment, sharedLibraryPageAlignment, false) == 0;
    env->ReleaseStringUTFChars(zipFile, fileName);
    return verified ? JNI_TRUE : JNI_FALSE;
}
