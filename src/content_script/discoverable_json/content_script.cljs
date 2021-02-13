(ns discoverable-json.content-script
  (:require-macros [chromex.support :refer [runonce]])
  (:require [discoverable-json.content-script.core :as core]))

(runonce
  (core/init!))
