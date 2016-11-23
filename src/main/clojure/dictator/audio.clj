;; Copyright 2014 Leonid Bogdanov
;;
;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;;    http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.

(ns dictator.audio
  (:require
    [clojure.core.async :as async :refer [<! >! >!! alt! chan close! go go-loop thread timeout]]
    [dictator.util :refer [platform ->ValueHolder]])
  (:import
    dictator.Native$VAD
    java.io.ByteArrayOutputStream
    java.nio.ByteOrder
    [javax.sound.sampled AudioFormat AudioInputStream AudioSystem DataLine$Info Mixer Mixer$Info TargetDataLine]))

(def ^:private ^:const big-endian? (= ByteOrder/BIG_ENDIAN (ByteOrder/nativeOrder)))
;; the only value supported by WebRTC VAD?
(def ^:private ^:const bit-depth 16)
;; 30ms frames to make WebRTC VAD happy
(def ^:private ^:const frame-length 0.03)

(defn get-mixers
  "Returns a sequence of mixers supporting audio capturing. A mixer is wrapped into a ValueHolder instance."
  []
  (let [is-win? (= (:os platform) :win)
        ;; let's use the max supported sample rate to filter out mixers
        test-format (AudioFormat. 32000 bit-depth 1 true big-endian?)
        line-info (DataLine$Info. TargetDataLine test-format)
        can-capture? (fn [info]
                       (with-open [mixer (AudioSystem/getMixer info)]
                         (.open mixer)
                         (.isLineSupported mixer line-info)))
        wrap (fn [info]
               (let [name (.getName info)
                     fixed-name (if is-win?
                                  (-> name (.getBytes "windows-1252") String.)
                                  name)]
                 (->ValueHolder fixed-name info)))]
    (->> (AudioSystem/getMixerInfo) (filter can-capture?) (map wrap))))

(defn capture-sound
  "Returns a channel of captured voice frames."
  [mixer-info sample-rate]
  (let [out-channel (chan)]
    (thread
      (let [frmt (AudioFormat. sample-rate bit-depth 1 true big-endian?)
            buffer-size (int (* frame-length (.getFrameSize frmt) (.getFrameRate frmt)))
            buffer (byte-array buffer-size)]
        (with-open [line (AudioSystem/getTargetDataLine frmt (.value mixer-info))
                    stream (AudioInputStream. line)]
            (.open line frmt)
            (.start line)
            (while (and
                     (= (.read stream buffer) buffer-size)
                     (>!! out-channel (aclone buffer))))
            (.stop line)
            (.flush line))))
    out-channel))

(defn filter-voice
  "Returns a channel of frames with actual voice."
  [audio-channel frame-rate vad-mode]
  (let [out-channel (chan)
        ;; let's do voice / no voice decision based on 5 consecutive frames
        parted-channel (async/partition 5 audio-channel)]
    (go
      (with-open [vad (Native$VAD. vad-mode)]
        (loop []
          (let [frames (<! parted-channel)
                has-voice? #(.isVoice vad frame-rate %)]
            (if frames
              (do
                (when (every? has-voice? frames)
                  (doseq [frame frames]
                    (>! out-channel frame)))
                (recur))
              (close! out-channel))))))
    out-channel))

(defn aggregate-chunks
  "Returns a channel with aduio frames aggregated into larger chunks
   based on pauses in speech of greater than or equal to specified ms length."
  [audio-channel pause-lenght]
  (let [out-channel (chan)
        ;; reusable buffer of initially 50kB
        chunk (ByteArrayOutputStream. 50000)]
    (go-loop []
      (alt! audio-channel ([frame] (if frame
                                     (do
                                       (.write chunk frame)
                                       (recur))
                                     (close! out-channel)))
            (timeout pause-lenght) (if (zero? (.size chunk))
                                     (recur)
                                     (do
                                       (>! out-channel (.toByteArray chunk))
                                       (.reset chunk)
                                       (recur)))))
    out-channel))
