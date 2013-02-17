(ns shopify.resources.routes
  "Functions to determine the endpoint of a resource. You probably don't need to use these—use the `shopify.resources` namespace to make requests."
  (:use [shopify.resources.names :only [collection-name]])
  (:require [clojure.string :as str]
            [clojure.set :as set]
            [shopify.util :as util]
            clj-http.util))

(def path-params
  ^{:doc "Takes a route template like `\"/admin/blogs/:blog_id/articles\"` and returns a set of dynamic segments as keywords like `#{:blog_id}`."}
  (memoize (fn path-params [route]
             (->> (re-seq #"(?<=:)\w+" route)
                  (map keyword)
                  set))))

(defn pick-route
  "Pick the first satisfyable route template in a collection, given a collection of available keys."
  [routes params]
  (let [available-keys (->> params
                            (filter #(not (nil? (val %))))
                            (map first)
                            set)]
    (some #(let [unfilled-segments (set/difference (path-params %)
                                                   (set available-keys))]
             (when (empty? unfilled-segments) %))
          routes)))


(defn render-route
  "Given a route template and a map of params, return a partial request map of `:uri` and `:params`."
  [route params]
  (let [dynamic-segments (path-params route)
        reducer (fn [req [k v :as param]]
                  (if (dynamic-segments k)
                    (assoc req
                      :uri (str/replace-first
                             (:uri req)
                             (re-pattern (str k "(?=$|\\/)"))
                             (clj-http.util/url-encode (util/name-str v))))
                    (assoc-in req [:params k] v)))]
    (reduce reducer
            {:uri route}
            params)))


(defn- collection-route
  ([resource]
    (collection-route resource "/admin"))
  ([resource prefix]
    (str prefix \/ (collection-name resource))))

(defn- collection-action-route
  ([resource]
    (collection-action-route resource "/admin"))
  ([resource prefix]
    (str (collection-route resource prefix) "/:action")))

(defn- member-route
  ([resource]
    (member-route resource "/admin"))
  ([resource prefix]
    (str (collection-route resource prefix) "/:id")))

(defn- member-action-route
  ([resource]
    (member-action-route resource "/admin"))
  ([resource prefix]
    (str (collection-route resource prefix) "/:id/:action")))

(defn- prefixed-collection-routes
  [resource prefix]
  (list (collection-action-route resource prefix)
        (collection-route resource prefix)))

(defn- prefixed-member-routes
  [resource prefix]
  (list (member-action-route resource prefix)
        (member-route resource prefix)))

(defn- shallow-collection-routes
  [resource]
  (prefixed-collection-routes resource "/admin"))

(defn- shallow-member-routes
  [resource]
  (prefixed-member-routes resource "/admin"))

(defn- prefixed-and-shallow-collection-routes
  [resource prefix]
  (concat (prefixed-collection-routes resource prefix)
          (shallow-collection-routes resource)))

(defn- prefixed-and-shallow-member-routes
  [resource prefix]
  (concat (prefixed-member-routes resource prefix)
          (shallow-member-routes resource)))

(defn- prefixed-and-shallow-routes
  [resource prefix]
  {:collection (prefixed-and-shallow-collection-routes resource prefix)
   :member (prefixed-and-shallow-member-routes resource prefix)})

(def resource-types
  {:articles
   {:routes
    {:collection (prefixed-collection-routes
                   :articles "/admin/blogs/:blog_id")}}
   :assets
   {:routes
    {:collection (prefixed-and-shallow-collection-routes
                   :assets "/admin/themes/:theme_id")
     :member (prefixed-and-shallow-collection-routes
               :assets "/admin/themes/:theme_id")}}
   :customers
   {:routes
    {:collection (prefixed-and-shallow-collection-routes
                   :customers "/admin/customer_groups/:customer_group_id")}}
   :events
   {:routes (prefixed-and-shallow-routes
              :events "/admin/:resource/:resource_id")}
   :fulfillments
   {:routes
    {:collection (prefixed-collection-routes
                   :fulfillments "/admin/orders/:order_id")}}
   :metafields
   {:routes (prefixed-and-shallow-routes
              :metafields "/admin/:resource/:resource_id")}
   :product_images
   {:routes
    {:collection (prefixed-collection-routes
                   :product_images "/admin/products/:product_id")}}
   :product_variants
   {:routes
    {:collection (prefixed-collection-routes
                   :product_variants "/admin/products/:product_id")}}
   :provinces
   {:routes
    {:collection (prefixed-collection-routes
                   :provinces "/admin/countries/:country_id")}}
   :shop
   {:routes
    {:collection (shallow-collection-routes :shop)
     :member (shallow-collection-routes :shop)}}
   :transactions
   {:routes
    {:collection (prefixed-collection-routes
                   :transactions "/admin/orders/:order_id")}}})

(defn routes-for-resource
  "Takes a resource type keword (e.g. `:products`) and cardinality (`:member` or `:collection`) and returns a sequence of routes."
  [resource-type cardinality]
  (or (get-in resource-types [resource-type :routes cardinality])
      (if (= :member cardinality)
        (shallow-member-routes resource-type)
        (shallow-collection-routes resource-type))))

(defn endpoint
  "Takes a resource type keword (e.g. `:products`), a cardinality (`:member` or `:collection`), and a map of params. Returns a partial request map of `:uri` and `:params`."
  [resource-type cardinality params]
  (-> (routes-for-resource resource-type cardinality)
      (pick-route params)
      (render-route params)))

(defmulti prepare-path-params
  "Takes a resource type keyword and a map of member attributes, and returns a possibly altered map suitable for extracting path params."
  (fn [resource-type attrs] resource-type))
(defmethod prepare-path-params :default
  [_ attrs]
  attrs)
(defn transform-parent-resource-attrs
  [attrs]
  (let [renamed-attrs (set/rename-keys attrs {:owner_resource :resource
                                              :owner_id :resource_id})
        resource (:resource renamed-attrs)]
    (if (string? resource)
      (assoc renamed-attrs :resource (collection-name resource))
      renamed-attrs)))
(defmethod prepare-path-params :events
  [_ attrs] (transform-parent-resource-attrs attrs))
(defmethod prepare-path-params :metafields
  [_ attrs] (transform-parent-resource-attrs attrs))

(def path-param-keys-for-resource
  ^{:doc "Returns a set of all the "}
  (memoize (fn path-param-keys-for-resource
             [resource-type]
             (let [routes (mapcat (partial routes-for-resource resource-type)
                                  [:collection :member])]
               (into #{} (mapcat path-params routes))))))

(defn extract-path-params
  "Takes a resource type-keyword and a map of member attributes, and returns a map of path params and a map of the remaining attributes"
  [resource-type member-attrs]
  (util/partition-keys (prepare-path-params resource-type member-attrs)
                  (path-param-keys-for-resource resource-type)))