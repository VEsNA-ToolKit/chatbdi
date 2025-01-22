// !instrument_all.

// This plan instruments all the agents that are not the chat_bdi Agent
@atomic
+!instrument_all
//    :   focusing(_, interpreter, _, _, _, _)
    <-  .all_names(Agents);
        .my_name(Me);
        .delete( Me, Agents, NewAgents);
        .length( NewAgents, N );
        for( .member(Agent, NewAgents) ){
            .print("I instrument ", Agent);
            !instrument(Agent);
        };
        while( .count( literals( _ ), X ) & X < N ){
            .wait(500);
        }.

// +!instrument_all
//     :   true
//     <-  .wait({+focusing(_, interpreter, _, _, _, _)});
//         !instrument_all.
// 
// +!init_embeddings
//     :   literals( _, Literals)
//     <-  init_ollama( Literals ).
// 
// +!init_embeddings
//     :   true
//     <-  .print("There are no literals").

// Instrument plan sends to Agent the provide_plans instructions and then asks the agent to achieve it.
+!instrument(Agent)
    :   .my_name(Me)
    <-  .send(Agent, tellHow, "+!list_plans <- interpreter.list_plans( Plans ); .concat( \"Make a summary of what you can do as a dotted list. The list of the plans you have available is \", Plans, PlanString ); .send( interpreter, tell, describe(PlanString)).");
        .send(Agent, tellHow, "+!provide_literals( Interpreter ) : .my_name( Me ) <- interpreter.list_plans( Plans ); .send( Interpreter, tell, plans( Plans ) ); interpreter.list_beliefs( Beliefs ); .send( Interpreter, tell, beliefs( Beliefs ) ); interpreter.list_useful_literals( Literals ); .send( Interpreter, tell, literals( Literals ) ).");
        .wait(250);
        .send(Agent, achieve, provide_literals(Me));
        .send(Agent, tellHow, "+!kqml_received(Agent, Performative, Msg, X) : true <- .send(Agent, tell, error_message).").

// chat_bdi saves the received plans
+!kqml_received(Sender, tell, plans(Triggers), X)
    :   true
    <-  .print("Got triggers from ", Sender);
        +triggers(Triggers)[source(Sender)].

// chat_bdi saves the received beliefs
+!kqml_received(Sender, tell, beliefs(Beliefs), X)
    :   true
    <-  .print("Got beliefs from ", Sender, ": ", Beliefs);
        +beliefs(Beliefs)[source(Sender)].

// chat_bdi saves the received literals (beliefs, conditions and triggering signals)
+!kqml_received(Sender, tell, literals(Literals), X)
    :   true
    <-  .print("Got Literals from ", Sender, ": ", Literals);
        +literals(Literals)[source(Sender)].

// provide_plans is the plan that is sent to every other agent of the mas
// It uses three custom internal actions to:
//  - create a list of plan names and parameters;
//  - create a list of beliefs;
//  - create a list of beliefs, plan conditions and triggering signals.
// The custom actions are necessary to have access to the Belief Base and to the plans body.
// They also are needed to clean the lists from default internal actions that normally are not interesting for our purpose.
// @provide_plans
// +!provide_plans(Interpreter)
//     :   .my_name(Me)
//     <-  interpreter.list_plans(Plans);
//         .send(Interpreter, tell, triggers(Me, Plans));
//         interpreter.list_beliefs(Beliefs);
//         .send(Interpreter, tell, beliefs(Me, Beliefs));
//         interpreter.list_useful_literals(Literals);
//         .send(Interpreter, tell, literals(Me, Literals)).