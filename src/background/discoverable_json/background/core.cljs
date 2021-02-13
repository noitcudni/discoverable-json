(ns discoverable-json.background.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [goog.string :as gstring]
            [goog.string.format]
            [clojure.string]
            [cljs.core.async :refer [<! chan timeout]]
            [cljs-http.client :as http]
            [discoverable-json.content-script.common :as common]
            [chromex.logging :refer-macros [log info warn error group group-end]]
            [chromex.config :refer-macros [with-custom-event-listener-factory]]
            [chromex.ext.web-request :as web-request]
            [cljs.core.async :refer [<!]]
            [chromex.chrome-event-channel :refer [make-chrome-event-channel]]
            [chromex.protocols.chrome-port :refer [post-message! get-sender]]
            [chromex.ext.tabs :as tabs]
            [chromex.ext.runtime :as runtime]
            [cljs-gron-online.gron :as g]
            [discoverable-json.background.storage :refer [get-license]]))

(def clients (atom []))
(def json-url-resp-atom (atom {}))


; -- clients manipulation ---------------------------------------------------------------------------------------------------

(defn add-client! [client]
  (log "BACKGROUND: client connected" (get-sender client))
  ;; (prn ">> add-client! " (-> client get-sender js->clj))
  (swap! clients conj client))

(defn remove-client! [client]
  (log "BACKGROUND: client disconnected" (get-sender client))
  (let [remove-item (fn [coll item] (remove #(identical? item %) coll))]
    (swap! clients remove-item client)))

; -- client event loop ------------------------------------------------------------------------------------------------------

(defn run-client-message-loop! [client]
  (prn "BACKGROUND: starting event loop for client:" (get-sender client))
  (go-loop []
    (when-some [message (<! client)]
      (let [{:keys [type] :as whole-edn} (common/unmarshall message)
            url (-> (get-sender client)
                    js->clj
                    (get "url"))]
        (case type
          :open-new-tab (tabs/create (clj->js {:url "dj.html" :active true}))
          nil
          ))
      (recur))
    (prn "BACKGROUND: leaving event loop for client:" (get-sender client))
    (remove-client! client)))

; -- event handlers ---------------------------------------------------------------------------------------------------------

(defn handle-client-connection! [client]
  (add-client! client)
  (run-client-message-loop! client))

(defn tell-clients-about-new-tab! []
  (doseq [client @clients]
    (post-message! client "a new tab was created")))

; -- main event loop --------------------------------------------------------------------------------------------------------

(defn process-chrome-event [event-num event]
  (log (gstring/format "BACKGROUND: got chrome event (%05d)" event-num) event)
  (let [[event-id event-args] event
        _ (prn "event-id: " event-id)
        _ (prn "event-args: " event-args)
        ]
    (case event-id
      ::runtime/on-connect (apply handle-client-connection! event-args)
      ::tabs/on-created (tell-clients-about-new-tab!)
      nil)))

(defn run-chrome-event-loop! [chrome-event-channel]
  (log "BACKGROUND: starting main event loop...")
  (go-loop [event-num 1]
    (when-some [event (<! chrome-event-channel)]
      (process-chrome-event event-num event)
      (recur (inc event-num)))
    (log "BACKGROUND: leaving main event loop")))

(defn is-redirect? [status]
  (and (>= status 300) (< status 400)))

(defn json-header? [header-value]
  (re-find #"^application/([a-z]+\+)?json($|;)" header-value))

#_(defn my-event-listener-factory []
  (fn [& args]
    (let [event (js->clj (first args))]
      (when (and (contains? event "responseHeaders")
                 (not (is-redirect? (-> event (get "statusCode")))))
        (let [json-header (->> (get event "responseHeaders")
                               (map (fn [x]
                                      (assoc x "name" (clojure.string/lower-case (get x "name")))
                                      ))
                               (filter (fn [x]
                                         (and (= "content-type" (get x "name"))
                                              (json-header? (get x "value"))
                                              )))
                               first)
              _ (prn ">> json-header: " json-header)
              url (-> event (get "url"))]
          (if (some? json-header)
            (go
              (let [resp (<! (http/get url))
                    _ (prn "resp: " (type (:body resp)))
                    _ (swap! json-url-resp-atom assoc url (:body resp))]
                ))
            (swap! json-url-resp-atom assoc url false))
          )
        ))
    #js ["return native answer"])) ; note: this value will be passed back to Chrome as-is, marshalling won't be applied here


(defn boot-chrome-event-loop! []
  (let [chrome-event-channel (make-chrome-event-channel (chan))]
    (tabs/tap-all-events chrome-event-channel)
    (runtime/tap-all-events chrome-event-channel)

    #_(with-custom-event-listener-factory my-event-listener-factory
      (web-request/tap-on-headers-received-events
       chrome-event-channel
       (clj->js {"urls" ["<all_urls>"] "types" ["main_frame"]})
       #js ["blocking" "responseHeaders"]
       ))

    (run-chrome-event-loop! chrome-event-channel)))

; -- main entry point -------------------------------------------------------------------------------------------------------

(defn init! []
  (log "BACKGROUND: init")
  (boot-chrome-event-loop!))
