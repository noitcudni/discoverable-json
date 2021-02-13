(ns discoverable-json.popup.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop]])
  (:require [cljs.core.async :refer [<! put! chan]]
            [clojure.walk]
            [clojure.string]
            [chromex.ext.tabs :as tabs]
            [re-com.core :as recom]
            [reagent.core :as reagent :refer [atom]]
            [reagent.dom :refer [render]]
            [cognitect.transit :as t]
            [domina :refer [single-node nodes]]
            [domina.xpath :refer [xpath]]
            [discoverable-json.content-script.common :as common]
            [discoverable-json.background.storage :refer [store-license get-license remove-license ]]
            [chromex.logging :refer-macros [log info warn error group group-end]]
            [chromex.protocols.chrome-port :refer [post-message!]]
            [chromex.ext.runtime :as runtime :refer-macros [connect]]))

(def upload-chan (chan 1 (map (fn [e]
                                (let [target (.-currentTarget e)
                                      file (-> target .-files (aget 0))]
                                  (set! (.-value target) "")
                                  file
                                  )))))
(def read-chan (chan 1 (map #(-> % .-target .-result js->clj))))


(defn licensed-page [background-port]
  [:div "got a valid license"]
  (fn []
    [recom/v-box
     :width "480px"
     :align :center
     :children [
                [recom/v-box
                 :align :start
                 :style {:padding "10px"}
                 :children [[recom/title :label "Discoverable JSON (DJ)" :level :level1]
                            [recom/label :label "is automatically triggered when visiting a page with a JSON blob."]
                            [recom/gap :size "10px"]
                            [recom/label :label "If you'd like to run the extension on a JSON file on your local system,"]
                            [recom/label :label "click on the button below and click on 'Upload Json' to upload your JSON file."]
                            [recom/gap :size "10px"]]
                 ]

                [recom/v-box
                 :gap "10px"
                 :align :stretch
                 :children [[recom/button
                             :label "Open a New Tab"
                             :tooltip [recom/v-box
                                       :children [[recom/label :label "It will open a new tab"]]]
                             :style {:width "200px"
                                     :background-color "#007bff"
                                     :color "white"}
                             :on-click (fn [e]
                                         (post-message! background-port (common/marshall {:type :open-new-tab})))
                             ]]

                 ]

                ]
     ]))

; -- a message loop ---------------------------------------------------------------------------------------------------------

(defn process-message! [message]
  (log "POPUP: got message:" message))

(defn run-message-loop! [message-channel]
  (log "POPUP: starting message loop...")
  (go-loop []
    (when-some [message (<! message-channel)]
      (process-message! message)
      (recur))
    (log "POPUP: leaving message loop")))

(defn connect-to-background-page! [background-port]
  (run-message-loop! background-port))

(defn current-page[background-port]
  [licensed-page background-port])

(defn mount-root [background-port]
  (render [current-page background-port] (.getElementById js/document "app")))

; -- main entry point -------------------------------------------------------------------------------------------------------
(defn init! []
  (let [background-port (runtime/connect)]

    ;; handle onload
    (go-loop []
      (let [reader (js/FileReader.)
            file (<! upload-chan)]
        (set! (.-onload reader) #(put! read-chan %))
        (.readAsText reader file)
        (recur)))

    ;; handle read the file
    (go-loop []
      (let [file-content (clojure.string/trim (<! read-chan))
            r (t/reader :json)
            json-data (t/read r file-content)
            ]
        (prn "json-data: " json-data)
        ;; TODO : call set-json-data
        (recur)))

    (connect-to-background-page! background-port)
    (mount-root background-port)))
