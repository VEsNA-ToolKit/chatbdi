import ollama
import numpy as np
import time

with open('literals.txt', 'r') as file:
    literals = file.readlines()
    literals = [literal.strip() for literal in literals]


embeddings = {}
for embedding_model in ['nomic-embed-text', 'mxbai-embed-large', 'snowflake-arctic-embed', 'granite-embedding']:
    start_embedding_time = time.time()
    for literal in literals:
        embeddings[literal] = ollama.embed(model=embedding_model, input=literal)["embeddings"]
    print(f'Embedding time for {embedding_model}: {time.time() - start_embedding_time}')

    print('TEMP\tEMBEDDING\t\tGENERATOR\tCORR\tEMB_CORR\tTIME')

    for generate_model in ['llama3.1', 'llama3.2', 'phi3', 'phi4', 'mistral']:
        avg_time = 0
        num_sentences = 0
        num_right = 0
        embed_right = 0

        f_sentences = open('sentences.txt', 'r')
        f_sol_embed = open('embeddings.txt', 'r')
        f_solutions = open('solutions.txt', 'r')
        sentences = f_sentences.readlines()
        sol_embed = f_sol_embed.readlines()
        solutions = f_solutions.readlines()

        for solution, sol_em, user_sentence in zip(solutions, sol_embed, sentences):
            start_time = time.time()
            user_embedding = ollama.embed(model=embedding_model, input=user_sentence)

            max_similar = None
            max_value = 0
            for key, value in embeddings.items():
                vec1 = np.array(value).flatten()
                vec2 = np.array(user_embedding["embeddings"]).flatten()
                cosine_similarity = np.dot(vec1, vec2) / (np.linalg.norm(vec1) * np.linalg.norm(vec2))
                if cosine_similarity > max_value:
                    max_value = cosine_similarity
                    max_similar = key
            if max_similar.strip() == sol_em.strip():
                embed_right += 1

            for temp in [0.0, 0.2, 0.4, 0.6, 0.8, 1.0]:
                modelfile = f'''
                    FROM {generate_model}
                    PARAMETER temperature {temp}
                    PARAMETER penalize_newline true
                    SYSTEM """
                        You are a logician who works with Prolog. You will receive a logical property and a sentence.
                        Modify the logical property according to the sentence and answer with the modified logical property.
                        If an information is not contained in the sentence, place an underscore in the place of the value or the variable.
                        The underscore must be not be surrounded by quotes, it should be _ and not "_".
                        Remember that words that starts with a capital letter are variables and words that starts with a lowercase letter are values.
                        Examples:
                        Logical property: hasColor(apple, red)
                        Sentence: The apple is green.
                        Answer: hasColor(apple, green)

                        Logical property: order(pizza, "1/1/1999", 12)
                        Sentence: I ordered a sushi at 14:00.
                        Answer: order(sushi, _, 14)
                    """
                '''

                ollama.create(model="generator", modelfile=modelfile)
                for _ in range(1):
                    answer = ollama.generate(model="generator", prompt=f'Modify this logical property ```{max_similar}``` according to this sentence "{user_sentence}". Answer only with the modified logical property in plain text. If an information is not contained in the sentence, place an underscore in the place of the value.')
                    avg_time += time.time() - start_time
                    if answer.response.strip('`').replace(" ", "") == solution.strip().replace(" ", ""):
                        num_right += 1
                    num_sentences += 1
                    with open(f'res/results-{embedding_model}-{generate_model}-{temp}.txt', 'a') as file:
                        file.write(f'[USER] "{user_sentence.strip()}" \n[EMBED] {max_similar} \n[GENERATE] {answer.response.strip('`')}\n\n')
                ollama.delete("generator")

                stats = f'{temp}\t'
                stats += embedding_model + "\t"
                if len(embedding_model) < 8:
                    stats += "\t"
                stats += generate_model + "\t"
                if len(generate_model) < 8:
                    stats += "\t"
                stats += str(round(num_right/200, 2)) + "%\t" + str(round(embed_right, 2)) + "/2\t\t" + str(round(avg_time / num_sentences, 2))
                print(stats) 