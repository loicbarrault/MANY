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
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
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

	/** The property that defines the size of the nbest-list file : BTEC, MOSES */
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

	public TokenPassDecoder()
	{

	}

	public TokenPassDecoder(float lm_weight, float null_penalty, float word_penalty, int maxNbTokens, int nbest_length,
			String nbest_format, LargeNGramModel lm, SimpleDictionary dictionary, LogMath logMath, Logger log,
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
		this.nbest_format = nbest_format;
		this.DEBUG = debug;
		// System.err.println("DEBUG : "+DEBUG);
		for (int i = 0; i < tokens.length; i++)
			tokens[i] = new TokenList();
		lastTokens = new TokenList();
	}

	public TokenPassDecoder(float lm_weight, float null_penalty, float word_penalty, int maxNbTokens, int nbest_length,
			String nbest_format, NetworkLanguageModel lm, SimpleDictionary dictionary, LogMath logMath, Logger log,
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
		logger.info("TokenPassDecoder::allocate");
		dictionary.allocate();
		if (useNGramModel == true)
		{
			logger.info("calling LargeNGramModel::allocate ... ");
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
		logger.info("TokenPassDecoder::allocate OK");
	}
	
	public void deallocate()
	{
		logger.info("TokenPassDecoder::deallocate");
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
            +   ((useNGramModel)?"\n\t - LOCAL LM":"\n\t - LMSERVER") );
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

	public String decode(int sentId, Graph lat)
	{
		if (DEBUG)
			logger.info("TokenPassDecoder::decode START ");
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
		Token t = new Token(null, n, null, null, 0.0f, 0, 0, null);
		tokens[origine].clear();
		tokens[origine + 1].clear();
		lastTokens.clear();

		tokens[origine].add(t);
		
		while (tokens[origine].isEmpty() == false)
		{
			maxScore = -Float.MAX_VALUE;
			for (Token pred : tokens[origine])
			{
				//if(DEBUG) logger.info("pred node = "+pred.node.id);
				// spread tokens
				for (Link l : pred.node.nextLinks)
				{
					String ws = lat.idToWord.get(l.wordId);
					//System.err.println("The word "+ws+" has been proposed by system(s) #"+TERutilities.join(", #",l.sysids));
					// logger.info("## link id="+l.wordId+" w="+ws);
					// logger.info("next node = "+l.endNode.id);
					WordSequence ns = null;
					WordSequence lmns = null;
					Word w = null;
					float nscore = pred.score;
					float lm_score = pred.lm_score;
					int nb_words = pred.nb_words;
					int nb_nulls = pred.nb_nulls;
					int[] word_by_sys = null;
					if(pred.word_by_sys != null)
					{
						//clone does a shallow clone, but has independent storage for primitive 1 dimensional array
						word_by_sys = pred.word_by_sys.clone(); 
					}
					else
					{
						word_by_sys = new int[lat.sys_weights.length];
					}
					
					boolean itsanepsilon = Graph.null_node.equals(ws);
					
					if (pred.history == null) // this is a first link
					{
						if (itsanepsilon)
						{
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
						}
						else // IMPOSSIBLE !
						{
							System.err.println("******************");
							System.err.println("A first link should not convey a word ! (sentId="+sentId+")");
							System.err.println("******************");
							System.exit(0); 
						}
					}
					else
					{
						if (itsanepsilon)
						{
							ns = pred.history.addWord(new Word(ws, null, true), maxHist);
							lmns = pred.lmhistory;
							// logger.info("... Adding "+ws+" (null_node) to the word sequence, -> "+ns.toText()+" [(3) for LM : "+lmns.toText()+" ]");
							if (l.endNode != lat.lastNode) //the last link doesn't count 
							{
								nb_nulls++;
							}
						}
						else
						{
							w = dictionary.getWord(ws);
							if (w == dictionary.getUnknownWord())
							{
								ns = pred.history.addWord(new Word(ws, null, false), maxHist);
							}
							else
							{
								ns = pred.history.addWord(w, maxHist);
							}
							lmns = pred.lmhistory.addWord(new Word(ws, null, false), maxHist);
							// logger.info("... Adding "+ws+" to the word sequence, -> "+ns.toText()+" [(4) for LM : "+lmns.toText()+" ]");

							float lmscore = 0.0f;
							if (useNGramModel == true)
							{
								lmscore = languageModel.getProbability(lmns.withoutWord(Graph.null_node));
								if(DEBUG) 
									logger.info("Proba lm : "+lmscore);
							}
							else
							{
								lmscore = networklm.getProbability(lmns.withoutWord(Graph.null_node));
								if(DEBUG)	
									logger.info("Proba lmonserver : "+lmscore);
							}
							
							lm_score = lm_score + lmscore;
							
							nb_words++;
							
							// logger.info("Link proba : "+l.posterior+" -> en log :"+logMath.linearToLog(l.posterior));
							for (int sysid : l.sysids)
							{
								word_by_sys[sysid]++;
							}
						}
					}

					Token tok = new Token(pred, l.endNode, ns, lmns, lm_score, nb_words, nb_nulls, word_by_sys);
					nscore = setTokenScore(tok, lambdas);
					if (nscore > maxScore)
						maxScore = nscore;

					
					if (l.endNode == lat.lastNode)
					{
						// logger.info("adding "+tok.node.time+" to lastTokens");
						lastTokens.add(tok);
					}
					else
					{
						tokens[cible].add(tok);
					}
				}
			}
			// pruning tokens can be done on the number of tokens or on a prob threshold
			if (tokens[cible].size() > maxNbTokens)
			{
				// taking only maxNbTokens best tokens
				Collections.sort(tokens[cible], Collections.reverseOrder());
				tokens[cible].removeRange(maxNbTokens, tokens[cible].size()); 
			}
			normalizeToken(tokens[cible], maxScore);

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
				logger.info("TokenPassDecoder : BufferedWriter for nbest is null : should not happen ... exiting ! ");
				System.exit(0);
			}

			int nn = Math.min(lastTokens.size(), nbest_length);
			res = new String[nn];

			if (DEBUG)
				logger.info("Looking for " + nn + " best tokens (nbest list format) ...");

			ArrayList<String> obest = new ArrayList<String>();
			for (int nb = 0; nb < nn; nb++)
			{
				obest.clear();
				Token best = lastTokens.get(nb);
				Token current_tok = best;
				
				while (best != null && best.node != lat.firstNode)
				{
					if (DEBUG)
						logger.info(best.history.getNewestWord().getSpelling() + " " + best.node.id + " time="
								+ best.node.time);
					if (Graph.null_node.equals(best.history.getNewestWord().getSpelling()) == false)
					{
						obest.add(0, best.history.getNewestWord().getSpelling());
					}
					else if (DEBUG)
						logger.info("found null_node : " + best.history.getNewestWord().getSpelling());
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
		} // if(nbest_length > 0)

		if (DEBUG)
			logger.info("Looking for best token (1 hypo per line) ...");

		double max = -Double.MAX_VALUE;
		/*
		 * for (Token last : lastTokens) { if (last.score > max) { max =
		 * last.score; best = last; } }
		 */
		Token best = lastTokens.get(0);
		if (DEBUG)
			logger.info("best score: " + max + " best=" + best.node.time);

		// reproducing 1-best
		ArrayList<String> obest = new ArrayList<String>();
		while (best != null && best.node != lat.firstNode)
		{
			if (DEBUG)
				logger
						.info(best.history.getNewestWord().getSpelling() + " " + best.node.id + " time="
								+ best.node.time);
			if (Graph.null_node.equals(best.history.getNewestWord().getSpelling()) == false)
			{
				obest.add(0, best.history.getNewestWord().getSpelling());
			}
			best = best.pred;
		}

		StringBuilder sb = new StringBuilder();
		if (obest.size() > 0)
		{
			sb.append(obest.get(0));
			for (int no = 1; no < obest.size(); no++)
			{
				sb.append(" ").append(obest.get(no));
			}
		}
		return sb.toString();
	}

	private float setTokenScore(Token tok, float[] lambdas)
	{
		if(tok == null || lambdas == null)
			return Float.MAX_VALUE;
		
		float score = 0.0f;
		if(lambdas.length != (3+tok.word_by_sys.length))
		{
			System.err.println("Number of lambdas is not the same as number of feature functions ... exiting!");
			System.exit(0);
		}
		
		score += lambdas[0]*tok.lm_score;
		score += lambdas[1]*(-tok.nb_words);
		score += lambdas[2]*(-tok.nb_nulls);
		
		for(int i=0; i<tok.word_by_sys.length; i++)
		{
			score += lambdas[i+3]*(-tok.word_by_sys[i]);
		}
		tok.setScore(score);
		
		return score;
	}
	
	private void normalizeToken(TokenList lesTokens, double maxi)
	{
		for (Token t : lesTokens)
			t.score -= maxi;
	}

}
