(ns org-analyzer.view.dom
  (:require [org-analyzer.view.geo :as geo]))

(defn scrolled-window-bounds []
  [(.-scrollLeft js/document.documentElement)
   (.-scrollTop js/document.documentElement)
   (.-clientWidth js/document.documentElement)
   (.-clientHeight js/document.documentElement)])


(defn screen-relative-bounds [el]
  (let [b-rect (.getBoundingClientRect el)]
    [(.-left b-rect)
     (.-top b-rect)
     (.-width b-rect)
     (.-height b-rect)]))

(defn global-bounds [el]
  (geo/translate
   (screen-relative-bounds el)
   [js/window.scrollX js/window.scrollY]))

(defn mouse-position [event & {:keys [relative?]}]
  (let [client-x (.-clientX event) client-y (.-clientY event)]
    (if client-x
      (let [target (.-target event)
            doc (or (and target (.-ownerDocument target)) js/document)
            doc-el (and doc (.-documentElement doc))
            body (.-body doc)
            scroll-left (or (.-scrollLeft doc-el) (.-scrollLeft body) 0)
            scroll-top (or (.-scrollTop doc-el) (.-scrollTop body) 0)
            client-left (or (.-clientLeft doc-el) (.-clientLeft body) 0)
            client-top (or (.-clientTop doc-el) (.-clientTop body) 0)
            [offset-x offset-y] (if-not relative?
                                  [0 0]
                                  (loop [target target offset-x 0 offset-y 0]
                                    (if-not target
                                      [(- offset-x) (- offset-y)]
                                      (recur (.-offsetParent target)
                                             (+ offset-x (.-offsetLeft target))
                                             (+ offset-y (.-offsetTop target))))))]
        [(+ client-x (- scroll-left client-left) offset-x)
         (+ client-y (- scroll-top client-top) offset-y)])
      [0 0])))

(defn align-element!
  ([how el to-point]
   (align-element! how el to-point 0))
  ([how el to-point offset]
   (align-element! how el to-point offset true))
  ([how el to-point offset keep-inside-view-bounds?]
   (let [[rect-point-fn o-x o-y] (case how
                                   :top [geo/bottom-center 0 (- offset)]
                                   :bottom [geo/top-center 0 offset]
                                   :left [geo/right-center (- offset) 0]
                                   :right [geo/left-center offset 0])

         bounds (screen-relative-bounds el)
         [x y w h] (geo/align bounds
                              (rect-point-fn bounds)
                              to-point)

         x (+ o-x x)
         y (+ o-y y)

         [x y] (if keep-inside-view-bounds?
                 (let [[win-x win-y win-w win-h :as screen-window-bounds]
                       (when keep-inside-view-bounds? (scrolled-window-bounds))
                       r (+ x w)
                       b (+ y h)
                       win-r (+ win-x win-w)
                       win-b (+ win-y win-h)]
                   [(cond
                      (< x 0) 0
                      (> r win-r) (- x (- r win-r))
                      :else x)
                    (cond
                      (< y 0) 0
                      (> b win-b) (- y (- b win-b))
                      :else y)])
                 [x y])]

     (set! (.. el -style -left) (str x "px"))
     (set! (.. el -style -top) (str y "px")))))

(defn select-element [el]
  (.empty (js/document.getSelection))
  (let [r (js/document.createRange)]
    (-> r (.selectNode el))
    (.addRange (js/document.getSelection) r)))
