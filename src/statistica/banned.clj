(ns statistica.banned
  (:require [watchtower.core :refer [watcher 
                                     rate
                                     file-filter
                                     on-change
                                     ignore-dotfiles
                                     extensions]])) 

(def banlist-path "resources/ban_list.clj")

(def banlist (atom (load-file banlist-path)))

(defn update-banlist [& args]
  (reset! banlist (load-file banlist-path)))

(defn get-banlist []
  @banlist)

(watcher [banlist-path]
  (rate 50) ;; poll every 50ms
  (file-filter ignore-dotfiles) ;; add a filter for the files we care about
  (file-filter (extensions :clj)) ;; filter by extensions
  (on-change update-banlist)) 
