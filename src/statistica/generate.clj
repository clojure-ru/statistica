(ns statistica.generate 
  (:require [statistica.counters-db :refer [get-best-repositories]]
            [statistica.redis :refer [cache]]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [clj-time.format :as f]
            [clj-time.periodic :as p]
            [plumbing.core :as pc]
            [statistica.redis :as r]
            [clojure.pprint :refer [print-table]]
            [cheshire.core :refer [generate-string]]
            [statistica.calculation :refer [stats]]
            [statistica.banned :refer [get-banlist]]))

;; HELPERS

(def minute 60000)

(defn from-sql-to-utc [date]
  (c/from-long (- (.getTime date) (* (.getTimezoneOffset date) minute))))

(defn prepare-jdbc-array-dates [dates]
  (map from-sql-to-utc (seq (.getArray dates))))

(def limit-for-weekly-select 1000)

;; FILTER-FNS

(defn length-filter [{size :max-size-of-incrs} repo-stat]
  (= (count (:incrs repo-stat)) size))

(defn last-increment-filter [_ repo-stat]
  (let [incrs (:incs repo-stat)
        n (count incrs)]
    (> (apply + (drop (- n 3) incrs)) 0)))

(defn composite-filter [global repo-stat]
  (and (length-filter global repo-stat)
       (last-increment-filter global repo-stat)))

;; DB FUNC's

(defn banlist-filter [repos]
  (let [ban (get-banlist)]
    (filter (fn [repo] (not (some #(= % (:full_name repo)) ban))) repos)))

(defn prepare-data [repos]
 (let [dates (prepare-jdbc-array-dates (:dates repos))
       counts (seq (.getArray (:increments repos)))]
  (assoc repos :dates dates :increments counts)))

(defn make-statistics [from to]
  (->> (get-best-repositories from to limit-for-weekly-select)
       banlist-filter
       (map prepare-data)))

(defn get-min-freq [x]
  (get (:freq x) (:min x)))

(defn calculate-globals [statistica]
 (let [init-stat {:max-size-of-incrs 0}]
  (reduce (fn [res repo-stat]
           (assoc res :max-size-of-incrs (max (count (:incrs repo-stat)) (:max-size-of-incrs res))))
   init-stat statistica)))   

(defn generate-and-sort [sort-key filter-fn statistica]
  (let [globals (calculate-globals statistica)]
   (sort-by sort-key > (filter #(filter-fn globals %) statistica))))

;; #TODO move to test.core
;; TEST PRINT

(def keys-for-print #(select-keys % [:sum :incrs :moda :w3 :w2 :w :name]))

(defn print-fmt  [xs]
  (map (pc/fnk [name w sum moda incrs]
         (prn (str name " " sum "  " (seq incrs) " " (seq moda) " " w)))
       xs))

(defn print-tbl [statistica]
 (print-table (map keys-for-print statistica)))

(defn sort-and-print-frequencies [xs]
  (prn "------------------")
  (prn (str "min-val " (key xs)))
  (doall
   (map #(do
           (prn (str " -------- frequency for min-val " (key %) " ------------ " ))
           (doall (print-fmt (take 10 (sort-by :sum > (val %)))))) (val xs))))

(defn sort-and-print-probs [xs]
  (prn " ------ MAX expected value ------ ")
  (doall (map print-fmt (take 10 (sort-by :m > xs))))
  (prn " ------ MAX dispersion ------ ")
  (doall (map print-fmt  (take 10 (sort-by :m2 < xs))))
  (prn " ------ MAX MOM ------ ")
  (doall (map print-fmt (take 10 (sort-by :m3 > xs)))))

(defn moda-filter [freq-moda xs]
  (filter #(= (-> % :moda second) freq-moda) xs))

(defn sort-by-moda [xs x]
  (prn " -------- MODA ---------- ")
  ;;(print-fmt )
  (print-table (map keys-for-print (take 100 (sort-by #(-> % :sum) > (moda-filter x xs))))))

;; CALCULUS

(defn gigants [statics]
  (->>  (group-by :min statics)
        (map #(hash-map (key %) (group-by get-min-freq (val %))))
        (into (sorted-map))))

(defn best-increments [from to]
  (map stats (filter #(= (:counter_id %) 4) (make-statistics from to))))

(def number-of-best-repositories 10)
(def keys-for-json #(select-keys % [:name :incrs :url :diffw :w :cplx :sum]))

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

(defn make-classification [sort-key from to]
  (->> (best-increments from to)
       (generate-and-sort sort-key composite-filter)
       (take number-of-best-repositories)
       (map keys-for-json)
       (#(generate-string {:dates (map formate-date (make-date-range from to))
                           :data %}))))

(defn make-key [from to]
  (let [formatter (f/formatter "dd.MM.yyyy")]
    (apply str (map #(f/unparse formatter (c/to-date-time %)) [from to]))))
  
(defn get-classification 
  ([sort-key]
    (get-classification sort-key (t/minus (t/today) (t/days 8)) (t/today)))
  ([sort-key from] 
    (get-classification sort-key from (t/today)))
  ([sort-key from to]
    (let [key (make-key from to)]
     (or (r/get key)
         (r/set key (make-classification sort-key from to))))))
