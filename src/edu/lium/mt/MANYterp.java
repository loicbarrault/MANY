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
import com.bbn.mt.terp.TERcomputetask;
import com.bbn.mt.terp.TERoutput;
import com.bbn.mt.terp.TERtask;
import com.bbn.mt.terp.TERutilities;
import edu.cmu.sphinx.util.props.Configurable;
import edu.cmu.sphinx.util.props.ConfigurationManager;
import edu.cmu.sphinx.util.props.PropertyException;
import edu.cmu.sphinx.util.props.PropertySheet;
import edu.cmu.sphinx.util.props.S4Double;
import edu.cmu.sphinx.util.props.S4Integer;
import edu.cmu.sphinx.util.props.S4String;

public class MANYterp implements Configurable
{
	private boolean DEBUG = true;
	private String name;
	private Logger logger;
	
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

	/** The property that defines the Wordnet database location */
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
	private String reference;
	private String hypotheses;
	private String hypotheses_scores;
	
	//private String terp_costs;
	private float ins, del, sub, match, shift, stem, syn;
	private String priors_str;
	private String wordnet;
	private String shift_word_stop_list;
	private String paraphrases;
	private String method;
	private int nb_threads;
	private boolean allocated;
	
	private String ref = null;
	private String[] hyps = null, hyps_scores = null;
	private float[] costs = null;
	private float[] priors = null;

	/**
	 * Main method of this MTSyscomb tool.
	 * 
	 * @param argv
	 *            argv[0] : config.xml
	 */
	public static void main(String[] args)
	{
		if (args.length < 1)
		{
			MANYterp.usage();
			System.exit(0);
		}

		ConfigurationManager cm;
		MANYterp compute;

		try
		{
			URL url = new File(args[0]).toURI().toURL();
			cm = new ConfigurationManager(url);
			compute = (MANYterp) cm.lookup("MANYTERP");
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

		if (compute == null)
		{
			System.err.println("Can't find MANYterp" + args[0]);
			return;
		}
		
		compute.allocate();
		compute.run();
	}

	private void allocate()
	{
		allocated = true;
		ref = reference;
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
			// System.err.println(" >"+priors[i]+"< ");
		}
	}

	public MANYterp()
	{

	}

	public void run()
	{
		double[][] ter_scores = new double[hyps.length][];
		ArrayList<String> lst = null;
		ArrayList<String> lst_sc = null;
		String backbone = null;
		String backbone_scores = null;
		int[] hyps_idx;

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

			logger.info("run : " + backbone + " (" + backbone_scores + ") is the reference ....");
			logger.info("run : " + TERutilities.join(" ", lst) + " (" + TERutilities.join(" ", lst_sc)
					+ ") are the hypotheses ....");

			hyps_idx = new int[lst.size()];
			for (int idx = 0, pos = 0; idx < hyps.length; idx++)
			{
				if (idx != i)
					hyps_idx[pos++] = idx;
			}

			// 1.2 Generate terp.params file
			String suffix = ".thread" + i;
			TERutilities.generateParams(terpParamsFile + suffix, outfile + suffix, backbone, backbone_scores, i, lst, lst_sc, hyps_idx, 
					costs, priors, wordnet, shift_word_stop_list, paraphrases);
			logger.info("TERp params file generated ...");

	
			TERutilities.generateParams(terpParamsFile + ".refalign" + suffix,	outfile + ".refalign" + suffix, ref, lst, lst_sc, 
					costs, wordnet, shift_word_stop_list, paraphrases);
			logger.info("TERp params file for ref alignment prepared for later ...");

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

			ExecutorService ref_executor = Executors.newFixedThreadPool(nb_threads);
			ArrayList<TERcomputetask> computetasks = new ArrayList<TERcomputetask>();
			for (int i = 0; i < results.size(); ++i)
			{
				TERoutput output = null;
				// print this CN in a file named OUTPFX.cn.0, OUTPFX.cn.1 ...
				try
				{
					output = results.get(i).get();

				}
				catch (InterruptedException ie)
				{
					System.err.println("The task " + i + " had a problem : " + ie.getMessage());
					ie.printStackTrace();
					System.exit(-1);
				}
				catch (ExecutionException ee)
				{
					System.err.println("The task " + i + " had a problem : " + ee.getMessage());
					ee.printStackTrace();
					System.exit(-1);
				}
				
				// 2. Calculate TER between theref and CN
				logger.info("run : launching TERp for ref eval with system " + i + " as backbone ...");
				computetasks.add(new TERcomputetask(i, terpParamsFile + ".refalign.thread" + i, output));
			}
			// launch threads
			List<Future<double[]>> results_ref = null;
			try
			{
				results_ref = ref_executor.invokeAll(computetasks);
			}
			catch (InterruptedException ie)
			{
				System.err.println("A task for ref alignment had a problem : " + ie.getMessage());
				ie.printStackTrace();
				System.exit(-1);
			}
			ref_executor.shutdown(); // we don't need ref_executor anymore

			for (int i = 0; i < results_ref.size(); ++i)
			{
				try
				{
					ter_scores[i] = results_ref.get(i).get();
				}
				catch (InterruptedException ie)
				{
					System.err.println("The task " + i + " had a problem : " + ie.getMessage());
					ie.printStackTrace();
					System.exit(-1);
				}
				catch (ExecutionException ee)
				{
					System.err.println("The task " + i + " had a problem : " + ee.getMessage());
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
				catch (Exception e1)
				{
					e1.printStackTrace();
				}
				
				if (output == null)
				{
					logger.info("output is null ... exiting !");
					System.exit(-1);
				}
				// else { logger.info("we have an output ...");}
				
				// 2. Calculate TER between the ref and CN
				logger.info("run : launching TERp for ref eval with system " + i + " as backbone ...");
				try
				{
					ter_scores[i] = new TERcomputetask(i, terpParamsFile + ".refalign.thread" + i, output).call();
				}
				catch (Exception e)
				{
					System.err.println("ERROR : MANYterp::run : ");
					e.printStackTrace();
					System.exit(0);
				}
			}
		}
		// 3. Output scores according to aggregation function (set by 'method'
		// parameter)
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

		logger.info("run : Calculating final score for each sentence (according to 'method' parameter) ...");
		for (int i = 0; i < ter_scores[0].length; i++) // for each sentences
		{
			if (DEBUG)
				System.err.print("Sentence #" + i);

			double sentScore = 0.0;
			if ("MEAN".equals(method)) // average of all scores
			{
				for (int j = 0; j < hyps.length; j++)
				{
					sentScore += ter_scores[j][i];
				}
				sentScore /= hyps.length;
			}
			else if ("MAX".equals(method)) // minimizing the worst case
			{
				sentScore = ter_scores[0][i];
				for (int j = 1; j < hyps.length; j++)
				{
					if (ter_scores[j][i] > sentScore)
						sentScore = ter_scores[j][i];
				}
			}
			else if ("MIN".equals(method)) // get 1 CN with the lowest score
											// possible
			{
				sentScore = ter_scores[0][i];
				for (int j = 1; j < hyps.length; j++)
				{
					if (ter_scores[j][i] < sentScore)
						sentScore = ter_scores[j][i];
				}
			}
			else
			{
				logger.info("run : unknown method '" + method + "' (should be one of MEAN, MAX, MIN) ... aborting !");
				System.exit(0);
			}

			if (DEBUG)
				System.err.println(" score : " + sentScore);

			try
			{
				bw.write("" + sentScore);
				bw.newLine();
			}
			catch (IOException ioe)
			{
				ioe.printStackTrace();
				System.exit(-1);
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
		if (allocated) {
            throw new RuntimeException("Can't change properties after allocation");
        }
		
		logger = ps.getLogger();
		// Files
		reference = ps.getString(PROP_REFERENCE_FILE);
		hypotheses = ps.getString(PROP_HYPOTHESES_FILES);
		hypotheses_scores = ps.getString(PROP_HYPS_SCORES_FILES);
		outfile = ps.getString(PROP_OUTPUT_FILE);

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
		
		wordnet = ps.getString(PROP_WORD_NET);
		shift_word_stop_list = ps.getString(PROP_STOP_LIST);
		paraphrases = ps.getString(PROP_PARAPHRASES);

		// Other
		method = ps.getString(PROP_METHOD);
		priors_str = ps.getString(PROP_PRIORS);
		nb_threads = ps.getInt(PROP_MULTITHREADED);
	}

	// @Override
	/*
	 * public void register(String name, Registry reg) throws PropertyException
	 * { this.name = name; reg.register("terp", PropertyType.COMPONENT);
	 * reg.register(PROP_OUTPUT_FILE, PropertyType.STRING);
	 * reg.register(PROP_TERP_PARAMS_FILE, PropertyType.STRING);
	 * reg.register(PROP_REFERENCE_FILE, PropertyType.STRING);
	 * reg.register(PROP_HYPOTHESES_FILES, PropertyType.STRING);
	 * reg.register(PROP_HYPS_SCORES_FILES, PropertyType.STRING);
	 * reg.register(PROP_COSTS, PropertyType.STRING); reg.register(PROP_PRIORS,
	 * PropertyType.STRING); reg.register(PROP_WORD_NET, PropertyType.STRING);
	 * reg.register(PROP_STOP_LIST, PropertyType.STRING);
	 * reg.register(PROP_PARAPHRASES, PropertyType.STRING);
	 * reg.register(PROP_METHOD, PropertyType.STRING);
	 * reg.register(PROP_MULTITHREADED, PropertyType.INT); }
	 */

	static String[] usage_ar =
	{"Usage : ", "java -Xmx8G -cp MANYterp.jar edu.lium.mt.MANYterp parameters.xml "};
	public static void usage()
	{
		System.err.println(TERutilities.join("\n", usage_ar));
	}

	

}
