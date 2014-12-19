(ns statistica.generate 
  (:require [statistica.counters-db :refer [get-best-repositories]]
            [statistica.redis :refer [cache]]
            [statistica.banned :refer [get-banlist]]
            [statistica.date-helpers :refer :all]
            [statistica.redis :as r]
            [statistica.calculation :refer [stats]]
            [clj-time.core :as t]
            [plumbing.core :as pc]
            [cheshire.core :refer [generate-string]]))

(def limit-for-weekly-select 1000)

(def number-of-best-repositories 10)

(def keys-for-json #(select-keys % [:name :incrs :url :diffw :w :cplx :sum]))

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

;; CALCULUS

(defn gigants [statics]
  (->>  (group-by :min statics)
        (map #(hash-map (key %) (group-by get-min-freq (val %))))
        (into (sorted-map))))

(defn best-increments [from to]
  (map stats (filter #(= (:counter_id %) 4) (make-statistics from to))))

(defn make-classification [sort-key from to]
  (->> (best-increments from to)
       (generate-and-sort sort-key composite-filter)
       (take number-of-best-repositories)
       (map keys-for-json)
       (#(generate-string {:dates (map formate-date (make-date-range from to))
                           :data %}))))

(defn get-classification 
  ([sort-key]
    (get-classification sort-key (t/minus (t/today) (t/days 8)) (t/today)))
  ([sort-key from] 
    (get-classification sort-key from (t/today)))
  ([sort-key from to]
    (let [key (make-key from to)]
     (or (r/get key)
         (r/set key (make-classification sort-key from to))))))
