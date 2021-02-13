(ns discoverable-json.content-script.core
  (:require-macros [cljs.core.async.macros :refer [go-loop go]])
  (:require [cljs.core.async :refer [<! timeout]]
            [discoverable-json.content-script.common :as common]
            [chromex.logging :refer-macros [log info warn error group group-end]]
            [chromex.protocols.chrome-port :refer [post-message!]]
            [chromex.ext.runtime :as runtime :refer-macros [connect]]
            [domina :refer [single-node nodes append!]]
            [domina.xpath :refer [xpath]]
            [clojure.walk]
            [clojure.string]
            [cognitect.transit :as t]
            [cljs-gron-online.core]
            [reagent.core :as reagent :refer [atom]]
            ))

; -- a message loop ---------------------------------------------------------------------------------------------------------
(defn main-panel []
  [:div "rendered from reagent!"])

(defn override-body [json-data]
  (let [_ (set! js/document.body.innerHTML "<div id='app'></div>")
        css-resources ["css/bootstrap.css"
                       "css/material-design-iconic-font.min.css"
                       "css/re-com.css"
                       "css/gron.css"]
        ;; load all the needed css
        _ (doseq [css css-resources]
            (let [link (.createElement js/document "link")
                  _ (set! (.. link -rel) "stylesheet")
                  _ (set! (.. link -href) (runtime/get-url css))]
              (.appendChild js/document.head link)
              ))]
    (go
      (<! (timeout 200))
      (cljs-gron-online.core/init-with-data (clojure.walk/stringify-keys json-data)))
    ))

(defn process-message! [message]
  (let [_ (prn ">> message: " message) ;;xxx
        {:keys [type] :as whole-msg} (common/unmarshall message)]
    (case type
      nil
      )
    ))

(defn run-message-loop! [message-channel]
  (log "CONTENT SCRIPT: starting message loop...")
  (go-loop []
    (when-some [message (<! message-channel)]
      (process-message! message)
      (recur))
    (log "CONTENT SCRIPT: leaving message loop")))

; -- a simple page analysis  ------------------------------------------------------------------------------------------------

(defn remove-jsonp-padding [s]
  (-> s
      (clojure.string/replace #"\s*while\((1|true)\)\s*;?" "")
      (clojure.string/replace #"\s*for\(;;\)\s*;?" "")
      (clojure.string/replace #"^[^{\[].+\(\s*?\{" "{")
      (clojure.string/replace #"\}\s*?\);?\s*$" "}")))

(def has-license-ratom? (reagent/atom true))

(defn connect-to-background-page! []
  (go
    (let [background-port (runtime/connect)]
      ;; NOTE: can override the content
      ;; (post-message! background-port (common/marshall {:type :init-content}))

      (run-message-loop! background-port)
      (.addEventListener
       js/document
       "DOMContentLoaded"
       (fn []
         (let [child-nodes (-> "//body/node()" xpath nodes)
               r (t/reader :json)]
           (when (and (= 1 (count child-nodes)) (= "PRE" (.-nodeName (first child-nodes))))
             (set! (.-hidden (first child-nodes)) true)
             (go-loop []
               (cond (nil? @has-license-ratom?) (do
                                                  (<! (timeout 100))
                                                  (prn "trying again..")
                                                  (recur))
                     (true? @has-license-ratom?) (try
                                                   (override-body (t/read r (-> child-nodes
                                                                                first
                                                                                .-textContent
                                                                                remove-jsonp-padding)))
                                                   ;; (set! (.-hidden (first child-nodes)) true)
                                                   ;; (prn "NEED to proceed!!")
                                                   (catch js/Error e
                                                     ;; not a json payload
                                                     (set! (.-hidden (first child-nodes)) false)
                                                     ))
                     :else (do (prn "no valid license")
                               (set! (.-hidden (first child-nodes)) false))
                     ))))))
      )))

; -- main entry point -------------------------------------------------------------------------------------------------------

(defn init! []
  (log "CONTENT SCRIPT: init")
  (connect-to-background-page!))
