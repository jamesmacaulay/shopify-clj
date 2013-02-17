(ns shopify.resources-test
  (:use clojure.test
        [shopify.resources :as shop :exclude [comment]]))

(deftest member-name-test
  (testing "(member-name resource) chops off a trailing \"s\""
    (is (= "product" (member-name :products))))
  (testing "(member-name resource) leaves singulars alone"
    (is (= "product" (member-name :product))))
  (testing "(member-name resource) does :countries"
    (is (= "country" (member-name :countries)))
    (is (= "country" (member-name :country)))))

(deftest collection-name-test
  (testing "(collection-name resource) adds a trailing \"s\""
    (is (= "products" (collection-name :products))))
  (testing "(collection-name resource) leaves plurals alone"
    (is (= "products" (collection-name :product))))
  (testing "(collection-name resource) does :countries"
    (is (= "countries" (collection-name :country)))
    (is (= "countries" (collection-name :countries)))))

(deftest path-params-test
  (testing "path-params takes a route template string and returns a set of keywords corresponding to its colon-prefixed dynamic segments"
    (is (= #{:owner_resource :owner_id}
           (path-params "/admin/:owner_resource/:owner_id/metafields")))))


(deftest pick-route-test
  (testing "(pick-route routes params) returns the first satisfyable route template in routes, given the available keys in params"
    (is (= "/admin/blogs/:blog_id/articles/:id"
           (pick-route ["/admin/blogs/:blog_id/articles/:id"
                        "/admin/articles/:id"
                        "/admin/articles"]
                       {:blog_id 99
                        :id 101
                        :foo "bar"})))
    (is (= "/admin/articles/:id"
           (pick-route ["/admin/blogs/:blog_id/articles/:id"
                        "/admin/articles/:id"
                        "/admin/articles"]
                       {:id 99
                        :foo "bar"})))
    (is (= "/admin/articles"
           (pick-route ["/admin/articles"
                        "/admin/blogs/:blog_id/articles/:id"
                        "/admin/articles/:id"]
                       {:foo "bar"})))))


(deftest render-route-test
  (testing "render-route takes a route template and some params and returns a partial request map"
    (is (= {:uri "/admin/blogs/1/articles/2"}
           (render-route "/admin/blogs/:blog_id/articles/:id"
                       {:blog_id 1
                        :id 2})))
    (is (= {:uri "/admin/blogs/1/articles/2"
            :params {:article {:body_html "<p>foo</p>"}}}
           (render-route "/admin/blogs/:blog_id/articles/:id"
                       {:blog_id 1
                        :id 2
                        :article {:body_html "<p>foo</p>"}})))))


(deftest routes-for-resource-test
  (testing "(routes-for-resource :future_resources :collection) returns the default shallow collection routes"
    (is (= ["/admin/future_resources/:action"
            "/admin/future_resources"]
           (routes-for-resource :future_resources :collection))))
  (testing "(routes-for-resource :future_resources :member) returns the default shallow member routes"
    (is (= ["/admin/future_resources/:id/:action"
            "/admin/future_resources/:id"]
           (routes-for-resource :future_resources :member))))
  (testing "(routes-for-resource :metafields :collection) returns collection routes for metafields"
    (is (= ["/admin/:resource/:resource_id/metafields/:action"
            "/admin/:resource/:resource_id/metafields"
            "/admin/metafields/:action"
            "/admin/metafields"]
           (routes-for-resource :metafields :collection))))
  (testing "(routes-for-resource :metafields :member) returns member routes for metafields"
    (is (= ["/admin/:resource/:resource_id/metafields/:id/:action"
            "/admin/:resource/:resource_id/metafields/:id"
            "/admin/metafields/:id/:action"
            "/admin/metafields/:id"]
           (routes-for-resource :metafields :member))))
  (testing "(routes-for-resource :shop :collection) returns the shop routes"
    (is (= ["/admin/shop/:action"
            "/admin/shop"]
           (routes-for-resource :shop :collection))))
  (testing "(routes-for-resource :shop :member) returns the shop routes"
    (is (= ["/admin/shop/:action"
            "/admin/shop"]
           (routes-for-resource :shop :member)))))

(deftest endpoint-test
  (testing "(endpoint :orders :collection {:since_id 99}) returns the endpoint with those params"
    (is (= {:uri "/admin/orders"
            :params {:since_id 99}}
           (endpoint :orders :collection {:since_id 99}))))
  (testing "(endpoint :orders :member {:id 101}) returns the endpoint for the order with that id"
    (is (= {:uri "/admin/orders/101"}
           (endpoint :orders :member {:id 101})))))

(deftest path-param-keys-for-resource-test
  (testing "(path-param-keys-for-resource resource-type) returns the set of all keys which are used as dynamic segments in the resource's routes"
    (is (= #{:action :id}
           (path-param-keys-for-resource :pages)))
    (is (= #{:action :blog_id :id}
           (path-param-keys-for-resource :articles)))
    (is (= #{:action :resource :resource_id :id}
           (path-param-keys-for-resource :metafields)))))

(deftest extract-path-params-test
  (testing "(extract-path-params resource-type member-attrs) returns a map of scope params and a map of the remaining attributes"
    (is (= [{:blog_id 99}
            {:title "foo" :body_html "bar"}]
           (extract-path-params :articles {:blog_id 99
                                            :title "foo"
                                            :body_html "bar"}))))
  (testing "extract-path-params knows how to map special attributes"
    (is (= [{:id 101 :resource "products" :resource_id 99}
            {:value "foo"}]
           (extract-path-params :metafields {:owner_resource "product"
                                              :owner_id 99
                                              :id 101
                                              :value "foo"})))))


(deftest create-request-test
  (testing "(create-request resource-type attrs) returns a partial request map"
    (is (= {:method :post
            :uri "/admin/pages"
            :params {:page {:title "About us"
                            :body_html "We make nice things"}}}
           (create-request :pages
                           {:title "About us"
                            :body_html "We make nice things"}))))
  (testing "create-request uses nested routes"
    (is (= {:method :post
            :uri "/admin/blogs/99/articles"
            :params {:article {:title "Black Fridayyyyy"
                               :body_html "It's a-comin'."}}}
           (create-request :articles
                           {:blog_id 99
                            :title "Black Fridayyyyy"
                            :body_html "It's a-comin'."}))))
  (testing "create-request works with metafields"
    (is (= {:method :post
            :uri "/admin/pages/99/metafields"
            :params {:metafield {:namespace "myapp"
                                 :key "foo"
                                 :value_type "string"
                                 :value "bar"}}}
           (create-request :metafields
                           {:owner_resource "page"
                            :owner_id 99
                            :namespace "myapp"
                            :key "foo"
                            :value_type "string"
                            :value "bar"}))))
  (testing "create-request does an update-request for theme assets"
    (is (= {:method :put
            :uri "/admin/themes/99/assets"
            :params {:asset {:key "snippets/foo.liquid"
                             :value "<p>{{settings.foo}}</p>"}}}
           (create-request :assets
                           {:theme_id 99
                            :key "snippets/foo.liquid"
                            :value "<p>{{settings.foo}}</p>"})))))

(deftest get-collection-request-test
  (testing "(get-collection-request resource-type params) returns a partial request map"
    (is (= {:method :get
            :uri "/admin/pages"
            :params {:since_id 99}}
           (get-collection-request :pages
                                   {:since_id 99}))))
  (testing "get-collection-request works with metafields"
    (is (= {:method :get
            :uri "/admin/pages/101/metafields"
            :params {:since_id 99}}
           (get-collection-request :metafields
                                   {:resource :pages
                                    :resource_id 101
                                    :since_id 99})))))

(deftest get-member-request-test
  (testing "(get-member-request resource-type attrs) returns a partial request map"
    (is (= {:method :get
            :uri "/admin/pages/99"}
           (get-member-request :pages {:id 99}))))
  (testing "get-member-request works with theme assets"
    (is (= {:method :get
            :uri "/admin/themes/99/assets"
            :params {:asset {:key "snippets/foo.liquid"}}}
           (get-member-request :assets {:theme_id 99
                                        :key "snippets/foo.liquid"})))))

(deftest update-request-test
  (testing "(update-request resource-type attrs) returns a partial request map"
    (is (= {:method :put
            :uri "/admin/pages/99"
            :params {:page {:title "a new title"}}}
           (update-request :pages
                           {:id 99
                            :title "a new title"}))))
  (testing "update-request works with theme assets"
    (is (= {:method :put
            :uri "/admin/themes/99/assets"
            :params {:asset {:key "snippets/foo.liquid"
                             :value "<p>{{settings.foo}}</p>"}}}
           (update-request :assets
                           {:theme_id 99
                            :key "snippets/foo.liquid"
                            :value "<p>{{settings.foo}}</p>"}))))
  (testing "update-request works with metafields"
    (is (= {:method :put
            :uri "/admin/pages/99/metafields/101"
            :params {:metafield {:value 42}}}
           (update-request :metafields
                           {:owner_resource "page"
                            :owner_id 99
                            :id 101
                            :value 42}))))
  (testing "update-request works with shallow-routed metafields"
    (is (= {:method :put
            :uri "/admin/metafields/101"
            :params {:metafield {:value 42}}}
           (update-request :metafields
                           {:id 101
                            :value 42})))))
            
(deftest delete-request-test
  (testing "(delete-request resource-type attrs) returns a partial request map"
    (is (= {:method :delete
            :uri "/admin/pages/99"}
           (delete-request :pages
                           {:id 99}))))
  (testing "delete-request works with metafields"
    (is (= {:method :delete
            :uri "/admin/pages/99/metafields/101"}
           (delete-request :metafields
                           {:owner_resource "page"
                            :owner_id 99
                            :id 101}))))
  (testing "delete-request works with metafield and shallow routes"
    (is (= {:method :delete
            :uri "/admin/metafields/101"}
           (delete-request :metafields
                           {:id 101}))))
  (testing "delete-request works with assets"
    (is (= {:method :delete
            :uri "/admin/themes/99/assets"
            :params {:asset {:key "snippets/foo.liquid"}}}
           (delete-request :assets
                           {:theme_id 99
                            :key "snippets/foo.liquid"})))))

(deftest persisted?-test
  (testing "(persisted? resource-type attrs) returns true if it has its primary key"
    (is (= true
           (persisted? :pages {:id 99})))
    (is (= false
           (persisted? :pages {:title "foo"}))))
  (testing "persisted? works with assets"
    (is (= true
           (persisted? :assets {:theme_id 99 :key "assets/foo.txt"})))
    (is (= false
           (persisted? :pages {:value "foo"})))))
            
(deftest save-request-test
  (testing "(save-request resource-type attrs) uses update-request if attrs look persisted (primary key is present), or uses create-request otherwise"
    (is (= {:method :put
            :uri "/admin/pages/99"
            :params {:page {:title "a new title"}}}
           (save-request :pages
                         {:id 99
                          :title "a new title"})))
    (is (= {:method :post
            :uri "/admin/pages"
            :params {:page {:title "About us"
                            :body_html "We make nice things"}}}
           (save-request :pages
                         {:title "About us"
                          :body_html "We make nice things"})))))

(deftest get-count-request-test
  (testing "(get-count-request resource-type params) returns a partial request map"
    (is (= {:method :get
            :uri "/admin/pages/count"
            :params {:since_id 99}}
           (get-count-request :pages {:since_id 99})))))
