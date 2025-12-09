{ include( "interpreter.asl" ) }

instrumentation( true ).
interpreter_class( "interpreter.LLMWithEmbeddingsInterpreter" ).

// * KQML TO NATURAL LANGUAGE
// Each message received is translated in a sentence and sent on the chat
+!kqml_received( Sender, tell, i_say( Msg ), _ )
    <-  .print( "Received '", Msg, "' from ", Sender );
        generate_sentence( tell, Msg, Sentence );
        msg( Sender, Sentence ).

+!kqml_received( Sender, tell, describe( Msg ), _ )
    <-  .print( "Received '", Msg, "' from ", Sender );
        generate_sentence( tell, Msg, Sentence );
        msg( Sender, Sentence ).

+!kqml_received( Sender, tell, Msg, X )
    <-  .print("Received ", Msg, " from ", Sender );
        generate_sentence( tell, Msg, Sentence );
        msg( Sender, Sentence ).


// * NATURAL LANGUAGE TO KQML
// If the user has asked one agent to describe a plan now the interpreter is waiting for the plan name
+user_msg( Msg )
    :   plan_description_choice( Agent )
    <-  if( not .substring( "+!", Msg ) ){
            .concat( "+!", Msg, PlanName );
            .send( Agent, askHow, PlanName, Plan );
        } else {
            .send( Agent, askHow, Msg, Plan );
        }
        .concat( "Describe this plan that you can perform: ", Plan, Prompt );
        generate_sentence( Prompt, Sentence);
        msg( Agent, Sentence );
        -plan_description_choice( Agent ).

// The user sent a message without recipients -> broadcast
// It manages a request from the user of which are the available agents
+user_msg( Msg )
    :   true
    <-  .print( "Broadcast msg" );
        generate_property( Msg, NewBelief );
        if ( NewBelief == which_available_agents ){
            .print( "I enumerate the agents for the user" );
            !enumerate_agents;
        } else {
            .broadcast( tell, NewBelief );
        }.

// The user sent a message with recipients -> send to all recipients
// It manages the request from the user of available plans and describe a plan
+user_msg( Recipients, Msg )
    :   true
    <-  .print( "Message to ", Recipients );
        generate_property( Msg, NewBelief );
        .print( "Generated Property: ", NewBelief );
        for ( .member(Recipient, Recipients ) ){
            !manage_msg( Recipient, NewBelief );
        }.

+!manage_msg( Recipient, which_are_your_available_plans )
    <-  .send( Recipient, achieve, list_plans ).

+!manage_msg( Recipient, describe_plan )
    <-  +plan_description_choice( Recipient );
        ?plans( Plans )[ source( Recipient ) ];
        .concat( "Please write on the chat the plan you want: ", Plans, PlanChoice );
        msg( interpreter, PlanChoice ).

+!manage_msg( Recipient, my_price( Target, _ ) )
    <-  .send( Recipient, askOne, my_price( Target, P ) ).

+!manage_msg( Recipient, my_price( _ ) )
    <-  .send( Recipient, askOne, my_price( P ) ).

+!manage_msg( Recipient, my_price )
    <-  .send( Recipient, askOne, my_price( P ) );
        .concat( "My price is ", P, Descripition );
        .print( Description );
        msg( Recipient, Descripition ).

+!manage_msg( Recipient, NewBelief )
    <-  .send( Recipient, tell, NewBelief ).

// * INSTRUMENTATION PLANS
// Every time other agents update their own beliefs, plans or other literals this plan is triggered
+!update_kn_base( beliefs( NewInfo) )
    <-  update_embeddings( NewInfo ).

+!update_kn_base( plans( NewInfo) )
    <-  update_embeddings( NewInfo ).

+!update_kn_base( literals( NewInfo) )
    <-  update_embeddings( NewInfo ).

// * AUX PLANS
// Enumerates the name of the agents.
+!enumerate_agents
    :   .all_names( Names ) & .my_name( Name )
    <-  .delete(Name, Names, NewNames);
        .concat( "Enumerate the agents in this multi-agent system in a dotted list. The agents are: ", NewNames, Description);
        generate_sentence( tell, describe( Description ), Sentence );
        msg( interpreter, Sentence ).