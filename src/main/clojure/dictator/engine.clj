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
    [clojure.core.async :refer [<! >! chan close! go]])
  (:import
    dictator.Native$MP3Enc
    [java.util Locale Map]
    us.monoid.json.JSONObject
    [us.monoid.web Content Resty Resty$Option]))

(def ^:private en Locale/ENGLISH)
(def ^:private ru (Locale. "ru"))
(def ^:private resty-options (into-array Resty$Option [(Resty$Option/timeout 5000)]))

(defn- to-str [s]
  (if (= s JSONObject/NULL)
    ""
    (str s)))

(defprotocol SRFactory
  "A speech recognition engine factory."
  (supports [this] "Returns a list of supported locales.")
  (create [this quality lang] "Given a locale and a quality specifier returns an SR engine."))

(defprotocol SR
  "A generic speech recognition technology."
  (sample-rate [this] "Required PCM audio sample rate.")
  (as-text [this audio] "Turns provided PCM audio chunk into text."))

(deftype WitAI []
  SRFactory
    (supports [_] [en ru])
    (create [_ quality lang]
      (let [params {:qlow [8000 64]
                    :qnormal [16000 128]
                    :qhigh [32000 192]}
            [srate brate] (quality params)
            api-url "https://api.wit.ai/speech?v=20160110&n=0"
            wit-tokens {en "WLHZ3HZPSLBUOR2SEOAX5PMDSKKYNFKU"
                        ru "6K6G5BQWQSCRHT23CVL6ECBGOHER2CI3"}
            auth-header (str "Bearer " (wit-tokens lang))
            resty (doto (Resty. resty-options)
                    (.withHeader "Authorization" auth-header))
            encoder (Native$MP3Enc. srate brate)]
        (reify
          SR
            (sample-rate [_] srate)
            (as-text [_ audio]
              (let [content (->> audio (.encode encoder) (Content. "audio/mpeg3"))
                    result (.json resty api-url content)]
                (if (.status result 200)
                  (-> result (.get "_text") to-str)
                  (throw (ex-info "Speech recongnition failed" {:code (-> result .http .getResponseCode)
                                                                :msg (-> result .http .getResponseMessage)})))))
          java.lang.AutoCloseable
            (close [_] (.close encoder)))))
  java.lang.Object
    (toString [_] "WitAI"))

(deftype ApiAI []
  SRFactory
    (supports [_] [en ru])
    (create [_ quality lang]
      (let [api-url "https://api.api.ai/v1/query?v=20160110"
            auth-header "Bearer 4ac5e5727ad9432f8ca167f3a92b55e1"
            subscription-header "18bd8281-bf78-4d70-b170-c43f0d17ea80"
            request-content (Resty/content (JSONObject. ^Map {"lang" (.getLanguage lang)
                                                              "sessionId" (str (System/currentTimeMillis))}))
            resty (doto (Resty. resty-options)
                    (.withHeader "Authorization" auth-header)
                    (.withHeader "ocp-apim-subscription-key" subscription-header))]
        ;; currently supports 16kHz 16-bit mono signed PCM audio only
        (reify
          SR
            (sample-rate [_] 16000)
            (as-text [_ audio]
              (let [form-data [(Resty/data "request" request-content)
                               (Resty/data "voiceData" (Content. "audio/wav" audio))]
                    result (.json resty api-url (-> form-data into-array Resty/form))]
                (if (-> result (.get "status.code") (= 200))
                  (-> result (.get "result.resolvedQuery") to-str)
                  (throw (ex-info "Speech recongnition failed" {:code (.get result "status.code")
                                                                :msg (.get result "status.errorDetails")})))))
          java.lang.AutoCloseable
            (close [_]))))
  java.lang.Object
    (toString [_] "ApiAI"))

(defonce engine-factories [(WitAI.) (ApiAI.)])

(defn recognize-speech [audio-channel engine]
  "Returns a channel with recongnized speech."
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
