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
import java.io.Writer;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.logging.Logger;
import com.bbn.mt.terp.TERutilities;
import edu.cmu.sphinx.util.props.Configurable;
import edu.cmu.sphinx.util.props.ConfigurationManager;
import edu.cmu.sphinx.util.props.PropertyException;
import edu.cmu.sphinx.util.props.PropertySheet;
import edu.cmu.sphinx.util.props.S4Boolean;
import edu.cmu.sphinx.util.props.S4Component;
import edu.cmu.sphinx.util.props.S4Integer;
import edu.cmu.sphinx.util.props.S4String;
import edu.lium.decoder.Graph;
import edu.lium.decoder.MANYcn;
import edu.lium.decoder.TokenPassDecoder;

import edu.lium.utilities.MANYutilities;

import edu.lium.utilities.json.*;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.io.json.JsonWriter;
import com.thoughtworks.xstream.io.json.*; 
import com.google.gson.*;

public class MANYdecode implements Configurable
{
	public boolean DEBUG = false;
	private String name;
	private Logger logger;
	
	/** The property that defines the token pass decoder component. */
	@S4Component(type = TokenPassDecoder.class)
	public final static String PROP_DECODER = "decoder";
	
	/* The property that defines the output filename */
	@S4String(defaultValue = "many.output")
        public final static String PROP_OUTPUT_FILE = "output";
	
	/** The property that defines the json-list file for alternatives */
	@S4String(defaultValue = "")
	public final static String PROP_ALTERNATIVES_FILE = "alternatives-file";

	/* The property that defines the hypotheses ConfusionNetworks filenames */
	@S4String(defaultValue = "")
        public final static String PROP_HYPOTHESES_CN_FILES = "hypotheses-cn";
	
        /* The property that defines the hypotheses text filenames */
	@S4String(defaultValue = "")
        public final static String PROP_HYPOTHESES_FILES = "hypotheses";
	
        /* The property that defines the hypotheses text filenames */
	@S4String(defaultValue = "")
        public final static String PROP_SOURCE_FILES = "sources";

	/* The property that defines the system priors */
	@S4String(defaultValue = "")
	public final static String PROP_PRIORS = "priors";
	
	@S4Boolean(defaultValue = true)
	public final static String PROP_PRIOR_AS_CONFIDENCE = "priors-as-confidence";
	
	/** The property that defines the number of threads used */
	@S4Integer(defaultValue = 0)
	public final static String PROP_MULTITHREADED = "multithread";


	private String outfile;
	private String hypotheses_cn;   // confusion networks to merge and decode
	private String hypotheses;      // initial systems hypotheses
	private String sources;      // source text
	private String priors_str;
	private boolean priors_as_confidence;
	private boolean mustReWeight = false;
	private boolean allocated;
	//private int nb_threads;
	
	private String[] hyps_cn = null;
	private String[] hyps = null;
	private float[] priors = null;
	
	private TokenPassDecoder decoder = null;
	private JSONdocs docs = null;
	
	public boolean alternatives;
	public String alternatives_file;
	private BufferedWriter alter_bw;
	
	/**
	 * Main method of this MANYdecode tool.
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
		MANYdecode decode;

		try
		{
			URL url = new File(args[0]).toURI().toURL();
			cm = new ConfigurationManager(url);
			decode = (MANYdecode) cm.lookup("MANYDECODE");
            if(args.length == 2) decode.DEBUG = true;
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

		if (decode == null)
		{
			System.err.println("Can't find MANYdecode" + args[0]);
			return;
		}
		
		decode.allocate();
		decode.run();
		decode.deallocate();
	}
	
	private void deallocate()
	{
		decoder.deallocate();
		allocated = false;
	}

	private void allocate()
	{
		allocated = true;
		logger.info("MANYdecode::allocate ...");
		hyps_cn = hypotheses_cn.split("\\s+");
		
		if(! "".equals(hypotheses)){
		    hyps = hypotheses.split("\\s+");
		    // at the moment, only required to load first JSON documents.
		    init_JSON(hyps[0]);
		    decoder.init_JSON(docs);
		}

		String[] lst = priors_str.split("\\s+");
		priors = new float[lst.length];
		
		for (int i = 0; i < lst.length; i++)
		{
			priors[i] = Float.parseFloat(lst[i]);
			//System.err.println(" >"+lst[i]+"< donne >"+priors[i]+"< ");
		}
		logger.info("priors : "+TERutilities.join(" ", priors));
		// init decoder
		try
		{
			decoder.allocate();
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
		//System.err.println("MANYdecode::newProperties ... START");
		if (allocated) {
                    throw new RuntimeException("Can't change properties after allocation");
                }
		
		logger = ps.getLogger();
		decoder = (TokenPassDecoder) ps.getComponent(PROP_DECODER);
		outfile = ps.getString(PROP_OUTPUT_FILE);
		hypotheses_cn = ps.getString(PROP_HYPOTHESES_CN_FILES);
		hypotheses = ps.getString(PROP_HYPOTHESES_FILES);
		sources = ps.getString(PROP_SOURCE_FILES);
		priors_str = ps.getString(PROP_PRIORS);
		priors_as_confidence = ps.getBoolean(PROP_PRIOR_AS_CONFIDENCE);
		if(priors_str.equals("") == false && priors_as_confidence)
			mustReWeight = true;
		//nb_threads = ps.getInt(PROP_MULTITHREADED);
                
		alternatives_file = ps.getString(PROP_ALTERNATIVES_FILE);
		System.err.println("ALT FILE : "+alternatives_file);
                alter_bw = null;
                if (alternatives_file != null && !("".equals(alternatives_file)))
                {
			alternatives = true;
			decoder.setAlternatives(alternatives);
                        try
                        {
                                alter_bw = new BufferedWriter(new FileWriter(alternatives_file));
                        }
                        catch (IOException ioe)
                        {
                                System.err.println("I/O error when creating output file " + String.valueOf(alternatives_file) + " " + ioe);
                        }
                }

		//System.err.println("MANYdecode::newProperties ...END");
	}

	public void run()
	{
		//logger.info("MANYdecode::run");
		//load CNs for each system
		ArrayList<ArrayList<MANYcn>> all_cns = new ArrayList<ArrayList<MANYcn>>(); 
		int nbSentences = -1;
		logger.info("About to load "+hyps_cn.length+" CNs files ... ");
		for(int i=0; i<hyps_cn.length; i++)
		{
			logger.info("Loading CN file : "+hyps_cn[i]);
			ArrayList<MANYcn> fullcns = MANYcn.loadFullCNs(hyps_cn[i]);
			ArrayList<MANYcn> cns = new ArrayList<MANYcn>();
			//if we have to re-weight, then do it !
			if(mustReWeight)
			{
				MANYcn.changeSysWeights(fullcns, priors);
			}
			
			if(DEBUG) MANYcn.outputFullCNs(fullcns,"output.fullcn.reweight."+i);
			cns = MANYcn.fullCNs2CNs(fullcns);
			if(DEBUG) MANYcn.outputCNs(cns,"output.cn.reweight."+i);
			fullcns = null;
			
			all_cns.add(cns);
			
			if(i == 0)
			{
				nbSentences = all_cns.get(i).size();
			}
			else if(nbSentences != all_cns.get(i).size())
			{
				System.err.println("MANYdecode::run : not the same number of hypotheses for each system ... exiting !");
				System.exit(0);
			}
		}
		
		//prepare decoding tasks
		/*ArrayList<DecodeTask> tasks = new ArrayList<DecodeTask>();
		for (int i = 0; i < nbSentences; i++)
		{
			int from = 0; 
			int to = 0;	
			decoder = new TokenPassDecoder(decoder.fudge, decoder.null_penalty, decoder.length_penalty, decoder.maxNbTokens, decoder.nbest_length, 
											decoder.languageModel, decoder.dictionary, decoder.logMath, logger, DEBUG);
			tasks.add(new DecodeTask(i, all_cns, priors, from, to, decoder));
		}*/
		
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
				System.err.println("I/O error when creating output file " + String.valueOf(outfile) + " " + ioe);
			}
		}
		
		ArrayList<MANYcn> cns = new ArrayList<MANYcn>();
		for (int i = 0; i < nbSentences; i++) // foreach sentences
		{
			cns.clear();
			// build a lattice from all the results
			for (int j = 0; j < hyps_cn.length; j++)
			{
				cns.add(all_cns.get(j).get(i));
			}
			
			if(i%100==0) { logger.info("run : decoding graph for sentence "+i); }
			
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

		// all sentences have been processed, print alternatives into alternatives_file

		//XStream xstream = new XStream(new JsonHierarchicalStreamDriver());
		XStream xstream = new XStream(new JsonHierarchicalStreamDriver() {
		        public HierarchicalStreamWriter createWriter(Writer writer) {
			            return new JsonWriter(writer, JsonWriter.DROP_ROOT_MODE);
		        }
		});
		xstream.alias("trans_units", JSONtrans_unit.class);
		xstream.setMode(XStream.NO_REFERENCES);
		String xstreamstr = xstream.toXML(docs);
		//String xstreamstr = xStream.marshal(alt, new CompactWriter(new OutputStreamWriter(stream, encoding)));
		logger.info("TEST XSTREAM : \n"+xstreamstr);
		 
		// sortir les alternatives présentes dans le réseau de confusion
		try
		{
			alter_bw.write(xstreamstr);
			alter_bw.newLine();
			//alter_bw.flush();
			alter_bw.close();
		}
		catch (IOException e)
		{
			System.err.println("TokenPassDecoder : error printing into alternatives file ... : ");
			e.printStackTrace();
			System.exit(0);
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

	public int init_JSON(String file){
	    
            System.err.println("MANYdecode::init_JSON_file: Loading JSON file "+file);
	   
	    // create LIUM-Syscomb phase 
	    JSONphase phase = new JSONphase("LIUM", "SC1", "syscomb");

	    //With GSON
	    Gson gson = new Gson();
	    String file_content = MANYutilities.readFile(file);
	    docs = gson.fromJson(file_content, JSONdocs.class); 
	    return 1;
	}


}
