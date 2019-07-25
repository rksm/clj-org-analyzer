(ns org-analyzer.browser
  (:require [reagent.core :as r]
            [reagent.ratom :refer [atom] :rename {atom ratom}]))

(enable-console-print!)

(println "running")

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defonce counter (ratom 0))

(defn test-component [xxx]
  [:div
   [:div "hello hello " xxx]
   [:input {:type "button" :value (str "test "  @counter)
            :on-click #(do
                         (println "wat???")
                         (swap! counter inc))}]])


(defn start []
  (r/render [test-component "owarstarsta"]
            (js/document.querySelector "#app")))

(start)
