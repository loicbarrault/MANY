/*
 * Copyright 2009 Loic BARRAULT.  
 * Portions Copyright BBN and UMD (see LICENSE_TERP.txt).  
 * Portions Copyright 1999-2008 CMU (see LICENSE_SPHINX4.txt).
 * All Rights Reserved.  Use is subject to license terms.
 * 
 * See the file "LICENSE.txt" for information on usage and
 * redistribution of this file, and for a DISCLAIMER OF ALL 
 * WARRANTIES.
 *
 */
package edu.lium.decoder;
import java.io.BufferedWriter;
import java.io.Writer;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Collections;
import java.util.logging.Logger;
import com.bbn.mt.terp.TERutilities;
import edu.cmu.sphinx.linguist.WordSequence;
import edu.cmu.sphinx.linguist.dictionary.SimpleDictionary;
import edu.cmu.sphinx.linguist.dictionary.Word;
import edu.cmu.sphinx.linguist.language.ngram.NetworkLanguageModel;
import edu.cmu.sphinx.linguist.language.ngram.large.LargeNGramModel;
import edu.cmu.sphinx.util.LogMath;
import edu.cmu.sphinx.util.props.Configurable;
import edu.cmu.sphinx.util.props.PropertyException;
import edu.cmu.sphinx.util.props.PropertySheet;
import edu.cmu.sphinx.util.props.S4Boolean;
import edu.cmu.sphinx.util.props.S4Component;
import edu.cmu.sphinx.util.props.S4Double;
import edu.cmu.sphinx.util.props.S4Integer;
import edu.cmu.sphinx.util.props.S4String;

import edu.lium.utilities.MANYutilities;

//import com.google.gson.*;
import com.thoughtworks.xstream.XStream;
//import com.thoughtworks.xstream.io.json.JettisonMappedXmlDriver;
//import com.thoughtworks.xstream.io.json.JsonHierarchicalStreamDriver; 
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.io.json.JsonWriter;
//import com.thoughtworks.xstream.io.json.JsonWriter.Format;

import com.thoughtworks.xstream.io.json.*; 
import edu.lium.utilities.json.*;
import com.google.gson.*;

public class TokenPassDecoder implements Configurable
{
	/** The property that defines the logMath component. */
	@S4Component(type = LogMath.class)
	public final static String PROP_LOG_MATH = "logMath";

	/** The property that defines the network language model component. */
	@S4Component(type = NetworkLanguageModel.class)
	public final static String PROP_LANGUAGE_MODEL_ON_SERVER = "lmonserver";

	/** The property that defines the local ngram language model component. */
	@S4Component(type = LargeNGramModel.class)
	public final static String PROP_LANGUAGE_MODEL = "ngramModel";

	/** The property that defines the dictionary component. */
	@S4Component(type = SimpleDictionary.class)
	public final static String PROP_DICTIONARY = "dictionary";

	/** The property that defines the max number of tokens considered */
	@S4Integer(defaultValue = 1000)
	public final static String PROP_MAX_NB_TOKENS = "max-nb-tokens";

	/** The property that defines the lm weight */
	@S4Double(defaultValue = 1.0)
	public final static String PROP_LM_WEIGHT = "lm-weight";

	/** The property that defines the penalty when crossing null arc */
	@S4Double(defaultValue = 1.0)
	public final static String PROP_NULL_PENALTY = "null-penalty";

	/** The property that defines the length penalty */
	@S4Double(defaultValue = 1.0)
	public final static String PROP_WORD_PENALTY = "word-penalty";

	/** The property that defines the size of the nbest-list */
	@S4Integer(defaultValue = 0)
	public final static String PROP_NBEST_SIZE = "nbest-size";

	/**
	 * The property that defines the size of the nbest-list format : BTEC, MOSES
	 */
	@S4String(defaultValue = "MOSES")
	public final static String PROP_NBEST_FORMAT = "nbest-format";

	/** The property that defines the size of the nbest-list file */
	@S4String(defaultValue = "nbest")
	public final static String PROP_NBEST_FILE = "nbest-file";


	/** The property that defines the debugging */
	@S4Boolean(defaultValue = false)
	public final static String PROP_DEBUG = "debug";

	/** The property that determines whether to use the local lm or network lm */
	@S4Boolean(defaultValue = false)
	public final static String PROP_USE_NGRAM_LM = "use-local-lm";

	public LargeNGramModel languageModel;
	public boolean DEBUG = false;
	public LogMath logMath;
	public SimpleDictionary dictionary;
	public Logger logger;
	private String name;
	private TokenList tokens[];
	private TokenList lastTokens;
	public NetworkLanguageModel networklm;
	public int maxNbTokens;
	public float lm_weight;
	public float null_penalty;
	public float word_penalty;
	public int nbest_length;
	public String nbest_format;
	public String nbest_file;
	private boolean useNGramModel;
	private BufferedWriter nbest_bw;

	public boolean alternatives = false;
	private Hashtable<Integer, JSONsentence> line2json;
	
	String phase_name = "SC1";
	JSONphase phase = new JSONphase("LIUM", phase_name, "syscomb");

	public TokenPassDecoder() {

	}

	public TokenPassDecoder(float lm_weight, float null_penalty, float word_penalty, int maxNbTokens, int nbest_length,
			String nbest_format, boolean alternatives, LargeNGramModel lm, SimpleDictionary dictionary, LogMath logMath, Logger log,
			boolean debug)
	{
		this.logger = log;
		this.logMath = logMath;
		this.dictionary = dictionary;

		useNGramModel = true;
		this.languageModel = lm;
		this.maxNbTokens = maxNbTokens;
		this.lm_weight = lm_weight;
		this.null_penalty = null_penalty;
		this.word_penalty = word_penalty;
		this.nbest_length = nbest_length;
		this.alternatives = alternatives;
		this.nbest_format = nbest_format;
		this.DEBUG = debug;
		// System.err.println("DEBUG : "+DEBUG);
		for (int i = 0; i < tokens.length; i++)
			tokens[i] = new TokenList();
		lastTokens = new TokenList();
	}

	public TokenPassDecoder(float lm_weight, float null_penalty, float word_penalty, int maxNbTokens, int nbest_length,
			String nbest_format, boolean alternatives, NetworkLanguageModel lm, SimpleDictionary dictionary, LogMath logMath, Logger log,
			boolean debug)
	{
		this.logger = log;
		this.logMath = logMath;
		this.dictionary = dictionary;

		useNGramModel = false;
		this.networklm = lm;
		this.maxNbTokens = maxNbTokens;
		this.lm_weight = lm_weight;
		this.null_penalty = null_penalty;
		this.word_penalty = word_penalty;
		this.nbest_length = nbest_length;
		this.alternatives = alternatives;
		this.nbest_format = nbest_format;
		this.DEBUG = debug;
		// System.err.println("DEBUG : "+DEBUG);
		tokens = new TokenList[2];
		for (int i = 0; i < tokens.length; i++)
			tokens[i] = new TokenList();
		lastTokens = new TokenList();
	}

	public void allocate() throws java.io.IOException
	{
		//logger.info("TokenPassDecoder::allocate");
		dictionary.allocate();
		if (useNGramModel == true)
		{
			//logger.info("calling LargeNGramModel::allocate ... ");
			languageModel.allocate();
		}
		else
		{
			networklm.allocate();
		}
		tokens = new TokenList[2];
		for (int i = 0; i < tokens.length; i++)
			tokens[i] = new TokenList();
		lastTokens = new TokenList();
		//logger.info("TokenPassDecoder::allocate OK");
	}
	
	public void deallocate()
	{
		//logger.info("TokenPassDecoder::deallocate");
		dictionary.deallocate();

		if (useNGramModel)
			try
			{
				languageModel.deallocate();
			}
			catch (IOException ioe)
			{
				System.err.println("I/O error when deallocating language model. "+ioe);
			}
		else
			networklm.deallocate();

		if (nbest_file != null && !("".equals(nbest_file)))
		{
			try
			{
				nbest_bw.close();
			}
			catch (IOException ioe)
			{
				System.err.println("I/O error when closing output file " + String.valueOf(nbest_file) + " " + ioe);
			}
		}
		
		
	}

	public void newProperties(PropertySheet ps) throws PropertyException
	{

		logger = ps.getLogger();
		logMath = (LogMath) ps.getComponent(PROP_LOG_MATH);
		dictionary = (SimpleDictionary) ps.getComponent(PROP_DICTIONARY);
		useNGramModel = ps.getBoolean(PROP_USE_NGRAM_LM);
		if (useNGramModel)
		{
			languageModel = (LargeNGramModel) ps.getComponent(PROP_LANGUAGE_MODEL);
		}
		else
		{
			networklm = (NetworkLanguageModel) ps.getComponent(PROP_LANGUAGE_MODEL_ON_SERVER);
		}
		maxNbTokens = ps.getInt(PROP_MAX_NB_TOKENS);
		lm_weight = ps.getFloat(PROP_LM_WEIGHT);
		null_penalty = ps.getFloat(PROP_NULL_PENALTY);
		word_penalty = ps.getFloat(PROP_WORD_PENALTY);
		
		
		nbest_length = ps.getInt(PROP_NBEST_SIZE);
		if (nbest_length > 0)
		{
			nbest_format = ps.getString(PROP_NBEST_FORMAT);
			nbest_file = ps.getString(PROP_NBEST_FILE);
			nbest_bw = null;
			if (nbest_file != null && !("".equals(nbest_file)))
			{
				try
				{
					nbest_bw = new BufferedWriter(new FileWriter(nbest_file));
				}
				catch (IOException ioe)
				{
					System.err.println("I/O error when creating output file " + String.valueOf(nbest_file) + " " + ioe);
				}
			}
			
			
		}
		DEBUG = ps.getBoolean(PROP_DEBUG);
		
			logger.info("TokenPassDecoder::decode parameters"
			+	"\n\t - lm weight : " + lm_weight
			+	"\n\t - null penalty : " + null_penalty
			+	"\n\t - length penalty : " + word_penalty
			+	"\n\t - Max nb tokens : " + maxNbTokens
			+	"\n\t - N-Best length : " + nbest_length
			+	"\n\t - N-Best format : " + nbest_format
            +   ((useNGramModel)?"\n\t - LOCAL LM":"\n\t - LMSERVER") 
            + (alternatives?"\n\t - with alternatives":"\n\t - without alternatives") );
	}

	public String getName()
	{
		return name;
	}

	public void decode(int sentId, Graph lat, BufferedWriter outWriter) throws IOException
	{
		String ss = decode(sentId, lat);

		outWriter.write(ss);
		outWriter.newLine();
	}

	public String decode(int sentId, Graph lat) {
		if (DEBUG) logger.info("TokenPassDecoder::decode START ");
		if (lat == null)
		{
			logger.info("Lattice is null .. skipping sentence "+sentId);
			return null;
		}
		
		if(DEBUG)
			logger.info("TokenPassDecoder::decode parameters"
			+	"\n\t - lm weight : " + lm_weight
			+	"\n\t - null penalty : " + null_penalty
			+	"\n\t - word penalty : " + word_penalty
			+	"\n\t - Max nb tokens : " + maxNbTokens
			+	"\n\t - N-Best length : " + nbest_length
			+	"\n\t - N-Best format : " + nbest_format
			+	"\n\t - System weights : " + TERutilities.join(" ", lat.sys_weights));
		
		
		
		float[] lambdas = new float[3+lat.sys_weights.length];
		lambdas[0] = lm_weight;
		lambdas[1] = word_penalty;
		lambdas[2] = null_penalty;
		for(int i=0; i<lat.sys_weights.length; i++)
		{
			lambdas[3+i] = lat.sys_weights[i];
		}
		String[] res = null;

		int origine = 0, cible = 1;
		int maxHist = 0;
		if (useNGramModel)
			maxHist = languageModel.getMaxDepth();
		else
			maxHist = networklm.getMaxDepth();
		
		float maxScore;
		
		Node n = lat.firstNode;
		Token t = new Token(null, n, null, null, logMath.getLogOne(), 0, 0, null);
		tokens[origine].clear();
		tokens[origine + 1].clear();
		lastTokens.clear();

		tokens[origine].add(t);
		
		while (tokens[origine].isEmpty() == false) {
			maxScore = -Float.MAX_VALUE;
			for (Token pred : tokens[origine]) { //foreach token
				//if(DEBUG) logger.info("pred node = "+pred.node.id);
				// spread tokens
				for (Link l : pred.node.nextLinks) { // foreach link from this node
					String ws = lat.idToWord.get(l.wordId);
					//System.err.println("The word "+ws+" has been proposed by system(s) #"+TERutilities.join(", #",l.sysids));
					// logger.info("## link id="+l.wordId+" w="+ws);
					// logger.info("next node = "+l.endNode.id);
					WordSequence ns = null;
					WordSequence lmns = null;
					Word w = null;
					float nscore = pred.score;
					float link_score = logMath.getLogOne();
					float lm_score = pred.lm_score;
					int nb_words = pred.nb_words;
					int nb_nulls = pred.nb_nulls;
					int[] word_by_sys = null;
					if(pred.word_by_sys != null) {
						//clone does a shallow clone, but has independent storage for primitive 1 dimensional array
						word_by_sys = pred.word_by_sys.clone(); 
					} else {
						word_by_sys = new int[lat.sys_weights.length];
					}
					
					boolean itsanepsilon = Graph.null_node.equals(ws);
					
					if (pred.history == null) { // this is a first link
						if (itsanepsilon) {
							ns = new WordSequence(new Word[]
							{new Word(ws, null, true)});
							lmns = new WordSequence(new Word[]
							{new Word(ws, null, true)});
							// logger.info("... Creating word sequence for first link "+ws+" (null_node) gives -> "+ns.toText()+" [(1) for LM : "+lmns.toText()+" ]");
							// logger.info("l.posterior = "+l.posterior);
							// this is the prior probability for this backbone
							
							if(l.sysids.size() > 1)
							{
								System.err.println("TokenPassDecoder::decode : a first link have more than 1 sysid -> should not happen!");
								System.exit(0);
							}
							word_by_sys[l.sysids.get(0)]++;
							link_score += (-1.0f)*lambdas[3+l.sysids.get(0)];
							//logger.info(" first link system "+l.sysids.get(0)+": (-1.0f*"+lambdas[3+l.sysids.get(0)]+") -> link_score="+link_score); /**/

						} else { // IMPOSSIBLE !
							System.err.println("******************");
							System.err.println("A first link should not convey a word ! (sentId="+sentId+")");
							System.err.println("******************");
							System.exit(0); 
						}
					} else { // not a first link
						if (itsanepsilon) {
							ns = pred.history.addWord(new Word(ws, null, true), maxHist);
							lmns = pred.lmhistory;
							// logger.info("... Adding "+ws+" (null_node) to the word sequence, -> "+ns.toText()+" [(3) for LM : "+lmns.toText()+" ]");
							if (l.endNode != lat.lastNode) //the last link doesn't count 
							{
							    nb_nulls++;
							    link_score += (-1.0f*lambdas[2]); //simulate log value for word penalty by using negative value
							    //logger.info(" null word detected: (-1.0f*"+lambdas[2]+") -> link_score="+link_score); /**/
							}
						} else {
							w = dictionary.getWord(ws);
							if (w == dictionary.getUnknownWord()) {
								ns = pred.history.addWord(new Word(ws, null, false), maxHist);
							} else {
								ns = pred.history.addWord(w, maxHist);
							}
							lmns = pred.lmhistory.addWord(new Word(ws, null, false), maxHist);
							// logger.info("... Adding "+ws+" to the word sequence, -> "+ns.toText()+" [(4) for LM : "+lmns.toText()+" ]");

							float lmscore = logMath.getLogOne();
							if (useNGramModel == true) {
								lmscore = languageModel.getProbability(lmns.withoutWord(Graph.null_node));
								//if(DEBUG) logger.info("Proba lm : "+lmscore);
							} else {
								lmscore = networklm.getProbability(lmns.withoutWord(Graph.null_node));
								//if(DEBUG) logger.info("Proba lmonserver : "+lmscore); 
							}
							
							lm_score = lm_score + lmscore;
							link_score += lmscore*lambdas[0];
							//logger.info(" adding lm_score ("+lmscore+"*"+lambdas[0]+") -> link_score="+link_score); /**/
							nb_words++;
							link_score += (-1.0f*lambdas[1]); //simulate log value for word penalty by using negative value
							//logger.info(" new word detected: (-1.0f*"+lambdas[1]+") -> link_score="+link_score); /**/
							
							// logger.info("Link proba : "+l.posterior+" -> en log :"+logMath.linearToLog(l.posterior));
							for (int sysid : l.sysids) {
								word_by_sys[sysid]++;
								link_score += (-1.0f*lambdas[3+sysid]); //simulate log value for systems counts by using negative value
								//logger.info(" word from system "+sysid+": (-1.0f*"+lambdas[3+sysid]+") -> link_score="+link_score); /**/
							}
						}
					} // END not a first link


					Token tok = new Token(pred, l.endNode, ns, lmns, lm_score, nb_words, nb_nulls, word_by_sys);
					nscore = setTokenScore(tok, lambdas);
					//logger.info("NEW TOKEN : "+tok.toString());
					if (nscore > maxScore)
						maxScore = nscore;

					if (l.endNode == lat.lastNode)
					{
						// logger.info("adding "+tok.node.id+" to lastTokens");
						lastTokens.add(tok);
					} else {
						tokens[cible].add(tok);
					}
					
					// add score to this link
				    
					//logger.info("SCORE="+nscore+" and PRED: " + pred.toString());
					//l.addToLinkScore(logMath.logToLinear(logMath.subtractAsLinear(nscore, pred.score)));
					float old_score = l.getLinkScore();
					float new_score = logMath.addAsLinear(old_score, link_score);
					//logger.info(l.toString()+" from old_score="+old_score+" ("+logMath.logToLinear(old_score)+") to new_score=" + new_score + " ("+logMath.logToLinear(new_score)+") -> link_score=" + link_score + "("+logMath.logToLinear(link_score)+")");
					l.setLinkScore(new_score);

				} // END foreach link of this node
			} // END foreach token
			// pruning tokens can be done on the number of tokens or on a prob threshold
			if (tokens[cible].size() > maxNbTokens)
			{
				// taking only maxNbTokens best tokens
				Collections.sort(tokens[cible], Collections.reverseOrder());
				tokens[cible].removeRange(maxNbTokens, tokens[cible].size()); 
			}
			// normalisation is necessary in order to have negative values similar to log values
			// this cannot be ensured since the feature weights can be negative
			normalizeTokensScores(tokens[cible], maxScore);

			tokens[origine].clear();
			cible = origine;
			origine = (cible + 1) % 2;
		}

		// looking for best tokens in lastTokens
		Collections.sort(lastTokens, Collections.reverseOrder());

		
		
		if (nbest_length > 0) // generate a nbest-list (BTEC or MOSES format)
		{
			if (nbest_bw == null)
			{
				logger.info("TokenPassDecoder: BufferedWriter for nbest is null -> should not happen ... exiting ! ");
				System.exit(0);
			}

			int nn = Math.min(lastTokens.size(), nbest_length);
			res = new String[nn];

			if (DEBUG)
				logger.info("TokenPassDecoder: " + nn + " best tokens (nbest list format) ...");

			ArrayList<String> obest = new ArrayList<String>();
			for (int nb = 0; nb < nn; nb++)
			{
				obest.clear();
				Token best = lastTokens.get(nb);
				Token current_tok = best;
				
				while (best != null && best.node != lat.firstNode)
				{
					if (DEBUG)
						logger.info("TokenPassDecoder: WORD: " + best.history.getNewestWord().getSpelling() + " ID:" + best.node.id + " TIME="
								+ best.node.time + " SCORE: " + best.score); // + " NORM_SCORE: " + best.norm_score);
					if (Graph.null_node.equals(best.history.getNewestWord().getSpelling()) == false)
					{
						obest.add(0, best.history.getNewestWord().getSpelling());
					}
					//else if (DEBUG)
						//logger.info("found null_node : " + best.history.getNewestWord().getSpelling());
					best = best.pred;
				}

				if ("MOSES".equals(nbest_format))
				{
					StringBuilder sb = new StringBuilder(sentId + " ||| ");
					if (obest.size() > 0)
					{
						sb.append(obest.get(0));
						for (int no = 1; no < obest.size(); no++)
						{
							sb.append(" ").append(obest.get(no));
						}
					}
					sb.append(" ||| ");
					/*sb.append("ins: "+lat.getCost(3)
							+" del: "+lat.getCost(0)
							+" sub: "+lat.getCost(4)
							+" shift: "+lat.getCost(6)
							+" stem: "+lat.getCost(1)
							+" syn: "+lat.getCost(2)
							+" match: "+lat.getCost(5)*/
					sb.append(" lm: "+current_tok.lm_score
							+" word: "+(-current_tok.nb_words)
							+" null: "+(-current_tok.nb_nulls)
							+" priors:");
					for(int np=0; np<lat.sys_weights.length; ++np)
					{
						sb.append(" "+(-current_tok.word_by_sys[np]));
					}
					sb.append(" ||| ").append(current_tok.score);
					res[nb] = sb.toString();
				}
				else
				// format is BTEC
				{
					StringBuilder sb = new StringBuilder("##0#");
					if (obest.size() > 0)
					{
						sb.append(obest.get(0));
						for (int no = 1; no < obest.size(); no++)
						{
							sb.append(" ").append(obest.get(no));
						}
					}
					sb.append("#0.000000e+00#").append(current_tok.score).append("#");
					res[nb] = sb.toString();
				} // end of format cases

				try
				{
					nbest_bw.write(res[nb]);
					nbest_bw.newLine();
					nbest_bw.flush();
				}
				catch (IOException e)
				{
					System.err.println("TokenPassDecoder : error printing into nbest file ... : ");
					e.printStackTrace();
					System.exit(0);
				}
			} // for(int nb=0; nb<nn; nb++)
		} // END IF NBEST  // if(nbest_length > 0)

		//if (DEBUG) logger.info("Looking for best token (1 hypo per line) ...");

		Token best = lastTokens.get(0);
		Token current_tok = best;

		if (DEBUG) logger.info(" BEST: "+ best.toString());

		// reproducing 1-best
		ArrayList<String> obest = new ArrayList<String>();
		while (current_tok != null && current_tok.node != lat.firstNode)
		{
			if (DEBUG) logger.info(" WORD: " + current_tok.history.getNewestWord().getSpelling() + current_tok.toString()); 

			// don't add NULL words
			if (Graph.null_node.equals(current_tok.history.getNewestWord().getSpelling()) == false) {
				obest.add(0, current_tok.history.getNewestWord().getSpelling());
			}
			current_tok = current_tok.pred;
		}

		StringBuilder sb = new StringBuilder();
		if (obest.size() > 0) {
			sb.append(obest.get(0));
			for (int no = 1; no < obest.size(); no++) {
				sb.append(" ").append(obest.get(no));
			}
		}
		
                if(alternatives){
				if(DEBUG) logger.info(" ############# ALTERNATIVES !!! ############## ");  
				    
				// get corresponding JSON data structure for line sentId
				//logger.info("Getting JSON for sentence " + sentId);
                                JSONsentence sentence = line2json.get(sentId); 

                                ArrayList<JSONtag> tags = new ArrayList<JSONtag>();
				
				ArrayList<JSONtrans> transes = sentence.getAltTranses();
				if(transes == null || transes.isEmpty()){
				    System.err.println("No alternative translations available for sentence" + sentId + "... exiting !");
				    System.exit(0);

				}
			
				// get the last transes
				JSONtrans last_trans = transes.get(transes.size()-1);
				last_trans.setPhaseName(phase_name);
				// remove all alt_transes, just keep syscomb translation
				transes.clear();
                                
				JSONtarget target = new JSONtarget(sb.toString(), JSONtoken.tokenise(sb.toString(), ' ', 1000), tags); 
				last_trans.setTarget(target);
                               
				// get the tsource
				JSONtsource ts = last_trans.getTsource(); 
				ts.getAnnotations().clear();
                              
                                // only annotations will be useful ... hopefully!
                                JSONannotation annotation = new JSONannotation(JSONannotation.ALT);

				// get CN containing the 1-best -> tokens list
                                ArrayList<Token> btoks = new ArrayList<Token>();
				current_tok = best.pred; //skip last link (no word on it)
				while ((current_tok != null) && (current_tok.node != lat.firstNode)){
					btoks.add(current_tok);
					current_tok = current_tok.pred;
				}
				
				// foreach word, put the alternatives in the annotation
                                for(int num=btoks.size()-1, i=0; num>0; num--, i++){
					
					String sel_word = btoks.get(num-1).history.getNewestWord().getSpelling();
					
					//logger.info(" **** Selected WORD : "+sel_word);
					Token tok = btoks.get(num);
					JSONannot annot = new JSONannot();
                                        annot.addToken(""+i);
				        //String msg = "Confusion set ["+i+"] : ";	
					float total_score = logMath.getLogZero();
					for (Link l : tok.node.nextLinks){
					    total_score = logMath.addAsLinear(total_score, l.getLinkScore());
                                            //msg += lat.getWordFromId(l.wordId) + " || ";
					    
					    JSONvalue val = new JSONvalue(lat.getWordFromId(l.wordId), l.getLinkScore());
					   
					    // TODO: add @|selected|@ before selected word
					    if(sel_word.equals(lat.getWordFromId(l.wordId))){
						//StringBuilder wordbuilder = new StringBuilder(lat.getWordFromId(l.wordId));
						//wordbuilder.insert(0, "@|selected|@");
						val.setTgt("@|selected|@"+val.getTgt());
					    } else {
					    }
					    annot.addValue(val);
					}
					for(JSONvalue v : annot.getValues()){
					    v.setProb(Double.valueOf(logMath.logToLinear(v.getProb() - total_score)).floatValue()); // prob are in log, make the difference in log to normalize, then back to linear space
					    if(DEBUG) logger.info("JSONvalue : "+v.toString());
					}
					    
					//logger.info(msg);
					//logger.info(tok.toString());
					
					annotation.addAnnot(annot);
					sel_word = tok.history.getNewestWord().getSpelling();
                                }
                               
				// remove all annotations
				//ts.getAnnotations().clear();
				// add new annotation
                                //ts.addAnnotation(annotation);
				
				target.addAnnotation(annotation);

                                sentence.getAltTranses().add(last_trans);
				if(DEBUG) logger.info(" #############  END ALTERNATIVES ############## ");  
		} // END of alternatives
		if (DEBUG) logger.info("TokenPassDecoder::decode END ");
		return sb.toString();
	}

	private float setTokenScore(Token tok, float[] lambdas) {
		if(tok == null || lambdas == null) {
			System.err.println("TokenPassDecoder::setTokenScore : null token or lambdas ... exiting!");
			System.exit(0);
			//return -Float.MAX_VALUE;
		}

		float score = 0.0f;
		if(lambdas.length != (3+tok.word_by_sys.length))
		{
			System.err.println("Number of lambdas is not the same as number of feature functions ... exiting!");
			System.exit(0);
		}
		
		//StringBuilder sb = new StringBuilder();
		score += lambdas[0]*tok.lm_score;
		//sb.append(lambdas[0]).append("*").append(tok.lm_score);
		score += lambdas[1]*(-tok.nb_words);
		//sb.append("+").append(lambdas[1]).append("*(-").append(tok.nb_words).append(")");
		score += lambdas[2]*(-tok.nb_nulls);
		//sb.append("+").append(lambdas[2]).append("*(-").append(tok.nb_nulls).append(")");
		
		for(int i=0; i<tok.word_by_sys.length; i++)
		{
			score += lambdas[i+3]*(-tok.word_by_sys[i]);
			//sb.append("+").append(lambdas[i+3]).append("*(-").append(tok.word_by_sys[i]).append(")");
		}
		tok.setScore(score);
		//sb.append(" = ").append(score);
		
		//logger.info("setTokenScore: WORD: " + tok.history.getNewestWord().getSpelling()+" "+tok.toString()+" -> "+sb.toString());

		return score;
	}
	
	private void normalizeTokensScores(TokenList tokens, float maxi) {
		for (Token t : tokens)
			t.norm_score = t.score - maxi;
	}

	public int init_JSON(JSONdocs docs){
	    
            System.err.println("TokenPassDecoder::init_JSON_file: START");
	    
	    // link all lines to its corresponding result (easier to retrieve when decoding)
	    line2json = new Hashtable<Integer, JSONsentence>();

	    int num_line = 0;
	    for(int j=0; j<docs.size(); j++){
	
		// add LIUM-Syscomb phase 
		docs.get(j).removePhases(); // remove all previous phases, since nothing reflects this in the JSON file -> to be confirmed!
		docs.get(j).addPhase(phase);
		//System.err.println("#########: doc -> "+docs.get(j).getName());
		ArrayList<JSONtrans_unit> tus = docs.get(j).getTransUnits();
		for(int k=0; k<tus.size(); k++){
		    JSONtrans_unit tu = tus.get(k);
		    //System.err.println("TU["+k+"] : "+tu.getId());
		    for(final JSONsentence sent : tu.getSentences()){
			    //System.err.println("*********** NEW sentence ");
			    line2json.put(num_line++, sent);
			    /*for(JSONtrans trans : sent.getAltTranses()){
			    	JSONtarget target = trans.getTarget();
				line2json.put(num_line++, sent);
			    }*/
		    }
		}
	    }
	    return 1;
	}
	public void setAlternatives(boolean alt){ alternatives = alt;}

}
