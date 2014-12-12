(ns statistica.core
  (:require [statistica.generate :refer :all]
            [compojure.core :refer :all]
            [ring.util.response :refer :all]
            [clj-time.coerce :as c]
            [environ.core :refer [env]]
            [ring.middleware.defaults :refer [wrap-defaults site-defaults]]
            [compojure.route :as route]
            [clojure.java.io :as io])
  (:gen-class))

(defn parse-date [date]
  (c/from-string date))

(defn good-response [body]
  (-> (response body)
      (content-type "application/javascript")))

(def bad-request {:status 403
                  :headers {"Content-Type" "text/plain; charset=utf-8"}
                  :body "Bad request."})

(defroutes statistica-api
  (GET "/" {{from :from to :to} :params} 
    (let [from# (parse-date from)
          to# (parse-date to)]
      (cond
        (not (or from# to#)) (good-response (generate-json-stat (take 10 (classification :w))))
        (and from# (not to#)) (good-response (generate-json-stat (take 10 (classification :w from#))))
        (and from# to#) (good-response (generate-json-stat (take 10 (classification :w from# to#))))
        :else bad-request)))
  (GET "/test/:method" [method] 
    (cond
      (= method "html") {:status 200 
                         :headers {"Content-Type" "text/html; charset=utf-8"}
                         :body (io/file "resources/public/index.html")}
      :else bad-request))
  (GET "/resources/index.html" [] bad-request)
  (route/files "/js/")
  (route/not-found {:status 404
                    :headers {"Content-Type" "text/plain; charset=utf-8"}
                    :body "Page not found."}))

;; (def app (wrap-defaults statistica-api site-defaults))

(defn -main [& args]
  (wrap-defaults statistica-api site-defaults))
