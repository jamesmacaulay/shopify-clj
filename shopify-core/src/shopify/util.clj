(ns shopify.util)

(defn name-str
  "Returns the name of a keyword, or the string value of anything else."
  [x]
  (if (keyword? x)
    (name x)
    (str x)))

(defn partition-keys
  "Takes a map and a predicate and returns two maps split by which keys satisfy the predicate"
  [m pred]
  (reduce (fn [result [k v]]
            (let [i (if (pred k) 0 1)]
              (assoc-in result [i k] v)))
          [{} {}]
          m))
