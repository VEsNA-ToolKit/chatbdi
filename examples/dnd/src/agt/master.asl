{ include ("character_sheet.asl") }
{ include("campaign.asl") }

is_action(Action) :-    Action == action(talk(Person)) | 
                        Action == action(move) | action(attack) | action(search).

quest_stage(0).

!start_game.

+!start_game
    :   true
    <-  .wait(20000);
        .print("Start the plot");
        !manage_plot_event.

+!manage_plot_event
    :   quest_stage(N) & plot_event(N, Event) & location(N, Location) & people_present(N, People)
    <-  .send(player, tell, player_location(Location));
        .send(player, tell, people_in_location(People));
        .send(player, tell, event(Event)).

+action(attack(Who, Weapon))
    :   quest_stage(N) & equipment(Weapon)
    <-  !manage_action(attack(Who, Weapon)).

+action(attack(Who, Weapon))
    :   quest_stage(N)
    <-  .send(player, tell, "You don't have this weapon!").

+action(Action)
    :   quest_stage(N)
    <-  !manage_action(Action).

+!manage_action(Action)
    :   quest_stage(N) & quest_completed(N, _)[source(self)]
    <-  ?next_event(N, Action, Event);
        .send(player, tell, Event);
        !manage_end_quest.

+!manage_action(Action)
    :   quest_stage(N)
    <-  ?next_event(N, Action, Event);
        .send(player, tell, Event).

+!manage_end_quest
    :   quest_stage(N) & quest_completed(N, Quest)[source(self)]
    <-  .send(player, tell, quest_completed(_, Quest));
        -+quest_stage(N+1);
        !manage_plot_event.