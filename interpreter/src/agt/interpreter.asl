{ include ("interpreter/instrumentation.asl") }

literals( interpreter, [ which_available_agents, which_are_your_available_plans ] ).

!init_interpreter.

+!init_interpreter
    :   true
    <-  makeArtifact(chat, "interpreter.ChatArtifact", [], ArtId);
        focus(ArtId);
        makeArtifact(interpreter, "interpreter.Interpreter", [], ArtId2);
        focus(ArtId2);
        !instrument_all.

+!kqml_received( Sender, tell, Msg, X )
    <-  .print("Received ", Msg, " from ", Sender );
        generate_sentence( Msg, Sentence );
        msg( Sender, Sentence ).

// +!kqml_received( Sender, tell, available_plans_of_agent( Agent ), X )
//     :   plans(Plans)[source(Agent)]
//     <-  generate_sentence( the_plans_of_agent_are(Agent, Plans), Sentence).

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

+user_msg( Recipients, Msg )
    :   true
    <-  .print( "Message to ", Recipients );
        generate_property( Msg, NewBelief );
        for ( .member(Recipient, Recipients ) ){
            if ( NewBelief == which_are_your_available_plans ) {
                .send( Recipient, achieve, list_plans );
            } else {
                .send( Recipient, tell, NewBelief );
            };
        }.

+!enumerate_agents
    :   .all_names( Names ) & .my_name( Name )
    <-  .delete(Name, Names, NewNames);
        .concat( "Enumerate the agents in this multi-agent system in a dotted list. The agents are: ", NewNames, Description);
        generate_sentence( describe( Description ), Sentence );
        msg( interpreter, Sentence ).