(ns org-analyzer.browser
  (:require [reagent.core :as r]
            [reagent.ratom :refer [atom] :rename {atom ratom}]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<!]])
  (:require-macros [cljs.core.async.macros :refer [go]]))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(enable-console-print!)

(println "running")

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defonce counter (ratom 0))

(defn test-component [xxx]
  [:div
   [:div "hello hello " xxx]
   [:input  {:type "button" :value (str "test "  @counter)
             :on-click #(do
                          (println "wat???")
                          (swap! counter inc))}]])


(defn start []
  (r/render [test-component "owarstarsta"]
            (js/document.querySelector "#app")))

(start)



#_(go (let [response (<! (http/get "/baz"))]
        (prn response)
        (println (cljs.tools.reader.edn/read-string (:body response)))))
