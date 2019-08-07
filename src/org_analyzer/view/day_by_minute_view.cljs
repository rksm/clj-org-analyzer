(ns org-analyzer.view.day-by-minute-view
  (:require [reagent.core :as rg :refer [cursor]]
            [reagent.ratom :refer [atom reaction] :rename {atom ratom}]
            [org-analyzer.view.geo :as geo]
            [org-analyzer.view.dom :as dom]
            [clojure.set :refer [intersection]]))

(defn clock-bounds-on-canvas [intervals-with-clocks offset-x offset-y minute-width row row-height]
  (for [[from to clocks] intervals-with-clocks
        :let [x (* from minute-width)
              y (* row row-height)
              w (- (* to minute-width) x)
              h (- (* (inc row) row-height) y)
              x (+ x offset-x)
              y (+ y offset-y)]]
    [x y w h clocks]))

(defn activities-by-minute-view
  [clock-minute-intervals-by-day
   highlighted-clocks
   tooltip
   & [{:keys [height width]}]]

  (rg/with-let [moused-over (ratom nil)
                ideal-width (ratom (or width js/document.body.clientWidth))]
    (let [heading-h 12
          height (min js/document.documentElement.clientHeight
                      (let [n-days (count clock-minute-intervals-by-day)]
                        (cond
                          (< n-days 10) (* 15 (count clock-minute-intervals-by-day))
                          (< n-days 20) (* 12 (count clock-minute-intervals-by-day))
                          (< n-days 35) (* 10 (count clock-minute-intervals-by-day))
                          :else (* 8 (count clock-minute-intervals-by-day)))))
          ;; height (max (+ 20 heading-h) height)
          ;; width @ideal-width
          w-ea (/ width (* 60 24))
          h-ea (/ (- height heading-h) (count clock-minute-intervals-by-day))
          clock-bounds (doall
                        (for [[row [date clock-intervals]] (map-indexed vector clock-minute-intervals-by-day)
                                        ; :let [_ (println date)]
                              bounds (clock-bounds-on-canvas clock-intervals 0 heading-h w-ea row h-ea)]
                          (with-meta bounds {:row row})))

          selected @moused-over
          selected-clocks @highlighted-clocks]

      [:canvas.activities-by-minute
       {:id "canvas"
        :width width
        :height height
        :on-mouse-out (fn [evt] (reset! moused-over nil))

        :on-mouse-move (fn [evt]
                         (let [p (dom/mouse-position evt :relative? true)
                               bounds (->> clock-bounds
                                           (filter #(geo/contains-point? % p))
                                           first)]
                           (when-let [[_ _ _ _ clocks] bounds]
                             (reset! highlighted-clocks (set (map :id clocks)))
                             (when tooltip
                               (reset! tooltip [:div (interpose
                                                      [:br]
                                                      (map (fn [{:keys [location start end]}]
                                                             ^{:key start} [:span (str location " " start "-" end)])
                                                           clocks))])))
                           (reset! moused-over bounds)))
        :ref (fn [canvas]
               (when canvas
                 (let [[_ _ w _] (dom/screen-relative-bounds (.-parentElement canvas))]
                   (reset! ideal-width w))
                 (let [ctx (. canvas (getContext "2d"))]
                   (doto ctx
                     (.clearRect 0 0 width height)
                     (.save))
                   (doseq [bounds clock-bounds]
                     (let [[x y w h clocks] bounds
                           {row :row} (meta bounds)
                           {selected-row :row} (meta selected)
                           row-selected? (= row selected-row)
                           bounds-selected? (= selected bounds)
                           no-clocks? (empty? clocks)
                           highlighted-a-bit? (not (empty? (intersection selected-clocks (set (map :id clocks)))))
                           color (cond
                                   (and no-clocks? row-selected?) "#ddd"
                                   no-clocks? "#fff"
                                   bounds-selected? "red"
                                   highlighted-a-bit? "salmon"
                                   row-selected? "#6fA"
                                   :else "#3e8")]

                       (.save ctx)
                       (set! (.-fillStyle ctx) color)

                       (set! (.-strokeStyle ctx) "rgba(60,160,120,.4)")
                       (set! (.-lineWidth ctx) 1)

                       #_(when no-clocks?
                           (set! (.-shadowColor ctx) "#d53")
                           (set! (.-shadowBlur ctx) 10)
                           (set! (.-lineJoin ctx) "bevel"))
                       (.fillRect ctx x y w h)
                       (when-not no-clocks?
                         (.strokeRect ctx (inc x) (inc y) (dec w) (dec h)))
                       (.restore ctx)

                       ))
                   (doto ctx (.restore) (.save))
                   (set! (.-textBaseline ctx) "top")
                   (set! (.-textAlign ctx) "center")
                   (set! (.-font ctx) "10px sans-serif")
                   (set! (.-strokeStyle ctx) "white")
                   (doseq [h (drop 1 (range 24))]
                     (. ctx (fillText (str h) (* h 60 w-ea) 2)))
                   (doto ctx
                     (.stroke)
                     (.restore)))))}])))
