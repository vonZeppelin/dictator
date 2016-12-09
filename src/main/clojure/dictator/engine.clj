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

(ns dictator.engine
  (:require
    [dictator.audio :refer [bit-depth big-endian?]]
    [dictator.tokens :as tokens]
    [dictator.util :as util]
    [clojure.core.async :refer [<! >! chan close! go]]
    [clojure.string :refer [join lower-case]])
  (:import
    dictator.Native$MP3Enc
    [java.io ByteArrayInputStream IOException ByteArrayOutputStream]
    [java.util Locale UUID]
    [javax.sound.sampled AudioFileFormat$Type AudioFormat AudioInputStream AudioSystem]
    us.monoid.json.JSONObject
    [us.monoid.web Content Resty Resty$Option]))

(def ^:private en Locale/US)
(def ^:private ru (Locale. "ru" "RU"))
(def ^:private de (Locale. "de" "DE"))
(def ^:private fr (Locale. "fr" "FR"))
(def ^:private it (Locale. "it" "IT"))
(def ^:private nl (Locale. "nl" "NL"))
(def ^:private resty-options (into-array Resty$Option [(Resty$Option/timeout 5000)]))
(def ^:private bing-token-validity (.toNanos java.util.concurrent.TimeUnit/SECONDS 570))

(defn- to-str [s]
  (if (= s JSONObject/NULL)
    ""
    (str s)))

(defn- to-params [params]
  (join
    "&"
    (for [[k v] params]
      (str (Resty/enc k) "=" (Resty/enc v)))))

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
      (let [params {:qlow [8000 64]
                    :qnormal [16000 128]
                    :qhigh [32000 192]}
            [srate brate] (quality params)
            frmt (AudioFormat. srate bit-depth 1 true big-endian?)
            api-url "https://api.wit.ai/speech?v=20160110&n=0"
            wit-tokens {en tokens/witai-en-key
                        ru tokens/witai-ru-key}
            auth-header (str "Bearer " (wit-tokens lang))
            resty (doto (Resty. resty-options)
                    (.withHeader "Authorization" auth-header))
            encoder (Native$MP3Enc. srate brate)]
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

(deftype BingSpeech []
  SRFactory
    (supports [_] [en nl fr de it ru])
    (create [_ quality lang]
      (let [frmt (AudioFormat. 16000 bit-depth 1 true big-endian?)
            api-url "https://speech.platform.bing.com/recognize"
            params {"appID" "d4d52672-91d7-4c74-8ad8-42b1d98141a5"
                    "device.os" (-> util/platform :os name)
                    "format" "json"
                    "instanceid" (str (UUID/randomUUID))
                    "locale" (.toLanguageTag lang)
                    "result.profanitymarkup" "0"
                    "scenarios" "websearch"
                    "version" "3.0"}
            resty (doto (Resty. resty-options)
                    (.withHeader "Ocp-Apim-Subscription-Key" tokens/bing-key))
            token (atom {:token nil :timestamp 0})
            issue-token (fn []
                          (let [token-url "https://api.cognitive.microsoft.com/sts/v1.0/issueToken"
                                result (try-io
                                         "Token issuing failed"
                                         (.text resty token-url (Resty/content "")))]
                            (if (.status result 200)
                              (str result)
                              (throw
                                (ex-info
                                  "Couldn't get access token"
                                  {:code (-> result .http .getResponseCode)
                                   :msg (-> result .http .getResponseMessage)})))))
            get-token (fn []
                        (let [{tk :token ts :timestamp} @token]
                          (if (and tk (< (- (System/nanoTime) ts) bing-token-validity))
                            tk
                            (:token (swap! token assoc :token (issue-token) :timestamp (System/nanoTime))))))]
        ;; currently supports 16kHz 16-bit mono signed PCM audio only
        (reify
          SR
            (audio-format [_] frmt)
            (as-text [_ audio]
              (let [auth-header (str "Bearer " (get-token))
                    resty (doto (Resty. resty-options)
                            (.withHeader "Authorization" auth-header))
                    content (Content. "audio/wav;samplerate=16000" (to-wav frmt audio))
                    url (->> (UUID/randomUUID) str (assoc params "requestid") to-params (str api-url "?"))
                    result (try-io
                             "Speech recongnition failed"
                             (.json resty url content))]
                (if (.status result 200)
                  (if (-> result (.get "header.status") lower-case (= "success"))
                    (to-str (.get result "results[0].lexical"))
                    "")
                  (throw (ex-info "Speech recongnition failed" {:code (-> result .http .getResponseCode)
                                                                :msg (-> result .http .getResponseMessage)})))))
          java.lang.AutoCloseable
            (close [_]))))
  Object
    (toString [_] "Bing Speech"))

(defonce engine-factories [(BingSpeech.) (WitAI.)])

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
