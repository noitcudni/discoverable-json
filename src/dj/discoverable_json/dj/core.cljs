(ns discoverable-json.dj.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [<! put! chan] :as async]
            [cljs-http.client :as http]
            [clojure.walk]
            [clojure.string]
            [chromex.ext.tabs :as tabs]
            [re-com.core :as recom]
            [reagent.core :as reagent :refer [atom]]
            [reagent.dom :refer [render]]
            [cljs-time.core :as time]
            [cljs-time.coerce :as tc]
            [cljs-time.format :as tf]
            [cognitect.transit :as t]
            [cljs-gron-online.core]
            [domina :refer [single-node nodes]]
            [domina.xpath :refer [xpath]]
            [discoverable-json.content-script.common :as common]
            [discoverable-json.background.storage :refer [store-license get-license remove-license]]
            [chromex.logging :refer-macros [log info warn error group group-end]]
            [chromex.protocols.chrome-port :refer [post-message!]]
            [chromex.ext.runtime :as runtime :refer-macros [connect]])
  )


(defn init! []
  (let [background-port (runtime/connect)]
    ;; (prn ">> dj.initi!!!!")
    (cljs-gron-online.core/init true)

    ))
