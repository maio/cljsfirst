(ns cljsfirst.first
  (:require [cljs.core.async :refer [chan close! timeout put!]]
            [goog.dom :as dom])
  (:require-macros [cljs.core.async.macros :as m :refer [go]]))

;; -- https://github.com/swannodette/async-tests/blob/master/src/async_test/utils/helpers.cljs

(defn by-id [id] (dom/getElement id))

(defn set-html! [el s]
  (aset el "innerHTML" s))

(defn event-chan
  ([type] (event-chan js/window type))
  ([el type] (event-chan (chan) el type))
  ([c el type]
    (let [writer #(put! c %)]
      (.addEventListener el type writer)
      {:chan c
       :unsubscribe #(.removeEventListener el type writer)})))

;; --

(defn log [msg]
  (.log js/console msg))

(let [start (event-chan (by-id "start") "click")]
  (go
    (while true
      (log (<! (:chan start))))))

(let [src (chan)]
  (go
    (while true
      (>! src "PING")
      (<! (timeout 800))))
  (go
    (while true
      (>! src "ping")
      (<! (timeout 1000))))
  (go
    (while true
      (log (<! src)))))

