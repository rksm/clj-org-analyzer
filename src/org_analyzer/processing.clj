(ns org-analyzer.processing
  (:require [clojure.pprint :refer [cl-format]]
            [clojure.string :as s]
            [clojure.zip :as zip]
            [java-time :as time :refer [duration]]
            [organum.core :as org]
            [clojure.java.io :as io])
  (:import [java.time LocalDate LocalDateTime]
           java.time.format.DateTimeFormatter
           java.util.Locale))

(defn org-zipper [parsed]
  (zip/zipper (fn [node] (and (map? node) :content some?))
              (fn [node] (:content node))
              (fn [node children] (assoc node :content children))
              parsed))

(defn parse-and-zip
  ([org-file]
   (let [org-file (if (string? org-file) (io/file org-file) org-file)]
     (parse-and-zip (.getName org-file) org-file)))
  ([name org-file]
   (org-zipper {:type :file
                :name name
                :content (org/parse-file org-file)})))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-
;; CLOCK parsing

(defrecord Clock [start end duration clock-string sections])

(def clock-re #"(?i)^\s*CLOCK:\s*((?:\[|<)[0-9a-z :-]+(?:\]|>))(?:--((?:\[|<)[0-9a-z :-]+(?:\]|>)))?(?:\s*=>\s*([0-9:]+))?\s*$")

(def date-time-patterns [{:parse #(LocalDateTime/parse %1 (DateTimeFormatter/ofPattern %2 %3))
                          :pattern "y-M-d[ ][cccc][ccc][ ]H:m"}
                         {:parse #(. (LocalDate/parse %1 (DateTimeFormatter/ofPattern %2 %3)) (atTime 0 0))
                          :pattern "y-M-d[ ][cccc][ccc]"}])

(def locales [Locale/ENGLISH Locale/GERMAN])

(defn parse-timestamp
  "`string` like \"[2019-06-19 Wed 14:11]\". Returns LocalDateTime."
  [string]
  (let [sanitized (s/replace string #"^(\[|<)|(\]|>)$" "")]
    (first
     (filter some?
             (for [locale locales
                   {:keys [parse pattern]} date-time-patterns]
               (try (parse sanitized pattern locale) (catch Exception e nil)))))))

(defn parse-duration
  "`duration-string` like \"3:22\"."
  [duration-string]
  (as-> duration-string it
    (s/replace it #":" "H")
    (str "PT" it "M")
    (duration it)))

(defn parse-clock [clock-string]
  (let [[_ start end duration] (re-find clock-re clock-string)]
    {:start (parse-timestamp start)
     :end (when end (parse-timestamp end))
     :duration (when duration (parse-duration duration))}))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

(defn find-clocks [zipped-org]
  (loop [cursor zipped-org
         parents []
         clocks []]
    (let [val (zip/node cursor)
          section? (#{:section :file} (:type val))
          metadata? (#{:metadata} (:line-type val))
          with-parents (if section? (conj parents val) parents)
          text-content (:text val)
          clock (when (and metadata? (s/starts-with? text-content "CLOCK:"))
                  (let [{:keys [start end duration]} (parse-clock text-content)]
                    (->Clock start end duration text-content with-parents)))
          clocks (if clock (conj clocks clock) clocks)
          n (zip/next cursor)]
      (if (zip/end? n)
        clocks
        (recur n with-parents clocks)))))

(defn clocks-by-section [clocks]
  (group-by (comp last :sections) clocks))

;; -=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-

