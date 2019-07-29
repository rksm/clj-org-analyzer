(ns org-analyzer.view.selection
  (:require [org-analyzer.view.geo :refer [rect]]
            [org-analyzer.view.dom :refer [mouse-position]]))

(def empty-rectangle-selection-state {:mousedown? false
                                      :start-pos nil
                                      :current-pos nil
                                      :offset [0 0]
                                      :relative-bounds (rect)
                                      :global-bounds (rect)})

(defn make-rectangle
  ([x1 y1 x2 y2]
   (make-rectangle x1 y1 x2 y2 0 0))
  ([x1 y1 x2 y2 offset-x offset-y]
   (let [left (min x1 x2)
         top (min y1 y2)
         right (max x1 x2)
         bottom (max y1 y2)
         w (- right left)
         h (- bottom top)]
     (rect (- left offset-x) (- top offset-y) w h))))

(defn with-selection-bounds [selection-state]
  (let [{[x2 y2] :start-pos [x1 y1] :current-pos [o-x o-y] :offset} selection-state]
    (assoc selection-state
           :global-bounds (make-rectangle x1 y1 x2 y2)
           :relative-bounds (make-rectangle x1 y1 x2 y2 o-x o-y))))


(defn on-mouse-down-track-drag [state evt]
  (let [pos (mouse-position evt)]
    (swap! state
           #(with-selection-bounds (assoc %
                                          :mousedown? true
                                          :start-pos pos
                                          :current-pos pos)))))

(defn on-mouse-up-track-drag [state evt]
  (swap! state assoc :mousedown? false))

(defn on-mouse-move-track-drag [state evt]
  (let [{:keys [mousedown?]} @state]
    (when mousedown?
      (let [pos (mouse-position evt)]
        (swap! state
               #(with-selection-bounds (assoc % :current-pos pos)))))))

(defn drag-mouse-handlers [state & {:keys [on-selection-start on-selection-end on-selection-change]}]
  {:ref (fn [el]
          (swap! state assoc :offset (if el [(.-offsetLeft el) (.-offsetTop el)] [0 0])))
   :on-mouse-down #(do
                     (on-mouse-down-track-drag state %)
                     (when (fn? on-selection-start)
                       (on-selection-start @state)))
   :on-mouse-up #(do
                   (on-mouse-up-track-drag state %)
                   (when (fn? on-selection-end)
                     (on-selection-end @state)))
   :on-mouse-move #(do
                     (on-mouse-move-track-drag state %)
                     (when (fn? on-selection-change)
                       (on-selection-change @state)))})
