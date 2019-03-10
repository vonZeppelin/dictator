;; Copyright 2014-2019 Leonid Bogdanov
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

(ns dictator.engine
  (:require
    [dictator
      [audio :as audio]
      [tokens :as tokens]
      [util :as util]]
    [clojure.core.async :refer [<! >! chan close! go]]
    [clojure.string :refer [join lower-case]])
  (:import
    dictator.Native$MP3Enc
    us.monoid.json.JSONObject
    [us.monoid.web Content Resty Resty$Option]
    [java.io ByteArrayInputStream IOException ByteArrayOutputStream]
    [java.util Locale UUID]
    [javax.sound.sampled AudioFileFormat$Type AudioFormat AudioInputStream AudioSystem]))

(def ^:private de Locale/GERMANY)
(def ^:private en Locale/US)
(def ^:private fr Locale/FRANCE)
(def ^:private it Locale/ITALY)
(def ^:private nl (Locale. "nl" "NL"))
(def ^:private ru (Locale. "ru" "RU"))

(def ^:private resty-options (into-array
                               Resty$Option
                               [(Resty$Option/timeout 5000)]))

(defn- to-params [params]
  (join
    "&"
    (for [[k v] params]
      (str (Resty/enc k) "=" (Resty/enc v)))))

(defn- to-str [s]
  (if (= s JSONObject/NULL)
    ""
    (str s)))

(defn- to-wav [format raw]
  ;; no need to close streams because they're in-memory
  (let [frame-count (/ (alength raw) (.getFrameSize format))
        input (AudioInputStream. (ByteArrayInputStream. raw) format frame-count)
        output (ByteArrayOutputStream. 16384)]
    (AudioSystem/write input AudioFileFormat$Type/WAVE output)
    (.toByteArray output)))

(defmacro ^:private try-io [msg body]
  `(try
     ~body
     (catch IOException e#
       (throw (ex-info ~msg {} e#)))))

(defprotocol SRFactory
  "A speech recognition engine factory."
  (supports [this] "Returns a list of supported locales.")
  (create [this quality lang] "Given a locale and a quality specifier returns an SR engine."))

(defprotocol SR
  "A generic speech recognition technology."
  (audio-format [this] "Required audio format.")
  (as-text [this audio] "Turns provided PCM audio chunk into text."))

(deftype WitAI []
  SRFactory
    (supports [_] [en ru])
    (create [_ quality lang]
      (let [api-url "https://api.wit.ai/speech?v=20190101&n=1"
            encoder-params {:qlow [8000 64]
                            :qnormal [16000 128]
                            :qhigh [32000 192]}
            wit-tokens {en tokens/witai-en-key
                        ru tokens/witai-ru-key}
            [sample-rate bitrate] (quality encoder-params)
            frmt (AudioFormat. sample-rate audio/bit-depth 1 true (:is-big-endian util/platform))
            auth-header (str "Bearer " (wit-tokens lang))
            resty (doto (Resty. resty-options)
                    (.withHeader "Authorization" auth-header))
            encoder (Native$MP3Enc. sample-rate bitrate)]
        (reify
          SR
            (audio-format [_] frmt)
            (as-text [_ audio]
              (let [content (->> audio (.encode encoder) (Content. "audio/mpeg3"))
                    result (try-io
                             "Speech recongnition failed"
                             (.json resty api-url content))]
                (if (.status result 200)
                  (to-str (.get result "_text"))
                  (throw (ex-info "Speech recongnition failed" {:code (-> result .http .getResponseCode)
                                                                :msg (-> result .http .getResponseMessage)})))))
          java.lang.AutoCloseable
            (close [_] (.close encoder)))))
  Object
    (toString [_] "WitAI"))

(deftype AzureSpeech []
  SRFactory
    (supports [_] [de en fr it nl ru])
    (create [_ quality lang]
      (let [api-url "https://southeastasia.stt.speech.microsoft.com/speech/recognition/conversation/cognitiveservices/v1"
            query-params {"format" "simple"
                          "language" (.toLanguageTag lang)
                          "profanity" "raw"}
            frmt (AudioFormat. 16000 audio/bit-depth 1 true (:is-big-endian util/platform))
            resty (doto (Resty. resty-options)
                    (.withHeader "Ocp-Apim-Subscription-Key" tokens/azure-key))]
        ;; currently supports 16kHz 16-bit mono signed PCM audio only
        (reify
          SR
            (audio-format [_] frmt)
            (as-text [_ audio]
              (let [content (->> audio (to-wav frmt) (Content. "audio/wav;codecs=audio/pcm;samplerate=16000"))
                    url (->> query-params to-params (str api-url "?"))
                    result (try-io
                             "Speech recongnition failed"
                             (.json resty url content))]
                (if (.status result 200)
                  (if (= (.get result "RecognitionStatus") "Success")
                    (to-str (.get result "DisplayText"))
                    "")
                  (throw (ex-info "Speech recongnition failed" {:code (-> result .http .getResponseCode)
                                                                :msg (-> result .http .getResponseMessage)})))))
          java.lang.AutoCloseable
            (close [_]))))
  Object
    (toString [_] "Azure Speech"))

(defonce engine-factories [(AzureSpeech.) (WitAI.)])

(defn recognize-speech
  "Returns a channel with recongnized speech."
  [audio-channel engine]
  (let [out-channel (chan)]
    (go
      (with-open [engine engine]
        (loop []
          (if-let [chunk (<! audio-channel)]
            (do
              (>! out-channel (as-text engine chunk))
              (recur))
            (close! out-channel)))))
    out-channel))
