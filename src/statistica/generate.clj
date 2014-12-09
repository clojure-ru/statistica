(ns statistica.generate 
  (:require [statistica.counters-db :refer [get-best-repositories]]
            [clj-time.core :as t]
            [clj-time.coerce :as c]
            [plumbing.core :as pc]
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

(defn generate-and-sort [statistica sort-key filter-fn]
  (let [globals (calculate-globals statistica)]
   (sort-by sort-key > (filter #(filter-fn globals %) statistica))))

;; JSON GENERATE

(def keys-for-json #(select-keys % [:name :incrs :url :diffw :w :cplx :sum]))

(defn generate-json-stat [statistica]
  (generate-string (map keys-for-json statistica)))

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

(defn classification 
  ([sort-key]
    (classification sort-key (t/minus (t/today) (t/days 8)) (t/today)))
  ([sort-key from] 
    (classification sort-key from (t/today)))
  ([sort-key from to]
    (let [statistica (best-increments from to)]
      (generate-and-sort statistica sort-key composite-filter))))
