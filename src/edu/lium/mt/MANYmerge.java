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
import java.util.logging.Logger;
import com.bbn.mt.terp.TERutilities;
import edu.cmu.sphinx.util.props.Configurable;
import edu.cmu.sphinx.util.props.ConfigurationManager;
import edu.cmu.sphinx.util.props.PropertyException;
import edu.cmu.sphinx.util.props.PropertySheet;
import edu.cmu.sphinx.util.props.S4String;
import edu.lium.decoder.Graph;
import edu.lium.decoder.MANYcn;

public class MANYmerge implements Configurable
{
	public boolean DEBUG = false;
	private String name;
	private Logger logger;
	
	/* The property that defines the output filename */
	@S4String(defaultValue = "many.output")
    public final static String PROP_OUTPUT_FILE = "output";
	
	/* The property that defines the hypotheses ConfusionNetworks filenames */
	@S4String(defaultValue = "")
    public final static String PROP_HYPOTHESES_CN_FILES = "hypotheses-cn";
	
	/* The property that defines the system priors */
	@S4String(defaultValue = "")
	public final static String PROP_PRIORS = "priors";
	
	private String outfile;
	private String hypotheses_cn;
	private String priors_str;
	private boolean mustReWeight = false;
	private boolean allocated;
	
	private String[] hyps_cn = null;
	private float[] priors = null;
	
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
		MANYmerge merge;

		try
		{
			URL url = new File(args[0]).toURI().toURL();
			cm = new ConfigurationManager(url);
			merge = (MANYmerge) cm.lookup("MANYDECODE");
            if(args.length == 2) merge.DEBUG = true;
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

		if (merge == null)
		{
			System.err.println("Can't find MANYdecode" + args[0]);
			return;
		}
		
		merge.allocate();
		merge.run();
		merge.deallocate();
	}
	
	private void deallocate()
	{
		allocated = false;
	}

	private void allocate()
	{
		allocated = true;
		logger.info("MANYmerge::allocate ...");
		hyps_cn = hypotheses_cn.split("\\s+");
		String[] lst = priors_str.split("\\s+");
		priors = new float[lst.length];
		
		for (int i = 0; i < lst.length; i++)
		{
			priors[i] = Float.parseFloat(lst[i]);
			//System.err.println(" >"+lst[i]+"< gives >"+priors[i]+"< ");
		}
		logger.info("priors : "+TERutilities.join(" ", priors));
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
		outfile = ps.getString(PROP_OUTPUT_FILE);
		hypotheses_cn = ps.getString(PROP_HYPOTHESES_CN_FILES);
		priors_str = ps.getString(PROP_PRIORS);
		if(priors_str.equals("") == false)
			mustReWeight = true;
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
			//if we have to re-weight, then do it!
			if(mustReWeight)
			{
				MANYcn.changeSysWeights(fullcns, priors);
				if(DEBUG) MANYcn.outputFullCNs(fullcns,"output.fullcn.reweight."+i);
				
				cns = MANYcn.fullCNs2CNs(fullcns);
				if(DEBUG) MANYcn.outputCNs(cns,"output.cn.reweight."+i);
				
				fullcns = null;
			}
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
				System.err.println("I/O error during creation of output file " + String.valueOf(outfile) + " " + ioe);
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
			
			if(i%100==0) { logger.info("run : generating graph for sentence "+i); }
			
            Graph g = new Graph(cns, priors);
            try
			{
				bw.write(g.getHTKString());
			}
			catch (IOException e)
			{
				e.printStackTrace();
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
}
