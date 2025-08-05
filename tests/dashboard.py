import os
import pandas as pd
import json
# from pyswip import Prolog
# from number_parser import parse
# import re
# from Levenshtein import distance
# import argparse
import streamlit as st
import altair as alt

OUT_FOLDER : str = "../llm-test-py/out/"
# prolog : Prolog = Prolog()
# prolog.consult( "are_equals.pl" )
#
# parser = argparse.ArgumentParser()
# parser.add_argument( '-r', '--reload', help='Update the csv files used to visualize results', action='store_true')
# args = parser.parse_args()

class colors():
	emb_pri = "#35A77C"
	emb_sec = "#065631"
	perf_pri = '#F57D26'
	perf_sec = '#840900'
	gen_pri = '#3A94C5'
	gen_sec = '#06436F'
	lev = "#8DA101"

def is_term( term : str ) -> bool:
    query = f"""
            catch((read_term_from_atom('{term}', _, [syntax_errors(error)])), _, fail).
        """
    try:
        result = list(prolog.query(query.strip()))
        return bool(result)
    except Exception:
        return False

def are_equals( term1 : str, term2 : str):
    try:
        results : list = list( prolog.query( f"same_structure({term1}, {term2}).") )
        if not results:
            return False
        return True
    except:
        return False

def is_sol_correct( sentence : str, sol : str, ex_sol : str ) -> bool:
    sentence = parse( sentence )
    sol_without_strings : list = re.sub(r'(["\'])(?:(?=(\\?))\2.)*?\1', '', sol)
    numbers : list = re.findall( r'\b\d+(?:\.\d+)?\b', sol_without_strings )
    for number in numbers:
        if number not in sentence:
            sol = sol.replace( number, "_" )
    var_lists : list = re.findall( r'\[[A-Z]\w*\]', sol )
    for var_list in var_lists:
        sol = sol.replace( var_list, "_" )
    if not is_term( sol ):
        return False
    if are_equals( sol, ex_sol ):
        return True
    return False

def load_gen_model( folder : str ) -> pd.DataFrame :
    data : list = []
    header : list[ str ] = [ 'sentence', 'perf', 'sol', 't_perf', 't_gen', 't_tot', 'ex_perf', 'ex_sol', 'perf_eq', 'sol_eq', 'dist']
    for record_name in sorted( os.listdir( folder ) ):
        # print( folder + record_name )
        with open( folder + record_name ) as record_file:
            record : dict = json.loads( record_file.read() )
        row : list = []
        row.append( record[ 'sentence' ] )
        row.append( record[ 'performative' ] )
        row.append( record[ 'solution' ] )
        row.append( record[ 'time' ][ 'performative' ] )
        row.append( record[ 'time' ][ 'generation' ] )
        row.append( record[ 'time' ][ 'total' ] )
        row.append( record[ 'expected' ][ 'performative' ] )
        row.append( record[ 'expected' ][ 'solution' ] )
        row.append( record['performative'].strip() == record['expected']['performative'].strip() )
        row.append( is_sol_correct( record['sentence'], record['solution'], record['expected']['solution']) )
        row.append( distance(record['solution'].strip(), record['expected']['solution'].strip()))
        data.append( row )
    return pd.DataFrame( data, columns=header)

def load_generation( folder : str ) -> pd.DataFrame:
    df : pd.DataFrame = pd.DataFrame()
    for temp in [ f for f in os.listdir( folder ) if f != '.DS_Store' ]:
        temp_folder : str = folder + f"{temp}/"
        for model in [ f for f in os.listdir( temp_folder ) if f != '.DS_Store' ] :
            model_folder : str = temp_folder + f"{model}/"
            model_df = load_gen_model( model_folder )
            model_name_col : list = [ model ] * len(model_df)
            model_df[ 'model' ] = model_name_col
            model_df[ 'temp' ] = temp
            df = pd.concat( [df, model_df], axis=0)
    return df

def load_emb_model( folder : str ):
    data : list = []
    header : list = [ 'sentence', 'emb', 'sim', 'exp', 't', 'correct' ]
    for record_name in sorted( os.listdir( folder ) ):
        with open( folder + record_name ) as record_file:
            record : dict = json.loads( record_file.read() )
        row : list = []
        row.append( record[ 'sentence' ] )
        row.append( record[ 'embedding' ] )
        row.append( record[ 'similarity' ] )
        row.append( record[ 'expected' ] )
        row.append( record[ 'time' ][ 'compute_embedding' ] )
        functor = record[ 'expected' ][:-2]
        row.append( record[ 'embedding' ].startswith( functor ) )
        data.append( row )
    return pd.DataFrame( data, columns=header )

def load_embeddings( folder : str ):
    df : pd.DataFrame = pd.DataFrame()
    for model in os.listdir( folder ):
        model_folder : str = folder + f"{model}/"
        model_df = load_emb_model( model_folder )
        model_name_col : list = [ model ] * len(model_df)
        model_df[ 'model' ] = model_name_col
        df = pd.concat( [df, model_df], axis=0)
    return df

def load_domain( domain_str : str ) -> tuple[pd.DataFrame, pd.DataFrame]:
    generation_folder : str = OUT_FOLDER + domain_str + "/generation/"
    embeddings_folder : str = OUT_FOLDER + domain_str + "/embeddings/"
    gen_domain_df : pd.DataFrame = load_generation( generation_folder )
    emb_domain_df : pd.DataFrame = load_embeddings( embeddings_folder )
    gen_domain_name_col : list = [ domain_str ] * len( gen_domain_df )
    emb_domain_name_col : list = [ domain_str ] * len( emb_domain_df )
    gen_domain_df[ 'domain' ] = gen_domain_name_col
    emb_domain_df[ 'domain' ] = emb_domain_name_col
    return gen_domain_df, emb_domain_df

def update_csv_data( ) -> None:
    gen_df : pd.DataFrame = pd.DataFrame()
    emb_df : pd.DataFrame = pd.DataFrame()
    for domain in [ folder for folder in os.listdir( OUT_FOLDER ) if folder != ".DS_Store" ]:
        gen_domain_df, emb_domain_df = load_domain( domain )
        gen_df = pd.concat( [gen_df, gen_domain_df ], axis=0 )
        emb_df = pd.concat( [emb_df, emb_domain_df ], axis=0 )
    gen_df.to_csv( 'data/gen.csv', mode='w+' )
    emb_df.to_csv( 'data/emb.csv', mode='w+' )

def analyse_emb():
    st.header( "Embeddings" )
    st.markdown( """
        All the embedding models provided by Ollama have been tested.
        on the sidebar you can choose from which models and domains see the results.
        By default they are all selected.
        """)
    df : pd.DataFrame = pd.read_csv( 'data/emb.csv' )
    models = df[ 'model' ].drop_duplicates()
    domains = df[ 'domain' ].drop_duplicates()
    with st.sidebar:
        sel_domains = st.pills( "Domains", domains, selection_mode="multi", default=domains, key='domains')
        sel_models = st.pills( "Models", models, selection_mode="multi", default=models )
    sel_df = df.loc[ df['model'].isin(sel_models) & df['domain'].isin(sel_domains) ]
    cor : pd.DataFrame = sel_df.groupby( 'model', as_index=False)[[ 'correct', 't' ]].mean()
    best_model = cor.loc[ cor[ 'correct' ].idxmax() ]
    st.metric( "Best Embedding Model", best_model[ 'model' ], best_model[ 'correct' ] )
    tab1, tab2 = st.tabs( [ 'Chart', 'Data' ] )
    #tab1.bar_chart( cor, x='model', y='correct')
    emb_acc_chart = alt.Chart(cor).mark_bar(color=colors.emb_pri ).encode(
        x=alt.X('model', title='Model'),
        y=alt.Y('correct', title='Accuracy')
    )
    emb_time_chart = alt.Chart(cor).mark_circle(color=colors.emb_sec).encode(
        x=alt.X('model', title='Model'),
        y=alt.Y('t', title='Time')
    )
    emb_chart = alt.layer(
        emb_acc_chart,
        emb_time_chart
    ).resolve_scale(
        y='independent'
    )
    tab1.altair_chart( emb_chart )
    tab2.dataframe( sel_df )
    with st.expander( 'Raw results' ):
        col1, col2 = st.columns( 2 )
        entry_domain = col1.selectbox("Select a domain", domains)
        entry_model = col2.selectbox("Select a model", models)

        subset = df.loc[ ( df[ 'domain' ] == entry_domain ) & ( df[ 'model' ] == entry_model ) ]
        for i, row in subset.iterrows():
            # col1, col2 = st.columns( 2 )
            cntr = st.container( border=True)
            cntr_col1, cntr_col2, cntr_col3 = cntr.columns([1, 5, 1])
            cntr_col1.badge( "Sentence" )
            cntr_col2.text( row['sentence'] )
            # cntr_col1.markdown( f":blue-badge[Sentence] {row[ 'sentence' ]}" )
            cntr_col1.badge( "Embedding", color="green" )
            cntr_col2.text( row['emb'] )
            cntr_col1.badge( "Expected", color="orange" )
            cntr_col2.text( row[ 'exp' ] )
            cntr_col1.badge( "Similarity", color="red" )
            cntr_col2.text( row["sim"] )
            if row[ 'correct' ]:
                cntr_col3.badge( "Correct", color="green" )
            else:
                cntr_col3.badge( "Incorrect", color="red" )

def analyse_gen( ):
    st.header( "Generative Models" )
    st.markdown( """
        You can see here the results from the generative models.
        We tried to extract from the input sentence the KQML Performative, limited to `tell`, `askOne` and `askAll`.
        We also tried to create, from the best fitting embedding and examples from the domain, a logical term as message content.
        Both tests have been performed with a set of different temperatures: 0.0, 0.2, 0.4, 0.6, 0.8 and 1.0.
        As for the embeddings, you can modify the values used for the visualization in the sidebar.
        Last but not least, we also computed the Levenshtein distance between the generated term and the expected one.
        """)
    df : pd.DataFrame = pd.read_csv( 'data/gen.csv' )
    temps = [ 0.0, 0.2, 0.4, 0.6, 0.8, 1.0 ]
    models = df[ 'model' ].drop_duplicates()
    domains = df[ 'domain' ].drop_duplicates()
    with st.sidebar:
        sel_temps : list = st.pills( "Temperature", temps, default=temps, selection_mode='multi' )
        sel_models : list = st.pills( "Generation Models", models, default=models, selection_mode='multi' )
    df_t = df.loc[ df['temp'].isin( sel_temps ) & df['model'].isin( sel_models ) & df['domain'].isin( st.session_state[ 'domains' ] ) ]
    with st.expander( "See full dataframe" ):
        st.dataframe( df_t )
    gen_cor : pd.DataFrame = df_t.groupby( 'model', as_index=False )[[ 'sol_eq', 't_gen']].mean()
    avg_dist : pd.DataFrame = df_t.groupby( 'model', as_index=False )[[ 'dist', 'sol_eq', 't_gen' ]].mean()
    perf_cor : pd.DataFrame = df_t.groupby( 'model', as_index=False )[[ 'perf_eq', 't_perf']].mean()
    best_model_avg = gen_cor.loc[ gen_cor[ 'sol_eq' ].idxmax() ]
    best_model_perf = perf_cor.loc[ perf_cor[ 'perf_eq' ].idxmax() ]
    best_model_dist = avg_dist.loc[ avg_dist[ 'dist' ].idxmin() ]
    merged = pd.merge( gen_cor, perf_cor, on=[ 'model' ])
    merged[ 'sum' ] = merged[ 'sol_eq' ] + merged[ 'perf_eq' ]
    best_model_all = merged.loc[ merged[ 'sum' ].idxmax() ]
    col1, col2, col3 = st.columns( 3 )
    col1.metric( "Best Perf Acc", best_model_perf[ 'model'], best_model_perf['perf_eq'].item())
    col2.metric( "Best Gen Acc", best_model_avg[ 'model'], best_model_avg['sol_eq'].item())
    col3.metric( "Best Gen Dist", best_model_dist['model'], best_model_dist[ 'dist'].item())
    st.subheader( "Performative Classification Results" )
    st.markdown( """
        The chart shows the results of the tests for performative classification.
        The bars show the accuracies while the dots the time consumed to compute the answer.
    """)
    # st.bar_chart( perf_cor, x='model', y=['perf_eq', 't_perf'], x_label="Models", y_label="Accuracy", color=['#29b09d', '#7DEFA1'])
    perf_acc_chart = alt.Chart( perf_cor ).mark_bar( color=colors.perf_pri ).encode( x=alt.X('model', title='Models'), y=alt.Y('perf_eq', title='Accuracy'))
    perf_time_chart = alt.Chart( perf_cor ).mark_circle(color=colors.perf_sec ).encode( x=alt.X('model', title='Models'), y=alt.Y('t_perf', title='Time (s)'))
    perf_chart = alt.layer(perf_acc_chart, perf_time_chart ).resolve_scale( y='independent' )
    st.altair_chart( perf_chart, use_container_width=True )
    st.markdown( """
        This scatter plot shows a comparison between the models with respect to accuracy and time on _y_ and _x_ respectively.
    """)

    st.scatter_chart( perf_cor, x='t_perf', y='perf_eq', color='model', x_label="Time", y_label="Accuracy" )

    st.subheader( "Term Generation Results" )
    # st.bar_chart( gen_cor, x='model', y='sol_eq', x_label="Models", y_label="Accuracy", stack=False )
    gen_acc_chart = alt.Chart( gen_cor ).mark_bar( color=colors.gen_pri ).encode( x=alt.X('model', title='Models'), y=alt.Y('sol_eq', title='Accuracy'))
    gen_time_chart = alt.Chart( gen_cor ).mark_circle(color=colors.gen_sec ).encode( x=alt.X('model', title='Models'), y=alt.Y('t_gen', title='Time (s)'))
    gen_chart = alt.layer(gen_acc_chart, gen_time_chart ).resolve_scale( y='independent' )
    st.markdown( """
        The chart shows the results of the tests for term generation.
        The bars show the accuracies while the dots the time consumed to compute the answer.
    """)
    st.altair_chart( gen_chart, use_container_width=True )
    st.markdown( """
        This scatter plot shows a comparison between the models with respect to accuracy and time on _y_ and _x_ respectively.
    """)
    st.scatter_chart( gen_cor, x='t_gen', y='sol_eq', color='model', x_label="Time", y_label="Accuracy" )

    st.markdown("""
        #### Levenshtein Distance
        Here you can find a chart with the Levenshtein distances of generated terms with respect to expected ones.
        Since LLMs work with strings, this can be thought as a naive metrics of "_how far_" we are from the correct result.
        """)
    st.bar_chart( avg_dist, x='model', y='dist', x_label="Models", y_label="Distance", stack=False, color=colors.lev )
    # st.scatter_chart( avg_dist, x='t_gen', y='sol_eq', size='dist', color='model', x_label="Time", y_label="Accuracy" )
    st.subheader( "Generative Results" )
    st.markdown( """
        Here you can find an evaluation of the best model to perform all the generative tasks.
        While the best solution is to use the best model for performative extraction and the best one for term generation, this can be not suitable for computers with limited resources that can benefit the use of a single model.
        The chart below simply shows a sum of the two accuracies and it is a number between 0 and 2.
        """)
    merged.rename( columns={ 'sol_eq': 'Term Accuracy', 'perf_eq': 'Performative Accuracy' }, inplace=True)
    st.metric( "Overall Best Model", best_model_all[ 'model' ], best_model_all[ 'sum' ] )
    st.bar_chart( merged, x='model', y=[ 'Performative Accuracy', 'Term Accuracy' ], x_label="Model", y_label="Accuracy", color=[ colors.perf_pri, colors.gen_pri ])

    with st.expander( 'Raw results' ):
        col1, col2, col3 = st.columns( 3 )
        entry_domain = col1.selectbox("Select a domain", sorted( domains ), key="gen_domain_raw" )
        entry_model = col2.selectbox("Select a model", sorted( models ), key="gen_model_raw" )
        entry_temp = col3.selectbox( "Select a temperature", sorted( temps ), key="gen_temp_raw" )

        subset = df.loc[ ( df[ 'domain' ] == entry_domain ) & ( df[ 'model' ] == entry_model ) & ( df['temp'] == entry_temp ) ]
        for i, row in subset.iterrows():
            cntr = st.container( border=True)
            cntr_col1, cntr_col2 = cntr.columns([8, 1])
            cntr_col1.badge( "Sentence" )
            cntr_col1.text( row['sentence'] )
            perf_cntr = cntr.container( border=True)
            res_col1, res_col2, _ = perf_cntr.columns( 3 )
            res_col1.badge( "Performative", color="green" )
            res_col1.text( row['perf'] )
            res_col2.badge( "Expected", color="orange" )
            res_col2.text( row['ex_perf'] )
            term_cntr = cntr.container( border=True)
            res_col1, res_col2, res_col3 = term_cntr.columns( 3 )
            res_col1.badge( "Term", color="violet")
            res_col1.text( row['sol'] )
            res_col2.badge( "Expected", color="orange" )
            res_col2.text( row['ex_sol'] )
            res_col3.badge( "Levenshtein", color="blue" )
            res_col3.text( row[ 'dist' ] )
            t_col1, t_col2, t_col3 = cntr.columns( 3, border=True )
            t_col1.badge( "Performative Time", color="green" )
            t_col2.badge( "Generation Time", color="violet" )
            t_col3.badge( "Total Time", color="red" )
            t_col1.text( f"{round( row['t_perf'], 3 )} s" )
            t_col2.text( f"{round( row['t_gen'], 3 )} s" )
            t_col3.text( f"{round( row['t_tot'], 3 )} s" )
            if row[ 'perf_eq' ] and row[ 'sol_eq' ]:
                cntr_col2.badge( "Correct", color="green" )
            elif row[ 'perf_eq' ] or row[ 'sol_eq' ]:
                cntr_col2.badge( "Partial", color="orange" )
            else:
                cntr_col2.badge( "Incorrect", color="red" )

def main() -> None :
    st.set_page_config(page_title="NL2KQML Results Viewer", page_icon='📊', layout="wide")
    st.title( "NL2KQML Results Viewer" )
    with st.sidebar:
        st.header( "Configuration" )
    st.markdown( """
        Here you can read the results of the tests from the NL2KQML tests.
        To perform the tests we used Ollama.

        In particular:
        """)
    col1, col2, col3 = st.columns( 3 )
    with col1.container( border = True ):
	    st.markdown( """
	    ##### Embedding Finding
	    Given a set of terms used as domain and the input sentence:
	    - we compute the embedding of the domain terms creating a vector space of the belief base of the agent (terms are quite preprocessed);
	    - we compute the embedding of the sentence;
	    - we look for the nearest term in the vector space;
	    - we will use the found term as template.
	    """)
    with col2.container( border=True ):
        st.markdown("""
            ##### Performative Extraction
            Given the input sentence, we extract the performative from the sentence.
        """)
    with col3.container( border=True ):
        st.markdown("""
           ##### Term Generation
           Given the set of terms from the domain with the same functor of the nearest embedding term and the input sentence:
           - we create a JSON formatted dictionary from all the terms;
           - we create a typed JSON format dictionary to force the models use the correct syntax;
           - we ask the model, given the input sentence and a list of examples to fill the format template.
        """)

    st.markdown("""
        Some post-processing have been done to compute the correct result, in particular:
        - numbers that are not contained in the sentence have been replaced with _ ;
        - lists containing only a variable have been replaced with _ ;
        - variables and _ are considered as equals;
        - variables with different name are equals.

        """)
        # Data are displayed from a cache. If you want to update the cache with new values click the button below.
    # if not os.path.exists( '.cache' ):
    #     os.makedirs( '.cache' )
    # if args.reload:
    #     update_csv_data()
    # if not os.path.exists( '.cache/gen.csv' ) or not os.path.exists( '.cache/emb.csv' ):
    #     update_csv_data()
    # st.warning( 'Pressing this button you will delete all the cached data and load the new ones.' )
    # st.button( 'Update Cache', on_click=update_csv_data)
    analyse_emb()
    analyse_gen()


if __name__ == '__main__':
    main()
