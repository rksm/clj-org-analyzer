(ns org-analyzer.view.timeline
  (:require [reagent.core :as rg :refer [cursor]]
            [reagent.ratom :refer [atom reaction] :rename {atom ratom}]
            [org-analyzer.view.geo :as geo]
            [org-analyzer.view.dom :as dom]
            [clojure.set :refer [intersection]]
            [org-analyzer.view.util :as util]
            [cljs.pprint :refer [cl-format]]
            [clojure.string :as s]))

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
   highlighted-entries
   tooltip
   & [{:keys [height width]}]]

  (rg/with-let [hovered-over-clock (ratom nil)]
    (let [heading-h 12
          n-days (count clock-minute-intervals-by-day)
          w-ea (/ width (* 60 24))
          h-ea (condp > n-days
                 10 15
                 20 12
                 28 11
                 100 8
                 4)

          height (-> h-ea
                     (* (count clock-minute-intervals-by-day))
                     (+ heading-h))
          clock-bounds (doall
                        (for [[row [date clock-intervals]] (map-indexed vector clock-minute-intervals-by-day)
                              bounds (clock-bounds-on-canvas clock-intervals 0 heading-h w-ea row h-ea)]
                          (with-meta bounds {:row row})))

          hovered-over @hovered-over-clock
          selected-clocks @highlighted-entries]

      [:canvas.activities-by-minute-canvas
       {:id "canvas"
        :width width
        :height height
        :on-mouse-out (fn [evt] (reset! hovered-over-clock nil))

        :on-mouse-move (fn [evt]
                         (let [p (dom/mouse-position evt :relative? true)
                               bounds (->> clock-bounds
                                           (filter #(geo/contains-point? % p))
                                           first)]

                           (when-let [[_ _ _ _ clocks] bounds]
                             (reset! highlighted-entries (->> clocks (map :location) set))
                             (when tooltip
                               (reset! tooltip
                                       (when-not (zero? (count clocks))
                                         (let [[{:keys [start end]}] clocks
                                               time (str start " - " end)]
                                           [:div
                                            time
                                            (interpose [:br]
                                                       (for [{:keys [path name]} clocks]
                                                         ^{:key start}
                                                         [:div
                                                          "["
                                                          (interpose " > " (map (comp util/parse-all-org-links s/trim) path))
                                                          "]"
                                                          [:br]
                                                          [:span.name (util/parse-all-org-links name)]]
                                                         ))])))))

                           (reset! hovered-over-clock bounds)))
        :ref (fn [canvas]
               (when canvas
                 (let [ctx (. canvas (getContext "2d"))]
                   (doto ctx
                     (.clearRect 0 0 width height)
                     (.save))
                   (doseq [bounds clock-bounds]
                     (let [[x y w h clocks] bounds
                           {row :row} (meta bounds)
                           {selected-row :row} (meta hovered-over)
                           row-selected? (= row selected-row)
                           bounds-selected? (= hovered-over bounds)
                           no-clocks? (empty? clocks)
                           highlighted-a-bit? (not (empty? (intersection selected-clocks (->> clocks (map :location) set))))
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
