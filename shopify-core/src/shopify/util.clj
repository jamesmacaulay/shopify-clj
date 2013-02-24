(ns shopify.util
  (:require [clojure.string :as str]))

(defn as-namespaced-str
  "Returns the name of a keyword, or the string value of anything else."
  [x]
  (if (keyword? x)
    (subs (str x) 1)
    (str x)))

(defn dashes->underscores
  [string]
  (str/replace (as-namespaced-str string) "-" "_"))

(defn underscores->dashes
  [string]
  (str/replace (as-namespaced-str string) "_" "-"))

(defn partition-keys
  "Takes a map and a predicate and returns two maps split by which keys satisfy the predicate"
  [m pred]
  (reduce (fn [result [k v]]
            (let [i (if (pred k) 0 1)]
              (assoc-in result [i k] v)))
          [{} {}]
          m))
