# shopify-clj

A [Clojure][clojure] library for interacting with the [Shopify][shopify] platform.

* `shopify.resources`: functions for interacting with [a shop's resources][resource-docs].
* `shopify.friend`: provides a [friend][friend] workflow to authenticate [ring][ring] apps with Shopify shops using [OAuth2][auth-docs].

There is [documentation][codox-docs] and [annotated source][marginalia-docs].

[clojure]: http://clojure.org
[shopify]: http://www.shopify.com/
[resource-docs]: http://docs.shopify.com/api
[ring]: https://github.com/ring-clojure/ring
[friend]: https://github.com/cemerick/friend
[auth-docs]: http://docs.shopify.com/api/tutorials/oauth
[codox-docs]: http://jamesmacaulay.github.com/shopify-clj/docs/codox/index.html
[marginalia-docs]: http://jamesmacaulay.github.com/shopify-clj/docs/marginalia/uberdoc.html

## Installation

Shopify artifacts are [released to Clojars][clojars-shopify].

[Leiningen][leiningen] is the way to go for managing dependencies in Clojure. Add the following dependency to your `project.clj`:

```
:dependencies [[shopify "0.1.0"]]
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

Fire up a REPL and define an auth map for a private app as shown above. Now bring in the `shopify.resources` namespace:

```clojure
(require '[shopify.resources :as shop])
```

And try out a few simple requests:

```clojure
(shop/get-shop auth)
; {:country "CA", :longitude "-79.385324", ...}

(shop/get-list :pages {:limit 2 :fields "id,title"} auth)
; [{:shopify.resources/type :page, :id 9855484, :title "About Us"}
;  {:shopify.resources/type :page, :id 9855482, :title "Welcome"}]

(shop/get-one :page {:id 9855484} auth)
; {:published-at "2013-03-10T23:49:41-04:00", :handle "about-us", ...}

(shop/get-count :pages {} auth)
; 2
```

The first map argument is always assumed to be the params or attributes of the request, which is why an empty map needed to be passed before `auth` in the `get-count` request (`get-shop` never takes any params).

If you don't want to bother passing in the same auth map with every request, you can wrap your requests with the `with-opts` macro:

```clojure
(shop/with-opts auth
  {:page-count (shop/get-count :pages)
   :first-title (-> (shop/get-list :pages {:limit 1}) first :title)})
; {:page-count 2, :first-title "About Us"}
```

You can create and update with `save!`:

```clojure
(shop/save! :page {:title "New page!" :body_html "<p>Hello!</p>"} auth)
; {:published-at "2013-03-11T00:11:00-04:00", :handle "new-page", :id 9855576, ...}

(shop/with-opts auth
  (-> (shop/get-one :page {:id 9855576})
      (update-in [:title] clojure.string/upper-case)
      shop/save!
      (select-keys [:id :title])))
; {:title "NEW PAGE!", :id 9855576}
```

The call to `save!` in the second example just had a single altered attribute map as its only argument (via the [thread-first macro][thread-first]). Instead of figuring out the resource type from the usual first keyword argument, it extracted the type from a `:shopify.resources/type` key which is embedded into all attribute maps returned by the library (and which is stripped from request params before they get serialized).

Lastly, you can delete stuff:

```clojure
(shop/delete! :page {:id 9855576} auth)
; nil
```

[thread-first]: http://clojuredocs.org/clojure_core/clojure.core/-%3E

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
