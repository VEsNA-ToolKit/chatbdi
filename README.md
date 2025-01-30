# KQML Interpreter

This framework provides a template for building an interpreter agent using the [JaCaMo](https://jacamo-lang.github.io/) framework.

The framework provides:

- a chat GUI artifact;
- an Interpreter artifact interface;
- an set of interpreter actions.

To make an agent an interpreter it is sufficient to add this line to your code:

```
{ include( "interpreter.asl" ) }
```

The agent should then have two beliefs:

- `instrumentation(true|false)`: if true the agent will send to all the other agents the instrumentation;
- `interpreter_class( "[YourPackage.]YourInterpreter" )`: this belief tells the agent the name of the class to be used as interpreter artifact.

The agent can handle messages sent from the user with two triggering events:

- `user_msg( Msg )`: the user sent a broadcast message `Msg`;
- `user_msg( Recipients, Msg )`: the user sent a message `Msg` to `Recipients`.

The Interpreter class should provide these operations:

- `generate_property( Sentence, Property )`: given `Sentence` it produces a logical property in the feedback parameter `Property`;
- `generate_sentence( Performative, Literal, Sentence)`: given `Performative` the performative of the kqml message, `Literal` the literal received it produces a sentence in the feedback parameter `Sentence`;
- [OPTIONAL] `classify_performative( Sentence, Performative )`: given `Sentence` it infers the performative in the feedback parameter `Performative`. If not implemented in the interpreter class the operation will always return `tell`.

An example of agent interpreter is:

```
{ include( "interpreter.asl" ) }

instrumentation( true ).
interpreter_class( "LLMInterpreter" )

+user_msg( Msg )
	<-	classify_performative( Msg, Performative );
			generate_property( Msg, Property );
			.broadcast( Performative, Msg ).
```

The chat interface provides an operation `msg( Sender, Msg )` that displays the message `Msg` with sender `Sender` on the chat.