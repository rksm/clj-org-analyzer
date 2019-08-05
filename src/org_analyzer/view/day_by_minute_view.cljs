(ns org-analyzer.view.day-by-minute-view
  (:require [reagent.core :as rg :refer [cursor]]
            [reagent.ratom :refer [atom reaction] :rename {atom ratom}]
            [org-analyzer.view.geo :as geo]
            [org-analyzer.view.dom :as dom]))

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
   tooltip
   & [{:keys [width height]
       :or {width (- js/document.body.clientWidth 20)
            height (* 12 (count clock-minute-intervals-by-day))}}]]

  (rg/with-let [moused-over (ratom nil)]
    (let [heading-h 12
          height (max (+ 20 heading-h) height)
          w-ea (/ width (* 60 24))
          h-ea (/ (- height heading-h) (count clock-minute-intervals-by-day))
          clock-bounds (doall
                        (for [[row [date clock-intervals]] (map-indexed vector clock-minute-intervals-by-day)
                              bounds (clock-bounds-on-canvas clock-intervals 0 heading-h w-ea row h-ea)]
                          (with-meta bounds {:row row})))
          [_ _ _ _ selected-clocks :as selected] @moused-over
          selected-clocks (set (map :location selected-clocks))]

      [:canvas {:id "canvas"
                :width width
                :height height
                :on-mouse-move (fn [evt]
                                 (let [p (dom/mouse-position evt :relative? true)
                                       bounds (->> clock-bounds
                                                   (filter #(geo/contains-point? % p))
                                                   first)]
                                   (when-let [[_ _ _ _ clocks] bounds]

                                     (reset! tooltip [:div (interpose
                                                            [:br]
                                                            (map (fn [{:keys [location start end]}]
                                                                   ^{:key start} [:span (str location " " start "-" end)])
                                                                 clocks))])
                                     (println @tooltip))
                                   (reset! moused-over bounds)))
                :ref (fn [canvas]
                       (when canvas
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
                                   highlighted-a-bit? (not (empty? (clojure.set/intersection selected-clocks (set (map :location clocks)))))
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
