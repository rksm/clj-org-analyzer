(ns org-analyzer.view.expts-helper)

(defmacro defexpt
  ""
  {:style/indent [1]}
  [name & body]
  `(let [key# (keyword '~name)
         fun# (defn ~name [] ~@body)]
     (swap! org-analyzer.view.expts-helper/expt-registry assoc key# fun#)
     fun#))
