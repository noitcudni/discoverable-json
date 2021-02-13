(ns discoverable-json.background.storage
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [<! chan]]
            [clojure.string]
            [chromex.logging :refer-macros [log info warn error group group-end]]
            [chromex.protocols.chrome-storage-area :as storage-area]
            [chromex.ext.storage :as storage]))

(defn store-license [{:keys [email key] :as license}]
  (let [local-storage (storage/get-local)]
    (go
      (storage-area/set local-storage (clj->js {"license" license}))
      )))

(defn get-license []
  (let [local-storage (storage/get-local)]
    (go
      (let [[[items] error] (<! (storage-area/get local-storage "license"))]
        (-> items js->clj (get "license"))
        ))))

(defn remove-license []
  (let [local-storage (storage/get-local)]
    (go
      (<! (storage-area/remove local-storage "license")))))
