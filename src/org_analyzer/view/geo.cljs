(ns org-analyzer.view.geo)

(defn pos+ [[x1 y1] [x2 y2]]
  [(+ x1 x2) (+ y1 y2)])

(defn pos- [[x1 y1] [x2 y2]]
  [(- x1 x2) (- y1 y2)])

(defn valid-rect?
  [[x y w h]]
  (and (>= w 0)
       (>= h 0)))

(defn rect
  ([] (rect 0 0 0 0))
  ([w h] (rect 0 0 w h))
  ([x y w h] [x y w h]))

(defn contains-point?
  [[x y w h] [px py]]
  (and (< x px (+ x w)) (< y py (+ y h))))

(defn contains-rect?
  "does rect-1 (first arg) fully contain rect-2 (second arg)?"
  [[l1 t1 w1 h1] [l2 t2 w2 h2]]
  (let [r1 (+ l1 w1)
        b1 (+ t1 h1)
        r2 (+ l2 w2)
        b2 (+ t2 h2)]
    (and (<= l1 l2)
         (<= t1 t2)
         (<= r2 r1)
         (<= b2 b1))))

(defn translate
  [[x y w h] [px py]]
  [(+ x px) (+ y py) w h])

(defn align
  [rect p1 p2]
  (translate rect (pos- p2 p1)))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn translate-to-fit-in
  [[x1 y1 w1 h1 :as smaller-rect]
   [x2 y2 w2 h2 :as larger-rect]]
  (let [r1 (+ x1 w1)
        b1 (+ y1 h1)
        r2 (+ x2 w2)
        b2 (+ y2 h2)

        x1 (cond
             (< x1 x2) x2
             (> r1 r2) (- x1 (- r1 r2))
             :else x1)
        y1 (cond
             (< y1 y2) y2
             (> b1 b2) (- y1 (- b1 b2))
             :else y1)]
    [x1 y1 w1 h1]))

;; (translate-to-fit-in [10 10 10 10] [0 0 30 30])

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn top
  [[_ y _ _]]
  y)

(defn left
  [[x _ _ _]]
  x)

(defn right
  [[x _ w _]]
  (+ x w))

(defn bottom
  [[_ y _ h]]
  (+ y h))

(defn center
  [[x y w h]]
  [(+ x (/ w 2)) (+ y (/ h 2))])

(defn top-left
  [r]
  [(left r) (top r)])

(defn left-center
  [[_ y _ h :as r]]
  [(left r) [(+ y (/ h 2))]])

(defn bottom-left
  [r]
  [(left r) (bottom r)])

(defn top-center
  [[x _ w _ :as r]]
  [(+ x (/ w 2)) (top r)])

(defn top-right
  [r]
  [(right r) (top r)])

(defn right-center
  [[_ y _ h :as r]]
  [(right r) [(+ y (/ h 2))]])

(defn bottom-right
  [r]
  [(right r) (bottom r)])

(defn bottom-center
  [[x _ w _ :as r]]
  [(+ x (/ w 2)) (bottom r)])
