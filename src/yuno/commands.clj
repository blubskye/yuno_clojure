;; Yuno Gasai 2 (Clojure Edition) - Commands
;; Copyright (C) 2025 blubskye
;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns yuno.commands
  (:require [clojure.string :as str]
            [discljord.messaging :as m]
            [discljord.permissions :as perms]
            [yuno.database :as db]
            [clojure.tools.logging :as log]))

;; 8-ball responses
(def eightball-responses
  [;; Positive
   "It is certain~ 💕"
   "It is decidedly so~ 💗"
   "Without a doubt~ 💖"
   "Yes, definitely~ 💕"
   "You may rely on it~ 💗"
   "As I see it, yes~ ✨"
   "Most likely~ 💕"
   "Outlook good~ 💖"
   "Yes~ 💗"
   "Signs point to yes~ ✨"
   ;; Neutral
   "Reply hazy, try again~ 🤔"
   "Ask again later~ 💭"
   "Better not tell you now~ 😏"
   "Cannot predict now~ 🔮"
   "Concentrate and ask again~ 💫"
   ;; Negative
   "Don't count on it~ 💔"
   "My reply is no~ 😤"
   "My sources say no~ 💢"
   "Outlook not so good~ 😞"
   "Very doubtful~ 💔"])

;; Utility functions

(defn parse-user-mention
  "Parse a user mention or raw ID"
  [mention]
  (when mention
    (if-let [match (re-find #"<@!?(\d+)>" mention)]
      (parse-long (second match))
      (parse-long mention))))

(defn calculate-level
  "Calculate level from XP"
  [xp]
  (int (Math/sqrt (/ xp 100.0))))

;; Command implementations

(defn cmd-ping
  "Ping command"
  [messaging channel-id]
  @(m/create-message! messaging channel-id
                      :content "💓 **Pong!**\nI'm always here for you~ 💕"))

(defn cmd-help
  "Help command"
  [messaging channel-id prefix]
  @(m/create-message! messaging channel-id
                      :content (str "💕 **Yuno's Commands** 💕\n"
                                    "*\"Let me show you everything I can do for you~\"* 💗\n"
                                    "Prefix: `" prefix "`\n\n"
                                    "**🔪 Moderation**\n"
                                    "`ban` - Ban a user\n"
                                    "`kick` - Kick a user\n"
                                    "`unban` - Unban a user\n"
                                    "`timeout` - Timeout a user\n"
                                    "`clean` - Delete messages\n"
                                    "`mod-stats` - View moderation stats\n\n"
                                    "**⚙️ Utility**\n"
                                    "`ping` - Check latency\n"
                                    "`prefix` - Set server prefix\n"
                                    "`source` - View source code\n"
                                    "`help` - This menu\n\n"
                                    "**✨ Leveling**\n"
                                    "`xp` - Check XP and level\n"
                                    "`leaderboard` - Server rankings\n\n"
                                    "**🎱 Fun**\n"
                                    "`8ball` - Ask the magic 8-ball\n\n"
                                    "💕 *Yuno is always watching over you~* 💕")))

(defn cmd-source
  "Source command"
  [messaging channel-id]
  @(m/create-message! messaging channel-id
                      :content (str "📜 **Source Code**\n"
                                    "*\"I have nothing to hide from you~\"* 💕\n\n"
                                    "**Clojure Version**: https://github.com/blubskye/yuno_clojure\n"
                                    "**C# Version**: https://github.com/blubskye/yuno_csharp\n"
                                    "**C Version**: https://github.com/blubskye/yuno_c\n"
                                    "**PHP Version**: https://github.com/blubskye/yuno_php\n"
                                    "**Go Version**: https://github.com/blubskye/yuno-go\n"
                                    "**Rust Version**: https://github.com/blubskye/yuno_rust\n"
                                    "**Original JS**: https://github.com/japaneseenrichmentorganization/Yuno-Gasai-2\n\n"
                                    "Licensed under **AGPL-3.0** 💗")))

(defn cmd-prefix
  "Prefix command"
  [messaging channel-id ds guild-id args default-prefix]
  (if (str/blank? args)
    (let [current (db/get-prefix ds guild-id default-prefix)]
      @(m/create-message! messaging channel-id
                          :content (str "💕 Current prefix: `" current "`")))
    (if (> (count args) 5)
      @(m/create-message! messaging channel-id
                          :content "💔 Prefix too long! Max 5 characters~")
      (do
        (db/set-prefix! ds guild-id args)
        @(m/create-message! messaging channel-id
                            :content (str "🔧 **Prefix Updated!**\nNew prefix is now: `" args "` 💕"))))))

(defn cmd-xp
  "XP command"
  [messaging channel-id ds guild-id user-id args]
  (let [target-id (or (parse-user-mention args) user-id)
        {:keys [xp level]} (db/get-user-xp ds target-id guild-id)
        next-level (inc level)
        xp-for-next (* next-level next-level 100)
        progress (if (> xp-for-next 0)
                   (int (* 100 (/ xp xp-for-next)))
                   0)]
    @(m/create-message! messaging channel-id
                        :content (str "✨ **XP Stats**\n"
                                      "<@" target-id ">'s progress~ 💕\n\n"
                                      "**Level:** " level "\n"
                                      "**XP:** " xp "\n"
                                      "**Progress to Next:** " progress "%"))))

(defn cmd-leaderboard
  "Leaderboard command"
  [messaging channel-id ds guild-id]
  (let [top-users (db/get-leaderboard ds guild-id 10)
        medals ["🥇" "🥈" "🥉"]]
    @(m/create-message! messaging channel-id
                        :content (str "🏆 **Server Leaderboard**\n"
                                      "*\"Look who's been the most active~\"* 💕\n\n"
                                      (if (empty? top-users)
                                        "No one has earned XP yet~"
                                        (str/join "\n"
                                                  (map-indexed
                                                   (fn [i user]
                                                     (str (get medals i "")
                                                          " " (inc i) ". <@" (:user_id user) "> - "
                                                          "Level " (:level user) " (" (:xp user) " XP)"))
                                                   top-users)))))))

(defn cmd-8ball
  "8ball command"
  [messaging channel-id args]
  (if (str/blank? args)
    @(m/create-message! messaging channel-id
                        :content "💔 You need to ask a question~ 🎱")
    @(m/create-message! messaging channel-id
                        :content (str "🎱 **Magic 8-Ball**\n\n"
                                      "**Question:** " args "\n\n"
                                      "**Answer:** " (rand-nth eightball-responses) "\n\n"
                                      "*shakes the 8-ball mysteriously*"))))

(defn cmd-ban
  "Ban command"
  [messaging channel-id ds guild-id mod-id args rest-client]
  (let [[user-mention & reason-parts] (str/split args #"\s+" 2)
        target-id (parse-user-mention user-mention)
        reason (or (first reason-parts) "No reason provided")]
    (if (nil? target-id)
      @(m/create-message! messaging channel-id
                          :content "💔 Please specify a user to ban~")
      (do
        (try
          @(m/create-guild-ban! messaging guild-id target-id)
          (db/log-mod-action! ds {:guild-id guild-id
                                  :moderator-id mod-id
                                  :target-id target-id
                                  :action-type "ban"
                                  :reason reason
                                  :timestamp (System/currentTimeMillis)})
          @(m/create-message! messaging channel-id
                              :content (str "🔪 **Banned!**\n"
                                            "They won't bother you anymore~ 💕\n\n"
                                            "**User:** <@" target-id ">\n"
                                            "**Moderator:** <@" mod-id ">\n"
                                            "**Reason:** " reason))
          (catch Exception e
            (log/error e "Failed to ban user")
            @(m/create-message! messaging channel-id
                                :content (str "💔 Failed to ban: " (.getMessage e)))))))))

(defn cmd-kick
  "Kick command"
  [messaging channel-id ds guild-id mod-id args]
  (let [[user-mention & reason-parts] (str/split args #"\s+" 2)
        target-id (parse-user-mention user-mention)
        reason (or (first reason-parts) "No reason provided")]
    (if (nil? target-id)
      @(m/create-message! messaging channel-id
                          :content "💔 Please specify a user to kick~")
      (do
        (try
          @(m/remove-guild-member! messaging guild-id target-id)
          (db/log-mod-action! ds {:guild-id guild-id
                                  :moderator-id mod-id
                                  :target-id target-id
                                  :action-type "kick"
                                  :reason reason
                                  :timestamp (System/currentTimeMillis)})
          @(m/create-message! messaging channel-id
                              :content (str "👢 **Kicked!**\n"
                                            "Get out! 💢\n\n"
                                            "**User:** <@" target-id ">\n"
                                            "**Moderator:** <@" mod-id ">\n"
                                            "**Reason:** " reason))
          (catch Exception e
            (log/error e "Failed to kick user")
            @(m/create-message! messaging channel-id
                                :content (str "💔 Failed to kick: " (.getMessage e)))))))))

(defn cmd-unban
  "Unban command"
  [messaging channel-id ds guild-id mod-id args]
  (let [[user-id-str & reason-parts] (str/split args #"\s+" 2)
        target-id (parse-long user-id-str)
        reason (or (first reason-parts) "No reason provided")]
    (if (nil? target-id)
      @(m/create-message! messaging channel-id
                          :content "💔 Please specify a valid user ID to unban~")
      (do
        (try
          @(m/remove-guild-ban! messaging guild-id target-id)
          (db/log-mod-action! ds {:guild-id guild-id
                                  :moderator-id mod-id
                                  :target-id target-id
                                  :action-type "unban"
                                  :reason reason
                                  :timestamp (System/currentTimeMillis)})
          @(m/create-message! messaging channel-id
                              :content (str "💕 **Unbanned!**\n"
                                            "I'm giving them another chance~ Be good this time!\n\n"
                                            "**User:** <@" target-id ">\n"
                                            "**Moderator:** <@" mod-id ">\n"
                                            "**Reason:** " reason))
          (catch Exception e
            (log/error e "Failed to unban user")
            @(m/create-message! messaging channel-id
                                :content (str "💔 Failed to unban: " (.getMessage e)))))))))

(defn cmd-timeout
  "Timeout command"
  [messaging channel-id ds guild-id mod-id args]
  (let [[user-mention minutes-str & reason-parts] (str/split args #"\s+" 3)
        target-id (parse-user-mention user-mention)
        minutes (parse-long minutes-str)
        reason (or (first reason-parts) "No reason provided")]
    (cond
      (nil? target-id)
      @(m/create-message! messaging channel-id
                          :content "💔 Usage: timeout <user> <minutes> [reason]~")

      (or (nil? minutes) (<= minutes 0))
      @(m/create-message! messaging channel-id
                          :content "💔 Invalid duration~")

      :else
      (let [timeout-until (.plusMinutes (java.time.Instant/now)
                                        minutes)]
        (try
          @(m/modify-guild-member! messaging guild-id target-id
                                   :communication-disabled-until (.toString timeout-until))
          (db/log-mod-action! ds {:guild-id guild-id
                                  :moderator-id mod-id
                                  :target-id target-id
                                  :action-type "timeout"
                                  :reason (str reason " (" minutes " minutes)")
                                  :timestamp (System/currentTimeMillis)})
          @(m/create-message! messaging channel-id
                              :content (str "⏰ **Timed Out!**\n"
                                            "Think about what you did~ 😤\n\n"
                                            "**User:** <@" target-id ">\n"
                                            "**Duration:** " minutes " minutes\n"
                                            "**Moderator:** <@" mod-id ">\n"
                                            "**Reason:** " reason))
          (catch Exception e
            (log/error e "Failed to timeout user")
            @(m/create-message! messaging channel-id
                                :content (str "💔 Failed to timeout: " (.getMessage e)))))))))

(defn cmd-clean
  "Clean command"
  [messaging channel-id args]
  (let [count (or (parse-long args) 10)
        count (min count 100)]
    (try
      (let [messages @(m/get-channel-messages! messaging channel-id :limit (inc count))
            message-ids (map :id messages)]
        @(m/bulk-delete-messages! messaging channel-id message-ids)
        (let [confirm @(m/create-message! messaging channel-id
                                          :content (str "🧹 Deleted " (dec (count message-ids)) " messages~ 💕"))]
          (Thread/sleep 3000)
          @(m/delete-message! messaging channel-id (:id confirm))))
      (catch Exception e
        (log/error e "Failed to clean messages")
        @(m/create-message! messaging channel-id
                            :content (str "💔 Failed to clean: " (.getMessage e)))))))

(defn cmd-mod-stats
  "Mod stats command"
  [messaging channel-id ds guild-id user-id args]
  (let [target-id (or (parse-user-mention args) user-id)
        stats (db/get-mod-stats ds guild-id target-id)
        total (+ (:ban stats 0) (:kick stats 0) (:timeout stats 0) (:unban stats 0))]
    @(m/create-message! messaging channel-id
                        :content (str "📊 **Moderation Statistics**\n"
                                      "Stats for <@" target-id ">~ 💕\n\n"
                                      "**Total Actions:** " total "\n"
                                      "🔪 Bans: " (:ban stats 0) "\n"
                                      "👢 Kicks: " (:kick stats 0) "\n"
                                      "⏰ Timeouts: " (:timeout stats 0) "\n"
                                      "💕 Unbans: " (:unban stats 0)))))

;; Command router

(defn handle-command
  "Route and execute a command"
  [messaging ds config guild-id channel-id user-id command args]
  (let [cmd (str/lower-case command)
        prefix (:default-prefix config)]
    (case cmd
      "ping" (cmd-ping messaging channel-id)
      "help" (cmd-help messaging channel-id (db/get-prefix ds guild-id prefix))
      "source" (cmd-source messaging channel-id)
      ("sources" "github" "repo") (cmd-source messaging channel-id)
      "prefix" (cmd-prefix messaging channel-id ds guild-id args prefix)
      "xp" (cmd-xp messaging channel-id ds guild-id user-id args)
      ("level" "rank") (cmd-xp messaging channel-id ds guild-id user-id args)
      "leaderboard" (cmd-leaderboard messaging channel-id ds guild-id)
      ("lb" "top") (cmd-leaderboard messaging channel-id ds guild-id)
      "8ball" (cmd-8ball messaging channel-id args)
      "ban" (cmd-ban messaging channel-id ds guild-id user-id args nil)
      "kick" (cmd-kick messaging channel-id ds guild-id user-id args)
      "unban" (cmd-unban messaging channel-id ds guild-id user-id args)
      "timeout" (cmd-timeout messaging channel-id ds guild-id user-id args)
      "clean" (cmd-clean messaging channel-id args)
      ("mod-stats" "modstats") (cmd-mod-stats messaging channel-id ds guild-id user-id args)
      nil)))
