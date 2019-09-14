(ns org-analyzer.graal-resource-indexer
  (:require [clojure.java.io :as io]
            [clojure.pprint :as pp]
            [clojure.string :as s]))

(defn pattern-json-for-files [files]
  (pp/cl-format nil "
{
  \"resources\": [
~{    {\"pattern\": ~s}~^,~%~}
  ]
}

" files))

(defn -main [& args]
  (let [public-dir (io/file "resources/public/")
        config-file (io/file "./target/graal-resource-config.json")
        files (->> public-dir
                   file-seq
                   (filter #(.isFile %))
                   (map #(.getPath %))
                   (map #(s/replace % #"^.*/(public.*$)" "$1"))
                   (map #(s/escape % {\. "\\."})))
        json (pattern-json-for-files files)]
    (when-not (.exists (.getParentFile config-file))
      (.mkdir (.getParentFile config-file)))
    (spit (io/file config-file) json)
    (pp/cl-format true "~a written" config-file)))
