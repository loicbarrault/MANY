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
package edu.lium.mt;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Logger;
import com.bbn.mt.terp.TERalignment;
import com.bbn.mt.terp.TERalignmentCN;
import com.bbn.mt.terp.TERoutput;
import com.bbn.mt.terp.TERtask;
import com.bbn.mt.terp.TERutilities;
import edu.cmu.sphinx.util.props.Configurable;
import edu.cmu.sphinx.util.props.ConfigurationManager;
import edu.cmu.sphinx.util.props.PropertyException;
import edu.cmu.sphinx.util.props.PropertySheet;
import edu.cmu.sphinx.util.props.S4Component;
import edu.cmu.sphinx.util.props.S4Double;
import edu.cmu.sphinx.util.props.S4Integer;
import edu.cmu.sphinx.util.props.S4String;
import edu.lium.decoder.Graph;
import edu.lium.decoder.MANYcn;
import edu.lium.decoder.TokenPassDecoder;

public class MANY implements Configurable
{
	private String name;
	private Logger logger;
	private TokenPassDecoder decoder = null;

	/** The property that defines the decoder component. */
	@S4Component(type = TokenPassDecoder.class)
	public final static String PROP_DECODER = "decoder";

	/** The property that defines the output filename */
	@S4String(defaultValue = "many.output")
	public final static String PROP_OUTPUT_FILE = "output";

	/** The property that defines the TERp parameters filename */
	@S4String(defaultValue = "terp.params")
	public final static String PROP_TERP_PARAMS_FILE = "terpParams";

	/** The property that defines the reference filename */
	@S4String(defaultValue = "")
	public final static String PROP_REFERENCE_FILE = "reference";

	/** The property that defines the hypotheses filenames */
	@S4String(defaultValue = "")
	public final static String PROP_HYPOTHESES_FILES = "hypotheses";

	/** The property that defines the hypotheses scores filenames */
	@S4String(defaultValue = "")
	public final static String PROP_HYPS_SCORES_FILES = "hyps-scores";

	/** The property that defines the TERp costs 
	@S4String(defaultValue = "1.0 1.0 1.0 1.0 1.0 0.0 1.0")
	public final static String PROP_COSTS = "costs";
	*/
	@S4Double(defaultValue = 1.0)
	public final static String PROP_INS_COST = "insertion";
	
	@S4Double(defaultValue = 1.0)
	public final static String PROP_DEL_COST = "deletion";
	
	@S4Double(defaultValue = 1.0)
	public final static String PROP_SUB_COST = "substitution";
	
	@S4Double(defaultValue = 0.0)
	public final static String PROP_MATCH_COST = "match";
	
	@S4Double(defaultValue = 1.0)
	public final static String PROP_SHIFT_COST = "shift";
	
	@S4Double(defaultValue = 1.0)
	public final static String PROP_STEM_COST = "stem";
	
	@S4Double(defaultValue = 1.0)
	public final static String PROP_SYN_COST = "synonym";

	/** The property that defines the system priors */
	@S4String(defaultValue = "")
	public final static String PROP_PRIORS = "priors";

	/** The property that defines the shift constraint*/
	@S4String(defaultValue = "")
	public final static String PROP_SHIFT_CONSTRAINT = "shift-constraint";

	/** The property that defines the wordnet database location */
	@S4String(defaultValue = "")
	public final static String PROP_WORD_NET = "wordnet";

	/** The property that defines the stop word list filename */
	@S4String(defaultValue = "")
	public final static String PROP_STOP_LIST = "shift-word-stop-list";

	/** The property that defines the paraphrase-table filename */
	@S4String(defaultValue = "")
	public final static String PROP_PARAPHRASES = "paraphrases";

	/** The property that defines the evaluation method (can be MIN, MEAN or MAX) */
	@S4String(defaultValue = "MEAN")
	public final static String PROP_METHOD = "method";

	/** The property that defines the number of threads used */
	@S4Integer(defaultValue = 0)
	public final static String PROP_MULTITHREADED = "multithread";

	private String outfile;
	private String terpParamsFile;
	private String hypotheses;
	private String hypotheses_scores;
	//private String terp_costs;
	private float ins, del, sub, match, shift, stem, syn;
	private String priors_str;
	private boolean mustReWeight = false;
	private String shift_constraint;
	private String wordnet;
	private String shift_word_stop_list;
	private String paraphrases;
	private int nb_threads;
	private boolean allocated;

	private String[] hyps = null, hyps_scores = null;
	private float[] costs = null;
	private float[] priors = null;

    public boolean DEBUG = false;

	/**
	 * Main method of this MTSyscomb tool.
	 * 
	 * @param argv
	 *            argv[0] : config.xml
	 */
	public static void main(String[] args)
	{
		if (args.length < 1 || (args.length == 2 && ("--debug".equals(args[1])==false)) || args.length > 2)
		{
			MANY.usage();
			System.exit(0);
		}


		ConfigurationManager cm;
		MANY syscomb;

		try
		{
			URL url = new File(args[0]).toURI().toURL();
			cm = new ConfigurationManager(url);
			syscomb = (MANY) cm.lookup("MANY");
            if(args.length == 2) syscomb.DEBUG = true;
		}
		catch (IOException ioe)
		{
			System.err.println("I/O error during initialization: \n   " + ioe);
			return;
		}
		catch (PropertyException e)
		{
			System.err.println("Error during initialization: \n  " + e);
			return;
		}

		if (syscomb == null)
		{
			System.err.println("Can't find MANY" + args[0]);
			return;
		}
		
		syscomb.allocate();
		syscomb.combine();
		syscomb.deallocate();
	}

	private void allocate()
	{
		if(!allocated)
		{
			allocated = true;
            System.err.println("hypos = "+hypotheses);
			hyps = hypotheses.split("\\s+");
			hyps_scores = hypotheses_scores.split("\\s+");
			
			//costs = terp_costs.split("\\s+");
			costs = new float[7];
			costs[0] = del;
			costs[1] = stem;
			costs[2] = syn;
			costs[3] = ins;
			costs[4] = sub;
			costs[5] = match;
			costs[6] = shift;
			
			String[] lst = priors_str.split("\\s+");
			priors = new float[lst.length];
			// System.err.println("priors : ");
			for (int i = 0; i < lst.length; i++)
			{
				// System.err.print(" >"+lst[i]+"< donne ");
				priors[i] = Float.parseFloat(lst[i]);
				// System.err.println(" >"+priors_lst[i]+"< ");
			}
		}
	}

	private void deallocate()
	{
		allocated = false;
		hyps = hyps_scores = null;
		costs = null;
		priors = null;
		decoder.deallocate();
	}

	public MANY()
	{

	}

	public void combine()
	{
		int nbSentences = 0;
		ArrayList<String> lst = null;
		ArrayList<String> lst_sc = null;
		String backbone = null;
		String backbone_scores = null;
		int[] hyps_idx;
		ArrayList<TERoutput> outputs = new ArrayList<TERoutput>();

		ArrayList<TERtask> tasks = new ArrayList<TERtask>();
		for (int i = 0; i < hyps.length; i++)
		{
			// 1. Generate CNs with system i as backbone for each sentence
			// 1.1 Init variables
			lst = new ArrayList<String>();
			lst.addAll(Arrays.asList(hyps));
			lst.remove(i);

			lst_sc = new ArrayList<String>();
			lst_sc.addAll(Arrays.asList(hyps_scores));
			lst_sc.remove(i);

			backbone = hyps[i];  
			backbone_scores = hyps_scores[i];

			logger.info("run : " + backbone + " is the reference ....");
			logger.info("run : " + TERutilities.join(" ", lst) + " are the hypotheses ....");

			hyps_idx = new int[lst.size()];
			for (int idx = 0, pos = 0; idx < hyps.length; idx++)
			{
				if (idx != i)
					hyps_idx[pos++] = idx;
			}

			// 1.2 Generate terp.params file
			String suffix = ".thread" + i;
			TERutilities.generateParams(terpParamsFile + suffix, outfile + suffix, backbone, backbone_scores, i, lst, lst_sc, hyps_idx,
					costs, priors, shift_constraint, wordnet, shift_word_stop_list, paraphrases);
			logger.info("TERp params file generated ...");

			tasks.add(new TERtask(i, terpParamsFile + suffix, outfile + ".cn." + i));
		}
		
		if (nb_threads > 1)
		{
			System.err.println("Launching in multithreaded : nb_threads = " + nb_threads);
			ExecutorService executor = Executors.newFixedThreadPool(nb_threads);
			List<Future<TERoutput>> results = null;
			try
			{
				results = executor.invokeAll(tasks);
			}
			catch (InterruptedException ie)
			{
				System.err.println("A task had a problem : " + ie.getMessage());
				ie.printStackTrace();
				System.exit(-1);
			}
			executor.shutdown(); // we don't need executor anymore

			for (int i = 0; i < results.size(); ++i)
			{
				try
				{
					outputs.add(results.get(i).get());

				}
				catch (InterruptedException ie)
				{
					System.err.println("The task " + i + " had a problem : " + ie.getMessage());
					ie.printStackTrace();
					System.exit(-1);
				}
				catch (ExecutionException ee)
				{
					System.err.println("The task " + i + " had a problem ... : " + ee.getMessage());
					ee.printStackTrace();
					System.exit(-1);
				}
			}
		}
		else
		{
			for (int i = 0; i < hyps.length; ++i) // foreach backbone
			{
				logger.info("run : launching TERp for system " + i + " as backbone ...");
				TERoutput output = null;
				
				try
				{
					output = tasks.get(i).call();
				}
				catch (Exception e)
				{
					e.printStackTrace();
				}
				
				if (output == null)
				{
					logger.info("output is null ... exiting !");
					System.exit(-1);
				}
				// else { logger.info("we have an output ...");}

				outputs.add(output);
			}
		}

		// Build the lattice and decode
		// init output
		BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(System.out));
		if (outfile != null)
		{
			try
			{
				bw = new BufferedWriter(new FileWriter(outfile));
			}
			catch (IOException ioe)
			{
				System.err.println("I/O erreur durant creation output file " + String.valueOf(outfile) + " " + ioe);
			}
		}


		// init decoder
		try
		{
			decoder.allocate();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
		
		nbSentences = outputs.get(0).getResults().size();

		ArrayList<ArrayList<MANYcn>> all_cns = new ArrayList<ArrayList<MANYcn>>();
		for (int i = 0; i < nbSentences; i++) // foreach sentences
		{
			all_cns.add(new ArrayList<MANYcn>());
			
			// build a lattice with all the results
			ArrayList<MANYcn> cns = new ArrayList<MANYcn>();
			for (int j = 0; j < outputs.size(); j++)
			{
				TERalignment al = outputs.get(j).getResults().get(i);
				if (al == null)
				{
					logger.info("combine : empty result for system " + j + "... continuing!");
                    all_cns.get(i).add(null);
				}
				else if (al instanceof TERalignmentCN)
				{
					MANYcn cn = new MANYcn(((TERalignmentCN) al).full_cn, ((TERalignmentCN) al).full_cn_scores, ((TERalignmentCN) al).full_cn_sys);
					
					all_cns.get(i).add(cn);
					//if we have to re-weight, then do it !
					if(mustReWeight)
					{
						MANYcn.changeSysWeights(all_cns.get(i), priors);
						if(DEBUG) MANYcn.outputFullCNs(all_cns.get(i),"output.fullcn.reweight."+i);
						
						cns = MANYcn.fullCNs2CNs(all_cns.get(i));
						if(DEBUG) MANYcn.outputCNs(cns,"output.cn.reweight."+i);
					}
				}
				else
				{
					logger.info("combine : not a confusion network ... aborting !");
					System.exit(0);
				}
			}
			
			if (all_cns.get(i).isEmpty())
			{
				logger.info("combine : no result for sentence " + i + "... skipping!");
				try
				{
					bw.newLine();
				}
				catch (IOException ioe)
				{
					ioe.printStackTrace();
					System.exit(0);
				}
				continue;
			}
			
            if(i%100==0) { logger.info("combine : decoding graph for sentence "+i); }

			Graph g = new Graph(cns, priors);
			if(DEBUG) g.printHTK("graph.htk_"+i+".txt");

			// Then we can decode this graph ...
			try
			{
				decoder.decode(i, g, bw);
			}
			catch (IOException ioe)
			{
				ioe.printStackTrace();
				System.exit(0);
			}
		}

		try
		{
			bw.close();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}

	
	// @Override
	public String getName()
	{
		return name;
	}

	@Override
	public void newProperties(PropertySheet ps) throws PropertyException
	{
		logger = ps.getLogger();

		// files
		outfile = ps.getString(PROP_OUTPUT_FILE);
		hypotheses = ps.getString(PROP_HYPOTHESES_FILES);
		hypotheses_scores = ps.getString(PROP_HYPS_SCORES_FILES);

		// decode
		decoder = (TokenPassDecoder) ps.getComponent(PROP_DECODER);
		priors_str = ps.getString(PROP_PRIORS);
		if(priors_str.equals("") == false)
			mustReWeight = true;

		// TERp
		terpParamsFile = ps.getString(PROP_TERP_PARAMS_FILE);
		//terp_costs = ps.getString(PROP_COSTS);
		ins = ps.getFloat(PROP_INS_COST);
		del = ps.getFloat(PROP_DEL_COST);
		sub = ps.getFloat(PROP_SUB_COST);
		match = ps.getFloat(PROP_MATCH_COST);
		shift = ps.getFloat(PROP_SHIFT_COST);
		stem = ps.getFloat(PROP_STEM_COST);
		syn = ps.getFloat(PROP_SYN_COST);
		
		shift_constraint = ps.getString(PROP_SHIFT_CONSTRAINT);
		if("relax".equals(shift_constraint.toLowerCase()))
		{
			wordnet = ps.getString(PROP_WORD_NET);
			shift_word_stop_list = ps.getString(PROP_STOP_LIST);
			paraphrases = ps.getString(PROP_PARAPHRASES);
		}	
		//Others
		nb_threads = ps.getInt(PROP_MULTITHREADED);
	}

	static String[] usage_ar = {"Usage : ", "java -Xmx8G -cp MANY.jar edu.lium.mt.MANY parameters.xml "};
	
	public static void usage()
	{
		System.err.println(TERutilities.join("\n", usage_ar));
	}
}
