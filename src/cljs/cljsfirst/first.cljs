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

(defn log [msg] (.log js/console msg))

(def data (atom {:flickr-photos []
                 :counter 0}))

(defn on-path-update [path f]
  (fn [_ _ old-state new-state]
    (when (not (= (get-in old-state path) (get-in new-state path)))
      (f old-state new-state))))

(defn render-flickr-photos [_ {photos :flickr-photos}]
  (set-html! (by-id "flickr") "")
  (doseq [src photos]
    (append! (by-id "flickr") (str "<img src='" src "' height='150' />"))))

(defn render-counter [_ {counter :counter}]
  (set-html! (by-id "counter") counter))

(add-watch data :flickr-photos (on-path-update [:flickr-photos] render-flickr-photos))
(add-watch data :counter (on-path-update [:counter] render-counter))

(let [flickr-fetch (event-chan (by-id "flickr-fetch") "click")]
  (go
    (while true
      (<! (:chan flickr-fetch))
      (let [photos (<! (jsonp-chan "http://api.flickr.com/services/feeds/photos_public.gne?format=json"))]
        (swap! data assoc :flickr-photos (map #(-> % (aget "media") (aget "m")) (aget photos "items")))))))

(let [c (chan)]
  (go
    (while true
      (>! c "inc")
      (<! (timeout 1000))))
  (go
    (while true
      (<! c)
      (swap! data update-in [:counter] inc))))
