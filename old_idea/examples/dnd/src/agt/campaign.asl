// World beliefs
campaign(dungeons_and_dragons).
world(shadow_wood).
theme(fantasy).

eldrin(elf, old, intelligent, village_head).

// Quest and objective beliefs
quest(0, talk_to_eldrin).
quest(1, explore_ruins).
quest(2, retrieve_artifact).

location(0, small_village).
location(1, ancient_ruins).
location(2, throne_room).

people_present(0, [eldrin]).
people_present(1, []).
people_present(2, []).

quest_completed(0, talk_to_eldrin) :- action(talk(eldrin)) & action(go(ruins)) & action(attack(goblin, bow)).
quest_completed(1, explore_ruins) :- action(search) & action(go(door)).
quest_completed(2, retrieve_artifact) :- action(search).

next_event(0, talk(eldrin), "eldrin says: 'I give you a map, go to the ruins'. While he is talking a goblin appears!").
next_event(0, attack(goblin, bow), "You killed the goblin!").
next_event(0, go(ruins), "The ruins of an old castle stand before you").
next_event(1, search, "You find a key and there is a door but a goblin comes in!").
next_event(1, go(door), "You open the door and a goblin is in there").
next_event(2, search, "You found the King Threasure! You finished your quest!").

// Important item beliefs
item(ruins_map, possessed_by_eldrin).
item(power_amulet, hidden_in_ruins).

// Plot event beliefs
plot_event(0, village_arrival).
plot_event(1, ruins_discovery).
plot_event(2, amulet_retrieval).
