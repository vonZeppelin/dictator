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
  (:require [dictator.util :refer [platform]])
  (:import
    [javax.sound.sampled AudioFormat AudioInputStream AudioSystem Mixer Mixer$Info]
    java.nio.ByteOrder))

(def ^:private ^:const sample-rate 8000)

;; A holder of a mixer's Info and name.
(deftype MixerInfo [info name]
  Object
  (toString [_] name))

(defn get-mixers []
  "Returns a sequence of mixers supporting audio capturing. A mixer is wrapped into a MixerInfo instance."
  (letfn [(can-capture? [info]
            (with-open [mixer (AudioSystem/getMixer info)]
              (.open mixer)
              (-> mixer .getTargetLineInfo seq)))
          (wrap [^Mixer$Info info]
            (let [name (.getName info)
                  fixed-name (if (= (:os platform) :win)
                               (-> name (.getBytes "windows-1252") String.)
                               name)]
              (->MixerInfo info fixed-name)))]
    (->> (AudioSystem/getMixerInfo) (filter can-capture?) (map wrap))))

(defn capture [mixer]
  (let [big-endian? (= ByteOrder/BIG_ENDIAN (ByteOrder/nativeOrder))
        frmt (AudioFormat. sample-rate 16 1 true big-endian?)
        line (AudioSystem/getTargetDataLine frmt (.info mixer))
        frame-size 160 ; (speex-get-frame-size speex-modeid-nb)
        filler (fn [fill]
                 (let [buffer-len (* frame-size 2)
                       buffer (byte-array buffer-len)]
                   (with-open [stream (AudioInputStream. line)]
                     (.open line frmt)
                     (.start line)
                     (loop [read (.read stream buffer)]
                       (when (= read buffer-len)
                         (-> buffer (java.util.Arrays/copyOf buffer-len) fill)
                         (recur (.read stream buffer)))))))]
    {:seq filler :line line}))
