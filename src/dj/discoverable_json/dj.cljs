(ns discoverable-json.dj
  (:require-macros [chromex.support :refer [runonce]])
  (:require [discoverable-json.dj.core :as core]))

(runonce
 (core/init!))
