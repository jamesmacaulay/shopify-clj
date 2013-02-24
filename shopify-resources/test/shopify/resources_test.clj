(ns shopify.resources-test
  (:use clojure.test
        [shopify.resources :as shop]))

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

(deftest get-list-request-test
  (testing "(get-list-request resource-type params) returns a partial request map"
    (is (= {:method :get
            :uri "/admin/pages"
            :params {:since_id 99}}
           (get-list-request :pages
                                   {:since_id 99}))))
  (testing "get-list-request works with metafields"
    (is (= {:method :get
            :uri "/admin/pages/101/metafields"
            :params {:since_id 99}}
           (get-list-request :metafields
                                   {:resource :pages
                                    :resource_id 101
                                    :since_id 99})))))

(deftest get-one-request-test
  (testing "(get-one-request resource-type attrs) returns a partial request map"
    (is (= {:method :get
            :uri "/admin/pages/99"}
           (get-one-request :pages {:id 99}))))
  (testing "get-one-request works with theme assets"
    (is (= {:method :get
            :uri "/admin/themes/99/assets"
            :params {:asset {:key "snippets/foo.liquid"}}}
           (get-one-request :assets {:theme_id 99
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
