import ollama
import numpy as np

literals = ['b(V, A)', 'bid(Service, V)', 'source(A)', '+bid(Service, _1)', '+auction(service, D)[source(A)]', 'bid(flight_ticket(paris, athens, "15/12/2015"), 17.4)']

embeddings = {}
for literal in literals:
    embeddings[literal] = ollama.embeddings(model='nomic-embed-text', prompt=literal)

user_sentence = 'I have a bid for the flight from Rome to Naple for 20 euros'
user_embedding = ollama.embeddings(model='nomic-embed-text', prompt=user_sentence )

max_similar = None
max_value = 0
for key, value in embeddings.items():
    vec1 = np.array(value['embedding'])
    vec2 = np.array(user_embedding['embedding'])
    cosine_similarity = np.dot(vec1, vec2) / (np.linalg.norm(vec1) * np.linalg.norm(vec2))
    if cosine_similarity > max_value:
        max_value = cosine_similarity
        max_similar = key
    print(f"Similarità del coseno con {key}: {cosine_similarity}")

answer = ollama.generate(model='llama3.1', prompt=f'Modify this logical property ``` `{max_similar} ```` with respect to this sentence "{user_sentence}". Answer only with the modified logical property.')
print(f'"{user_sentence}" -> {answer.response}')