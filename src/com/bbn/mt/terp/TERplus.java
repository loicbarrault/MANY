package com.bbn.mt.terp;

/*
 Copyright 2006 by BBN Technologies and University of Maryland (UMD)

 BBN and UMD grant a nonexclusive, source code, royalty-free right to
 use this Software known as Translation Error Rate COMpute (the
 "Software") solely for research purposes. Provided, you must agree
 to abide by the license and terms stated herein. Title to the
 Software and its documentation and all applicable copyrights, trade
 secrets, patents and other intellectual rights in it are and remain
 with BBN and UMD and shall not be used, revealed, disclosed in
 marketing or advertisement or any other activity not explicitly
 permitted in writing.

 BBN and UMD make no representation about suitability of this
 Software for any purposes.  It is provided "AS IS" without express
 or implied warranties including (but not limited to) all implied
 warranties of merchantability or fitness for a particular purpose.
 In no event shall BBN or UMD be liable for any special, indirect or
 consequential damages whatsoever resulting from loss of use, data or
 profits, whether in an action of contract, negligence or other
 tortuous action, arising out of or in connection with the use or
 performance of this Software.

 Without limitation of the foregoing, user agrees to commit no act
 which, directly or indirectly, would violate any U.S. law,
 regulation, or treaty, or any other international treaty or
 agreement to which the United States adheres or with which the
 United States complies, relating to the export or re-export of any
 commodities, software, or technical data.  This Software is licensed
 to you on the condition that upon completion you will cease to use
 the Software and, on request of BBN and UMD, will destroy copies of
 the Software in your possession.                                                

 */
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import com.bbn.mt.terp.phrasedb.PhraseDB;
import edu.cmu.sphinx.util.props.Configurable;
import edu.cmu.sphinx.util.props.ConfigurationManager;
import edu.cmu.sphinx.util.props.PropertyException;
import edu.cmu.sphinx.util.props.PropertySheet;
import edu.cmu.sphinx.util.props.S4String;

public class TERplus implements Configurable
{
	
	/** The property that defines the paraphrase-table filename */
	@S4String(defaultValue = "terp.params")
	public final static String PROP_TERP_PARAMS = "terpParams";
	
	private String name;
	public Logger logger;
	private String terpParams = null;
	
	private boolean DEBUG = false;
	
	private boolean write_to_disk = true;
	private TERcost _costfunc = null;
	private TERcalcCN calc = null;
	
	private TERinput terinput = null;
	private TERinputScores terinput_scores = null;
	
	private PhraseDB phrasedb = null;
	// Variables for Timing Information
	private double InitPTElapsedSec = 0.0;
	private double InitElapsedSec = 0.0;
	private double OutputElapsedSec = 0.0;
	private double OverallElapsedSec = 0.0;
	private int numScored = 0;
	
	private TERpara params = null;
	
	public static void main(String[] args)
	{
		if (args.length < 1)
		{
			TERplus.usage();
			System.exit(0);
		}

		ConfigurationManager cm;
		TERplus terplus;

		try
		{
			URL url = new File(args[0]).toURI().toURL();
			cm = new ConfigurationManager(url);
			terplus = (TERplus) cm.lookup("terp");
		} catch (IOException ioe)
		{
			System.err.println("I/O error during initialization: \n   " + ioe);
			return;
		}
		catch (PropertyException e)
		{
			System.err.println("Error during initialization: \n  " + e);
			return;
		}

		/*
		 * try { ConfigMonitor config = (ConfigMonitor)
		 * cm.lookup("configMonitor"); config.run();// peut etre faut-il faire
		 * un thread } catch (InstantiationException e) {
		 * System.err.println("Error during config: \n  " + e); // return; }
		 * catch (PropertyException e) {
		 * System.err.println("Error during config: \n  " + e); // return; }
		 */

		if (terplus == null)
		{
			System.err.println("Can't find TERplus " + args[0]);
			return;
		}
		
		terplus.allocate();
		terplus.run();
	}

	public void allocate()
	{
		String[] paramFile = {"-p", terpParams};
		//System.err.println("TERplus : allocate : calling getOpts >"+TERutilities.join("|", params)+"<");
		if(params == null)
			params = new TERpara(paramFile);
		else
			params.getOpts(paramFile);
	}

	public void deallocate()
	{
		params.deallocate();
	}
	
	public TERplus()
	{
		write_to_disk = true;
	}

	public TERplus(boolean write_to_disk)
	{
		this.write_to_disk = write_to_disk;
	}

	public TERpara getParams()
	{
		return params;
	}

	public TERcost getLastCostFunc()
	{
		return _costfunc;
	}

	public TERcalc getLastTERpCalc()
	{
		return calc;
	}

	public TERinput getLastTERpInput()
	{
		return terinput;
	}

	public PhraseDB getLastPhraseDB()
	{
		return phrasedb;
	}

	// Rerunning does not create a new calc, costfunc, or input
	public TERoutput rerun()
	{
		return run(true, true, false);
	}

	// Load everything from scratch
	public TERoutput run()
	{
		if (params.para().get_boolean(TERpara.OPTIONS.CREATE_CONFUSION_NETWORK))
		{
			//logger.info("Creating confusion network ...");
			return run_cn(false, true, true);
		}
		//logger.info("Creating classical pair-wise TER alignment ...");
		return run(false, true, true);
	}

	public void set_weights(double[] wgts)
	{
		if (_costfunc == null)
			init();
		_costfunc.set_weights(wgts);
	}

	public void init()
	{
		init(true);
	}

	public void init(boolean load_phrases)
	{
		run(false, false, load_phrases);
	}
	
	private TERoutput run(boolean rerun, boolean evaldata, boolean load_phrases)
	{
		long OverallStartTime = System.nanoTime();
		long InitStartTime = System.nanoTime();
		String ref_fn = params.para().get_string(TERpara.OPTIONS.REF);
		String[] hyp_fn = params.para().get_stringlist(TERpara.OPTIONS.HYP);
		String reflen_fn = params.para().get_string(TERpara.OPTIONS.REFLEN);
		String out_pfx = params.para().get_string(TERpara.OPTIONS.OUTPFX);
		ArrayList<String> formats = new ArrayList<String>(Arrays.asList(params.para().get_stringlist(TERpara.OPTIONS.FORMATS)));
		boolean caseon = params.para().get_boolean(TERpara.OPTIONS.CASEON);
		WordNet.setWordNetDB(params.para().get_string(TERpara.OPTIONS.WORDNET_DB_DIR));
		NormalizeText.init(params);

		// 2. init variables
		double TOTAL_EDITS = 0.0;
		double TOTAL_WORDS = 0.0;
		if (!rerun)
		{
			terinput = new TERinput(hyp_fn[0], ref_fn, reflen_fn, params);
			//.para().get_boolean(TERpara.OPTIONS.IGNORE_SETID)
		}

		// 3. verify input formats
		if (!verifyFormats(terinput.in_ref_format(), terinput.in_hyp_format(), formats))
			System.exit(1);

		// 4. init calculation variables
		TERoutput[] iouts = null;

		if (params.para().get_boolean(TERpara.OPTIONS.SHOW_ALL_REFS))
		{
			iouts = new TERoutput[terinput.max_num_refs()];
			String bpfx = "";
			if (out_pfx.endsWith(".") && (out_pfx.length() == 0) || out_pfx.endsWith("/"))
			{
				bpfx = "ref";
			} else
			{
				bpfx = ".ref";
			}

			for (int i = 0; i < iouts.length; i++)
			{
				iouts[i] = new TERoutput(out_pfx, formats, hyp_fn[0], ref_fn, reflen_fn, caseon, terinput, params);
				iouts[i].refname = bpfx + i;
			}
		}
		TERoutput terout = new TERoutput(out_pfx, formats, hyp_fn[0], ref_fn, reflen_fn, caseon, terinput, params);

		if (!rerun)
		{
			if (load_phrases)
			{
				System.out.println("Creating Segment Phrase Tables From DB");
				String pt_db = params.para().get_string(TERpara.OPTIONS.PHRASE_DB);
				if ((pt_db != null) && (!pt_db.equals("")))
				{
					this.phrasedb = new PhraseDB(pt_db);
				} else
				{
					this.phrasedb = null;
				}
				
			}
			this.calc = init_tercalc(terinput);
			// if (load_phrases) loadPTdb(terinput);
		}

		if (!evaldata)
			return null;

		int curScored = 0;
		int totalToScore = 0;
		for (TERid pid : terinput.getPlainIds())
		{
			for (String sysid : terinput.get_sysids())
			{
				for (TERid tid : terinput.getHypIds(sysid, pid))
				{
					if ((params.para().get_boolean(TERpara.OPTIONS.IGNORE_MISSING_HYP)) && (!terinput.hasHypSeg(tid)))
						continue;
					totalToScore++;
				}
			}
		}

		// 5. compute TER
		long InitEndTime = System.nanoTime();
		long InitElapsedTime = InitEndTime - InitStartTime;
		this.InitElapsedSec += (double) InitElapsedTime / 1.0E09;

		for (TERid pid : terinput.getPlainIds())
		{
			// Prep as much as we can for this plain ID (useful if we have
			// multiple systems or nbest lists)
			List<String[]> refs = terinput.getRefForHypTok(pid);
			List<String[]> lens = terinput.getLenForHypTok(pid);
			List<String> origrefs = terinput.getRefForHyp(pid);

			String[][] refs_ar = refs.toArray(new String[0][0]);
			String[][] lens_ar = null;
			if (lens != null)
			{
				lens_ar = lens.toArray(new String[0][0]);
			}
			calc.setRefLen(lens_ar);
			boolean b = calc.init(pid);
			if (b == false)
			{
				System.err.println("Unable to initialize phrase table to id: " + pid);
				System.exit(-1);
			}

			for (String sysid : terinput.get_sysids())
			{
				for (TERid tid : terinput.getHypIds(sysid, pid))
				{
					if ((params.para().get_boolean(TERpara.OPTIONS.IGNORE_MISSING_HYP)) && (!terinput.hasHypSeg(tid)))
					{
						continue;
					}
					List<String[]> hyps = terinput.getAllHypTok(tid);
					List<String> orighyps = terinput.getAllHyp(tid);
					curScored++;
					System.out.println("Processing " + tid);
					if (params.para().get_boolean(TERpara.OPTIONS.VERBOSE))
					{
						PhraseTable pt = calc.getCostFunc().getPhraseTable();
						if (pt != null)
						{
							System.out.printf("  PT Depth=%d Nodes=%d Terms=%d Entries=%d\n", pt.curTreeDepth(), pt
									.curTreeNumNodes(), pt.curTreeNumTerms(), pt.curTreeNumEntries());
							System.out.printf("  Segment %d of %d (%.1f%%)\n", curScored, totalToScore,
									(100.0 * curScored / totalToScore));
						}
					}
					if (hyps.size() > 1)
					{
						System.out.println("WARNING: Multiple hypotheses found for system " + sysid + " segment " + tid
								+ ".  Only scoring first segment.");
					}
					String[] hyp = hyps.get(0);

					if ((refs.size() == 0) || ((refs.size() == 1) && (refs.get(0).length == 0)))
					{
						logger.info("WARNING: Blank or empty reference for segment: " + tid);
					}

					if (hyp.length == 0)
					{
						logger.info("WARNING: Blank or empty hypothesis for segment: " + tid);
					}

					long startTime = System.nanoTime();
					TERalignment[] irefs = null;
					if ((Boolean) params.para().get(TERpara.OPTIONS.SHOW_ALL_REFS))
						irefs = new TERalignment[refs.size()];

					TERalignment result = score_all_refs(hyp, refs_ar, calc, irefs, orighyps, origrefs);
					TOTAL_EDITS += result.numEdits;
					TOTAL_WORDS += result.numWords;

					long endTime = System.nanoTime();
					long elapsedTime = endTime - startTime;
					double eTsec = elapsedTime / 1.0E09;
					if (params.para().get_boolean(TERpara.OPTIONS.VERBOSE))
					{
						System.out.printf("  Time: %.3f sec.  Score: %.2f (%.2f / %.2f)\n", eTsec, result.score(),
								result.numEdits, result.numWords);
					}
					numScored++;
					if ((Boolean) params.para().get(TERpara.OPTIONS.SHOW_ALL_REFS))
					{
						for (int i = 0; i < irefs.length; i++)
						{
							if (i < iouts.length)
							{
								iouts[i].add_result(tid, irefs[i]);
							}
						}
					}
					terout.add_result(tid, result);
				}
			}
		}
		if(DEBUG)
			System.out.println("Finished Calculating TERp");
		long OutputStartTime = System.nanoTime();
		if (this.write_to_disk)
		{
			if (params.para().get_boolean(TERpara.OPTIONS.VERBOSE))
			{
				System.out.println("Writing output to disk.");
			}
			terout.output();
			if ((Boolean) params.para().get(TERpara.OPTIONS.SHOW_ALL_REFS))
			{
				for (int i = 0; i < iouts.length; i++)
				{
					iouts[i].output();
				}
			}
		}
		long OutputEndTime = System.nanoTime();
		long OutputElapsedTime = OutputEndTime - OutputStartTime;
		this.OutputElapsedSec += (double) OutputElapsedTime / 1.0E09;
		long OverallEndTime = System.nanoTime();
		long OverallElapsedTime = OverallEndTime - OverallStartTime;
		this.OverallElapsedSec += (double) OverallElapsedTime / 1.0E09;

		if (params.para().get_boolean(TERpara.OPTIONS.VERBOSE))
		{
			System.out.println(calc.get_info());
			System.out.println(NormalizeText.get_info());
			showTime();
		}
		//System.out.printf("Total TER: %.2f (%.2f / %.2f)\n", (TOTAL_EDITS / TOTAL_WORDS), TOTAL_EDITS, TOTAL_WORDS);
		logger.info(String.format("Total TER: %.2f (%.2f / %.2f)\n", (TOTAL_EDITS / TOTAL_WORDS), TOTAL_EDITS, TOTAL_WORDS));
		return terout;
	}

	private TERoutput run_cn(boolean rerun, boolean evaldata, boolean load_phrases)
	{
		long OverallStartTime = System.nanoTime();
		long InitStartTime = System.nanoTime();
		String ref_fn = params.para().get_string(TERpara.OPTIONS.REF);
		String ref_scores_fn = params.para().get_string(TERpara.OPTIONS.REF_SCORES);
		String[] hyp_fn = params.para().get_stringlist(TERpara.OPTIONS.HYP);
		String[] hyp_scores_fn = params.para().get_stringlist(TERpara.OPTIONS.HYP_SCORES);
		String reflen_fn = params.para().get_string(TERpara.OPTIONS.REFLEN);
		String out_pfx = params.para().get_string(TERpara.OPTIONS.OUTPFX);
		ArrayList<String> formats = new ArrayList<String>(Arrays.asList(params.para().get_stringlist(
				TERpara.OPTIONS.FORMATS)));
		boolean caseon = params.para().get_boolean(TERpara.OPTIONS.CASEON);
		
		WordNet.setWordNetDB(params.para().get_string(TERpara.OPTIONS.WORDNET_DB_DIR));
		NormalizeText.init(params);
		
		// 2. init variables
		double TOTAL_EDITS = 0.0;
		double TOTAL_WORDS = 0.0;
		if (!rerun)
		{
			//terinput_scores = new TERinputScores(hyp_fn, hyp_scores_fn, ref_fn, ref_scores_fn, reflen_fn, params.para().get_boolean(TERpara.OPTIONS.IGNORE_SETID));
			terinput_scores = new TERinputScores(hyp_fn, hyp_scores_fn, ref_fn, ref_scores_fn, reflen_fn, params);
		}
		// 3. verify input formats
		if (!verifyFormats(terinput_scores.in_ref_format(), terinput_scores.in_hyp_format(), formats))
			System.exit(1);
		
		// 4. init calculation variables
		
		//TERoutput terout = new TERoutput(out_pfx, formats, hyp_fn, ref_fn, reflen_fn, caseon, terinput);
		TERoutput terout = new TERoutput(out_pfx, formats, hyp_fn, ref_fn, reflen_fn, caseon, terinput_scores, params);
		if (!rerun)
		{
			if (load_phrases)
			{
				//System.out.println("Creating Segment Phrase Tables From DB");
				String pt_db = params.para().get_string(TERpara.OPTIONS.PHRASE_DB);
				if ((pt_db != null) && (!pt_db.equals("")))
				{
					this.phrasedb = new PhraseDB(pt_db);
				} else
				{
					this.phrasedb = null;
				}
			}
			this.calc = init_tercalc(terinput_scores);
			// if (load_phrases) loadPTdb(terinput);
		}
		if (!evaldata)
			return null;
		int totalToScore = 0;
		for (TERid pid : terinput_scores.getPlainIds())
		{
			for (String sysid : terinput_scores.get_sysids())
			{
				for (TERid tid : terinput_scores.getHypIds(sysid, pid))
				{
					if ((params.para().get_boolean(TERpara.OPTIONS.IGNORE_MISSING_HYP)) && (!terinput_scores.hasHypSeg(tid)))
						continue;
					totalToScore++;
				}
			}
		}
		// 5. compute TER
		long InitEndTime = System.nanoTime();
		long InitElapsedTime = InitEndTime - InitStartTime;
		this.InitElapsedSec += (double) InitElapsedTime / 1.0E09;
		
		int npid = 0;
		for (TERid pid : terinput_scores.getPlainIds())
		{
			if(npid % 100 == 0)
			{
				logger.info("Processing sentence #"+npid+" : "+pid);
				//System.err.println("TERplus::run_cn : Processing sentence #"+npid+" : "+pid);
			}
			npid++;
			// Prep as much as we can for this plain ID (useful if we have
			// multiple systems or nbest lists)
			List<String[]> refs = terinput_scores.getRefForHypTok(pid);
			List<float[]> refs_scores = terinput_scores.getRefScoresForHypTok(pid);
			
			List<String[]> lens = terinput_scores.getLenForHypTok(pid);
			List<String> origrefs = terinput_scores.getRefForHyp(pid);
			String[][] refs_ar = refs.toArray(new String[0][0]);
			String[][] lens_ar = null;
			if (lens != null)
			{
				lens_ar = lens.toArray(new String[0][0]);
			}
			calc.setRefLen(lens_ar);
			boolean b = calc.init(pid);
			if (b == false)
			{
				System.err.println("Unable to initialize phrase table to id: " + pid);
				System.exit(-1);
			}

			// we must have 1 ref and many hyps
			if (refs.size() > 1)
			{
				logger.info("WARNING run_cn : Multiple ref detected ... only using first one");
			}

			// get all hypotheses with this plainID
			
			List<String[]> hyps = terinput_scores.getAllHypsTok(pid);
			List<float[]> hyps_scores = terinput_scores.getAllHypsScoresTok(pid);
			List<String> orighyps = terinput_scores.getAllHyps(pid);

			if ((refs.size() == 0) || ((refs.size() == 1) && (refs.get(0).length == 0)))
			{
				logger.info("WARNING run_cn : Blank or empty reference for segment: " + pid.id + " ... skipping !");
				numScored++;
				
				//terout.add_result(pid.getPlainIdent(), calc.getFakeTERAlignmentCN());
				terout.add_result(pid.getPlainIdent(), null);
				continue;
			}
			else if(DEBUG)
			{
				for(int i=0; i<refs.size(); i++)
				{
					System.err.print("REF[" + i + "] = ");
					System.err.println(TERutilities.join(" ", refs.get(i)));
					System.err.print("SCORES = ");
					System.err.println(TERutilities.join(" ", refs_scores.get(i)));
				}
			}
			if (hyps.size() == 0 || ((hyps.size() == 1) && (hyps.get(0).length == 0)))
			{
				logger.info("WARNING run_cn : no hypothesis for segment: " + pid.id);
			}
			else if(DEBUG)
			{
				for(int i=0; i<hyps.size(); i++)
				{
					System.err.print("HYP[" + i + "] = ");
					System.err.println(TERutilities.join(" ", hyps.get(i)));
					System.err.print("SCORES = ");
					System.err.println(TERutilities.join(" ", hyps_scores.get(i)));
				}
			}

			// OK we have all what we need ... let's build !
			long startTime = System.nanoTime();
			//logger.info("TERplus::run_cn : calling incremental_build_cn");
			//System.err.println("TERplus::run_cn : calling incremental_build_cn");
			TERalignmentCN result = incremental_build_cn(hyps, hyps_scores, refs_ar[0], refs_scores.get(0), calc, orighyps, origrefs);
			//logger.info("TERplus::run_cn : incremental_build_cn DONE !!");
			//System.err.println("TERplus::run_cn : incremental_build_cn DONE !!");
			if(result == null)
			{
				logger.info("result is null ... exiting !");
				System.exit(0);
			}
			
			TOTAL_EDITS += result.numEdits;
			TOTAL_WORDS += result.numWords;
			long endTime = System.nanoTime();
			long elapsedTime = endTime - startTime;
			double eTsec = elapsedTime / 1.0E09;
			if (params.para().get_boolean(TERpara.OPTIONS.VERBOSE))
			{
				System.out.printf("  Time: %.3f sec.  Score: %.2f (%.2f / %.2f)\n", eTsec, result.score(),
						result.numEdits, result.numWords);
			}
			numScored++;
			terout.add_result(pid.getPlainIdent(), result);
		}
		if(DEBUG) System.out.println("Finished Calculating TERp");
		long OutputStartTime = System.nanoTime();
		if (this.write_to_disk)
		{
			if (params.para().get_boolean(TERpara.OPTIONS.VERBOSE))
			{
				System.out.println("Writing output to disk.");
			}
			logger.info("Writing output to disk.");
			terout.output();
			//terout.output_all();
		}
		long OutputEndTime = System.nanoTime();
		long OutputElapsedTime = OutputEndTime - OutputStartTime;
		this.OutputElapsedSec += (double) OutputElapsedTime / 1.0E09;
		long OverallEndTime = System.nanoTime();
		long OverallElapsedTime = OverallEndTime - OverallStartTime;
		this.OverallElapsedSec += (double) OverallElapsedTime / 1.0E09;
		if (params.para().get_boolean(TERpara.OPTIONS.VERBOSE))
		{
			System.out.println(calc.get_info());
			System.out.println(NormalizeText.get_info());
			showTime();
		}
		
		//System.out.printf("Total TER: %.2f (%.2f / %.2f)", (TOTAL_EDITS / TOTAL_WORDS), TOTAL_EDITS, TOTAL_WORDS);
		logger.info(String.format("Total TER: %.2f (%.2f / %.2f)", (TOTAL_EDITS / TOTAL_WORDS), TOTAL_EDITS, TOTAL_WORDS));
		
		return terout;
	}
	
	
	public double[] compute_ter(TERoutput out)
	{
		long OverallStartTime = System.nanoTime();
		long InitStartTime = System.nanoTime();
		String ref_fn = params.para().get_string(TERpara.OPTIONS.REF);
		String ref_scores_fn = params.para().get_string(TERpara.OPTIONS.REF_SCORES);
		String[] hyp_fn = params.para().get_stringlist(TERpara.OPTIONS.HYP);
		String[] hyp_scores_fn = params.para().get_stringlist(TERpara.OPTIONS.HYP_SCORES);
		String reflen_fn = params.para().get_string(TERpara.OPTIONS.REFLEN);
		String out_pfx = params.para().get_string(TERpara.OPTIONS.OUTPFX);
		ArrayList<String> formats = new ArrayList<String>(Arrays.asList(params.para().get_stringlist(
				TERpara.OPTIONS.FORMATS)));
		boolean caseon = params.para().get_boolean(TERpara.OPTIONS.CASEON);
		WordNet.setWordNetDB(params.para().get_string(TERpara.OPTIONS.WORDNET_DB_DIR));
		NormalizeText.init(params);
		
		// 2. init variables
		double TOTAL_EDITS = 0.0;
		double TOTAL_WORDS = 0.0;
		
		//terinput_scores = new TERinputScores(hyp_fn, hyp_scores_fn, ref_fn, ref_scores_fn, reflen_fn, params.para().get_boolean(TERpara.OPTIONS.IGNORE_SETID));
		terinput_scores = new TERinputScores(hyp_fn, hyp_scores_fn, ref_fn, ref_scores_fn, reflen_fn, params);
		
		// 3. verify input formats
		if (!verifyFormats(terinput_scores.in_ref_format(), terinput_scores.in_hyp_format(), formats))
		{
			System.err.println("REF format : "+terinput_scores.in_ref_format()+" and HYP format : "+terinput_scores.in_hyp_format());
			System.exit(1);
		}
		
		// 4. init calculation variables
		TERoutput terout = new TERoutput(out_pfx, formats, hyp_fn, ref_fn, reflen_fn, caseon, terinput_scores, params);
		//System.out.println("Creating Segment Phrase Tables From DB");
		String pt_db = params.para().get_string(TERpara.OPTIONS.PHRASE_DB);
		if ((pt_db != null) && (!pt_db.equals("")))
		{
			this.phrasedb = new PhraseDB(pt_db);
		} else
		{
			this.phrasedb = null;
		}
		this.calc = init_tercalc(terinput_scores);
		
		double[] ter_scores = new double[out.getIds().size()];
		
		//for each sentence
		int npid = 0;
		for (TERid pid : out.getIds())
		{
			if(npid % 100 == 0)
			{
				logger.info("Processing sentence #"+npid+" : "+pid);
				//System.err.println("TERplus::run_cn : Processing sentence #"+npid+" : "+pid);
			}
			
			TERalignmentCN al = (TERalignmentCN) (out.getResult(pid));
			
			if(al == null || al.cn == null)
			{
				logger.info("compute_ter : empty result for sentence "+pid+"... continuing !");
				numScored++;
				terout.add_result(pid.getPlainIdent(), calc.getFakeTERAlignmentCN());
				ter_scores[npid] = 1.0; // 1.0 is max TER
				npid++;
				continue;
			}
			
				
			// 5. compute TER
			long InitEndTime = System.nanoTime();
			long InitElapsedTime = InitEndTime - InitStartTime;
			this.InitElapsedSec += (double) InitElapsedTime / 1.0E09;
			
			// Prep as much as we can for this plain ID (useful if we have
			// multiple systems or nbest lists)
			List<String[]> refs = terinput_scores.getRefForHypTok(pid);
			List<float[]> refs_scores = terinput_scores.getRefScoresForHypTok(pid);
			
			List<String[]> lens = terinput_scores.getLenForHypTok(pid);
			//List<String> origrefs = terinput_scores.getRefForHyp(pid);
			String[][] refs_ar = refs.toArray(new String[0][0]);
			String[][] lens_ar = null;
			if (lens != null)
			{
				lens_ar = lens.toArray(new String[0][0]);
			}
			calc.setRefLen(lens_ar);
			boolean b = calc.init(pid);
			if (b == false)
			{
				System.err.println("Unable to initialize phrase table to id: " + pid);
				System.exit(-1);
			}
	
			// we must have 1 ref and many hyps
			if (refs.size() > 1)
			{
				System.err.println("WARNING compute_ter : Multiple ref detected ("+refs.size()+")... only using first one");
				for(int n=0; n<refs.size(); n++)
				{
					//System.err.println("\tref["+n+"] = "+TERutilities.join(" ", refs.get(n)));
                    logger.info("\tref["+n+"] = "+TERutilities.join(" ", refs.get(n)));
				}
			}
	
			if ((refs.size() == 0) || ((refs.size() == 1) && (refs.get(0).length == 0)))
			{
				System.out.println("WARNING computer_ter : Blank or empty reference for segment: " + pid.id + " ... skipping !");
				numScored++;
				terout.add_result(pid.getPlainIdent(), calc.getFakeTERAlignmentCN());
				ter_scores[npid] = 1.0; // 1.0 is max TER
				npid++;
				continue;
			}
			else if(DEBUG)
			{
				for(int i=0; i<refs.size(); i++)
				{
					System.err.print("REF[" + i + "] = ");
					System.err.println(TERutilities.join(" ", refs.get(i)));
					System.err.print("SCORES = ");
					System.err.println(TERutilities.join(" ", refs_scores.get(i)));
				}
			}
			
			// OK we have all what we need ... let's compute terp !
			long startTime = System.nanoTime();
			TERalignmentCN align = calc.compute_ter(al.cn, refs_ar[0]);
			
			TOTAL_EDITS += align.numEdits;
			TOTAL_WORDS += align.numWords;
			long endTime = System.nanoTime();
			long elapsedTime = endTime - startTime;
			double eTsec = elapsedTime / 1.0E09;
			if (params.para().get_boolean(TERpara.OPTIONS.VERBOSE))
			{
				System.out.printf("  Time: %.3f sec.  Score: %.2f (%.2f / %.2f)\n", eTsec, align.score(),
						align.numEdits, align.numWords);
			}
			numScored++;
			terout.add_result(pid.getPlainIdent(), align);
			
			
			TERalignmentCN final_al = (TERalignmentCN) (terout.getResult(pid));
			if(final_al == null)
			{
				logger.info("compute_ter : empty final result for sentence "+pid+"... continuing !");
				ter_scores[npid] = 1.0; // 1.0 is max TER
			}
			else if (final_al instanceof TERalignmentCN)
			{
				ter_scores[npid] = final_al.score();
				if(DEBUG)
					logger.info("\tSystem "+pid+" - Sentence "+npid+" : "+ter_scores[npid]);
			}
			else
			{
				logger.info("run : not a confusion network ... aborting !");
				System.exit(0);
			}
			npid++;
		}
		
		if(DEBUG) System.err.println("Finished Calculating TERp");
		long OutputStartTime = System.nanoTime();
		if (this.write_to_disk)
		{
			if (params.para().get_boolean(TERpara.OPTIONS.VERBOSE))
			{
				System.out.println("Writing output to disk.");
			}
			terout.output_ter_scores();
		}
	
		long OutputEndTime = System.nanoTime();
		long OutputElapsedTime = OutputEndTime - OutputStartTime;
		this.OutputElapsedSec += (double) OutputElapsedTime / 1.0E09;
		long OverallEndTime = System.nanoTime();
		long OverallElapsedTime = OverallEndTime - OverallStartTime;
		this.OverallElapsedSec += (double) OverallElapsedTime / 1.0E09;
		if (params.para().get_boolean(TERpara.OPTIONS.VERBOSE))
		{
			System.out.println(calc.get_info());
			System.out.println(NormalizeText.get_info());
			showTime();
		}
		//System.out.printf("Total TER: %.2f (%.2f / %.2f)\n", (TOTAL_EDITS / TOTAL_WORDS), TOTAL_EDITS, TOTAL_WORDS);
		logger.info(String.format("End of compute_ter : Total TER: %.2f (%.2f / %.2f)", (TOTAL_EDITS / TOTAL_WORDS), TOTAL_EDITS, TOTAL_WORDS));
		
		return ter_scores;
	}
	

	public void loadPTdb(PhraseTable pt, TERinput terinput, TERcalc calc)
	{
		if (this.phrasedb == null)
			return;
		long InitPTStartTime = System.nanoTime();
		this.phrasedb.openDB();
		for (TERid pid : terinput.getPlainIds())
		{
			// Prep as much as we can for this plain ID (useful if we have
			// multiple systems or nbest lists)
			List<String[]> refs = terinput.getRefForHypTok(pid);
			String[][] refs_ar = refs.toArray(new String[0][0]);
			List<String[]> allTokHyps = terinput.getHypForPlainTok(pid);
			if (allTokHyps != null)
			{
				String[][] allTokHyps_ar = allTokHyps.toArray(new String[0][0]);
				pt.add_phrases(refs_ar, allTokHyps_ar, pid);
			}
		}
		this.phrasedb.closeDB();
		long InitPTEndTime = System.nanoTime();
		long InitPTElapsedTime = InitPTEndTime - InitPTStartTime;
		if (params.para().get_boolean(TERpara.OPTIONS.VERBOSE))
			System.out.printf("  Time: %.3f sec\n", (double) InitPTElapsedTime / 1.0E09);
		this.InitPTElapsedSec += (double) InitPTElapsedTime / 1.0E09;
	}

	private TERcost terCostFactory()
	{
		TERcost costfunc = null;
		if ((params.para().get(TERpara.OPTIONS.WORD_CLASS_FNAME) == null)
				|| (params.para().get(TERpara.OPTIONS.WORD_CLASS_FNAME).equals("")))
		{
			if ((Boolean) params.para().get(TERpara.OPTIONS.GENERALIZE_NUMBERS))
			{
				costfunc = new TERnumcost();
			} else
			{
				costfunc = new TERcost();
			}
			costfunc._delete_cost = (Double) params.para().get(TERpara.OPTIONS.DELETE_COST);
			costfunc._insert_cost = (Double) params.para().get(TERpara.OPTIONS.INSERT_COST);
			costfunc._shift_cost = (Double) params.para().get(TERpara.OPTIONS.SHIFT_COST);
			costfunc._match_cost = (Double) params.para().get(TERpara.OPTIONS.MATCH_COST);
			costfunc._stem_cost = (Double) params.para().get(TERpara.OPTIONS.STEM_COST);
			costfunc._syn_cost = (Double) params.para().get(TERpara.OPTIONS.SYN_COST);
			costfunc._substitute_cost = (Double) params.para().get(TERpara.OPTIONS.SUBSTITUTE_COST);
		} else
		{
			costfunc = new TERWordClassCost((String) params.para().get(TERpara.OPTIONS.WORD_CLASS_FNAME),
					(Double) params.para().get(TERpara.OPTIONS.SUBSTITUTE_COST), (Double) params.para().get(
							TERpara.OPTIONS.MATCH_COST), (Double) params.para().get(TERpara.OPTIONS.INSERT_COST),
					(Double) params.para().get(TERpara.OPTIONS.DELETE_COST), (Double) params.para().get(
							TERpara.OPTIONS.SHIFT_COST), (Double) params.para().get(TERpara.OPTIONS.STEM_COST),
					(Double) params.para().get(TERpara.OPTIONS.SYN_COST));
		}
		String weight_file = (String) params.para().get(TERpara.OPTIONS.WEIGHT_FILE);
		if ((weight_file != null) && (!weight_file.equals("")))
		{
			costfunc.load_weights(weight_file);
		}
		return costfunc;
	}

	private TERcalcCN terCalcFactory(TERinput terinput, TERcost costfunc)
	{
		TERcalcCN calc = new TERcalcCN(costfunc, params);
		boolean use_phrases = (this.phrasedb != null);
		// set options to compute TER
		calc.loadShiftStopWordList((String) params.para().get(TERpara.OPTIONS.SHIFT_STOP_LIST));
		calc.setBeamWidth((Integer) params.para().get(TERpara.OPTIONS.BEAMWIDTH));
		calc.setShiftDist((Integer) params.para().get(TERpara.OPTIONS.SHIFTDIST));
		calc.setShiftCon((String) params.para().get(TERpara.OPTIONS.SHIFT_CONSTRAINT));
		calc.setUseStemming((Boolean) params.para().get(TERpara.OPTIONS.USE_PORTER));
		calc.setUseSynonyms((Boolean) params.para().get(TERpara.OPTIONS.USE_WORDNET));
		calc.setUsePhrases(use_phrases);
		// Load Phrase Table
		PhraseTable phrases = null;
		if (use_phrases)
		{
			phrases = new PhraseTable(this.phrasedb);
			phrases.set_sum_dup_phrases(params.para().get_boolean(TERpara.OPTIONS.SUM_DUP_PHRASES));
			phrases.set_adjust_func(params.para().get_string(TERpara.OPTIONS.ADJUST_PHRASETABLE_FUNC), params.para()
					.get_doublelist(TERpara.OPTIONS.ADJUST_PHRASETABLE_PARAMS), params.para().get_double(
							TERpara.OPTIONS.ADJUST_PHRASETABLE_MIN), params.para().get_double(
									TERpara.OPTIONS.ADJUST_PHRASETABLE_MAX), calc);
			loadPTdb(phrases, terinput, calc);
		}
		costfunc.setPhraseTable(phrases);
		String weight_file = params.para().get_string(TERpara.OPTIONS.WEIGHT_FILE);
		if ((weight_file != null) && (!weight_file.equals("")))
		{
			costfunc.load_weights(weight_file);
		}
		return calc;
	}

	private TERcalcCN init_tercalc(TERinput terinput)
	{
		TERcost costfunc = terCostFactory();
		TERcalcCN calc = terCalcFactory(terinput, costfunc);
		_costfunc = costfunc;
		return calc;
	}

	// it will be more flexible to verify the input formats later.
	private static boolean verifyFormats(int in_ref_format, int in_hyp_format, ArrayList<String> out_formats)
	{
		if (in_ref_format != in_hyp_format)
		{
			System.out.println("** Error: Both hypothesis and reference have to be in the SAME format");
			return false;
		} else if (in_ref_format == 1 && out_formats.indexOf("xml") > -1)
		{
			System.out.println("** Warning: XML ouput may not have correct doc id for Trans format inputs");
			return true;
		} else
			return true;
	}

	private TERalignment score_all_refs(String[] hyp, String[][] refs, TERcalc calc, TERalignment[] irefs,
			List<String> orighyps, List<String> origrefs)
	{
		TERalignment[] results = calc.TERall(hyp, refs);
		if ((results == null) || (results.length == 0))
		{
			System.err.println("Internal error in scoring in TERplus.  Aborting!");
			System.exit(-1);
		}
		int bestref = 0;
		double tot_words = 0.0;
		for (int i = 0; i < results.length; i++)
		{
			results[i].orig_hyp = orighyps.get(0);
			results[i].orig_ref = origrefs.get(i);
			tot_words += results[i].numWords;
			if (results[i].numEdits < results[bestref].numEdits)
				bestref = i;
			if (irefs != null)
				irefs[i] = results[i];
		}
		TERalignment bestresult = new TERalignment(results[bestref]);
		if (params.para().get_boolean(TERpara.OPTIONS.USE_AVE_LEN))
			bestresult.numWords = tot_words / ((double) results.length);
		return bestresult;
	}

	private TERalignmentCN incremental_build_cn(List<String[]> hyps, List<float[]> hyps_scores, String[] ref, float[] ref_scores, TERcalcCN calc, List<String> orighyps, List<String> origrefs)
	{
		//System.out.println("START incremental_build_cn");
		TERalignmentCN result = calc.TERcn(hyps, hyps_scores, ref, ref_scores);
		if ((result == null))
		{
			//System.err.println("ERROR incremental_build_cn : Internal error in scoring in TERplus.  Aborting!");
			logger.info("ERROR incremental_build_cn : Internal error in scoring in TERplus.  Aborting!");
			//System.exit(-1);
			return null;
		}
		result.orig_hyps = new String[orighyps.size()];
		for (int i = 0; i < result.hyps.size(); i++)
		{
			//result.orig_hyps[i] = orighyps.get(i);
			result.orig_ref = origrefs.get(0);
		}
		//System.out.println("END incremental_build_cn");
		
		return result;
	}

	public void showTime()
	{
		int maxlen = 1;
		int l = String.format("%d", numScored).length();
		if (l > maxlen)
			maxlen = l;
		l = String.format("%.3f", this.OverallElapsedSec).length();
		if (l > maxlen)
			maxlen = l;
		double workTime = this.OverallElapsedSec - (this.InitElapsedSec + this.OutputElapsedSec);
		String secstr = "%" + maxlen + ".3f sec\n";
		System.out.println("Timing Information (averages are per hypothesis segment scored)");
		System.out.printf("  Number of Segments:   %" + maxlen + "d\n", numScored);
		System.out.printf("  Total Elapsed Time:   " + secstr, OverallElapsedSec);
		System.out.printf("  Initialization Time:  " + secstr, InitElapsedSec);
		System.out.printf("  PT Init Time:         " + secstr, InitPTElapsedSec);
		System.out.printf("  Output Time:          " + secstr, OutputElapsedSec);
		System.out.printf("  Total Work Time:      " + secstr, workTime);
		if (this.numScored > 0)
		{
			System.out.printf("  Avg Elapsed Time:     " + secstr, OverallElapsedSec / (double) numScored);
			System.out.printf("  Avg Work Time:        " + secstr, workTime / (double) numScored);
			System.out.printf("  Avg PT Init Time:     " + secstr, InitPTElapsedSec / (double) numScored);
			PhraseTable pt = calc.getCostFunc().getPhraseTable();
			System.out.printf("  Avg PT Insert Time:   " + secstr, (pt == null
					? 0.0
					: (pt.getInsertTime() / (double) numScored)));
			System.out.printf("  Avg PT DB Fetch Time: " + secstr, (pt == null
					? 0.0
					: (pt.getDbFetchTime() / (double) numScored)));
			System.out.printf("  Avg PT Search Time:   " + secstr, (pt == null
					? 0.0
					: (pt.getSearchTime() / (double) numScored)));
			System.out.printf("  Avg TER Calc Time:    " + secstr, calc.getCalcTime() / (double) numScored);
		}
	}

	public static final String license = ("\n"
			+ "Copyright 2006-2008 by BBN Technologies and University of Maryland (UMD)\n\n"
			+ "BBN and UMD grant a nonexclusive, source code, royalty-free right to\n"
			+ "use this Software known as Translation Error Rate COMpute (the\n"
			+ "\"Software\") solely for research purposes. Provided, you must agree\n"
			+ "to abide by the license and terms stated herein. Title to the\n"
			+ "Software and its documentation and all applicable copyrights, trade\n"
			+ "secrets, patents and other intellectual rights in it are and remain\n"
			+ "with BBN and UMD and shall not be used, revealed, disclosed in\n"
			+ "marketing or advertisement or any other activity not explicitly\n" 
			+ "permitted in writing.\n\n"
			+ "BBN and UMD make no representation about suitability of this\n"
			+ "Software for any purposes.  It is provided \"AS IS\" without express\n"
			+ "or implied warranties including (but not limited to) all implied\n"
			+ "warranties of merchantability or fitness for a particular purpose.\n"
			+ "In no event shall BBN or UMD be liable for any special, indirect or\n"
			+ "consequential damages whatsoever resulting from loss of use, data or\n"
			+ "profits, whether in an action of contract, negligence or other\n"
			+ "tortuous action, arising out of or in connection with the use or\n"
			+ "performance of this Software.\n\n"
			+ "Without limitation of the foregoing, user agrees to commit no act\n"
			+ "which, directly or indirectly, would violate any U.S. law,\n"
			+ "regulation, or treaty, or any other international treaty or\n"
			+ "agreement to which the United States adheres or with which the\n"
			+ "United States complies, relating to the export or re-export of any\n"
			+ "commodities, software, or technical data.  This Software is licensed\n"
			+ "to you on the condition that upon completion you will cease to use\n"
			+ "the Software and, on request of BBN and UMD, will destroy copies of\n"
			+ "the Software in your possession.\n\n");

	public static void PrintLicense()
	{
		System.out.println(license);
	}

	//@Override
	public String getName()
	{
		return name;
	}

	@Override
	public void newProperties(PropertySheet ps) throws PropertyException
	{
		logger = ps.getLogger();
		//terpParams = ps.getString(PROP_TERP_PARAMS, PROP_TERP_PARAMS_DEFAULT);
	}

	static String[] usage_ar =
	{"Usage : ", "java -cp TERp.jar com.bbn.mt.terp.TERplus parameters.xml "};
	public static void usage()
	{
		System.err.println(TERutilities.join("\n", usage_ar));
	}
	
	public String getTerpParams()
	{
		return terpParams;
	}

	public void setTerpParams(String terpParams)
	{
		this.terpParams = terpParams;
	}

}
