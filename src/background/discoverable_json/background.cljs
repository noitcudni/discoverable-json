(ns discoverable-json.background
  (:require-macros [chromex.support :refer [runonce]])
  (:require [discoverable-json.background.core :as core]))

(runonce
  (core/init!))
