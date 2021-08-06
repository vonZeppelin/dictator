/**
 * Copyright 2014 Leonid Bogdanov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package dictator;

import java.awt.*;

import java.io.File;
import java.io.IOException;

/**
 * The <code>Native</code> class provides access to platform specific
 * native code (e.g. VAD, MP3 encoder) and UI utility methods.
 *
 * @author Leonid Bogdanov
 */
public final class Native {
    /**
     * Levels of voice activity detector aggressiveness.
     */
    public static enum Aggressiveness {
        NORMAL, LOW_BITRATE, AGGRESSIVE, VERY_AGGRESSIVE;
    }

    /**
     * Voice activity detector based on the WebRTC project submodule.
     */
    public static final class VAD implements AutoCloseable {
        private volatile Long ptr;

        public VAD(Aggressiveness aggressiveness) {
            ptr = createVAD(aggressiveness.ordinal());
        }

        public boolean isVoice(int sampleRate, byte[] audio) {
            return processVAD(ptr, sampleRate, audio);
        }

        @Override
        public synchronized void close() {
            if (ptr != null) {
                freeVAD(ptr);
                ptr = null;
            }
        }
    }

    /**
     * MP3 encoder based on the LAME library.
     */
    public static final class MP3Enc implements AutoCloseable {
        private volatile Long ptr;

        public MP3Enc(int sampleRate, int brate) {
            ptr = createLAME(sampleRate, brate);
        }

        public byte[] encode(byte[] audio) {
            return encodeLAME(ptr, audio);
        }

        @Override
        public synchronized void close() {
            if (ptr != null) {
                freeLAME(ptr);
                ptr = null;
            }
        }
    }

    /**
     * Represents a UI element of a desktop application.
     */
    public static final class UIElem implements AutoCloseable {
        public final int pid;
        public final String appTitle;
        public final Rectangle area;

        private volatile Long ptr;

        public UIElem(long ptr, int pid, String appTitle, Rectangle area) {
            this.pid = pid;
            this.appTitle = appTitle;
            this.area = area;
            this.ptr = ptr;
        }

        public void insertText(String text) {
            Native.insertText(ptr, text);
        }

        @Override
        public synchronized void close() {
            if (ptr != null) {
                freeElement(ptr);
                ptr = null;
            }
        }
    }

    public static UIElem findElement(Window veil, Point cursor) {
        return findElement(veil, cursor.x, cursor.y);
    }

    public static void loadLibrary(File path) throws IOException {
        System.load(path.getCanonicalPath());
    }

    private static native long createVAD(int aggressiveness);
    private static native void freeVAD(long handle);
    private static native boolean processVAD(long handle, int sampleRate, byte[] audio);
    private static native long createLAME(int sampleRate, int brate);
    private static native byte[] encodeLAME(long handle, byte[] audio);
    private static native void freeLAME(long handle);
    private static native UIElem findElement(Window veil, int cursorX, int cursorY);
    private static native void insertText(long ptr, String text);
    private static native void freeElement(long ptr);

    private Native() {}
}
