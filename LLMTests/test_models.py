import ollama
import numpy as np
import time
import os

TEMPERATURES = [0.0, 0.2, 0.4, 0.6, 0.8, 1.0]
MODELS = ['gemma', 'gemma2', 'codegemma', 'llama3.1', 'llama3.2', 'codellama', 'tinyllama', 'phi3', 'phi4', 'qwen', 'qwen2', 'qwen2.5', 'qwen2.5-coder', 'mistral', 'deepseek-coder-v2']
#MODELS = ['llama3.1', 'llama3.2', 'phi3', 'mistral']
EMBEDDING_MODELS = ['nomic-embed-text', 'mxbai-embed-large', 'snowflake-arctic-embed', 'granite-embedding']
#EMBEDDING_MODELS = ['nomic-embed-text']
N_ITERATIONS = 1

TESTS_FOLDER = 'input_tests'
OUTPUT_FILE = 'results.log'

TEST_SENTENCES_PATH = 'sentences.txt'
TEST_SOLUTIONS_PATH = 'solutions.txt'
TEST_EMBEDDINGS_PATH = 'embeddings.txt'
TEST_LITERALS_PATH = 'literals.txt'

OUTPUT_FOLDER_PATH = 'output'

stats = {}

def load_data(domain):
	f_sentences = open(f'{TESTS_FOLDER}/{domain}/{TEST_SENTENCES_PATH}', 'r')
	f_embeddings = open(f'{TESTS_FOLDER}/{domain}/{TEST_EMBEDDINGS_PATH}', 'r')
	f_solutions = open(f'{TESTS_FOLDER}/{domain}/{TEST_SOLUTIONS_PATH}', 'r')
	f_literals = open(f'{TESTS_FOLDER}/{domain}/{TEST_LITERALS_PATH}', 'r')

	sentences = f_sentences.readlines()
	embeddings = f_embeddings.readlines()
	solutions = f_solutions.readlines()
	literals = f_literals.readlines()

	return sentences, solutions, literals, embeddings

def pull_models():
	models = ollama.list()
	model_names = []
	for model in models["models"]:
		model_names = model["model"].split(":", 1)[0]
	for embed_model in EMBEDDING_MODELS:
		if embed_model not in model_names:
			print(f'Pulling {embed_model}...')
			ollama.pull(embed_model)
	for gen_model in MODELS:
		if gen_model not in model_names:
			print(f'Pulling {gen_model}...')
			ollama.pull(gen_model)

def main():
	pull_models()
	for domain in os.listdir(TESTS_FOLDER):
		if domain != '.DS_Store':
			print(f' === DOMAIN: {domain} === ')
			with open(OUTPUT_FILE, 'a') as f:
				f.write(f'{domain}\n')
			test_domain(domain)
	print(stats)

def test_domain(domain):
	sentences, solutions, literals, embeddings = load_data(domain)
	print('TEMP\tEMBEDDING\t\tGENERATOR\tWEAK\tSTRONG\tEMB\tTIME')
	with open(OUTPUT_FILE, 'a') as f:
		f.write('TEMP\tEMBEDDING\t\tGENERATOR\tWEAK\tSTRONG\tEMB\tTIME\n')
	for embedding in EMBEDDING_MODELS:
		embedded_literals, t = test_embeddings(embedding, literals)
		for model in MODELS:
			for temperature in TEMPERATURES:
				load_model(model, temperature)
				strong_corrects = 0
				weak_corrects = 0
				embedding_corrects = 0
				total_time = 0
				for _ in range(N_ITERATIONS):
					for sentence, solution, embed_sol in zip(sentences, solutions, embeddings):
						# print(sentence, solution)
						logical_property, t_property = compute_most_similar_embedding(embedded_literals, sentence, embedding)
						answer, t_sentence = compute_sentence_property("generator", logical_property, sentence)
						if answer and answer[0] == '+':
							answer = answer[1:]
						total_time += t_sentence
						weak_correct, strong_correct, embedding_correct = check_stats(answer, solution, logical_property, embed_sol)
						# answer_correct = check_stats(answer, solution)
						update_stats(embedding, model, temperature, weak_correct, strong_correct, t_sentence)
						if strong_correct:
							strong_corrects += 1
						if weak_correct:
							weak_corrects += 1
						if embedding_correct:
							embedding_corrects += 1
						if not os.path.exists(f'{OUTPUT_FOLDER_PATH}'):
							os.mkdir(f'{OUTPUT_FOLDER_PATH}')
						if not os.path.exists(f'{OUTPUT_FOLDER_PATH}/{domain}'):
							os.mkdir(f'{OUTPUT_FOLDER_PATH}/{domain}')
						with open(f'{OUTPUT_FOLDER_PATH}/{domain}/{embedding}_{model}_{temperature}.txt', 'a') as file:
							file.write(f'[USER] {sentence.strip()}\n[EMBEDDING] {logical_property.strip()}\n[GENERATE] {answer.strip('`')}\n[SOLUTION] {solution.strip()}\n\n')
				total_trials = len(sentences) * N_ITERATIONS
				stats = f'{temperature}\t'
				stats += embedding + "\t"
				if len(embedding) < 8:
					stats += "\t"
				stats += model + "\t"
				if len(model) < 8:
					stats += "\t"
				#stats += str(round(answer_corrects/total_trials*100, 2)) + "%\t" + str(round(embedding_corrects/total_trials*100, 2)) + "%\t\t" + str(round(total_time / total_trials, 2))
				stats += str(round(weak_corrects/total_trials*100, 2)) + "%\t"
				stats += str(round(strong_corrects/total_trials*100, 2)) + "%\t"
				stats += str(round(embedding_corrects/total_trials*100, 2)) + "%\t"
				stats += str(round(total_time/total_trials, 2))
				# print(strong_corrects, weak_corrects, total_trials)
				print(stats)
				with open(OUTPUT_FILE, 'a') as f:
					f.write(stats + '\n')
				unload_model()

def test_embeddings(embedding_model, literals):
	start_time = time.time()
	literal_embeddings = {}
	for literal in literals:
		literal_embeddings[literal] = ollama.embed(model=embedding_model, input=literal)["embeddings"]
	return literal_embeddings, time.time() - start_time

def compute_most_similar_embedding(embeddings, sentence, embedding_model):
	start_time = time.time()
	user_embedding = ollama.embed(model=embedding_model, input=sentence)["embeddings"]
	
	max_similar = None
	max_value = -1

	for key, value in embeddings.items():
		vec1 = np.array(value).flatten()
		vec2 = np.array(user_embedding).flatten()
		cosine_similarity = np.dot(vec1, vec2) / (np.linalg.norm(vec1) * np.linalg.norm(vec2))
		if cosine_similarity > max_value:
			max_value = cosine_similarity
			max_similar = key
	
	return max_similar, time.time() - start_time

def compute_sentence_property(model, prop, sentence):
	start_time = time.time()
	answer = ollama.generate(model=model, prompt=f'Modify this logical property ```{prop}``` according to this sentence "{sentence}". Answer only with the modified logical property in plain text. If an information is not contained in the sentence, place an underscore in the place of the value.')
	return answer.response, time.time() - start_time

def load_model(model, temperature):
	modelfile = f'''
		FROM {model}
		PARAMETER temperature {temperature}
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
	system = f'''
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
		'''
	ollama.create(model="generator", from_=model, parameters={"temperature": temperature, "penalize_newline": True}, system=system)

def unload_model():
	ollama.delete("generator")

def check_stats(answer, solution, selected_property, solution_property):
	embedding_correct = selected_property.strip() == solution_property.strip()
	strong_correct = answer.strip('`').replace(" ", "") == solution.strip().replace(" ", "")
	weak_correct = solution.strip().replace(" ", "") in answer.replace(" ", "")
	return weak_correct, strong_correct, embedding_correct

# def check_stats(answer, solution):
# 	# return answer.strip('`').replace(" ", "") == solution.strip().replace(" ", "")
# 	return solution.strip().replace(" ", "") in answer.strip('`').replace(" ", "")

def update_stats(embedding_model, model, temperature, answer_correct, t):
	if embedding_model not in stats:
		stats[embedding_model] = {}
	if model not in stats[embedding_model]:
		stats[embedding_model][model] = {}
	if temperature not in stats[embedding_model][model]:
		stats[embedding_model][model][temperature] = 0
	if answer_correct:
		stats[embedding_model][model][temperature] += 1

def update_stats(embedding_model, model, temperature, weak_correct, strong_correct, t):
	if embedding_model not in stats:
		stats[embedding_model] = {}
	if model not in stats[embedding_model]:
		stats[embedding_model][model] = {}
	if temperature not in stats[embedding_model][model]:
		stats[embedding_model][model][temperature] = {}
		stats[embedding_model][model][temperature]['strong'] = 0
		stats[embedding_model][model][temperature]['weak'] = 0
	if strong_correct:
		stats[embedding_model][model][temperature]['strong'] += 1
	if weak_correct:
		stats[embedding_model][model][temperature]['weak'] += 1


if __name__ == '__main__':
	main()
