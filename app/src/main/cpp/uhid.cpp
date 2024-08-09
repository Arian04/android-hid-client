#include <jni.h>

#include <cstring>
#include <cerrno>
#include <fcntl.h>
#include <unistd.h>
#include <linux/uhid.h>
#include <cstdlib>
#include <android/log.h>
#include "uhid.h"

// Do not forget to dynamically load the C++ library into your application.
//
// For instance,
//
// In MainActivity.kt:
//    companion object {
//      init {
//         System.loadLibrary("uhid")
//      }
//    }

// Logging helper
#define android_log(prio, args...) __android_log_print(prio, "JNI", args)

static uhid_event getCreateEvent() {
    struct uhid_event ev;
    memset(&ev, 0, sizeof(ev));

    ev.type = UHID_CREATE2;
    strcpy((char *) ev.u.create2.name, "uhid-touchpad");
    memcpy(ev.u.create2.rd_data, rdesc, sizeof(rdesc));
    ev.u.create2.rd_size = sizeof(rdesc);
    ev.u.create2.bus = BUS_USB;
    ev.u.create2.vendor = 0x15d9;
    ev.u.create2.product = 0x0a37;
    ev.u.create2.version = 0;
    ev.u.create2.country = 0;

    return ev;
}

static uhid_event getDestroyEvent() {
    struct uhid_event ev;
    memset(&ev, 0, sizeof(ev));

    ev.type = UHID_DESTROY;

    return ev;
}

static int uhid_write(int fd, const struct uhid_event *ev) {
    ssize_t ret;

    ret = write(fd, ev, sizeof(*ev));
    if (ret < 0) {
        android_log(ANDROID_LOG_ERROR, "Cannot write to uhid: %m");
        return -errno;
    } else if (ret != sizeof(*ev)) {
        android_log(ANDROID_LOG_ERROR, "Wrong size written to uhid: %zd != %zu", ret, sizeof(ev));
        return -EFAULT;
    } else {
        return 0;
    }
}

static int create(int fd) {
    struct uhid_event ev = getCreateEvent();

    return uhid_write(fd, &ev);
}

static void destroy(int fd) {
    struct uhid_event ev = getDestroyEvent();

    uhid_write(fd, &ev);
    close(fd);
}

// I don't like the idea of my function names being dictated by things like which file it's being called from and such,
// so I'm keeping them all here to make any naming updates simpler, since it'll all be in one place. And within this
// file, I can use the "real" names.
//
// I'm also organizing things this way so I can separate JNI-specific logic from native logic.
extern "C" JNIEXPORT JNICALL
jbyteArray Java_me_arianb_usb_1hid_1client_hid_1utils_UHIDKt_getCreateEvent(JNIEnv *env, jclass clazz) {
    uhid_event event = getCreateEvent();
    jbyteArray ret = env->NewByteArray(sizeof(event));
    env->SetByteArrayRegion(ret, 0, sizeof(event), (jbyte *) &event);
    return ret;
}
extern "C" JNIEXPORT JNICALL
jbyteArray Java_me_arianb_usb_1hid_1client_hid_1utils_UHIDKt_getDestroyEvent(JNIEnv *env, jclass clazz) {
    uhid_event event = getDestroyEvent();
    jbyteArray ret = env->NewByteArray(sizeof(event));
    env->SetByteArrayRegion(ret, 0, sizeof(event), (jbyte *) &event);
    return ret;
}

extern "C" JNIEXPORT jint JNICALL
Java_me_arianb_usb_1hid_1client_hid_1utils_UHIDKt_createDevice(JNIEnv *env, jclass clazz, jstring jpath) {
    const char *path = env->GetStringUTFChars(jpath, nullptr);
    int fd;
    int ret;

    android_log(ANDROID_LOG_ERROR, "Attempting to open uhid-cdev: %s", path);
    fd = open(path, O_RDWR | O_CLOEXEC);
    env->ReleaseStringUTFChars(jpath, path);
    if (fd < 0) {
        android_log(ANDROID_LOG_ERROR, "Cannot open uhid-cdev: %m");
        return EXIT_FAILURE;
    }

    android_log(ANDROID_LOG_ERROR, "Create uhid device");
    ret = create(fd);
    if (ret) {
        close(fd);
        return EXIT_FAILURE;
    }

    return fd;
}

extern "C" JNIEXPORT void JNICALL
Java_me_arianb_usb_1hid_1client_hid_1utils_UHIDKt_destroyDevice(JNIEnv *env, jclass clazz, jint fd) {
    android_log(ANDROID_LOG_ERROR, "Destroy uhid device");
    destroy(fd);
}
