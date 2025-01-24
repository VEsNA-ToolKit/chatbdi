# Interpreter

The interpreter consists of two components:

- an interpreter agent
- a set of artifacts and additional DefaultInternalActions

## Interpreter Agent

The main functionalities of the interpreter are divided into:

- instrumentation
- interpretation

### Instrumentation

The interpreter agent first instruments all other agents with specific information and plans:

- the name of the interpreter agent;
- `+!list_plans`: allows listing all the plans of the agent and sending them to the interpreter;
- `+!provide_literals`: this plan is triggered at the start and causes each agent to send all beliefs, plans, and the various literals, including those contained in plan conditions, to the interpreter. This is achieved through three DefaultInternalActions;
- `+_`: a triggering event that ensures that every time an agent receives a new belief of any type, all beliefs are resent to the interpreter;
- Finally, a plan overrides the `+!kqml_received`: this plan is triggered whenever the user sends something to the agent that does not trigger anything and is not handled by the agent as originally designed. In this case, the default "error" behavior is applied.

When an agent sends its literals to the interpreter, they are sent in one of these forms:

- `plans( Plans )[ source (Ag) ]`: the plans of agent Ag
- `beliefs( Beliefs )[ source( Ag ) ]`: the beliefs of agent Ag
- `literal( Literals )[ source(Ag) ]`: the literals of agent Ag

Obviously, literals are redundant with beliefs, which are a subset of literals.

### Interpretation

The agent can then manage conversations between the user and agents in both directions, specifically:

- **Agent -> User**  
  The `kqml_received` is overridden, and every message received by the interpreter (that is not in the specific form outlined above for instrumentation) is intercepted by this function.  
  This plan calls the `generate_sentence( Msg, Sentence )` function of the `Interpreter` artifact, which generates a sentence `Sentence` from the `Msg` using the LLM.  
  The message is then passed to the `msg( Sender, Sentence )` function of the `ChatInterface` artifact, which displays the message with `Sender` as the title and `Sentence` as the message content.

- **User -> Agent - Broadcast**  
  A `user_msg( Msg )` event is triggered. The agent calls the `generate_property( Msg, NewBelief )` function of the `Interpreter` artifact, which translates the user's message `Msg` into a new belief `NewBelief`.  
  This function has a specific case when the user asks about the available agents. In this case, the `!enumerate_agents` plan is triggered, which simply sends all available agents (excluding the interpreter) to the user.

- **User -> Agent - Send**  
  A `user_msg( Recipients, Msg)` event is triggered. The agent first generates the new belief and then sends the message to each agent in `Recipients`.  
  There are also two special cases here:  
  - If the user asks an agent for its available plans, the interpreter sends the agent an `achieve` request for the `list_plans` plan (from instrumentation);  
  - If the user requests a plan description, the interpreter adds a `+plan_description_choice(Recipient)` belief, which is removed once the plan is described. This ensures the interpreter lists the `Recipient`'s plans for the user to choose from. The user must then type only the name of the desired plan in the chat. Normally, this input would fall into the broadcast `user_msg( Msg )` where `Msg` is the plan name. With the `plan_description_choice(Recipient)` belief, the broadcast handler takes the plan name, sends an `askHow` to the `Recipient` agent, receives the plan body, requests the `Interpreter` artifact to describe it, and displays the response to the user.

## Artifacts

The Java files for this project are:

- The interpreter: for us, the `Interpreter.java` interface implemented in `LLMWithEmbeddingsInterpreter.java`
- The chat: implemented in `ChatArtifact.java`
- Three new DefaultInternalActions: `list_beliefs.java`, `list_plans.java`, and `list_literals.java`

### The Interpreter

#### Interface

The interpreter must have two main functions:

- `@OPERATION void generate_property( String sentence, OpFeedBackParam<Literal> property)`  
  This function takes a `sentence` and generates a `Literal property`.  

- `@OPERATION void generate_sentence( String literal, OpFeedBackParam<String> sentence)`  
  This function takes a `literal` from the agent and generates a `sentence`.

These functions can be implemented in any way. A third function, such as `classify_performative( String sentence, OpFeedBackParam<Literal> performative)`, could also be added to classify the performative for a KQML message based on the user’s sentence.

#### LLM with Embeddings Interpreter

Our interpreter implements the interface using **Ollama** (https://ollama.com/) as the engine and:

- **nomic-embed-text** as the model for generating embeddings;
- **codegemma** as the model for translations.

The class constructor is called by the `interpreter` agent with the list of all literals from all agents as a parameter. The `init( Object[] literals )` function then performs two tasks:

1. Generates embeddings for each received literal and stores them in a `HashMap<Literal, List<Double>>`, where the literal is the key and the embedding is the value.
2. Creates models using Ollama's API `create` to generate the **nl_to_logic_model** and **logic_to_nl_model** for bidirectional translations from **codegemma**.

The `generate_property` function operates as follows:

1. Computes the embedding for the user's sentence (using the model).  
2. Finds the closest match among the literal embeddings using **cosine similarity** (**https://en.wikipedia.org/wiki/Cosine_similarity**).  
3. Sends a prompt to the **nl_to_logic_model**:

   > Modify this logical property {Property} according to this sentence "{Sentence}". Answer only with the modified logical property in plain text. If an information is not contained in the sentence, place an underscore in the place of the value.

4. Assigns the response value to `OpFeedBackParam`.

The `generate_sentence` function works more straightforwardly:

1. Sends a prompt to the **logic_to_nl_model**:

   > Generate a sentence describing this logical property: {Property}

2. Assigns the response value to `OpFeedBackParam`.

#### Communication with Ollama Models

Communication with **Ollama** is handled via HTTP requests to the Ollama server. We use two APIs (https://github.com/ollama/ollama/blob/main/docs/api.md):

- `/api/embed` for generating embeddings
- `/api/generate` for generating messages

It’s important to note that we do not use `/api/chat`, which tracks conversations: each message is processed independently as input-output.

### The Chat

The `ChatArtifact` artifact extends `GUIArtifact` instead of `Artifact`, as it is designed for GUI use in JaCaMo. It contains a `ChatView` object, which extends `JFrame` to create a graphical interface using Java Swing.

Its two main functions are:

1. `@OPERATION public void msg( String sender, String message )`  
   This function allows the interpreter agent to display a message in the chat by calling the `chatView`’s `appendMessage` function.

2. `@OPERATION public void notify_new_msg( String msg )`  
   `@OPERATION public void notify_new_msg( List<Literal> recipients, String msg )`  
   The first is triggered when the user does not specify a recipient, and the second when a recipient is indicated with `@recipient`. These functions trigger a signal for `+user_msg( Msg )` or `+user_msg( Recipient, Msg)`.

#### Graphical Interface

The graphical interface is implemented using Java Swing and extends `JFrame`. The chat resides in a `JFramePanel` that supports a limited version of HTML. The main functions are:

1. `sendMessage()`  
   Triggered when the user presses the Send button or the Enter key.  
   The text input is retrieved and displayed in the chat with the username using `appendMessage`. The function also determines recipients and calls the appropriate `notify_new_msg`.

2. `appendMessage()`  
   Highlights mentions using `<span>` and saves them in a dictionary. It then updates the interface with HTML and returns the mention dictionary.

--- 