(ns shopify.resources.routes-test
  (:use clojure.test
        [shopify.resources.routes :as routes]))


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
