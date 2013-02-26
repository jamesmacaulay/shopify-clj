(ns shopify.resources
  "Functions for making requests against a shop's authenticated API."
  (:use [shopify.resources.names :only [member-keyword
                                        collection-keyword]])
  (:require [shopify.resources.client :as client]
            [shopify.resources.routes :as routes]
            [flatland.useful.parallel :as parallel]
            [plumbing.core :as plumbing]))

(def request
  ^{:doc "Makes a request to the Shopify API."}
  client/request)

(defn attrs-to-params
  "Takes a resource type and a map of member attributes. Returns a transformed map with all non-path params hoisted into their own map keyed by the singular form of the type keyword. E.g. `{:id 99, :page {:title \"foo\"}}`."
  [resource-type attrs]
  (let [[scope-params attrs] (routes/extract-path-params resource-type attrs)
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
    (assoc (routes/endpoint resource-type :collection params)
      :method :post)))

(defmulti get-list-request
  "Returns a partial request map to get a collection of the given resource type with the given params."
  (fn [resource-type params] resource-type))
(defmethod get-list-request :default
  [resource-type params]
  (assoc (routes/endpoint resource-type :collection params)
    :method :get))

(defmulti get-one-request
  "Returns a partial request map to get a member of the given resource with the given attributes."
  (fn [resource-type attrs] resource-type))
(defn default-get-one-request
  [resource-type attrs]
  (let [params (attrs-to-params resource-type attrs)]
    (assoc (routes/endpoint resource-type :member params)
      :method :get)))
(defmethod get-one-request :assets
  [_ attrs]
  (let [pk-keys (if (nil? (:theme_id attrs))
                  #{:key}
                  #{:theme_id :key})]
    (default-get-one-request :assets (select-keys attrs pk-keys))))
(defmethod get-one-request :default
  [resource-type attrs]
  (default-get-one-request resource-type attrs))

(defmulti update-request
  "Returns a partial request map to update a member of the given resource with the given attributes."
  (fn [resource-type attrs] resource-type))
(defmethod update-request :default
  [resource-type attrs]
  (let [params (attrs-to-params resource-type attrs)]
    (assoc (routes/endpoint resource-type :member params)
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
    (assoc (routes/endpoint resource-type :member params)
      :method :delete)))

(defn get-count-request
  "Returns a partial request map to get the count of the given resource/params."
  [resource-type params]
  (get-list-request resource-type (assoc params :action :count)))

(defn extract-collection
  "Takes a response map and returns the collection of the given type, if it is present."
  [response resource-type]
  (get-in response [:body (collection-keyword resource-type)]))

(defn extract-member
  "Takes a response map and returns the member of the given type, if it is present."
  [response resource-type]
  (get-in response [:body (member-keyword resource-type)]))

(def ^:dynamic *base-request-opts* nil)

(defmacro with-opts
  [opts & exprs]
  `(binding [*base-request-opts* ~opts]
     ~@exprs))

(defn extract-kicker-args
  [args]
  (let [[params request-opts] (filter map? args)
        params (or params {})
        resource-type (if (keyword? (first args))
                        (first args)
                        (:shopify.resources/type params))]
    [resource-type params request-opts]))

(defn kicker-fn
  [request-fn extract-fn]
  (fn [& args]
    (let [[resource-type params request-opts] (extract-kicker-args args)
          response (request (merge *base-request-opts*
                                   (request-fn resource-type params)
                                   request-opts))]
      (extract-fn response resource-type))))

(def get-list
  ^{:doc "Takes a session (a partial request map with `:shop` and `:access-token`), a resource type keyword, and an optional map of params. Returns a sequence of fresh attribute maps from the server."}
  (kicker-fn get-list-request
             extract-collection))

(def get-one
  ^{:doc "Takes a session, a resource type, and an optional map of attributes (often with just an `:id`). Returns a fresh map of member attributes from the server."}
  (kicker-fn get-one-request
             extract-member))

(def get-count
  ^{:doc "Takes a session, a resource type keyword, and an optional map of params. Returns the count of the corresponding resource collection, as an integer."}
  (kicker-fn get-count-request
             (fn [response _] (get-in response [:body :count]))))

(defn get-shop
  "A convenience function to get the singleton shop resource."
  [& [request-opts]]
  (get-one :shop {} request-opts))

(def save!
  ^{:doc "Takes a session, resource type, and a map of attributes. Sends either a POST or a PUT to the server and returns an updated map of attributes for the updated resource."}
  (kicker-fn save-request
             extract-member))

(def delete!
  ^{:doc "Takes a session, resource type, and a map of attributes (often with just an `:id`). Sends a DELETE to the server and possibly returns an updated map of the deleted resource."}
  (kicker-fn delete-request
             extract-member))

(defn get-all
  "Eagerly gets _all_ the resources described by the arguments in parallel. Returns a sequence which is a lazy concatenation of all pages."
  [& args]
  (let [[resource-type params request-opts] (extract-kicker-args args)
        resource-count (get-count resource-type
                                  params
                                  request-opts)
        page-size 250
        page-count (-> (/ resource-count page-size) Math/ceil int)
        page-numbers (range 1 (+ 1 page-count))
        get-page (fn [n]
                   (get-list resource-type
                             (assoc params
                               :limit page-size
                               :page n)
                             request-opts))
        pages (parallel/pcollect get-page
                                 page-numbers)]
    (plumbing/aconcat pages)))

