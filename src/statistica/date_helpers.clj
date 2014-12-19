(ns statistica.date-helpers
  (:require [clj-time.core :as t]
            [clj-time.coerce :as c]
            [clj-time.format :as f]
            [clj-time.periodic :as p]))

(def minute 60000)

(defn from-sql-to-utc [date]
  (c/from-long (- (.getTime date) (* (.getTimezoneOffset date) minute))))

(defn prepare-jdbc-array-dates [dates]
  (map from-sql-to-utc (seq (.getArray dates))))

(defn local-to-utc-date [date]
  (t/date-time (t/year date) (t/month date) (t/day date)))

(defn make-date-range [from to]
  (let [from# (local-to-utc-date from)
        to# (local-to-utc-date to)]
  (rest (take (inc (t/in-days (t/interval from# to#))) 
              (p/periodic-seq from (t/days 1))))))

(defn formate-date [date]
  (let [formatter (f/formatter "dd.MM.yy")]
    (f/unparse formatter (local-to-utc-date date))))

(defn make-key [from to]
  (let [formatter (f/formatter "dd.MM.yyyy")]
    (apply str (map #(f/unparse formatter (c/to-date-time %)) [from to]))))
