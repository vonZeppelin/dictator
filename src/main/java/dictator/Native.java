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


/**
 * @author Leonid Bogdanov
 */
public class Native {
    public static enum Aggressiveness {
        NORMAL, LOW_BITRATE, AGGRESSIVE, VERY_AGGRESSIVE;
    }

    public static long createVAD(Aggressiveness aggressiveness) {
        return createVAD(aggressiveness.ordinal());
    }

    public static native void freeVAD(long handle);

    private static native long createVAD(int aggressiveness);
}
