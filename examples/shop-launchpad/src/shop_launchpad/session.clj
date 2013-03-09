(ns shop-launchpad.session)

(defn authentications
  [session]
  (-> session
      (get-in [:cemerick.friend/identity :authentications])
      vals))

(defn shop-auths
  [session]
  (map #(select-keys % #{:shop :access-token})
       (authentications session)))