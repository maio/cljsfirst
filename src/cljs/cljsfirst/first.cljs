(ns cljsfirst.first
  (:require [cljs.core.async :refer [chan close! timeout put!]]
            [goog.dom :as dom]
            [goog.net.Jsonp])
  (:require-macros [cljs.core.async.macros :as m :refer [go]]))

;; -- https://github.com/swannodette/async-tests/blob/master/src/async_test/utils/helpers.cljs

(defn by-id [id] (dom/getElement id))

(defn set-html! [el s]
  (aset el "innerHTML" s))

(defn append! [el html]
  (dom/append el (dom/htmlToDocumentFragment html)))

(defn event-chan
  ([type] (event-chan js/window type))
  ([el type] (event-chan (chan) el type))
  ([c el type]
    (let [writer #(put! c %)]
      (.addEventListener el type writer)
      {:chan c
       :unsubscribe #(.removeEventListener el type writer)})))

(defn jsonp-chan
  ([uri] (jsonp-chan (chan) uri))
  ([c uri]
    (let [jsonp (goog.net.Jsonp. (goog.Uri. uri) "jsoncallback")]
      (.send jsonp nil #(put! c %))
      c)))

;; --

(defn log [msg]
  (.log js/console msg))

(let [flickr-fetch (event-chan (by-id "flickr-fetch") "click")]
  (go
    (log (<! (:chan flickr-fetch)))
    (let [photos (<! (jsonp-chan "http://api.flickr.com/services/feeds/photos_public.gne?format=json"))]
      (doseq [desc (map #(aget % "description") (aget photos "items"))]
        (append! (by-id "flickr") desc)))))

(let [src (chan)]
  (go
    (while true
      (>! src "PING")
      (<! (timeout 2800))))
  (go
    (while true
      (>! src "ping")
      (<! (timeout 3000))))
  (go
    (while true
      (log (<! src)))))
