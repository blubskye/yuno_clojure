;; Yuno Gasai 2 (Clojure Edition) - Commands
;; Copyright (C) 2025 blubskye
;; SPDX-License-Identifier: AGPL-3.0-or-later

(ns yuno.commands
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.data.json :as json]
            [discljord.messaging :as m]
            [discljord.permissions :as perms]
            [yuno.database :as db]
            [clojure.tools.logging :as log])
  (:import [java.lang Runtime]
           [java.lang.management ManagementFactory]))

;; ============================================================================
;; Data - Quotes, Images, Responses
;; ============================================================================

(def yuno-quotes
  ["Your future belongs to me"
   "I'm glad Yukkis mother is a good person, I didn't have to use any of the tools I brought"
   "I'm the only friend you need"
   "I was practically dead, but you gave me a future. Yukki is my hope in life, but if it won't come true then I will die for Yukki, and even in death I will chase after Yukki"
   "They are all planning to betray you!!!"
   "What's insane is this world that won't let me and Yukki be together!"
   "A half moon, it has a dark half and a bright half, just like me…"
   "Everything in this world is just a game and we are merely the pawns."
   "Breaking curfew is 3 demerits. 3 demerits gets the cage, the cage means no food."
   "No matter what happens, I'll protect you~ 💕"
   "I won't let anyone take you away from me!"
   "My love for you is eternal... whether you like it or not~ 💗"])

(def praise-images
  ["https://media.giphy.com/media/ny8mlxWio6WBi/giphy.gif"
   "https://media.giphy.com/media/BXrwTdoho6hkQ/giphy.gif"
   "https://media.giphy.com/media/3o7TKoWXm3okO1kgHC/giphy.gif"])

(def scold-images
  ["https://i.imgur.com/ZLaayKG.gif"
   "https://media.giphy.com/media/WoF3yfYupTt8mHc7va/giphy.gif"
   "https://media.giphy.com/media/cOWNPwDDh1tYs/giphy.gif"])

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

;; Banned search terms for NSFW content
(def banned-search-terms
  #{"loli" "gore" "guro" "scat" "vore" "underage" "shota"})

;; ============================================================================
;; Utility Functions
;; ============================================================================

(defn parse-user-mention
  "Parse a user mention or raw ID"
  [mention]
  (when mention
    (if-let [match (re-find #"<@!?(\d+)>" mention)]
      (parse-long (second match))
      (parse-long mention))))

(defn parse-role-mention
  "Parse a role mention or raw ID"
  [mention]
  (when mention
    (if-let [match (re-find #"<@&(\d+)>" mention)]
      (parse-long (second match))
      (parse-long mention))))

(defn parse-channel-mention
  "Parse a channel mention or raw ID"
  [mention]
  (when mention
    (if-let [match (re-find #"<#(\d+)>" mention)]
      (parse-long (second match))
      (parse-long mention))))

(defn calculate-level
  "Calculate level from XP using Yuno formula"
  [xp]
  (int (Math/sqrt (/ xp 100.0))))

(defn xp-for-next-level
  "Calculate XP needed for a specific level"
  [level]
  (+ (* 5 level level) (* 50 level) 100))

(defn total-xp-for-level
  "Calculate total XP needed to reach a level"
  [target-level]
  (loop [level 0 total 0]
    (if (>= level target-level)
      total
      (recur (inc level) (+ total (xp-for-next-level level))))))

(defn valid-url?
  "Check if a string is a valid URL"
  [s]
  (when s
    (re-matches #"https?://.*" s)))

(defn parse-boolean-arg
  "Parse a boolean argument like enable/disable/true/false"
  [arg]
  (when arg
    (let [lower (str/lower-case arg)]
      (cond
        (str/starts-with? lower "enab") true
        (str/starts-with? lower "tru") true
        (str/starts-with? lower "disab") false
        (str/starts-with? lower "fa") false
        :else nil))))

(defn master-user?
  "Check if user is a master user"
  [config user-id]
  (some #(= % (str user-id)) (:master-users config)))

;; ============================================================================
;; Fun Commands
;; ============================================================================

(defn cmd-ping
  "Ping command"
  [messaging channel-id]
  @(m/create-message! messaging channel-id
                      :content "💓 **Pong!**\nI'm always here for you~ 💕"))

(defn cmd-quote
  "Quote command - Random Yuno quote"
  [messaging channel-id]
  @(m/create-message! messaging channel-id
                      :content (str "💕 *\"" (rand-nth yuno-quotes) "\"* 💕")))

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

(defn cmd-praise
  "Praise command"
  [messaging channel-id args]
  (if-let [target-id (parse-user-mention args)]
    @(m/create-message! messaging channel-id
                        :content (str "<@" target-id "> " (rand-nth praise-images)
                                      "\n*Good job~* 💕"))
    @(m/create-message! messaging channel-id
                        :content "💔 Who do you want me to praise?")))

(defn cmd-scold
  "Scold command"
  [messaging channel-id args]
  (if-let [target-id (parse-user-mention args)]
    @(m/create-message! messaging channel-id
                        :content (str "<@" target-id "> " (rand-nth scold-images)
                                      "\n*Bad!* 😤"))
    @(m/create-message! messaging channel-id
                        :content "💔 Who do you want me to scold?")))

(defn cmd-neko
  "Neko command - Get a neko image"
  [messaging channel-id args]
  (let [lewd? (= (str/lower-case (or args "")) "lewd")
        url (if lewd?
              "https://nekos.life/api/lewd/neko"
              "https://nekos.life/api/neko")]
    (try
      (let [response (slurp url)
            data (json/read-str response :key-fn keyword)
            image-url (:neko data)]
        @(m/create-message! messaging channel-id
                            :content (str "😺 Here's a neko for you~ 💕\n" image-url)))
      (catch Exception e
        (log/error e "Failed to fetch neko")
        @(m/create-message! messaging channel-id
                            :content "💔 Couldn't fetch a neko right now~")))))

(defn cmd-urban
  "Urban Dictionary lookup"
  [messaging channel-id args]
  (if (str/blank? args)
    @(m/create-message! messaging channel-id
                        :content "❌ Please provide a search term~")
    (try
      (let [url (str "https://api.urbandictionary.com/v0/define?term=" (java.net.URLEncoder/encode args "UTF-8"))
            response (slurp url)
            data (json/read-str response :key-fn keyword)
            results (:list data)]
        (if (empty? results)
          @(m/create-message! messaging channel-id
                              :content (str "❌ No results found for `" args "`"))
          (let [result (first results)
                definition (subs (:definition result) 0 (min 500 (count (:definition result))))
                example (when (:example result)
                          (subs (:example result) 0 (min 200 (count (:example result)))))]
            @(m/create-message! messaging channel-id
                                :content (str "📖 **" (:word result) "**\n\n"
                                              "**Definition:** " definition "\n\n"
                                              (when example (str "**Example:** " example "\n\n"))
                                              "👍 " (:thumbs_up result) " | 👎 " (:thumbs_down result))))))
      (catch Exception e
        (log/error e "Failed to fetch urban dictionary")
        @(m/create-message! messaging channel-id
                            :content "💔 Couldn't search Urban Dictionary right now~")))))

(defn cmd-hentai
  "NSFW image command (rule34)"
  [messaging channel-id args nsfw?]
  (if-not nsfw?
    @(m/create-message! messaging channel-id
                        :content "💔 I can't post those here... Try a NSFW channel~ 😳")
    (let [parts (str/split (or args "") #"\s+")
          [count-str & tag-parts] (if (and (first parts) (parse-long (first parts)))
                                    parts
                                    (cons "2" parts))
          count (min 25 (max 1 (or (parse-long count-str) 2)))
          tag (str/join " " tag-parts)
          clean-args (str/lower-case (str/replace (or args "") #"[^a-z]" ""))]
      (if (some #(str/includes? clean-args %) banned-search-terms)
        @(m/create-message! messaging channel-id
                            :content "❌ That search is against Discord ToS. I won't search for that.")
        (try
          (let [url (if (str/blank? tag)
                      (str "https://rule34.xxx/index.php?page=dapi&s=post&q=index&json=1&limit=100&pid=" (rand-int 2000))
                      (str "https://rule34.xxx/index.php?page=dapi&s=post&q=index&json=1&limit=100&tags=" (java.net.URLEncoder/encode tag "UTF-8")))
                response (slurp url)
                results (json/read-str response :key-fn keyword)]
            (if (empty? results)
              @(m/create-message! messaging channel-id
                                  :content (str "❌ No results found for `" tag "`"))
              (let [selected (take count (shuffle results))
                    urls (map #(str "https://img.rule34.xxx/images/" (:directory %) "/" (:image %)) selected)]
                @(m/create-message! messaging channel-id
                                    :content (str/join "\n" urls)))))
          (catch Exception e
            (log/error e "Failed to fetch hentai")
            @(m/create-message! messaging channel-id
                                :content "💔 Couldn't fetch images right now~")))))))

;; ============================================================================
;; Utility Commands
;; ============================================================================

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
                                    "`mod-stats` - View moderation stats\n"
                                    "`exportbans` - Export ban list\n"
                                    "`importbans` - Import ban list\n\n"
                                    "**⚙️ Utility**\n"
                                    "`ping` - Check latency\n"
                                    "`prefix` - Set server prefix\n"
                                    "`source` - View source code\n"
                                    "`stats` - Bot statistics\n"
                                    "`help` - This menu\n\n"
                                    "**✨ Leveling**\n"
                                    "`xp` - Check XP and level\n"
                                    "`leaderboard` - Server rankings\n"
                                    "`set-level` - Set user level\n"
                                    "`set-levelrolemap` - Map levels to roles\n"
                                    "`set-experiencecounter` - Toggle XP system\n\n"
                                    "**🎱 Fun**\n"
                                    "`8ball` - Ask the magic 8-ball\n"
                                    "`quote` - Yuno quote\n"
                                    "`praise` - Praise someone\n"
                                    "`scold` - Scold someone\n"
                                    "`neko` - Neko images\n"
                                    "`urban` - Urban Dictionary\n\n"
                                    "**⚙️ Configuration**\n"
                                    "`set-spamfilter` - Toggle spam filter\n"
                                    "`set-joinmessage` - Set join DM\n"
                                    "`set-banimage` - Set ban image\n"
                                    "`add-mentionresponse` - Add auto-response\n\n"
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

(defn cmd-stats
  "Stats command - Bot statistics"
  [messaging channel-id]
  (let [runtime (Runtime/getRuntime)
        mb (/ 1024.0 1024.0)
        used-mem (/ (- (.totalMemory runtime) (.freeMemory runtime)) mb)
        max-mem (/ (.maxMemory runtime) mb)
        uptime-ms (.getUptime (ManagementFactory/getRuntimeMXBean))
        uptime-sec (/ uptime-ms 1000)
        hours (int (/ uptime-sec 3600))
        mins (int (mod (/ uptime-sec 60) 60))
        secs (int (mod uptime-sec 60))]
    @(m/create-message! messaging channel-id
                        :content (str "📊 **Yuno Statistics** 💕\n\n"
                                      "**Uptime:** " hours "h " mins "m " secs "s\n"
                                      "**Memory:** " (format "%.1f" used-mem) "MB / " (format "%.1f" max-mem) "MB\n"
                                      "**Platform:** " (System/getProperty "os.name") " (" (System/getProperty "os.arch") ")\n"
                                      "**Java:** " (System/getProperty "java.version") "\n"
                                      "**Clojure:** " (clojure-version) "\n\n"
                                      "*I'll always be here for you~* 💗"))))

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

;; ============================================================================
;; XP/Leveling Commands
;; ============================================================================

(defn cmd-xp
  "XP command"
  [messaging channel-id ds guild-id user-id args]
  (let [target-id (or (parse-user-mention args) user-id)
        {:keys [xp level]} (db/get-user-xp ds target-id guild-id)
        next-level (inc level)
        xp-for-next (xp-for-next-level level)
        progress (if (> xp-for-next 0)
                   (int (* 100 (/ (mod xp xp-for-next) xp-for-next)))
                   0)]
    @(m/create-message! messaging channel-id
                        :content (str "✨ **XP Stats**\n"
                                      "<@" target-id ">'s progress~ 💕\n\n"
                                      "**Level:** " level "\n"
                                      "**XP:** " xp "\n"
                                      "**Progress to Level " next-level ":** " progress "%"))))

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

(defn cmd-set-level
  "Set level command"
  [messaging channel-id ds guild-id args]
  (let [parts (str/split (str/trim (or args "")) #"\s+")
        level-str (first parts)
        user-mention (second parts)
        level (when level-str (parse-long level-str))
        target-id (or (parse-user-mention user-mention) nil)]
    (cond
      (nil? level)
      @(m/create-message! messaging channel-id
                          :content "❌ Usage: `set-level <level> [@user]`")

      (< level 0)
      @(m/create-message! messaging channel-id
                          :content "❌ Level must be non-negative~")

      :else
      (let [target (or target-id "self")
            xp (total-xp-for-level level)]
        (if target-id
          (do
            (db/set-xp! ds target-id guild-id 0 level)
            @(m/create-message! messaging channel-id
                                :content (str "✅ Set <@" target-id "> to **Level " level "** 💕")))
          @(m/create-message! messaging channel-id
                              :content "❌ Please mention a user to set their level~"))))))

(defn cmd-set-experiencecounter
  "Toggle XP system for guild"
  [messaging channel-id ds guild-id args]
  (let [enabled? (parse-boolean-arg args)]
    (if (nil? enabled?)
      @(m/create-message! messaging channel-id
                          :content "❌ Usage: `set-experiencecounter <enable|disable>`\nExamples: enable, disable, true, false")
      (do
        (db/set-leveling! ds guild-id enabled?)
        @(m/create-message! messaging channel-id
                            :content (str "✅ Experience counter is now **" (if enabled? "enabled" "disabled") "** 💕"))))))

(defn cmd-set-levelrolemap
  "Map a level to a role"
  [messaging channel-id ds guild-id args]
  (let [parts (str/split (str/trim (or args "")) #"\s+")
        level-str (first parts)
        role-mention (second parts)
        level (when level-str (parse-long level-str))
        role-id (parse-role-mention role-mention)]
    (cond
      (or (nil? level) (nil? role-id))
      @(m/create-message! messaging channel-id
                          :content "❌ Usage: `set-levelrolemap <level> <@role>`")

      (< level 0)
      @(m/create-message! messaging channel-id
                          :content "❌ Level must be non-negative~")

      :else
      (let [current-map (or (db/get-level-role-map ds guild-id) {})
            new-map (assoc current-map (str level) (str role-id))]
        (db/set-level-role-map! ds guild-id new-map)
        @(m/create-message! messaging channel-id
                            :content (str "✅ Level **" level "** will now give <@&" role-id "> 💕"))))))

(defn cmd-mass-addxp
  "Add XP to all members with a role"
  [messaging channel-id ds guild-id args]
  (let [parts (str/split (str/trim (or args "")) #"\s+")
        xp-str (first parts)
        role-mention (second parts)
        xp-amount (when xp-str (parse-long xp-str))
        role-id (parse-role-mention role-mention)]
    (cond
      (or (nil? xp-amount) (nil? role-id))
      @(m/create-message! messaging channel-id
                          :content "❌ Usage: `mass-addxp <xp amount> <@role>`")

      (<= xp-amount 0)
      @(m/create-message! messaging channel-id
                          :content "❌ XP amount must be positive~")

      :else
      @(m/create-message! messaging channel-id
                          :content (str "✅ This command requires role member fetching.\n"
                                        "Use Discord to add XP to role **" role-id "** members.\n"
                                        "*Feature requires guild member intent* 💕")))))

(defn cmd-mass-setxp
  "Set all members with a role to a level"
  [messaging channel-id ds guild-id args]
  (let [parts (str/split (str/trim (or args "")) #"\s+")
        level-str (first parts)
        role-mention (second parts)
        level (when level-str (parse-long level-str))
        role-id (parse-role-mention role-mention)]
    (cond
      (or (nil? level) (nil? role-id))
      @(m/create-message! messaging channel-id
                          :content "❌ Usage: `mass-setxp <level> <@role>`")

      (< level 0)
      @(m/create-message! messaging channel-id
                          :content "❌ Level must be non-negative~")

      :else
      @(m/create-message! messaging channel-id
                          :content (str "✅ This command requires role member fetching.\n"
                                        "*Feature requires guild member intent* 💕")))))

;; ============================================================================
;; Moderation Commands
;; ============================================================================

(defn cmd-ban
  "Ban command"
  [messaging channel-id ds guild-id mod-id args]
  (let [[user-mention & reason-parts] (str/split (or args "") #"\s+" 2)
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
  (let [[user-mention & reason-parts] (str/split (or args "") #"\s+" 2)
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
  (let [[user-id-str & reason-parts] (str/split (or args "") #"\s+" 2)
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
  (let [[user-mention minutes-str & reason-parts] (str/split (or args "") #"\s+" 3)
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
      (let [timeout-until (.plusMinutes (java.time.Instant/now) minutes)]
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

(defn cmd-exportbans
  "Export ban list to file"
  [messaging channel-id guild-id]
  @(m/create-message! messaging channel-id
                      :content (str "📋 **Export Bans**\n"
                                    "This feature requires fetching all guild bans.\n"
                                    "Ban list would be saved to `BANS-" guild-id ".txt`\n"
                                    "*Feature requires ban list fetching* 💕")))

(defn cmd-importbans
  "Import bans from file"
  [messaging channel-id args]
  (let [guild-id args]
    (if (or (str/blank? guild-id) (not (re-matches #"^\d+$" guild-id)))
      @(m/create-message! messaging channel-id
                          :content "❌ Please provide a valid guild ID~")
      @(m/create-message! messaging channel-id
                          :content (str "📋 **Import Bans**\n"
                                        "This would import bans from `BANS-" guild-id ".txt`\n"
                                        "*Feature requires ban API access* 💕")))))

;; ============================================================================
;; Configuration Commands
;; ============================================================================

(defn cmd-set-spamfilter
  "Toggle spam filter"
  [messaging channel-id ds guild-id args]
  (let [enabled? (parse-boolean-arg args)]
    (if (nil? enabled?)
      @(m/create-message! messaging channel-id
                          :content "❌ Usage: `set-spamfilter <enable|disable>`")
      (do
        (db/set-spam-filter! ds guild-id enabled?)
        @(m/create-message! messaging channel-id
                            :content (str "✅ Spam filter is now **" (if enabled? "enabled" "disabled") "** 💕"))))))

(defn cmd-set-joinmessage
  "Set join DM message"
  [messaging channel-id ds guild-id args]
  (if (str/blank? args)
    @(m/create-message! messaging channel-id
                        :content "❌ Usage: `set-joinmessage <title> <message>`")
    (let [parts (str/split args #"\s+" 2)
          title (str/replace (or (first parts) "") "_" " ")
          message (or (second parts) title)]
      (db/set-join-message! ds guild-id title message)
      @(m/create-message! messaging channel-id
                          :content (str "✅ Join message updated!\n"
                                        "**Title:** " title "\n"
                                        "**Message:** " message " 💕")))))

(defn cmd-set-banimage
  "Set ban image for a user"
  [messaging channel-id ds guild-id user-id args]
  (let [parts (str/split (str/trim (or args "")) #"\s+")
        url (first parts)
        target-mention (second parts)
        target-id (or (parse-user-mention target-mention) user-id)]
    (if (or (str/blank? url) (not (valid-url? url)))
      @(m/create-message! messaging channel-id
                          :content "❌ Please provide a valid URL~")
      (do
        (db/set-ban-image! ds target-id guild-id url)
        @(m/create-message! messaging channel-id
                            :content "✅ Ban image set! 💕")))))

(defn cmd-del-banimage
  "Delete ban image"
  [messaging channel-id ds guild-id user-id args]
  (let [target-id (or (parse-user-mention args) user-id)]
    (db/delete-ban-image! ds target-id guild-id)
    @(m/create-message! messaging channel-id
                        :content "✅ Ban image deleted! 💕")))

(defn cmd-add-mentionresponse
  "Add a mention response"
  [messaging channel-id ds guild-id args]
  (let [parts (str/split (str/trim (or args "")) #"\s+" 3)
        trigger (first parts)
        response (second parts)
        image (let [img (nth parts 2 nil)]
                (when (and img (valid-url? img)) img))]
    (if (or (str/blank? trigger) (str/blank? response))
      @(m/create-message! messaging channel-id
                          :content "❌ Usage: `add-mentionresponse <trigger> <response> [image-url]`")
      (if (db/get-mention-response ds guild-id trigger)
        @(m/create-message! messaging channel-id
                            :content "❌ A response with that trigger already exists~")
        (do
          (db/add-mention-response! ds guild-id trigger response image)
          @(m/create-message! messaging channel-id
                              :content (str "✅ Mention response added!\n"
                                            "**Trigger:** " trigger "\n"
                                            "**Response:** " response "\n"
                                            (when image (str "**Image:** " image)) " 💕")))))))

(defn cmd-del-mentionresponse
  "Delete a mention response"
  [messaging channel-id ds guild-id args]
  (let [trigger (str/trim (or args ""))]
    (if (str/blank? trigger)
      @(m/create-message! messaging channel-id
                          :content "❌ Usage: `del-mentionresponse <trigger>`")
      (if-let [response (db/get-mention-response ds guild-id trigger)]
        (do
          (db/delete-mention-response! ds (:id response))
          @(m/create-message! messaging channel-id
                              :content "✅ Mention response deleted! 💕"))
        @(m/create-message! messaging channel-id
                            :content "❌ No response found with that trigger~")))))

(defn cmd-mentionresponses
  "List all mention responses"
  [messaging channel-id ds guild-id]
  (let [responses (db/get-mention-responses ds guild-id)]
    (if (empty? responses)
      @(m/create-message! messaging channel-id
                          :content "No mention responses configured~")
      @(m/create-message! messaging channel-id
                          :content (str "📋 **Mention Responses**\n\n"
                                        (str/join "\n"
                                                  (map #(str "• **" (:trigger_text %) "** → " (:response %))
                                                       responses)))))))

;; ============================================================================
;; Auto-Clean Commands
;; ============================================================================

(defn cmd-auto-clean
  "Auto-clean management"
  [messaging channel-id ds guild-id args]
  (let [parts (str/split (str/trim (or args "")) #"\s+")
        action (first parts)]
    (case action
      "list"
      (let [cleans (db/get-auto-cleans ds guild-id)]
        (if (empty? cleans)
          @(m/create-message! messaging channel-id
                              :content "No auto-cleans configured~")
          @(m/create-message! messaging channel-id
                              :content (str "📋 **Auto-Cleans**\n\n"
                                            (str/join "\n"
                                                      (map #(str "• #" (:channel_name %) " - every " (:time_between_cleans %) "h")
                                                           cleans))))))

      ("add" "edit")
      (let [channel-mention (second parts)
              hours-str (nth parts 2 nil)
              warning-str (nth parts 3 nil)
              channel-name (when channel-mention
                             (if-let [match (re-find #"<#(\d+)>" channel-mention)]
                               (second match)
                               channel-mention))
              hours (when hours-str (parse-long hours-str))
              warning (when warning-str (parse-long warning-str))]
          (if (or (nil? channel-name) (nil? hours) (nil? warning))
            @(m/create-message! messaging channel-id
                                :content "❌ Usage: `auto-clean add <#channel> <hours> <warning-mins>`")
            (do
              (db/set-auto-clean! ds guild-id channel-name hours warning nil)
              @(m/create-message! messaging channel-id
                                  :content (str "✅ Auto-clean configured for #" channel-name " every " hours "h 💕")))))

      "remove"
      (let [channel-name (second parts)]
        (if (nil? channel-name)
          @(m/create-message! messaging channel-id
                              :content "❌ Please specify a channel~")
          (do
            (db/delete-auto-clean! ds guild-id channel-name)
            @(m/create-message! messaging channel-id
                                :content "✅ Auto-clean removed! 💕"))))

      ;; Default
      @(m/create-message! messaging channel-id
                          :content "❌ Usage: `auto-clean <list|add|edit|remove> [#channel] [hours] [warning-mins]`"))))

;; ============================================================================
;; Admin Commands
;; ============================================================================

(defn cmd-shutdown
  "Shutdown the bot"
  [messaging channel-id]
  @(m/create-message! messaging channel-id
                      :content "💔 Shutting down... Don't forget me~ 💔")
  (Thread/sleep 1000)
  (System/exit 0))

(defn cmd-add-masteruser
  "Add a master user"
  [messaging channel-id args]
  (let [target-id (or (parse-user-mention args) (parse-long args))]
    (if (nil? target-id)
      @(m/create-message! messaging channel-id
                          :content "❌ Please specify a user ID or mention~")
      @(m/create-message! messaging channel-id
                          :content (str "✅ To add master user " target-id ", update config.json and restart~ 💕")))))

;; ============================================================================
;; Command Router
;; ============================================================================

(defn handle-command
  "Route and execute a command"
  [messaging ds config guild-id channel-id user-id command args & {:keys [nsfw?]}]
  (let [cmd (str/lower-case command)
        prefix (:default-prefix config)
        is-master? (master-user? config user-id)]
    (case cmd
      ;; Fun
      "ping" (cmd-ping messaging channel-id)
      "quote" (cmd-quote messaging channel-id)
      "8ball" (cmd-8ball messaging channel-id args)
      "praise" (cmd-praise messaging channel-id args)
      "scold" (cmd-scold messaging channel-id args)
      "neko" (cmd-neko messaging channel-id args)
      ("nya") (cmd-neko messaging channel-id args)
      "urban" (cmd-urban messaging channel-id args)
      ("ub") (cmd-urban messaging channel-id args)
      "hentai" (cmd-hentai messaging channel-id args nsfw?)
      ("hen") (cmd-hentai messaging channel-id args nsfw?)

      ;; Utility
      "help" (cmd-help messaging channel-id (db/get-prefix ds guild-id prefix))
      ("list" "commands") (cmd-help messaging channel-id (db/get-prefix ds guild-id prefix))
      "source" (cmd-source messaging channel-id)
      ("sources" "github" "repo") (cmd-source messaging channel-id)
      "stats" (cmd-stats messaging channel-id)
      ("inf" "info") (cmd-stats messaging channel-id)
      "prefix" (cmd-prefix messaging channel-id ds guild-id args prefix)
      "set-prefix" (cmd-prefix messaging channel-id ds guild-id args prefix)

      ;; XP/Leveling
      "xp" (cmd-xp messaging channel-id ds guild-id user-id args)
      ("level" "rank") (cmd-xp messaging channel-id ds guild-id user-id args)
      "leaderboard" (cmd-leaderboard messaging channel-id ds guild-id)
      ("lb" "top") (cmd-leaderboard messaging channel-id ds guild-id)
      "set-level" (when is-master? (cmd-set-level messaging channel-id ds guild-id args))
      ("slvl") (when is-master? (cmd-set-level messaging channel-id ds guild-id args))
      "set-experiencecounter" (when is-master? (cmd-set-experiencecounter messaging channel-id ds guild-id args))
      ("set-expcounter" "sexpcounter") (when is-master? (cmd-set-experiencecounter messaging channel-id ds guild-id args))
      "set-levelrolemap" (when is-master? (cmd-set-levelrolemap messaging channel-id ds guild-id args))
      ("slrmap") (when is-master? (cmd-set-levelrolemap messaging channel-id ds guild-id args))
      "mass-addxp" (when is-master? (cmd-mass-addxp messaging channel-id ds guild-id args))
      ("massxp" "bulkxp" "giftxp") (when is-master? (cmd-mass-addxp messaging channel-id ds guild-id args))
      "mass-setxp" (when is-master? (cmd-mass-setxp messaging channel-id ds guild-id args))
      "mass-levelup" (when is-master? (cmd-mass-setxp messaging channel-id ds guild-id args))

      ;; Moderation
      "ban" (cmd-ban messaging channel-id ds guild-id user-id args)
      "kick" (cmd-kick messaging channel-id ds guild-id user-id args)
      "unban" (cmd-unban messaging channel-id ds guild-id user-id args)
      "timeout" (cmd-timeout messaging channel-id ds guild-id user-id args)
      "clean" (cmd-clean messaging channel-id args)
      ("mod-stats" "modstats") (cmd-mod-stats messaging channel-id ds guild-id user-id args)
      "exportbans" (when is-master? (cmd-exportbans messaging channel-id guild-id))
      ("ebans") (when is-master? (cmd-exportbans messaging channel-id guild-id))
      "importbans" (when is-master? (cmd-importbans messaging channel-id args))
      ("ibans") (when is-master? (cmd-importbans messaging channel-id args))

      ;; Configuration
      "set-spamfilter" (when is-master? (cmd-set-spamfilter messaging channel-id ds guild-id args))
      ("ssf") (when is-master? (cmd-set-spamfilter messaging channel-id ds guild-id args))
      "set-joinmessage" (when is-master? (cmd-set-joinmessage messaging channel-id ds guild-id args))
      ("sjm") (when is-master? (cmd-set-joinmessage messaging channel-id ds guild-id args))
      "set-banimage" (cmd-set-banimage messaging channel-id ds guild-id user-id args)
      ("sbanimg") (cmd-set-banimage messaging channel-id ds guild-id user-id args)
      "del-banimage" (cmd-del-banimage messaging channel-id ds guild-id user-id args)
      "add-mentionresponse" (when is-master? (cmd-add-mentionresponse messaging channel-id ds guild-id args))
      "del-mentionresponse" (when is-master? (cmd-del-mentionresponse messaging channel-id ds guild-id args))
      "mentionresponses" (when is-master? (cmd-mentionresponses messaging channel-id ds guild-id))

      ;; Auto-Clean
      "auto-clean" (when is-master? (cmd-auto-clean messaging channel-id ds guild-id args))
      ("autoclean") (when is-master? (cmd-auto-clean messaging channel-id ds guild-id args))

      ;; Admin
      "shutdown" (when is-master? (cmd-shutdown messaging channel-id))
      "add-masteruser" (when is-master? (cmd-add-masteruser messaging channel-id args))
      ("add-mu") (when is-master? (cmd-add-masteruser messaging channel-id args))

      ;; Unknown command - do nothing
      nil)))
