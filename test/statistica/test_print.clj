(ns statistica.test-print
  (:require [statistica.generate :refer :all]
            [clojure.pprint :refer [print-table]]
            [plumbing.core :as pc]))

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
  (print-table (map keys-for-print (take 100 (sort-by #(-> % :sum) > (moda-filter x xs))))))
