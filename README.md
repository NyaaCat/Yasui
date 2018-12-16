# yasui
Control entity resource consumption by drop AI computing. Mobは安い。

### Use Cases

Yasui is the plugin for servers that would like to keep minimum impact on vanilla gameplay.

By dropping AI computing and reduce server random tick speed on demand automatically, yasui enables much higher entity load on your server.

### Get Started

1. Download and install [NyaaCore](https://github.com/NyaaCat/NyaaCore/releases) corresponding to your server version.
2. Download and install [yasui](https://github.com/NyaaCat/Yasui/releases).
3. [Optional] Install [NyaaUtils](https://github.com/NyaaCat/NyaaUtils/releases) (for realtime TPS logging).
4. Remove all your lag removal plugins and restart server.
5. Enjoy benefit.

### Configuration

Configuration file `yasui/config.yml`

```
language: en_US
enable: true  # enable yasui auto nerfing
check_interval_tick: 60
disableai:
  excluded:
    entity_type:    # those entities will not be removed AI
    - PLAYER
    - SNOWMAN
    - ARMOR_STAND
  chunk_entity: 3  # remove AI for chunks containing more entities than this value
ignored_world:
- ignored_world  # this world will be ignored
__class__: cat.nyaa.yasui.Configuration  # DON'T TOUCH THIS
rules:
  1:    # Restore AI when 1 / 5 / 15 minutes tps value higher than 19.0
    __class__: cat.nyaa.yasui.Rule
    enable: false
    condition: tps_1m >= 19.0 && tps_5m >= 19.0 && tps_15m >= 19.0 # you can also use getTPSFromNU(seconds) to get TPS if NyaaUtils is installed 
    enable_ai: '1'
    worlds:
      - world
      - world_nether
      - world_the_end
  2:    # Remove AI when 1 minute tps lower than 18.0
    __class__: cat.nyaa.yasui.Rule
    enable: false
    condition: tps_1m < 18.0 && world_living_entities > 2000
    disable_ai: '1'
    worlds:
      - world
      - world_nether
      - world_the_end
  3:
    __class__: cat.nyaa.yasui.Rule
    enable: false
    condition: tps_15m < 15.0 && world_living_entities > 3000
    worlds:
      - world
      - world_nether
      - world_the_end
    commands:
      - 'killall * {world_name}'
  tickspeed1:    # lift worlds' random tick speed (maximum 3) when tps higher than 19.0 and notify players in actionbar
    __class__: cat.nyaa.yasui.Rule
    enable: false
    condition: tps_1m > 19.0 && tps_5m > 19.0 && world_random_tick_speed < 3
    worlds:
      - world
      - world_nether
      - world_the_end
    world_random_tick_speed: MIN(world_random_tick_speed + 1,3)
    messageType: ACTION_BAR
    message: '&arandom tick speed&r: &9{world_random_tick_speed}'
  tickspeed2:    # reduce worlds' random tick speed (minimum 0) when tps lower than 18.0 and notify players in actionbar
    __class__: cat.nyaa.yasui.Rule
    enable: false
    condition: tps_1m < 18.0 && tps_5m < 18.0 && world_random_tick_speed > 0
    worlds:
      - world
      - world_nether
      - world_the_end
    world_random_tick_speed: MAX(world_random_tick_speed - 1,0)
    messageType: ACTION_BAR
    message: '&arandom tick speed&r: &9{world_random_tick_speed}'
listen_mob_spawn: false    # do not catch new mob spawn
ignored_spawn_reason:
- CUSTOM    # spawned by plugins (all nature spawned mobs will be removed AI when in high load)
entity_limit:  
  excluded:
    entity_type:
    - PLAYER
    - ARMOR_STAND
    has_custom_name: true
    has_owner: true
  per_chunk_max: 50
  global_enable: false
```


