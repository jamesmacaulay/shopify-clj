# shopify-clj

A [Clojure][clojure] library for interacting with the [Shopify][shopify] platform.

* `shopify.resources`: functions for interacting with [a shop's resources][resource-docs].
* `shopify.friend`: provides a [friend][friend] workflow to [authenticate with Shopify shops][auth-docs] using OAuth2.

[clojure]: http://clojure.org
[shopify]: http://www.shopify.com/
[friend]: https://github.com/cemerick/friend
[auth-docs]: http://docs.shopify.com/api/tutorials/oauth
[resource-docs]: http://docs.shopify.com/api

## Installation

Shopify artifacts are [released to Clojars][clojars-shopify].

[Leiningen][leiningen] is the way to go for managing dependencies in Clojure. Add the following to your project's `:dependencies`:

```
[shopify "0.1.0-SNAPSHOT"]
```

[clojars-shopify]: https://clojars.org/shopify/shopify
[leiningen]: https://github.com/technomancy/leiningen

## Usage

First, you'll need to [get set up with the API][api-getting-started].

The easiest way of accessing your dev shop is by [creating a private app][private-apps], but this is only really useful for experimentation. To use OAuth2, you'll have to [create an OAuth2 app through your Partner account][oauth2].

[api-getting-started]: http://docs.shopify.com/api/the-basics/getting-started
[private-apps]: http://docs.shopify.com/api/tutorials/creating-a-private-app
[oauth2]: http://docs.shopify.com/api/tutorials/oauth

### Private App Authentication

```clojure
(def auth {:shop "my-dev-shop.myshopify.com"
           :api-key "6dd96e9b19a0a7b3792f354eaf2a982b"
           :password "195b5baab7ce4581a861e925e930301d"})
```

### OAuth2

If you're building a Shopify web app, you'll need to use OAuth2. The `shopify.friend` namespace provides a [friend][friend] workflow for this purpose.

First, you'll need to define a map of key information about your app:

```clojure
(def my-shopify-app
  {:url "http://localhost:3000"
   :key "70bc2f19efa5129f202e661ac6fd38f3"
   :secret "8eb54f11bedfad9c3e2287479f2d525c"
   :scope [:read_products :read_orders]})
```

### shopify.resources

```clojure
(require '[shopify.resources :as shop])

(shop/get-list :products {:limit 2 :fields "id,title"} auth)

(shop/with-opts auth
  (let [shop (future (shop/get-shop))
        latest-order (future (shop/get-))]))
```

## License

Copyright © 2012-2013 James MacAulay & Shopify

Distributed under the Eclipse Public License, the same as Clojure.
