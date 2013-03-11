# shopify-clj

A [Clojure][clojure] library for interacting with the [Shopify][shopify] platform.

* `shopify.resources`: functions for interacting with [a shop's resources][resource-docs].
* `shopify.friend`: provides a [friend][friend] workflow to authenticate [ring][ring] apps with Shopify shops using [OAuth2][auth-docs].

[clojure]: http://clojure.org
[shopify]: http://www.shopify.com/
[resource-docs]: http://docs.shopify.com/api
[ring]: https://github.com/ring-clojure/ring
[friend]: https://github.com/cemerick/friend
[auth-docs]: http://docs.shopify.com/api/tutorials/oauth

## Installation

Shopify artifacts are [released to Clojars][clojars-shopify].

[Leiningen][leiningen] is the way to go for managing dependencies in Clojure. Add the following dependency to your `project.clj`:

```
:dependencies [[shopify "0.1.0-SNAPSHOT"]]
```

[clojars-shopify]: https://clojars.org/shopify/shopify
[leiningen]: https://github.com/technomancy/leiningen

## Getting started

First, you'll need to [get some API credentials and a development shop][api-getting-started].

The easiest way of accessing an individual shop is by [creating a private app][private-apps], but this is only really useful for experimentation. To use OAuth2, you need to [create an app through your Partner account][api-getting-started].

[api-getting-started]: http://docs.shopify.com/api/the-basics/getting-started
[private-apps]: http://docs.shopify.com/api/tutorials/creating-a-private-app

### Private App Authentication

Private apps are the quickest way of getting started. Just [get some credentials][private-apps] and put them in a map:

```clojure
(def auth {:shop "my-dev-shop.myshopify.com"
           :api-key "6dd96e9b19a0a7b3792f354eaf2a982b"
           :password "195b5baab7ce4581a861e925e930301d"})
```

### Making requests

```clojure
(require '[shopify.resources :as shop])

(shop/get-list :products {:limit 2 :fields "id,title"} auth)

(shop/with-opts auth
  (let [shop (future (shop/get-shop))
        latest-order (future (shop/get-))]))
```

### OAuth2

If you're building a Shopify web app, you'll need to use OAuth2. The `shopify.friend` namespace provides a [friend][friend] workflow for this purpose.

First, you'll need a map representing your app:

```clojure
(def my-shopify-app
  {:url "http://localhost:3000"
   :key "70bc2f19efa5129f202e661ac6fd38f3"
   :secret "8eb54f11bedfad9c3e2287479f2d525c"
   :scope [:read_products :read_orders]})
```

This is used to configure the workflow provided by `shopify.friend`:

```clojure
(def shopify-auth
  (shopify.friend/workflow {:api-client my-shopify-app}))

(def app
  (-> my-handler
      some-other-middleware
      (cemerick.friend/authenticate
        {:allow-anon? true
         :workflows [shopify-auth]})))
```

Here's [a working example][example-server]. With the middleware in place, you can authenticate a shop by hitting `/auth/shopify?shop=some-shop.myshopify.com`. This is most often done [with a form][example-login-form].

You can get a list of the current Shopify authentications from the request like so:

```clojure
(shopify.friend/shopify-auths request)
```

This returns a sequence of auth maps which can be used as the basis of shop resource requests in the same way as the auth map for a private app.

[example-server]: https://github.com/jamesmacaulay/shopify-clj/blob/master/examples/shop-launchpad/src/shop_launchpad/server.clj
[example-login-form]: https://github.com/jamesmacaulay/shopify-clj/blob/71344d9c0816d9b70c18b85fbb5fe15dd1523a80/examples/shop-launchpad/src/shop_launchpad/templates/index.html#L16-L19

## License

Copyright © 2012-2013 James MacAulay & Shopify

Distributed under the Eclipse Public License, the same as Clojure.
