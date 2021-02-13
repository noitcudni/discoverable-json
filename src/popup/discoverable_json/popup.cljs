(ns discoverable-json.popup
  (:require-macros [chromex.support :refer [runonce]])
  (:require [discoverable-json.popup.core :as core]))

(runonce
  (core/init!))
