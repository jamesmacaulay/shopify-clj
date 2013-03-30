(ns shopify.friend-test
  (:use clojure.test
        shopify.friend
        [ring.mock.request :only [request]]
        [ring.middleware.params :only [wrap-params]])
  (:require [shopify.resources.client]))

(def default-api-client
  {:url "http://shopify-test.heroku.com"
   :key "01abfc750a0c942167651c40d088531d"
   :secret "dc2e817cb95adce7164db4767a13a53f"
   :scope [:read_orders, :write_content]})

(defn mock-request
  [& args]
  ((wrap-params identity) (apply request args)))

(deftest user-auth-url-test
  (testing "(user-auth-url options) returns a user auth url"
    (is (= (str "https://xerxes.myshopify.com"
                "/admin/oauth/authorize"
                "?client_id=01abfc750a0c942167651c40d088531d"
                "&scope=read_orders,write_content"
                "&redirect_uri=http://shopify-test.heroku.com/landing-page")
           (user-auth-url default-api-client
                          "xerxes.myshopify.com"
                          "/landing-page"))))
  (testing "redirect param is optional"
    (is (= (str "https://xerxes.myshopify.com"
                "/admin/oauth/authorize"
                "?client_id=01abfc750a0c942167651c40d088531d"
                "&scope=read_orders,write_content")
           (user-auth-url default-api-client "xerxes.myshopify.com")))))

(deftest build-access-token-request-test
  (testing "(build-access-token-request options) generates a request to fetch a permanent access token")
    (let [shop "xerxes.myshopify.com"
          code "ffe51d3e7d8297237588704eeddc6ab2"]
      (is (= {:method :post
              :url "https://xerxes.myshopify.com/admin/oauth/access_token"
              :accept :json
              :as :json
              :form-params {:client_id "01abfc750a0c942167651c40d088531d"
                            :client_secret "dc2e817cb95adce7164db4767a13a53f"
                            :code "ffe51d3e7d8297237588704eeddc6ab2"}}
             (build-access-token-request default-api-client shop code)))))

(deftest callback-request-matcher-test
  (testing "(callback-request-matcher path request) returns true when request is to the given path, and includes shop and code params in the query"
    (let [match-path #(callback-request-matcher %1 (mock-request :get %2))]
      (is (= true
             (match-path "/callback" "/callback?shop=bar&code=baz")))
      (is (= true
             (match-path "/alt-callback" "/alt-callback?shop=bar&code=baz")))
      (is (= false
             (match-path "/callback" "/admin?shop=bar&code=baz")))
      (is (= false
             (match-path "/callback" "/callback?code=baz")))
      (is (= false
             (match-path "/callback" "/callback?shop=baz"))))))

(deftest login-request-matcher-test
  (testing "(login-request-matcher path request) returns true when request is to the given path, and includes shop param in the query"
    (let [match-path #(login-request-matcher %1 (mock-request :get %2))]
      (is (= true
             (match-path "/login" "/login?shop=bar")))
      (is (= true
             (match-path "/alt-login" "/alt-login?shop=bar")))
      (is (= false
             (match-path "/login" "/admin?shop=bar")))
      (is (= false
             (match-path "/login" "/login"))))))

(deftest handle-login-request-test
  (testing "(handle-login-request api-client request callback-path) returns a redirect response to the appropriate user-auth-url"
    (let [request-url "/login?shop=foo"
          response
          (handle-login-request default-api-client
                                (mock-request :get request-url)
                                "/callback")
          redirect-location
          (get-in response [:headers "Location"])]
      (is (= 302 (response :status)))
      (is (= (str "https://foo.myshopify.com"
                  "/admin/oauth/authorize"
                  "?client_id=01abfc750a0c942167651c40d088531d"
                  "&scope=read_orders,write_content"
                  "&redirect_uri=http://shopify-test.heroku.com/callback")
             redirect-location)))))

(deftest handle-callback-request-test
  (testing "(handle-callback-request api-client request) returns a friend auth map"
    (with-redefs [shopify.friend/fetch-access-token
                  (fn [api-client shop code]
                    (is (= default-api-client api-client))
                    (is (= "foo.myshopify.com" shop))
                    (is (= "1234" code))
                    "mock-access-token")]
      (let [request-url "/callback?shop=foo.myshopify.com&code=1234"
            response
            (handle-callback-request default-api-client
                                     (mock-request :get request-url))]
        (is (= {:identity "mock-access-token"
                :access-token "mock-access-token"
                :shop "foo.myshopify.com"}
               response))))))

(deftest normalize-shop-domain-test
  (testing "normalize-shop-domain doesn't touch .myshopify.com domains"
    (is (= "foo.myshopify.com"
           (normalize-shop-domain "foo.myshopify.com")))
  (testing "normalize-shop-domain appends .myshopify.com if it isn't there already"
    (is (= "foo.myshopify.com"
           (normalize-shop-domain "foo"))))
  (testing "normalize-shop-domain replaces .shopify.com with .myshopify.com"
    (is (= "foo.myshopify.com"
           (normalize-shop-domain "foo.shopify.com"))))))


(let [shopify-auth (with-meta {:identity "mock-access-token"
                               :access-token "mock-access-token"
                               :shop "foo.myshopify.com"}
                              {:type :cemerick.friend/auth
                               :cemerick.friend/workflow :shopify})
      other-auth (with-meta {:identity "james@shopify.com"}
                            {:type :cemerick.friend/auth
                             :cemerick.friend/workflow :interactive-form})]
  (deftest shopify-auth?-test
    (testing "shopify-auth? returns true for a shopify authentication"
      (is (= true
             (shopify-auth? shopify-auth))))
    (testing "shopify-auth? returns false for a non-shopify authentication"
      (is (= false
             (shopify-auth? other-auth)))))
  (deftest shopify-auths-test
    (testing "shopify-auths takes a ring request and only returns the auth maps from shopify.friend"
      (is (= [shopify-auth]
             (shopify-auths
               {:session
                {:cemerick.friend/identity
                 {:current "mock-access-token"
                  :authentications {"mock-access-token" shopify-auth
                                    "james@shopify.com" other-auth}}}})))))
  (deftest logged-in?-test
    (testing "logged-in? returns true if the given request has a shopify auth map"
      (is (= true
             (logged-in?
               {:session
                {:cemerick.friend/identity
                 {:current "mock-access-token"
                  :authentications {"mock-access-token" shopify-auth
                                    "james@shopify.com" other-auth}}}}))))
    (testing "logged-in? returns false if the given request has no shopify auth map"
      (is (= false
             (logged-in?
               {:session
                {:cemerick.friend/identity
                 {:current "james@shopify.com"
                  :authentications {"james@shopify.com" other-auth}}}})))))
  (deftest current-authentication-test
    (testing "current-authentication returns nil when there is only a non-shopify auth map present"
      (is (= nil
             (current-authentication
               {:session
                {:cemerick.friend/identity
                 {:current "james@shopify.com"
                  :authentications {"james@shopify.com" other-auth}}}}))))
    (testing "current-authentication returns the current authentication when it is a shopify auth"
      (is (= shopify-auth
             (current-authentication
               {:session
                {:cemerick.friend/identity
                 {:current "mock-access-token"
                  :authentications {"mock-access-token" shopify-auth
                                    "james@shopify.com" other-auth}}}}))))
    (testing "current-authentication returns the first shopify auth when a non-shopify auth is the current one"
      (is (= shopify-auth
             (current-authentication
               {:session
                {:cemerick.friend/identity
                 {:current "james@shopify.com"
                  :authentications {"mock-access-token" shopify-auth
                                    "james@shopify.com" other-auth}}}})))))
  (deftest wrap-current-authentication-for-shopify-resources-test
    (testing "binds shopify.resources.client/*base-request-opts* with current authentication"
      (let [wrapped-base-request-opts
            (wrap-current-authentication-for-shopify-resources
              (fn [req] shopify.resources.client/*base-request-opts*))]
        (is (= shopify-auth
               (wrapped-base-request-opts
                 {:session
                  {:cemerick.friend/identity
                   {:current "mock-access-token"
                    :authentications {"mock-access-token" shopify-auth
                                      "james@shopify.com" other-auth}}}})))))))
