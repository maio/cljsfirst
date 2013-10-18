(ns cljsfirst.first
  (:require [cljs.core.async :refer [chan close! timeout]])
  (:require-macros [cljs.core.async.macros :as m :refer [go]]))

(defn log [msg]
  (.write js/document msg))

(let [src (chan)]
  (go
    (while true
      (>! src "ping")
      (<! (timeout 1000))))
  (go
    (while true
      (log (<! src)))))


