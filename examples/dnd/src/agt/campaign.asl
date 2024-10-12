// World beliefs
campaign(dungeons_and_dragons).
world(shadow_wood).
theme(fantasy).

eldrin(elf, old, intelligent, village_head).

// Quest and objective beliefs
quest(0, explore_village).
quest(1, explore_ruins).
quest(2, retrieve_artifact).

location(0, small_village).
location(1, ancient_ruins).
location(2, throne_room).

people_present(0, [eldrin]).
people_present(1, []).
people_present(2, []).

quest_completed(0, explore_village) :- action(talk(eldrin)).
quest_completed(1, explore_ruins) :- action(search).
quest_completed(2, retrieve_artifact) :- action(search).

// Important item beliefs
item(ruins_map, possessed_by_eldrin).
item(power_amulet, hidden_in_ruins).

// Plot event beliefs
plot_event(0, village_arrival).
plot_event(1, ruins_discovery).
plot_event(2, amulet_retrieval).

// plot_event(N, Event)
// location(N, Location)
// people_present(N, [Person, ...])
// quest(N, Quest)
// quest_completed(N, Quest) :- action(X) & action(Y)
// action_result(N, Action, Result) 