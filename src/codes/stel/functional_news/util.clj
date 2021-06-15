(ns codes.stel.functional-news.util
  (:require [clojure.string :refer [capitalize]]
            [cemerick.url :as url]))

(def both [])
(def adjectives
  (concat both ["dynamic" "categorical" "monadic" "pure" "variadic" "impure" "effectful" "lazy" "reactive"]))
(def nouns
  (concat both
          ["alligator" "crocodile" "spider" "flamingo" "lion" "bobcat" "kaola" "tiger" "gorilla" "iguana" "racoon"
           "chipmunk" "sloth" "hedgehog" "bumblebee" "giraffe" "octopus" "tiger" "velicorapter"]))

(defn generate-username [] (str (capitalize (rand-nth adjectives)) (capitalize (rand-nth nouns))))

(defn parse-int [input] (Integer/parseInt input))

(defn validate-url [url] ((url/url url)))

