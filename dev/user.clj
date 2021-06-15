(ns user
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.pprint :refer (pprint)]
            [clojure.repl :refer :all]
            [clojure.tools.namespace.repl :refer (refresh refresh-all)]
            [org.httpkit.server :refer [run-server]]
            [codes.stel.functional-news.handler :refer [app]]
            [taoensso.timbre :as timbre :refer [info]]))

(def port 1202)

(defonce server (atom nil))

(defn listen ([] (run-server (app) {:port port})) ([_] (listen)))

(defn stop-server
  []
  (when-not (nil? @server)
    ;; graceful shutdown: wait 100ms for existing requests to be finished
    ;; :timeout is optional, when no timeout, stop immediately
    (@server)
    (reset! server nil)))

(defn start-server
  []
  ;; The #' is useful when you want to hot-reload code
  ;; You may want to take a look: https://github.com/clojure/tools.namespace
  ;; and https://http-kit.github.io/migration.html#reload
  (info (str "Listening for requests on port " port))
  (reset! server (run-server #'app {:port port})))

(defn reset [] (stop-server) (refresh) (start-server))
