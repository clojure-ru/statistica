(ns statistica.counters-db
  (:require [korma.core :refer :all]
            [korma.db :refer :all]
            [environ.core :refer [env]]
            [clj-time.coerce :refer [to-sql-date from-sql-date]]))

(defdb db (postgres {:db "grabber"
                     :user (or (:db-user env) "")
                     :password (or (:db-pass env) "")
                     :debug? false}))

(defentity repositories)

(defentity counters)

(defn get-best-repositories [from to selection-limit]
  (select counters
      (fields :repositories.full_name :counter_id (raw "sum(increment) AS incr")
              (raw "array_agg(increment order by date) as increments")
              (raw "array_agg(date order by date) as dates"))
      (join repositories (= :repositories.id :repository_id))
      (where {:date [between [(to-sql-date from) (to-sql-date to)]]})
      (group :repositories.full_name :counter_id)
      (order (raw "incr") :DESC)
      (limit selection-limit)))
