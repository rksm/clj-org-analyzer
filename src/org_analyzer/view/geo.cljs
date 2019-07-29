(ns org-analyzer.view.geo)

(defn pos+ [[x1 y1] [x2 y2]]
  [(+ x1 x2) (+ y1 y2)])

(defn pos- [[x1 y1] [x2 y2]]
  [(- x1 x2) (- y1 y2)])

(defn rect
  ([] (rect 0 0 0 0))
  ([w h] (rect 0 0 w h))
  ([x y w h] {:left x :top y :width w :height h}))
