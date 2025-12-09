;; Yuno Gasai 2 (Clojure Edition) - Core
;; Copyright (C) 2025 blubskye
;; SPDX-License-Identifier: AGPL-3.0-or-later
;;
;; "I'll protect this server forever... just for you~" <3

(ns yuno.core
  (:gen-class)
  (:require [clojure.string :as str]
            [clojure.core.async :as async :refer [<! >! go go-loop close!]]
            [clojure.tools.logging :as log]
            [discljord.connections :as c]
            [discljord.messaging :as m]
            [discljord.events :as e]
            [yuno.config :as config]
            [yuno.database :as db]
            [yuno.commands :as cmd]))

;; State atoms
(defonce state (atom nil))

;; XP cooldowns (user-id+guild-id -> last-xp-time)
(defonce xp-cooldowns (atom {}))

(def xp-cooldown-ms 60000) ;; 1 minute

(defn can-gain-xp?
  "Check if user can gain XP (cooldown expired)"
  [user-id guild-id]
  (let [key (str user-id "-" guild-id)
        last-time (get @xp-cooldowns key 0)
        now (System/currentTimeMillis)]
    (> (- now last-time) xp-cooldown-ms)))

(defn record-xp-gain!
  "Record that user gained XP"
  [user-id guild-id]
  (let [key (str user-id "-" guild-id)]
    (swap! xp-cooldowns assoc key (System/currentTimeMillis))))

(defn calculate-level
  "Calculate level from XP"
  [xp]
  (int (Math/sqrt (/ xp 100.0))))

(defn process-xp!
  "Process XP gain for a message"
  [messaging ds guild-id channel-id user-id]
  (when (can-gain-xp? user-id guild-id)
    (let [xp-gain (+ 15 (rand-int 11)) ;; 15-25 XP
          {:keys [xp level]} (db/get-user-xp ds user-id guild-id)
          new-xp (+ xp xp-gain)
          new-level (calculate-level new-xp)]
      (db/add-xp! ds user-id guild-id xp-gain)
      (record-xp-gain! user-id guild-id)
      ;; Check for level up
      (when (> new-level level)
        (db/set-level! ds user-id guild-id new-level)
        @(m/create-message! messaging channel-id
                            :content (str "🎉 **Level Up!** 🎉\n"
                                          "<@" user-id "> reached **Level " new-level "**! 💕\n"
                                          "*\"You're getting stronger... I love it~\"* 💗"))))))

(defn handle-message
  "Handle incoming message events"
  [{:keys [messaging ds config]} {:keys [author content guild-id channel-id]}]
  (when-not (:bot author)
    (let [user-id (:id author)
          prefix (db/get-prefix ds guild-id (:default-prefix config))]
      ;; Process XP for non-bot messages in guilds
      (when guild-id
        (process-xp! messaging ds guild-id channel-id user-id))
      ;; Check for command prefix
      (when (str/starts-with? content prefix)
        (let [without-prefix (subs content (count prefix))
              [command & args-parts] (str/split without-prefix #"\s+" 2)
              args (or (first args-parts) "")]
          (when-not (str/blank? command)
            (log/info "Command:" command "Args:" args "User:" user-id "Guild:" guild-id)
            (cmd/handle-command messaging ds config guild-id channel-id user-id command args)))))))

(defn handle-ready
  "Handle ready event"
  [{:keys [messaging]} data]
  (let [user (:user data)
        username (:username user)
        discriminator (:discriminator user)]
    (log/info (str "💕 Yuno is online as " username "#" discriminator " 💕"))
    (log/info "\"I'll protect this server forever... just for you~\" 💗")))

(defn handle-guild-create
  "Handle guild create (bot joins a guild)"
  [{:keys [ds]} {:keys [id name]}]
  (log/info (str "Joined guild: " name " (" id ")"))
  ;; Ensure guild has settings
  (when-not (db/get-guild-settings ds id)
    (db/set-guild-settings! ds id {:prefix "."
                                   :spam-filter-enabled false
                                   :leveling-enabled true})))

(defn start-event-loop!
  "Start the event processing loop"
  [event-ch ctx]
  (go-loop []
    (when-let [[event-type event-data] (<! event-ch)]
      (try
        (case event-type
          :ready (handle-ready ctx event-data)
          :guild-create (handle-guild-create ctx event-data)
          :message-create (handle-message ctx event-data)
          nil) ;; Ignore other events
        (catch Exception e
          (log/error e "Error handling event" event-type)))
      (recur))))

(defn start-bot!
  "Start the Discord bot"
  [config]
  (log/info "💕 Starting Yuno Gasai 2 (Clojure Edition)~ 💕")

  (let [token (:discord-token config)
        intents #{:guilds :guild-messages :message-content}
        event-ch (async/chan 100)
        connection-ch (c/connect-bot! token event-ch :intents intents)
        messaging (m/start-connection! token)
        ds (db/create-datasource (:database-path config))]

    ;; Initialize database
    (db/initialize-db! ds)

    ;; Create context
    (let [ctx {:messaging messaging
               :ds ds
               :config config
               :connection-ch connection-ch
               :event-ch event-ch}]

      ;; Start event loop
      (start-event-loop! event-ch ctx)

      ;; Store state
      (reset! state ctx)

      (log/info "💕 Yuno is ready to protect your server~ 💕")

      ;; Return context
      ctx)))

(defn stop-bot!
  "Stop the Discord bot"
  []
  (when-let [{:keys [messaging connection-ch event-ch]} @state]
    (log/info "💔 Yuno is shutting down... Don't forget me~ 💔")
    (when messaging
      (m/stop-connection! messaging))
    (when connection-ch
      (c/disconnect-bot! connection-ch))
    (when event-ch
      (close! event-ch))
    (reset! state nil)
    (log/info "Goodbye~ 💕")))

(defn -main
  "Main entry point"
  [& args]
  (let [config-path (first args)
        config (config/load-config config-path)]

    (when-not (config/valid-token? config)
      (log/error "Invalid or missing Discord token!")
      (log/error "Set DISCORD_TOKEN environment variable or configure config.json")
      (System/exit 1))

    (log/info "Configuration loaded~")
    (log/info (str "Prefix: " (:default-prefix config)))
    (log/info (str "Database: " (:database-path config)))

    ;; Start bot
    (start-bot! config)

    ;; Add shutdown hook
    (.addShutdownHook (Runtime/getRuntime)
                      (Thread. ^Runnable stop-bot!))

    ;; Keep main thread alive
    @(promise)))
