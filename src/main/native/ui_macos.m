#include <JavaNativeFoundation/JavaNativeFoundation.h>
#include <AppKit/AppKit.h>
#include <unistd.h>
#include "dictator_Native.h"
#include "jni_util.h"

static JNF_CLASS_CACHE(COMPONENT, "java/awt/Component");
static JNF_MEMBER_CACHE(COMPONENT_peer, COMPONENT, "peer", "Ljava/awt/peer/ComponentPeer;");

static JNF_CLASS_CACHE(LWWINDOWPEER, "sun/lwawt/LWWindowPeer");
static JNF_MEMBER_CACHE(LWWINDOWPEER_platformWindow, LWWINDOWPEER, "platformWindow", "Lsun/lwawt/PlatformWindow;");

static JNF_CLASS_CACHE(CPLATFORMWINDOW, "sun/lwawt/macosx/CPlatformWindow");
static JNF_MEMBER_CACHE(CPLATFORMWINDOW_ptr, CPLATFORMWINDOW, "ptr", "J");

static JNF_CLASS_CACHE(RECTANGLE, "java/awt/Rectangle");
static JNF_CTOR_CACHE(RECTANGLE_ctor, RECTANGLE, "(IIII)V");

static JNF_CLASS_CACHE(UIELEM, "dictator/Native$UIElem");
static JNF_CTOR_CACHE(UIELEM_ctor, UIELEM, "(JILjava/lang/String;Ljava/awt/Rectangle;)V");

JNIEXPORT jobject JNICALL Java_dictator_Native_findElement(JNIEnv *env, jclass clazz,
                                                           jobject veil, jint cursorX, jint cursorY) {
JNF_COCOA_ENTER(env)
    // JAWT is no longer supported on OSX, let's do some dirty digging to find NSWindow* of the veil
    jobject peer = JNFGetObjectField(env, veil, COMPONENT_peer);
    jobject platformWindow = JNFGetObjectField(env, peer, LWWINDOWPEER_platformWindow);
    NSWindow *nsVeil = jlong_to_ptr(JNFGetLongField(env, platformWindow, CPLATFORMWINDOW_ptr));

    CGPoint cursor = CGPointMake(cursorX, cursorY);
    CGWindowListOption windowSearchOpts = kCGWindowListOptionOnScreenBelowWindow | kCGWindowListExcludeDesktopElements;
    NSArray *windows = [(NSArray*) CGWindowListCopyWindowInfo(windowSearchOpts, nsVeil.windowNumber) autorelease];
    NSDictionary *selectedWindow = nil;
    for (NSDictionary *window in windows) {
        CGRect windowBounds;
        CGRectMakeWithDictionaryRepresentation((CFDictionaryRef) window[(id) kCGWindowBounds], &windowBounds);
        if (CGRectContainsPoint(windowBounds, cursor)) {
            selectedWindow = window;
            break;
        }
    }

    if (selectedWindow) {
        pid_t ownerPid = [selectedWindow[(id) kCGWindowOwnerPID] intValue];
        if (ownerPid == getpid()) {
            // skip Dictator own windows
            return NULL;
        }

        AXUIElementRef owner = AXUIElementCreateApplication(ownerPid);
        AXUIElementRef elem;
        if (AXUIElementCopyElementAtPosition(owner, cursor.x, cursor.y, &elem) != kAXErrorSuccess) {
            elem = nil;
        }
        CFRelease(owner);

        if (elem) {
            AXValueRef value;
            CGPoint elemPos;
            if (AXUIElementCopyAttributeValue(elem, kAXPositionAttribute, (CFTypeRef*) &value) != kAXErrorSuccess) {
                CFRelease(elem);
                return NULL;
            }
            AXValueGetValue(value, kAXValueCGPointType, &elemPos);
            CFRelease(value);

            CGSize elemSize;
            if (AXUIElementCopyAttributeValue(elem, kAXSizeAttribute, (CFTypeRef*) &value) != kAXErrorSuccess) {
                CFRelease(elem);
                return NULL;
            }
            AXValueGetValue(value, kAXValueCGSizeType, &elemSize);
            CFRelease(value);

            NSRunningApplication *ownerApp = [NSRunningApplication runningApplicationWithProcessIdentifier:ownerPid];
            jstring ownerTitle = JNFNSToJavaString(env, ownerApp.localizedName);
            jobject rect = JNFNewObject(env, RECTANGLE_ctor, (jint) elemPos.x, (jint) elemPos.y,
                                        (jint) elemSize.width, (jint) elemSize.height);
            return JNFNewObject(env, UIELEM_ctor, ptr_to_jlong(elem), ownerPid, ownerTitle, rect);
        }
    }
JNF_COCOA_EXIT(env)
    return NULL;
}

JNIEXPORT void JNICALL Java_dictator_Native_freeElement(JNIEnv *env, jclass clazz, jlong ptr) {
    CFRelease(jlong_to_ptr(ptr));
}

JNIEXPORT void JNICALL Java_dictator_Native_insertText(JNIEnv *env, jclass clazz, jlong ptr, jstring text) {
JNF_COCOA_ENTER(env)
    NSPasteboard *pboard = NSPasteboard.generalPasteboard;
    NSMutableArray *archive = [NSMutableArray array];
    for (NSPasteboardItem *item in pboard.pasteboardItems)
    {
        NSPasteboardItem *archivedItem = [[NSPasteboardItem alloc] init];
        for (NSString *type in [item types])
        {
            NSData *data = [[item dataForType:type] mutableCopy];
            if (data) {
                [archivedItem setData:data forType:type];
            }
            }
            [archive addObject:archivedItem];
        }
    @try {
        [pboard clearContents];
        [pboard setString:JNFJavaToNSString(env, text) forType:NSPasteboardTypeString];
    }
    @finally {
        [pboard clearContents];
        [pboard writeObjects:archive];
    }
JNF_COCOA_EXIT(env)
}
