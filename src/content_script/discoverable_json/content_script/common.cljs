(ns discoverable-json.content-script.common
  (:require-macros [cljs.core.async.macros :refer [go]])
  (:require [cljs.core.async :refer [<!]]
            [cognitect.transit :as t]
            [cljs-time.core :as time]
            [cljs-time.format :as tf]
            [cljs-time.coerce :as tc]
            [clojure.string]
            [clojure.walk]
            [cljs-http.client :as http]
            [discoverable-json.background.storage :refer [store-license get-license]]
            ))

;; TODO: Use a bulk rmeoval url extensions's id for now
(def GUMROAD-PRODUCT-ID "HQkuN")

(defn marshall [edn-msg]
  (let [w (t/writer :json)]
    (t/write w edn-msg)))

(defn unmarshall [msg-str]
  (let [r (t/reader :json)]
    (t/read r msg-str)))

(defn check-license*
  "Returns the license key if it's valid. Otherwise, false"
  [email key]
  (let [email (clojure.string/trim (or email ""))
        key (clojure.string/trim (or key ""))]
    (go
      (let [{success? :success
             body :body}
            (<! (http/post "https://api.gumroad.com/v2/licenses/verify"
                           {:json-params {:product_permalink GUMROAD-PRODUCT-ID :license_key key}}))]
        (if success?
          (let [{{purchasing-email :email
                  charge-back :chargebacked
                  refunded :refunded
                  license-key :license_key
                  uses :uses ;; NOTE: not limiting the number of installs for now
                  subscription-cancelled-at :subscription_cancelled_at
                  subscription-failed-at :subscription_failed_at
                  } :purchase} body
                subscription-has-expired? (when (some? subscription-cancelled-at)
                                            (> (tc/to-long (time/now))
                                               (tc/to-long (tf/parse (tf/formatters :date-time-no-ms) subscription-cancelled-at))))
                ]
            (if (and (= purchasing-email email) (not refunded) (not charge-back)
                     (nil? subscription-failed-at)
                     ;; if it has been cancelled. Give them another 30 days.
                     ;; NOTE: this will extend the subscription from the time of cancellation
                     (or (nil? subscription-cancelled-at)
                         (not subscription-has-expired?)
                         ;; subscription-not-yet-expired?
                         ))
              {:key license-key
               :email email
               :checked-ts (tc/to-long (time/now))}
              false))
          false)
        ))))

(defn check-license []
  (go
    (let [{:keys [email key checked-ts] :as license} (clojure.walk/keywordize-keys (<! (get-license)))
          ;; looks like cljs can't shirt circuit inside a go block that's why I'm doing this.
          time-to-check-again? (when (some? checked-ts)
                                 (>= (time/in-seconds (time/interval
                                                       (tc/from-long checked-ts)
                                                       (time/now))) (* 24 60 60)))]
      (if (and (not (false? license)) (seq license))
        ;; really check license if we haven't checked in 24 hours.
        (if (or (nil? checked-ts) time-to-check-again?)
          (do
            (prn ">> time out: checking license again")
            (let [license (<! (check-license* email key))]
              (if-not license
                false
                (do
                  (<! (store-license license))
                  true))))
          ;; else has has-license-ratom? true
          (do
            (prn ">> we are good. no need to check license")
            true))
        false)
      )))
