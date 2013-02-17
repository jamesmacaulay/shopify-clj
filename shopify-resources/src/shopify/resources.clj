(ns shopify.resources
  (:refer-clojure :exclude [comment])
  (:use [shopify.util :only [name-str partition-keys]])
  (:require [clojure.string :as str]
            [clojure.set :as set]
            [shopify.resources.client-middleware :as middleware]
            clj-http.util))

(def request
  ^{:doc "Makes a request to the Shopify API."}
  middleware/request)

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
                             (clj-http.util/url-encode (name-str v))))
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
  (partition-keys (prepare-path-params resource-type member-attrs)
                  (path-param-keys-for-resource resource-type)))

(defn attrs-to-params
  "Takes a resource type and a map of member attributes. Returns a transformed map with all non-path params hoisted into their own map keyed by the singular form of the type keyword. E.g. `{:id 99, :page {:title \"foo\"}}`."
  [resource-type attrs]
  (let [[scope-params attrs] (extract-path-params resource-type attrs)
        root-key (member-keyword resource-type)]
    (if (empty? attrs)
      scope-params
      (assoc scope-params root-key attrs))))

(defmulti create-request
  "Takes a keyword `resource-type` and a map of `attrs`, and returns a partial request map for creating a resource member."
  (fn [resource-type attrs] resource-type))
(declare update-request)
(defmethod create-request :assets
  [_ attrs]
  (update-request :assets attrs))
(defmethod create-request :default
  [resource-type attrs]
  (let [params (attrs-to-params resource-type attrs)]
    (assoc (endpoint resource-type :collection params)
      :method :post)))

(defmulti get-collection-request
  "Returns a partial request map to get a collection of the given resource type with the given params."
  (fn [resource-type params] resource-type))
(defmethod get-collection-request :default
  [resource-type params]
  (assoc (endpoint resource-type :collection params)
    :method :get))

(defmulti get-member-request
  "Returns a partial request map to get a member of the given resource with the given attributes."
  (fn [resource-type attrs] resource-type))
(defn default-get-member-request
  [resource-type attrs]
  (let [params (attrs-to-params resource-type attrs)]
    (assoc (endpoint resource-type :member params)
      :method :get)))
(defmethod get-member-request :assets
  [_ attrs]
  (let [pk-keys (if (nil? (:theme_id attrs))
                  #{:key}
                  #{:theme_id :key})]
    (default-get-member-request :assets (select-keys attrs pk-keys))))
(defmethod get-member-request :default
  [resource-type attrs]
  (default-get-member-request resource-type attrs))

(defmulti update-request
  "Returns a partial request map to update a member of the given resource with the given attributes."
  (fn [resource-type attrs] resource-type))
(defmethod update-request :default
  [resource-type attrs]
  (let [params (attrs-to-params resource-type attrs)]
    (assoc (endpoint resource-type :member params)
      :method :put)))

(defmulti persisted?
  "Returns true if the given attributes appear to refer to a member which already exists on the server."
  (fn [resource-type attrs] resource-type))
(defmethod persisted? :assets
  [resource-type attrs]
  (contains? attrs :key))
(defmethod persisted? :default
  [resource-type attrs]
  (contains? attrs :id))

(def new?
  ^{:doc "Returns false if the given attributes appear to refer to a member which already exists on the server."}
  (complement persisted?))

(defmulti save-request
  "Delegates to `create-request` if the attributes are new, or `update-request` if they're persisted."
  (fn [resource-type attrs] resource-type))
(defmethod save-request :default
  [resource-type attrs]
  (if (persisted? resource-type attrs)
    (update-request resource-type attrs)
    (create-request resource-type attrs)))

(defmulti delete-request
  "Returns a partial request map to delete a resource member."
  (fn [resource-type attrs] resource-type))
(defmethod delete-request :default
  [resource-type attrs]
  (let [params (attrs-to-params resource-type attrs)]
    (assoc (endpoint resource-type :member params)
      :method :delete)))

(defn get-count-request
  "Returns a partial request map to get the count of the given resource/params."
  [resource-type params]
  (get-collection-request resource-type (assoc params :action :count)))

(defn extract-collection
  "Takes a response map and returns the collection of the given type, if it is present."
  [response resource-type]
  (get-in response [:body (collection-keyword resource-type)]))

(defn extract-member
  "Takes a response map and returns the member of the given type, if it is present."
  [response resource-type]
  (get-in response [:body (member-keyword resource-type)]))

(defn get-collection
  "Takes a session (a partial request map with `:shop` and `:access-token`), a resource type keyword, and an optional map of params. Returns a sequence of fresh attribute maps from the server."
  [session resource-type & [params]]
  (-> (get-collection-request resource-type (or params {}))
      (merge session)
      request
      (extract-collection resource-type)))

(defn get-member
  "Takes a session, a resource type, and an optional map of attributes (often with just an `:id`). Returns a fresh map of member attributes from the server."
  [session resource-type attrs]
  (-> (get-member-request resource-type attrs)
      (merge session)
      request
      (extract-member resource-type)))

(defn get-count
  "Takes a session, a resource type keyword, and an optional map of params. Returns the count of the corresponding resource collection, as an integer."
  [session resource-type & [params]]
  (-> (get-count-request resource-type (or params {}))
      (merge session)
      request
      (extract-member :count)))

(defn save!
  "Takes a session, resource type, and a map of attributes. Sends either a POST or a PUT to the server and returns an updated map of attributes for the updated resource."
  [session resource-type attrs]
  (-> (save-request resource-type attrs)
      (merge session)
      request
      (extract-member resource-type)))

(defn delete!
  "Takes a session, resource type, and a map of attributes (often with just an `:id`). Sends a DELETE to the server and possibly returns an updated map of the deleted resource."
  [session resource-type attrs]
  (-> (delete-request resource-type attrs)
      (merge session)
      request
      (extract-member resource-type)))
