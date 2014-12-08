(ns statistica.core
  (:require [statistica.generate :refer :all]
            [compojure.core :refer :all]
            [clj-time.coerce :as c]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [compojure.route :as route]))

(defn parse-date [date]
  (c/from-string date))

(defn good-response [body]
  {:status 200
   :headers {"Content-Type" "application/javascript; charset=utf-8"}
   :body body})

(def bad-request {:status 403
                  :headers {"Content-Type" "text/plain; charset=utf-8"}
                  :body "Bad request."})

(defroutes statistica-api
  (GET "/" [] (good-response (generate-json-stat (take 10 (classification :w)))))
  (GET "/:from" [from] 
    (if-let [date (parse-date from)]
      (good-response (generate-json-stat (take 10 (classification :w date))))
      bad-request))
  (route/not-found {:status 404
                    :headers {"Content-Type" "text/plain; charset=utf-8"}
                    :body "Page not found."}))

(def app (wrap-defaults statistica-api site-defaults))
