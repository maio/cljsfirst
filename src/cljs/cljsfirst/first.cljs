(ns cljsfirst.first
  (:require [cljs.core.async :refer [chan close! timeout put! map<]]
            [goog.dom :as dom]
            [goog.style :as style]
            [goog.net.Jsonp])
  (:require-macros [cljs.core.async.macros :as m :refer [go alt!]]))

;; -- https://github.com/swannodette/async-tests/blob/master/src/async_test/utils/helpers.cljs

(defn now [] (/ (.now js/Date) 1000))

(defn by-id [id] (dom/getElement id))

(defn set-html! [el s]
  (aset el "innerHTML" s))

(defn set-style! [el k v]
  (style/setStyle el k v))

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

(defn log [& msgs] (.log js/console (apply str msgs)))

(def data (atom {:time (now)
                 :flickr-photos []
                 :counter 0
                 :fps 40
                 :current-fps 0
                 :ship {:x 100
                        :y 100}}))

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

(defn render-current-fps [_ {current-fps :current-fps}]
  (set-html! (by-id "current-fps") current-fps))

(defn render-ship [_ {{x :x y :y} :ship}]
  (set-style! (by-id "ship") "left" (str x "px"))
  (set-style! (by-id "ship") "top" (str y "px")))

(add-watch data :flickr-photos (on-path-update [:flickr-photos] render-flickr-photos))
(add-watch data :counter (on-path-update [:counter] render-counter))
(add-watch data :ship (on-path-update [:ship] render-ship))
(add-watch data :current-fps (on-path-update [:current-fps] render-current-fps))

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

(defn fan-in
  ([ins] (fan-in (chan) ins))
  ([c ins]
    (go (while true
          (let [[x] (alts! ins)]
            (>! c x))))
    c))

(defn distinct-chan
  ([source] (distinct-chan (chan) source))
  ([c source]
    (go
      (loop [last ::init]
        (let [v (<! source)]
          (when-not (= last v)
            (>! c v))
          (recur v))))
    c))

(defn parse-keys [c type]
  (let [convert {"Right" :right
                 "Left" :left
                 "Up" :up
                 "Down" :down}]
    (map< (fn [event] [type (convert (aget event "keyIdentifier"))]) c)))

(defn keys-chan [type]
  (let [{c :chan} (event-chan (name type))] (parse-keys c type)))

(defn noop [td]
  identity)

(defn add [n]
  (fn [td]
    (fn [x] (+ x (* n td)))))

(defn avg [& xs]
  (/ (apply + xs) (count xs)))

(let [key-chan (distinct-chan (fan-in [(keys-chan :keydown) (keys-chan :keyup)]))
      next-tick #(timeout (/ 1000 (:fps @data)))
      e2mod {[:keydown :right] {:x (add 200)}
            [:keydown :left] {:x (add -200)}
            [:keydown :up] {:y (add -200)}
            [:keydown :down] {:y (add 200)}
            [:keyup :right] {:x noop}
            [:keyup :left] {:x noop}
            [:keyup :up] {:y noop}
            [:keyup :down] {:y noop}}]
  (go
    (loop [tick (next-tick)
           mods {:x noop :y noop}]
      (alt!
        key-chan ([e] (recur tick (merge mods (or (e2mod e) {}))))
        tick ([_] (do
                    (let [tick (next-tick)
                          t0 (@data :time)
                          t1 (now)
                          td (- t1 t0)]
                      (swap! data #(-> %
                                       (update-in [:ship :x] ((:x mods) td))
                                       (update-in [:ship :y] ((:y mods) td))
                                       (update-in [:current-fps] (fn [old] (int (avg old (/ 1 td)))))
                                       (assoc :time t1)))
                      (recur tick mods))))))))

