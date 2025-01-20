{ include ("interpreter/instrumentation.asl") }

!init_interpreter.

+!init_interpreter
    :   true
    <-  makeArtifact(chat, "env.ChatArtifact", [], ArtId);
        focus(ArtId);
        makeArtifact(interpreter, "Interpreter", [], ArtId2);
        focus(ArtId2);
        !instrument_all;
        .wait(5000);
        generate_property("My name is Mario", Prop);
        .print(Prop).

+!kqml_received( Sender, tell, Msg, X )
    :   true
    <-  .print("Received ", Msg, " from ", Sender );
        generate_sentence( Msg, Sentence );
        msg( Sender, Sentence ).

+user_msg( Msg )
    :   true
    <-  generate_property( Msg, NewBelief );
        .broadcast( tell, NewBelief ).