(ns statistica.calculation
  (:require [plumbing.core :as pc]
            [plumbing.graph :as graph]
            [clj-time.coerce :as c]
            [clojure.string :as string]))

(def github-http-prefix "https://github.com/")

;; STATISTIC FUNCTIONS

(defn weight-with-count-days [increments count-of-day]
  (let [days (range count-of-day 1 -1)] 
    (float (apply + (map #(/ (* %1 count-of-day) %2) increments days)))))

(pc/defnk increasing-increments [increments]
  (reduce #(conj %1 (+ %2 (last %1))) [(first increments)] (rest increments)))

(pc/defnk diff-increments [increments]
  (map #(- %1 %2) (rest increments) increments))

(pc/defnk diff-weight [diff]
  (weight-with-count-days diff (count diff))) 

(pc/defnk my-weight [increments n]
  "increment / count-of-days"
  (weight-with-count-days increments n)) 

(pc/defnk my-weight2 [increments sum n]
  "increment / (count-of-days * avg)"
  (float (apply + (map #(/ (* %1 n) sum %2) increments (range n 1 -1))))) 
 
(pc/defnk my-weight3 [increments sum n]
  "my-weight2 with src"
  (apply + (map #(/ (Math/sqrt (/ (* %1 n) sum)) %2) increments (range n 1 -1)))) 

(def stats-graph {
;; OUT DATA
   :name   (pc/fnk [full_name]  (last (string/split full_name #"/")))
   :url    (pc/fnk [full_name]  (str github-http-prefix full_name))
   :incrs  increasing-increments
;; STATISTICA
   :n      (pc/fnk [increments] (count increments))
   :incs   (pc/fnk [increments] increments)
   :sum    (pc/fnk [increments] (apply + increments))
;; WEIGHT's
   :diff   diff-increments
   :w      my-weight
   :diffw  diff-weight
   :cplx   (pc/fnk [w diffw] (+ w (* diffw 2)))
})

(defn sum2 [mul-fn increments probs]
  (apply + (map #(* (mul-fn %1) %2) increments probs)))

(def debug-stats-graph {
;; OUT DATA
   :name       (pc/fnk [full_name]  (last (string/split full_name #"/")))
   :url        (pc/fnk [full_name]  (str github-http-prefix full_name))
   :incrs      increasing-increments
   :dats       (pc/fnk [dates]      (map c/to-string dates))
;; WEIGHT's
   :diff       diff-increments
   :w          my-weight
   :w2         my-weight2
   :w3         my-weight3
   :diffw      diff-weight
   :cplx       (pc/fnk [w diffw]    (+ w (* diffw 2)))
;; STATISTICA
   :n          (pc/fnk [increments] (count increments))
   :incs       (pc/fnk [increments] increments)
   :sum        (pc/fnk [increments] (apply + increments))
   :freq       (pc/fnk [increments] (frequencies increments))
   :moda       (pc/fnk [freq]       (first (sort-by val > freq)))
   :freq-moda  (pc/fnk [moda]       (sort-by val > (frequencies moda)))
   :min       (pc/fnk [increments]            (reduce min increments))
   :max       (pc/fnk [increments]            (reduce max increments))
;; PROBABILITY
   :sincs      (pc/fnk [increments]            (map inc increments))
   :probs      (pc/fnk [sincs n]                (map #(/ (val %) n) (frequencies sincs)))
   :m          (pc/fnk [increments probs]      (sum2 identity increments probs))
   :m2         (pc/fnk [increments probs m]    (- (sum2 #(* % %) increments probs) (* m m)))
   :m3         (pc/fnk [increments probs m2 m] (+ (* 2 m m m)
                                              (- (sum2 #(* % % %) increments probs))))
})

(def stats (graph/eager-compile stats-graph))
