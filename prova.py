import ollama
import numpy as np

sentence_embedded = ollama.embeddings(model='nomic-embed-text', prompt='The cat is on the table.')
logic_embedded = ollama.embeddings(model='nomic-embed-text', prompt='pos(cat, table, over).')
logic_embedded2 = ollama.embeddings(model='nomic-embed-text', prompt='name(cat, felix).')
s = sentence_embedded['embedding']
l = logic_embedded['embedding']
l2 = logic_embedded2['embedding']

vec1 = np.array(s)
vec2 = np.array(l)
vec3 = np.array(l2)

cosine_similarity = np.dot(vec1, vec2) / (np.linalg.norm(vec1) * np.linalg.norm(vec2))
print(f"Similarità del coseno: {cosine_similarity}")

cosine_similarity = np.dot(vec1, vec3) / (np.linalg.norm(vec1) * np.linalg.norm(vec3))
print(f"Similarità del coseno: {cosine_similarity}")