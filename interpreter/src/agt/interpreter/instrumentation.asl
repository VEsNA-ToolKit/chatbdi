// !instrument_all.

// This plan instruments all the agents that are not the chat_bdi Agent
+!instrument_all
    :   true
    <-  .wait(1000);
        .all_names(Agents);
        .my_name(Me);
        for( .member(Agent, Agents) ){
            if ( not Agent == Me ){
                .print("I instrument ", Agent);
                !instrument(Agent);
            };
        };
        .wait(2000);
        !init_embeddings.

+!init_embeddings
    :   literals( _, Literals)
    <-  init_ollama( Literals ).

+!init_embeddings
    :   true
    <-  .print("There are no literals").

// Instrument plan sends to Agent the provide_plans instructions and then asks the agent to achieve it.
+!instrument(Agent)
    :   .my_name(Me)
    <-  .plan_label(P3, provide_plans);
        .send(Agent, tellHow, P3);
        .wait(1000);
        .send(Agent, achieve, provide_plans(Me));
        .send(Agent, tellHow, "+!kqml_received(Agent, Performative, Msg, X) : true <- .send(Agent, tell, error_message).").

// chat_bdi saves the received plans
+!kqml_received(Sender, tell, triggers(Agent, Triggers), X)
    :   true
    <-  .print("Got triggers from ", Sender);
        +triggers(Sender, Triggers).

// chat_bdi saves the received beliefs
+!kqml_received(Sender, tell, beliefs(Agent, Beliefs), X)
    :   true
    <-  .print("Got beliefs from ", Sender, ": ", Beliefs);
        +beliefs(Sender, Beliefs).

// chat_bdi saves the received literals (beliefs, conditions and triggering signals)
+!kqml_received(Sender, tell, literals(Agent, Literals), X)
    :   true
    <-  .print("Got Literals from ", Sender, ": ", Literals);
        +literals(Sender, Literals).

// provide_plans is the plan that is sent to every other agent of the mas
// It uses three custom internal actions to:
//  - create a list of plan names and parameters;
//  - create a list of beliefs;
//  - create a list of beliefs, plan conditions and triggering signals.
// The custom actions are necessary to have access to the Belief Base and to the plans body.
// They also are needed to clean the lists from default internal actions that normally are not interesting for our purpose.
@provide_plans
+!provide_plans(Interpreter)
    :   .my_name(Me)
    <-  env.custom_list_plans(Plans);
        .send(Interpreter, tell, triggers(Me, Plans));
        env.custom_list_beliefs(Beliefs);
        .send(Interpreter, tell, beliefs(Me, Beliefs));
        env.custom_list_useful_literals(Literals);
        .send(Interpreter, tell, literals(Me, Literals)).