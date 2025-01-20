{ include ("interpreter/instrumentation.asl") }

!init_interpreter.

+!init_interpreter
    :   true
    <-  makeArtifact(chat, "env.ChatArtifact", [], ArtId);
        focus(ArtId);
        makeArtifact(interpreter, "Interpreter", [], ArtId2);
        focus(ArtId2);
        !instrument_all;
        generate_property("My name is Mario", Prop);
        .print(Prop).
