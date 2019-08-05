(ns org-analyzer.view.expts
  (:require [org-analyzer.view.app :as app :refer [fetch-data]]
            [org-analyzer.view.geo :as geo]
            [org-analyzer.view.dom :as dom]
            [reagent.core :as rg :refer [cursor]]
            [reagent.ratom :refer [atom reaction] :rename {atom ratom}]
            [cljs.core.async :refer [go <! chan]]
            [clojure.string :as s]
            [clojure.set :refer [union]]
            [cljs.pprint :refer [cl-format]]
            [org-analyzer.view.expts-helper :as e :refer [expts defexpt]]))

(enable-console-print!)

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn minutes-since-midnight
  [time-string]
  (let [[hours mins]
        (map cljs.reader/read-string
             (drop 1 (re-find #"0?([0-9]{1,2}):0?([0-9]{1,2})" time-string)))]
    (+ (* 60 hours) mins)))

(defn clocks-each-minute [clocks]
  (let [mins (mapv transient (repeat (* 24 60) []))]
    (doseq [clock clocks
            :let [start (minutes-since-midnight (:start clock))
                  end (minutes-since-midnight (:end clock))
                  end (cond
                        (zero? end) (if (zero? start) 0 1440)
                        (< end start) nil
                        :else end)]
            :when end
            i (range start end)]
      (conj! (nth mins i) clock))
    (mapv persistent! mins)))

(defn clock-minute-intervals [clocks]
  (loop [i 0
         current-clocks []
         each-minute (clocks-each-minute clocks)
         result []]
    (if (empty? each-minute)
      result
      (let [n (count (take-while #(= current-clocks %) each-minute))
            next-i (+ i n)
            next (drop n each-minute)]
        (recur next-i
               (first next)
               next
               (if (zero? n) result (conj result [i (+ i n) current-clocks])))))))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(def expt-1-state
  (let [app-state (app/empty-app-state)
        dom-state (app/empty-dom-state)
        event-handlers (app/event-handlers app-state dom-state)]

    ;; fetch data
    (go (let [{cal-data :calendar clock-data :clocks-by-day}
              (<! (fetch-data :from (js/Date. "2019-07-01") :to (js/Date. "2019-08-05")))]
          (swap! app-state assoc
                 :calendar cal-data
                 :clocks-by-day (into (sorted-map-by <) clock-data)
                 :clock-minute-intervals-by-day
                 (into (sorted-map-by <) (map
                                          (fn [[key clocks]] [key (clock-minute-intervals clocks)])
                                          clock-data)))))

    {:app-state app-state
     :dom-state dom-state
     :event-handlers event-handlers }))

(defexpt expt-1
  (let [{:keys [dom-state event-handlers app-state]} expt-1-state
        month-date-and-days (->> @app-state
                                 :calendar
                                 (group-by #(s/replace (:date %) #"^([0-9]+-[0-9]+).*" "$1"))
                                 (into (sorted-map))
                                 first)
        clocks-by-day (cursor app-state [:clocks-by-day])
        max-weight (reaction (->> @clocks-by-day
                                  (map (comp app/sum-clocks-mins second))
                                  (reduce max)))
        calendar-state {:max-weight max-weight
                        :clocks-by-day clocks-by-day
                        :selected-days (reaction (union (:selected-days @app-state)
                                                        (:selected-days-preview @app-state)))}]
    ;; (sc.api/spy)
    [:div.expt
     [:h1 "expt 1"]
     (if (empty? (:calendar @app-state))
       [:span "Loading..."]
       [app/month-view
        dom-state
        event-handlers
        month-date-and-days
        calendar-state])]))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

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
      (js/console.log selected-clocks)

      [:canvas {:id "canvas"
                :width width
                :height height
                :on-mouse-move (fn [evt]
                                 (let [p (dom/mouse-position evt :relative? true)
                                       bounds (->> clock-bounds
                                                   (filter #(geo/contains-point? % p))
                                                   first)]
                                   (when-let [[_ _ _ _ clocks] bounds]

                                     (reset! tooltip [:div (interpose [:br] (map (fn [clock] [:span (str (:location clock)
                                                                                                         " "
                                                                                                         (:start clock)
                                                                                                         "-"
                                                                                                         (:end clock))])
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

(declare with-tooltip-following-mouse)

(defexpt expt-2
  (let [app-state (:app-state expt-1-state)
        clocks-by-day (:clocks-by-day @app-state)
        clock-minute-intervals-by-day (:clock-minute-intervals-by-day @app-state)]
    (rg/with-let [tooltip (ratom "")]
      (with-tooltip-following-mouse
        tooltip
        [:div [activities-by-minute-view
               clock-minute-intervals-by-day
               tooltip]]))))

(defexpt fix-clocks-by-minute
  (let [app-state (:app-state expt-1-state)
        clocks-by-day (:clocks-by-day @app-state)
        clock-minute-intervals-by-day (:clock-minute-intervals-by-day @app-state)
        heading-h 20
        w-ea 10
        h-ea 10
        clock-bounds (doall
                      (for [[row [date clock-intervals]] (map-indexed vector clock-minute-intervals-by-day)
                            bounds (clock-bounds-on-canvas clock-intervals 0 heading-h w-ea row h-ea)]
                        (with-meta bounds {:row row})))]

    [:div.fix-clocks-by-minute.verbatim
     (map (fn [[_ _ _ _ clocks :as bounds] i] ^{:key i}
            (let [row (:row (meta bounds))]
              [:div.bounds
               [:span row " " (pr-str (take 4 bounds))]
               [:br]
               (for [{:keys [location start end]} clocks]
                 ^{:key (str row location)} [:div.clock "  [" start "] - [" end "]: " location])]))
          clock-bounds (range))]))

(defexpt fix-clocks-by-minute-2
  (let [app-state (:app-state expt-1-state)
        clocks-by-day (:clocks-by-day @app-state)]

    [:div.fix-clocks-by-minute.verbatim
     (doall (for [[day clocks] clocks-by-day]
              [:div
               [:div day]
               (doall (for [{:keys [location start end]} clocks]
                        [:div.clock "  [" start "] - [" end "]: " location]))
               ]))]))


(into (sorted-map-by <) (:clock-minute-intervals-by-day @(:app-state expt-1-state)))

(defexpt fix-clocks-by-minute-3
  (let [app-state (:app-state expt-1-state)
        clock-intervals (:clock-minute-intervals-by-day @app-state)]

    [:div.fix-clocks-by-minute.verbatim
     (doall (for [[day intervals] clock-intervals]
              [:div
               [:div day]
               (doall (for [[from to clocks] intervals]
                        [:div (str "  " from " " to)
                         (doall (for [{:keys [location start end]} clocks]
                                  ^{:key (str day location)}
                                  [:div.clock "    [" start "] - [" end "]: " location]))]))
               ]))]))


;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

;; (def el (first (js->clj (js/Array.from (js/document.getElementsByClassName "stalker")))))

;; (dom/el-bounds el)

(defn with-tooltip-following-mouse
  ([tooltip-content comp]
   (with-tooltip-following-mouse tooltip-content {} comp))
  ([tooltip-content {:keys [align offset] :as config} comp]
   (rg/with-let [bounds (atom [0 0 0 0])
                 el (atom nil)
                 follow (ratom false)]
     (let [react-el (rg/as-element comp)

           tooltip-content @tooltip-content
           stalker [:div.stalker
                    {:ref #(do (reset! el %)
                               (reset! bounds (if % (dom/el-bounds %) [0 0 0 0])))
                     :class (if (or (not (string? tooltip-content))
                                    (not-empty tooltip-content))
                              nil "hidden")}
                    tooltip-content]

           type (.-type react-el)
           ref (.-ref react-el)
           props (.-props react-el)
           children (.-children (.-props react-el))

           props (js->clj props)
           onMouseOver (get props "onMouseOver")
           onMouseOut (get props "onMouseOut")
           onMouseMove (get props "onMouseMove")

           offset (or offset [0 0])

           props (clj->js (merge props
                                 {"ref" ref
                                  "onMouseOver" (fn [evt]
                                                  (when (fn? onMouseOver) (onMouseOver evt))
                                                  (reset! follow true))
                                  "onMouseOut" (fn [evt]
                                                 (when (fn? onMouseOut) (onMouseOut evt))
                                                 (reset! follow false))
                                  "onMouseMove" (fn [evt]
                                                  (when (fn? onMouseMove)
                                                    (onMouseMove evt))
                                                  (when (and @el @follow)
                                                    (dom/align-element! :top @el (dom/mouse-position evt) 5)))}))
           ]

       (rg/create-element
        type
        props
        children
        (when @follow (rg/as-element stalker)))))))

(defexpt expt-3

  (rg/with-let [tooltip (ratom "foo")]
    (with-tooltip-following-mouse
      tooltip
      [:div {:style {:background "red"
                     :height "300px"
                     :width "300px"}
             :on-mouse-move (fn [evt] (let [[x y] (dom/mouse-position evt)]
                                        (reset! tooltip [:div [:h1 (str x "/" y)]])))}
       [:div.foo "barrr"]
       ;; [:div.foo "zorrr"]
       ])))

(defexpt expt-4
  (rg/with-let [el (atom nil)
                follow (ratom false)]
    [:div {:style {:background "red"
                   :height "300px"
                   :width "300px"}

           :on-mouse-over #(reset! follow true)
           :on-mouse-out #(reset! follow false)
           :on-mouse-move (fn [evt]
                            (when (and @el @follow)
                              (let [[x y] (dom/mouse-position evt)]
                                (set! (.. @el -style -left) (str x "px"))
                                (set! (.. @el -style -top) (str y "px")))))}
     "outer"
     (when @follow [:div.stalker
                    {:ref #(do (def el %) (println %) (reset! el %))}
                    "123"])]))

(defn start []
  (rg/render [e/expts]
             (. js/document (querySelector "#app"))
             #(println "rendered")))

(start)
