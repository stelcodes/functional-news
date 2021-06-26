(ns codes.stel.functional-news.config
  (:require [cprop.core :refer [load-config]]))

(def config
  (load-config :resource "config/config.edn"))
