(ns codes.stel.functional-news.util
  (:require [clojure.string :refer [capitalize]]
            [cemerick.url :as url]))

(def functional-jargon-adjectives
  ["dynamic" "currying" "categorical" "isomorphic" "homoiconic" "declaritive" "dataDriven" "endomorphic" "algebraic"
   "monadic" "stateful" "variadic" "effectful" "lazy" "reactive"])

(def animal-nouns
  ["alligator" "crocodile" "spider" "flamingo" "lion" "bobcat" "kaola" "tiger" "iguana" "racoon" "chipmunk" "sloth"
   "hedgehog" "bumblebee" "giraffe" "octopus" "tiger" "velicorapter" "mudpuppy" "antelope" "jaguar" "panther" "lemur"
   "badger" "tortoise" "toucan" "tapir" "springbok" "sturgeon" "beetle" "gecko" "llama" "macaw" "mongoose" "muskox"
   "numbat" "ocelot" "oyster" "opossum" "otter" "butterfly" "vaquita" "wombat" "zebu" "zebra" "pelican" "peacock"
   "quokka" "chameleon" "falcon" "oriole" "ibex" "jellyfish" "penguin"])

(defn generate-username
  []
  (str (capitalize (rand-nth functional-jargon-adjectives)) (capitalize (rand-nth animal-nouns))))

(comment
  (generate-username))

(defn parse-int [input] (Integer/parseInt input))

(defn validate-url [url] (try (url/url url) (catch Exception e (throw (ex-info "Invalid url" {:type :invalid-url} e)))))

