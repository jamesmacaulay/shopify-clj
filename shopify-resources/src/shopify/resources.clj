(ns shopify.resources
  "Functions for making requests against a shop's authenticated API."
  (:use [shopify.resources.names :only [member-keyword
                                        collection-keyword]])
  (:require [shopify.resources.client :as client]
            [shopify.resources.routes :as routes]))

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

(defmulti get-collection-request
  "Returns a partial request map to get a collection of the given resource type with the given params."
  (fn [resource-type params] resource-type))
(defmethod get-collection-request :default
  [resource-type params]
  (assoc (routes/endpoint resource-type :collection params)
    :method :get))

(defmulti get-member-request
  "Returns a partial request map to get a member of the given resource with the given attributes."
  (fn [resource-type attrs] resource-type))
(defn default-get-member-request
  [resource-type attrs]
  (let [params (attrs-to-params resource-type attrs)]
    (assoc (routes/endpoint resource-type :member params)
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
