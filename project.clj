(defproject statistica "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [ring/ring-core "1.3.1"]
                 [ring/ring-jetty-adapter "1.3.1"]
                 [compojure "1.3.1"]
                 [environ "1.0.0"]
                 [postgresql "9.3-1102.jdbc41"]
                 [prismatic/plumbing "0.2.1"]
                 [clj-time "0.8.0"]
                 [cheshire "5.3.1"]
                 [korma "0.4.0"]
                 [watchtower "0.1.1"]
                 [ring/ring-defaults "0.1.2"]]
  :ring {:handler statistica.core/app
         :port 8123}

  :main statistica.core
  :env {:port 8123}
)
