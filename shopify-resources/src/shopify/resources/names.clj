(ns shopify.resources.names
  "A few functions to help with resource type names."
  (:require [clojure.string :as str]))

(defn collection-name
  "Converts resource keywords to their plural forms, unless it's a singleton resource (e.g. `:shop`)."
  [resource]
  (let [resource (name resource)]
    (case resource
      ("country" "countries") "countries"
      ("shop" "shops") "shop"
      (str/replace-first resource #"s?$" "s"))))

(def collection-keyword (comp keyword collection-name))

(defn member-name
  "Converts resource keywords to their singular forms."
  [resource]
  (let [resource (name resource)]
    (case resource
      ("country" "countries") "country"
      (str/replace-first resource #"s?$" ""))))

(def member-keyword (comp keyword member-name))

